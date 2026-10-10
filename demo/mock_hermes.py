"""A fake `hermes serve` for the Hermes Remote demo.

The real bridge relays the phone to the desktop's own Hermes backend: a JSON-RPC WebSocket on
`/api/ws` plus a little REST (`/api/sessions`, `/api/status`). This module stands in for that
backend with scripted, synthetic data, so the demo can be recorded (and a new contributor can try
the app) without a real Hermes install, real API keys, or anyone's private conversations.

Only what the bridge and the app actually use is implemented, in the shapes real Hermes returns.
Everything it says is invented: no real session titles, hostnames, model names or prompts.

It registers itself in a spawn ledger (`$DEMO_HERMES_HOME/spawn-ledger.json`) exactly like
`hermes serve` does, which is how the bridge finds it. Run it through `demo/run-demo.sh`.
"""
from __future__ import annotations

import asyncio
import json
import os
import secrets
import time
import uuid
from pathlib import Path

import uvicorn
from fastapi import FastAPI, HTTPException, Request, WebSocket, WebSocketDisconnect
from fastapi.responses import HTMLResponse

PORT = int(os.environ.get("DEMO_HERMES_PORT", "8766"))
HOME = Path(os.environ.get("DEMO_HERMES_HOME", Path(__file__).parent / ".state" / "hermes-home"))
TOKEN = secrets.token_urlsafe(24)
# Multiplies every pause in a scripted turn. Raise it to record the demo at a readable pace
# (DEMO_SPEED=3), lower it to click through the app quickly.
SPEED = float(os.environ.get("DEMO_SPEED", "1"))
MODEL, PROVIDER = "hermes-4-405b", "demo"

# The scripted assistant's reply, streamed a few words at a time.
REPLY = (
    "I checked the repository and the answer is in `bridge/hermes_remote_bridge/relay.py`.\n\n"
    "The bridge never lets the phone talk to Hermes directly. It checks each device's own token, "
    "only listens on loopback, your Tailscale addresses and the Wi-Fi of a network you marked "
    "trusted, and passes through only the calls the desktop app itself makes.\n\n"
    "So the phone gets the agent, not the machine behind it."
)

SLASH = [
    {"text": "/status", "display": "/status", "meta": "Show session status"},
    {"text": "/title", "display": "/title", "meta": "Set the session title"},
    {"text": "/retry", "display": "/retry", "meta": "Resend the last message"},
    {"text": "/undo", "display": "/undo", "meta": "Remove the last exchange"},
    {"text": "/btw", "display": "/btw", "meta": "Ask a side question"},
    {"text": "/compress", "display": "/compress", "meta": "Summarise older turns"},
    {"text": "/plan", "display": "/plan", "meta": "Write a plan before acting", "kind": "skill"},
]

SESSIONS: list[dict] = []
MESSAGES: dict[str, list[dict]] = {}
RUNTIME: dict[str, str] = {}        # runtime id -> stored session id
TURNS: dict[str, asyncio.Task] = {}  # runtime id -> the turn in flight
WAITERS: dict[str, asyncio.Future] = {}
CLIENTS: set["Client"] = set()
_row = iter(range(1, 1_000_000))
_seq = iter(range(1, 1_000_000))


def _session(title: str, preview: str, source: str, pinned: bool = False) -> dict:
    sid = time.strftime("%Y%m%d_%H%M%S_") + uuid.uuid4().hex[:6]
    row = {"id": sid, "title": title, "preview": preview, "source": source, "message_count": 2,
           "last_active": time.time() - 3600, "model": MODEL, "pinned": pinned}
    SESSIONS.append(row)
    MESSAGES[sid] = [{"row_id": next(_row), "role": "user", "text": title},
                     {"row_id": next(_row), "role": "assistant", "text": preview}]
    return row


def seed() -> None:
    """Three synthetic sessions, so the session list has something honest to show."""
    _session("Which networks may reach the bridge?",
             "Loopback, Tailscale, and Wi-Fi you marked trusted. Nothing else.", "cli", pinned=True)
    _session("Add a reasoning-effort picker to the app",
             "Per-message reasoning effort, set on the session with config.set.", "desktop")
    _session("Explain how the phone shares the desktop's socket",
             "The bridge relays the phone to the same backend the desktop uses.", "telegram")


def _find(sid: str) -> dict | None:
    return next((s for s in SESSIONS if s["id"] == sid), None)


class Client:
    def __init__(self, ws: WebSocket):
        self.ws = ws
        self.lock = asyncio.Lock()

    async def send(self, frame: dict) -> None:
        async with self.lock:
            try:
                await self.ws.send_text(json.dumps(frame))
            except Exception:
                CLIENTS.discard(self)


async def broadcast(frame: dict) -> None:
    for c in list(CLIENTS):
        await c.send(frame)


async def emit(rid: str, type_: str, payload: dict | None = None) -> None:
    await broadcast({"jsonrpc": "2.0", "method": "event",
                     "params": {"type": type_, "session_id": rid, "seq": next(_seq), "payload": payload or {}}})


async def sessions_changed() -> None:
    await broadcast({"jsonrpc": "2.0", "method": "event",
                     "params": {"type": "sessions.changed", "session_id": "", "payload": {}}})


async def pause(seconds: float) -> None:
    await asyncio.sleep(seconds * SPEED)


async def run_turn(rid: str, text: str) -> None:
    """One scripted turn. A prompt mentioning a shell command asks for approval first."""
    sid = RUNTIME[rid]
    s = _find(sid)
    MESSAGES[sid].append({"row_id": next(_row), "role": "user", "text": text})
    if s:
        s.update(message_count=len(MESSAGES[sid]), last_active=time.time(), preview=text[:120])
        if not s.get("title"):
            s["title"] = text[:60]
            await emit(rid, "session.title", {"title": s["title"]})
    await sessions_changed()
    status = "complete"
    try:
        await emit(rid, "message.start")
        await pause(0.7)
        if any(w in text.lower() for w in ("run ", "shell", "command", "delete", "install")):
            req_id = f"srq-{uuid.uuid4().hex[:12]}"
            fut = asyncio.get_running_loop().create_future()
            WAITERS[req_id] = fut
            await broadcast({"jsonrpc": "2.0", "id": req_id, "method": "approval", "params": {
                "session_id": rid, "command": "git status --short",
                "description": "Approval demo: run a command on the PC",
                "choices": ["once", "session", "always", "deny"]}})
            choice = (await fut).get("choice", "deny")
            await emit(rid, "request.cancel", {"id": req_id, "reason": "answered"})
            if choice == "deny":
                await emit(rid, "message.complete", {"text": "Okay, I won't run it.", "status": "complete"})
                MESSAGES[sid].append({"row_id": next(_row), "role": "assistant", "text": "Okay, I won't run it."})
                return
            await emit(rid, "tool.start", {"tool_id": "t-git", "name": "terminal", "context": "git status --short"})
            await pause(0.6)
            await emit(rid, "tool.complete", {"tool_id": "t-git", "name": "terminal", "duration_s": 0.12,
                                              "result": "M  bridge/hermes_remote_bridge/relay.py"})
            MESSAGES[sid].append({"row_id": next(_row), "role": "tool", "name": "terminal",
                                  "context": "git status --short", "tool_call_id": "t-git"})
        await emit(rid, "tool.start", {"tool_id": "t-read", "name": "read_file",
                                       "context": "bridge/hermes_remote_bridge/relay.py"})
        await pause(0.7)
        await emit(rid, "tool.complete", {"tool_id": "t-read", "name": "read_file", "duration_s": 0.21,
                                          "result": "178 lines"})
        MESSAGES[sid].append({"row_id": next(_row), "role": "tool", "name": "read_file",
                              "context": "bridge/hermes_remote_bridge/relay.py", "tool_call_id": "t-read"})
        words = REPLY.split(" ")
        for i in range(0, len(words), 3):
            await emit(rid, "message.delta", {"text": " ".join(words[i:i + 3]) + " "})
            await pause(0.12)
        await emit(rid, "message.complete", {"text": REPLY, "status": "complete"})
        MESSAGES[sid].append({"row_id": next(_row), "role": "assistant", "text": REPLY})
    except asyncio.CancelledError:
        status = "interrupted"
        await emit(rid, "message.complete", {"text": "", "status": "interrupted"})
    finally:
        TURNS.pop(rid, None)
        if s:
            s.update(message_count=len(MESSAGES[sid]), last_active=time.time())
        await sessions_changed()
        _ = status


def _attach(sid: str) -> str:
    rid = next((r for r, s in RUNTIME.items() if s == sid), None) or f"rt{uuid.uuid4().hex[:8]}"
    RUNTIME[rid] = sid
    return rid


def _info(sid: str, rid: str) -> dict:
    s = _find(sid) or {}
    return {"title": s.get("title") or "", "model": MODEL, "provider": PROVIDER,
            "reasoning_effort": "medium", "running": rid in TURNS}


class RpcError(Exception):
    def __init__(self, code: int, message: str):
        super().__init__(message)
        self.code = code


def _rid(params: dict) -> str:
    rid = str(params.get("session_id") or "")
    if rid not in RUNTIME:
        raise RpcError(4001, "session not found")
    return rid


async def call(method: str, p: dict) -> dict:
    if method in ("client.capabilities", "ping", "config.set", "session.close", "session.steer"):
        return {"ok": True}
    if method == "session.create":
        row = {"id": time.strftime("%Y%m%d_%H%M%S_") + uuid.uuid4().hex[:6], "title": "", "preview": "",
               "source": p.get("source") or "desktop", "message_count": 0, "last_active": time.time(),
               "model": MODEL, "pinned": False}
        SESSIONS.insert(0, row)
        MESSAGES[row["id"]] = []
        rid = _attach(row["id"])
        return {"session_id": rid, "stored_session_id": row["id"], "info": _info(row["id"], rid)}
    if method == "session.resume":
        sid = str(p.get("session_id") or "")
        if not _find(sid):
            raise RpcError(4001, "session not found")
        rid = _attach(sid)
        return {"session_id": rid, "messages": MESSAGES[sid], "info": _info(sid, rid),
                "running": rid in TURNS, "open_requests": []}
    if method == "session.active_list":
        return {"sessions": [{"session_key": RUNTIME[r], "status": "working"} for r in TURNS]}
    if method == "prompt.submit":
        rid = _rid(p)
        if rid in TURNS:
            raise RpcError(4009, "session busy")
        TURNS[rid] = asyncio.create_task(run_turn(rid, str(p.get("text") or "")))
        return {"status": "streaming"}
    if method == "session.interrupt":
        task = TURNS.get(_rid(p))
        if task:
            task.cancel()
        return {"ok": True}
    if method == "session.title":
        s = _find(RUNTIME[_rid(p)])
        if p.get("title"):
            s["title"] = str(p["title"])
            await sessions_changed()
        return {"title": s.get("title") or ""}
    if method == "session.delete":
        sid = str(p.get("session_id") or "")
        SESSIONS[:] = [s for s in SESSIONS if s["id"] != sid]
        MESSAGES.pop(sid, None)
        await sessions_changed()
        return {"deleted": sid}
    if method == "session.status":
        sid = RUNTIME[_rid(p)]
        return {"output": f"Session {sid}\nModel: {MODEL} ({PROVIDER})\nMessages: {len(MESSAGES[sid])}"}
    if method == "model.options":
        return {"model": MODEL, "provider": PROVIDER, "providers": [{
            "slug": PROVIDER, "name": "Demo provider", "authenticated": True, "is_current": True,
            "models": ["hermes-4-405b", "hermes-4-70b", "hermes-3-405b"],
            "featured_models": ["hermes-4-405b", "hermes-4-70b"], "unavailable_models": []}]}
    if method == "complete.slash":
        text = str(p.get("text") or "").lower()
        return {"items": [i for i in SLASH if i["text"].startswith(text.split(" ")[0])]}
    if method in ("slash.exec", "command.dispatch"):
        return {"type": "exec", "output": "This is the demo backend: commands answer with canned text."}
    raise RpcError(-32601, f"demo backend has no {method}")


app = FastAPI()


def _check(request: Request) -> None:
    if request.headers.get("X-Hermes-Session-Token") != TOKEN:
        raise HTTPException(401, "bad demo token")


@app.get("/", response_class=HTMLResponse)
async def index():
    # Real Hermes injects its session token into the page it serves the desktop renderer.
    return f'<html><script>window.__HERMES_SESSION_TOKEN__="{TOKEN}";</script></html>'


@app.get("/api/status")
async def status(request: Request):
    _check(request)
    return {"version": "0.22.0-demo"}


@app.get("/api/sessions")
async def sessions(request: Request, limit: int = 40, offset: int = 0):
    _check(request)
    rows = sorted(SESSIONS, key=lambda s: s["last_active"], reverse=True)
    return {"sessions": rows[offset:offset + limit], "total": len(rows), "limit": limit, "offset": offset}


@app.patch("/api/sessions/{sid}")
async def patch(sid: str, request: Request):
    _check(request)
    s = _find(sid)
    if not s:
        raise HTTPException(404, "no such session")
    body = await request.json()
    for key in ("title", "pinned", "archived"):
        if key in body:
            s[key] = body[key]
    await sessions_changed()
    return {"session": s}


@app.websocket("/api/ws")
async def ws(socket: WebSocket):
    if socket.query_params.get("token") != TOKEN:
        await socket.close(code=4401)
        return
    await socket.accept()
    client = Client(socket)
    CLIENTS.add(client)
    try:
        while True:
            frame = json.loads(await socket.receive_text())
            fid = frame.get("id")
            if "method" not in frame:  # the answer to one of our questions
                fut = WAITERS.pop(str(fid), None)
                if fut and not fut.done():
                    fut.set_result(frame.get("result") or {})
                continue
            try:
                result = await call(frame["method"], frame.get("params") or {})
                await client.send({"jsonrpc": "2.0", "id": fid, "result": result})
            except RpcError as e:
                await client.send({"jsonrpc": "2.0", "id": fid, "error": {"code": e.code, "message": str(e)}})
    except WebSocketDisconnect:
        pass
    finally:
        CLIENTS.discard(client)


def register() -> None:
    """Announce this backend the way `hermes serve` does, so the bridge attaches to it."""
    HOME.mkdir(parents=True, exist_ok=True)
    (HOME / "spawn-ledger.json").write_text(json.dumps([{
        "purpose": "serve", "host": "127.0.0.1", "port": PORT, "pid": os.getpid(),
        "registered_at": time.time()}]))


def main() -> None:
    seed()
    register()
    print(f"mock hermes serve on http://127.0.0.1:{PORT} ({len(SESSIONS)} synthetic sessions)", flush=True)
    uvicorn.run(app, host="127.0.0.1", port=PORT, log_level="warning")


if __name__ == "__main__":
    main()
