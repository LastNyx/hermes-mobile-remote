"""Live end-to-end check against a running bridge + real Hermes.

Usage: uv run python tests/e2e_live.py <creds.json> [base_url]
Run it on the machine that hosts the bridge: one check compares the agent's `uname -r`
answer with the local kernel release. Spends a few cloud-model calls and creates and
deletes its own session.
"""
from __future__ import annotations

import json
import platform
import sys
import time
import uuid

import httpx

creds = json.load(open(sys.argv[1]))
base = sys.argv[2] if len(sys.argv) > 2 else creds["url"]
H = {"Authorization": f"Bearer {creds['token']}"}
c = httpx.Client(base_url=base, headers=H, timeout=30)
results: list[tuple[str, bool, str]] = []


def check(name: str, ok: bool, detail: str = "") -> None:
    results.append((name, ok, detail))
    print(f"[{'PASS' if ok else 'FAIL'}] {name} {detail}")


def events(run_id: str, after: int = 0, limit_s: float = 120, stop_after: int | None = None):
    out = []
    with c.stream("GET", f"/v1/runs/{run_id}/events", params={"after": after}, timeout=httpx.Timeout(limit_s)) as r:
        ev = {}
        for line in r.iter_lines():
            if line.startswith("id: "):
                ev["id"] = int(line[4:])
            elif line.startswith("event: "):
                ev["event"] = line[7:]
            elif line.startswith("data: "):
                ev["data"] = json.loads(line[6:])
            elif line == "" and ev:
                out.append(ev)
                ev = {}
                if stop_after and len(out) >= stop_after:
                    break
    return out


# auth
check("no token -> 401", httpx.get(f"{base}/v1/me").status_code == 401)
check("bad token -> 401", httpx.get(f"{base}/v1/me", headers={"Authorization": "Bearer hrb_x"}).status_code == 401)
me = c.get("/v1/me").json()
check("me", me["device"]["name"] == creds["device"], f"peer={me['peer_node']}")

st = c.get("/v1/status").json()["components"]
check("status components", {"bridge", "hermes", "model", "desktop", "tailscale"} <= st.keys(),
      f"hermes={st['hermes']['status']} desktop={st['desktop']['status']} model={st['model']}")
check("desktop info", c.get("/v1/desktop").json()["port"] == 3389)

sess = c.post("/v1/sessions", json={"title": "bridge-e2e"}).json()["session"]
sid = sess["id"]
check("create session", sid.startswith("api_"), sid)

# run with tool call; detach mid-run, reattach with Last-Event-ID semantics
req_id = uuid.uuid4().hex
body = {"session_id": sid, "client_request_id": req_id,
        "input": "Use the terminal tool to run: sleep 5; uname -r . Then reply with only the kernel version."}
run = c.post("/v1/runs", json=body).json()
rid = run["run_id"]
check("start run", run["status"] in ("running", "started"), rid)
dup = c.post("/v1/runs", json=body).json()
check("duplicate send dedup", dup["run_id"] == rid and dup["replayed"] is True)
busy = c.post("/v1/runs", json={**body, "client_request_id": uuid.uuid4().hex})
check("second run on busy session -> 409", busy.status_code == 409)
listed = c.get("/v1/runs", params={"session_id": sid, "active": True}).json()["data"]
check("active run listed", [r["run_id"] for r in listed] == [rid])

first = events(rid, 0, stop_after=1)
cut = first[-1]["id"]
time.sleep(8)  # client "offline" while the tool runs
rest = events(rid, cut)
names = [e["event"] for e in first + rest]
ids = [e["id"] for e in first + rest]
check("no gaps / no dupes across reconnect", ids == list(range(1, len(ids) + 1)), f"{len(ids)} events")
check("tool events present", "tool.started" in names and "tool.completed" in names)
check("terminal event", names[-1] == "run.completed", names[-1])
final = rest[-1]["data"].get("output", "")
check("answer correct", platform.release() in final, repr(final[:60]))
again = events(rid, 0)
check("full replay after completion", [e["id"] for e in again] == ids)

hist = c.get(f"/v1/sessions/{sid}/messages").json()["data"]
check("history persisted", [m["role"] for m in hist][:1] == ["user"] and hist[-1]["role"] == "assistant",
      f"{len(hist)} msgs")
detail = c.get(f"/v1/sessions/{sid}").json()
check("session has no active run", detail["active_run"] is None)

# stop
run2 = c.post("/v1/runs", json={"session_id": sid, "client_request_id": uuid.uuid4().hex,
                                "input": "Use the terminal tool to run: sleep 60; echo done"}).json()
time.sleep(6)
c.post(f"/v1/runs/{run2['run_id']}/stop")
tail = events(run2["run_id"], 0, limit_s=40)
check("stop -> cancelled", tail[-1]["event"] == "run.cancelled", tail[-1]["event"])

# approval endpoint guarded when nothing pending
na = c.post(f"/v1/runs/{rid}/approval", json={"choice": "once"})
check("approval without pending -> 409", na.status_code == 409)

# destructive op confirmation
check("delete without confirm -> 428", c.delete(f"/v1/sessions/{sid}").status_code == 428)
check("delete with confirm", c.delete(f"/v1/sessions/{sid}", params={"confirm": sid}).status_code == 200)

failed = [n for n, ok, _ in results if not ok]
print(f"\n{len(results) - len(failed)}/{len(results)} passed" + (f"; FAILED: {failed}" if failed else ""))
sys.exit(1 if failed else 0)
