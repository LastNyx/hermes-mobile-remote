"""A fake Hermes API server for the Hermes Remote demo.

The real bridge talks to the Hermes API server on 127.0.0.1:8642. This module stands in for it
with scripted, synthetic data, so the demo can be recorded (and a new contributor can try the
app) without a real Hermes install, real API keys, or anyone's private conversations.

Only the endpoints the bridge actually calls are implemented. Everything it returns is invented:
no real session titles, hostnames, model names or prompts.

Run it through ``demo/run-demo.sh``; it picks the port and the API key from the environment.
"""
from __future__ import annotations

import json
import os
import re
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("DEMO_HERMES_PORT", "8642"))
API_KEY = os.environ.get("API_SERVER_KEY", "demo-key")
# Multiplies every pause in a scripted run. Raise it to record the demo at a readable pace
# (DEMO_SPEED=3), lower it to click through the app quickly.
SPEED = float(os.environ.get("DEMO_SPEED", "1"))

# Sentences the scripted assistant streams back, one SSE delta at a time.
REPLY = [
    "I checked the repository and the answer is in `bridge/hermes_remote_bridge/app.py`.\n\n",
    "The bridge never lets the phone talk to Hermes directly. ",
    "It holds the API key, authenticates each device with its own token, and only listens on ",
    "loopback, your Tailscale addresses, and the Wi-Fi address of a network you marked trusted.\n\n",
    "So the phone gets the agent, not the machine behind it.",
]

SESSIONS: list[dict] = []
MESSAGES: dict[str, list[dict]] = {}
RUNS: dict[str, dict] = {}
_lock = threading.Lock()


def _new_id() -> str:
    return uuid.uuid4().hex[:12]


def _session(sid: str, title: str, preview: str, source: str, count: int,
             model: str, pinned: bool = False) -> dict:
    return {
        "id": sid, "title": title, "preview": preview, "source": source,
        "message_count": count, "last_active": time.time() - 3600, "model": model,
        "pinned": pinned, "ended_at": time.time() - 3600,
    }


def seed() -> None:
    """Three synthetic sessions, so the Sessions tab has something honest to show."""
    demos = [
        _session(_new_id(), "Which networks may reach the bridge?",
                 "Loopback, Tailscale, and Wi-Fi you marked trusted. Nothing else.",
                 "cli", 8, "hermes-4-405b", pinned=True),
        _session(_new_id(), "Add a reasoning-effort picker to the app",
                 "Per-message reasoning effort, sent as model_options.reasoning_effort.",
                 "desktop", 24, "hermes-4-405b"),
        _session(_new_id(), "Explain SSE replay in the bridge",
                 "The bridge owns the upstream subscription and buffers every event.",
                 "telegram", 12, "hermes-3-405b"),
    ]
    for s in demos:
        SESSIONS.append(s)
        MESSAGES[s["id"]] = [
            {"id": _new_id(), "role": "user", "content": s["title"]},
            {"id": _new_id(), "role": "assistant", "content": s["preview"]},
        ]


def _json(obj, code: int = 200):
    body = json.dumps(obj).encode()
    return body, code


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    # Keep the demo output readable; the bridge is the only real client.
    def log_message(self, format, *args):  # noqa: A002
        if os.environ.get("DEMO_VERBOSE"):
            print("[mock-hermes]", format % args)

    # ---------------------------------------------------------------- helpers
    def _auth_ok(self) -> bool:
        return self.headers.get("Authorization", "") == f"Bearer {API_KEY}"

    def _send(self, body: bytes, code: int = 200, ctype: str = "application/json"):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_json(self, obj, code: int = 200):
        body, code = _json(obj, code)
        self._send(body, code)

    def _error(self, code: int, message: str):
        self._send_json({"error": {"code": "demo", "message": message}}, code)

    def _body(self) -> dict:
        length = int(self.headers.get("Content-Length") or 0)
        if not length:
            return {}
        try:
            return json.loads(self.rfile.read(length) or b"{}")
        except ValueError:
            return {}

    def _find(self, sid: str) -> dict | None:
        return next((s for s in SESSIONS if s["id"] == sid), None)

    # ---------------------------------------------------------------- GET
    def do_GET(self):  # noqa: N802
        if not self._auth_ok():
            return self._error(401, "bad demo key")
        path = self.path.split("?")[0]
        query = dict(re.findall(r"(\w+)=([^&]*)", self.path.partition("?")[2]))

        if path == "/health/detailed":
            # The app treats the agent as healthy only when this is exactly "ok".
            return self._send_json({
                "status": "ok", "version": "0.22.0-demo", "gateway_state": "running",
                "readiness": {"status": "ok", "checks": {
                    "gateway": {"status": "ok"}, "providers": {"status": "ok"}}},
            })

        if path == "/api/model/options":
            return self._send_json({
                "model": "hermes-4-405b", "provider": "demo",
                "providers": [{
                    "slug": "demo", "name": "Demo provider", "authenticated": True,
                    "is_current": True, "source": "api_key", "models": [
                        "hermes-4-405b", "hermes-4-70b", "hermes-3-405b"],
                    "featured_models": ["hermes-4-405b", "hermes-4-70b"],
                    "unavailable_models": [],
                    "capabilities": {"hermes-4-405b": {"reasoning": True}},
                }],
            })

        if path == "/api/sessions":
            limit = min(int(query.get("limit", 30)), 100)
            offset = int(query.get("offset", 0))
            with _lock:
                page = SESSIONS[offset:offset + limit]
                return self._send_json(
                    {"data": page, "has_more": offset + limit < len(SESSIONS), "total": len(SESSIONS)})

        m = re.fullmatch(r"/api/sessions/([\w.:-]+)", path)
        if m:
            s = self._find(m.group(1))
            return self._send_json({"session": s}) if s else self._error(404, "no such session")

        m = re.fullmatch(r"/api/sessions/([\w.:-]+)/messages", path)
        if m:
            sid = m.group(1)
            if not self._find(sid):
                return self._error(404, "no such session")
            with _lock:
                rows = MESSAGES.get(sid, [])
                limit = min(int(query.get("limit", 200)), 500)
                offset = int(query.get("offset", 0))
                if query.get("order") == "oldest":
                    rows = rows[offset:offset + limit]
                else:
                    rows = rows[::-1][:limit]
                return self._send_json({"data": rows, "has_more": False})

        m = re.fullmatch(r"/v1/runs/([\w.:-]+)", path)
        if m:
            run = RUNS.get(m.group(1))
            return self._send_json(run) if run else self._error(404, "unknown run")

        m = re.fullmatch(r"/v1/runs/([\w.:-]+)/events", path)
        if m:
            return self._stream_events(m.group(1))

        return self._error(404, f"demo backend has no {path}")

    # ---------------------------------------------------------------- writes
    def do_POST(self):  # noqa: N802
        if not self._auth_ok():
            return self._error(401, "bad demo key")
        path = self.path.split("?")[0]
        body = self._body()

        if path == "/api/sessions":
            sid = _new_id()
            title = body.get("title") or "New chat"
            with _lock:
                SESSIONS.insert(0, _session(sid, title, "", body.get("source", "cli"), 0,
                                             "hermes-4-405b"))
                MESSAGES[sid] = []
            # Hermes wraps a single session in {"session": {...}}; the app relies on that.
            return self._send_json({"session": self._find(sid)}, 201)

        m = re.fullmatch(r"/api/sessions/([\w.:-]+)/fork", path)
        if m:
            src = self._find(m.group(1))
            if not src:
                return self._error(404, "no such session")
            sid = _new_id()
            with _lock:
                SESSIONS.insert(0, _session(sid, f"{src['title']} (fork)", "", "cli", 0, src["model"]))
                MESSAGES[sid] = list(MESSAGES.get(src["id"], []))
            return self._send_json({"session": self._find(sid)}, 201)

        if path == "/v1/runs":
            sid = str(body.get("session_id") or "")
            if not self._find(sid):
                return self._error(404, "no such session")
            run_id = f"run_{_new_id()}"
            text = str(body.get("input") or "")
            RUNS[run_id] = {"run_id": run_id, "session_id": sid, "status": "running",
                            "created_at": time.time(), "model_options": body.get("model_options", {})}
            with _lock:
                MESSAGES.setdefault(sid, []).append({"id": _new_id(), "role": "user", "content": text})
                s = self._find(sid)
                if s:
                    s["message_count"] = len(MESSAGES[sid])
                    s["last_active"] = time.time()
                    if s["title"] in ("New chat", "", None):
                        s["title"] = text[:60]
                    s["preview"] = text[:120]
            threading.Thread(target=self._perform, args=(run_id, sid, text), daemon=True).start()
            return self._send_json({"run_id": run_id, "status": "running"}, 202)

        m = re.fullmatch(r"/v1/runs/([\w.:-]+)/(stop|steer|approval)", path)
        if m:
            run = RUNS.get(m.group(1))
            if not run:
                return self._error(404, "unknown run")
            if m.group(2) == "stop":
                run["status"] = "cancelled"
            return self._send_json(run)

        return self._error(404, f"demo backend has no {path}")

    def do_PATCH(self):  # noqa: N802
        if not self._auth_ok():
            return self._error(401, "bad demo key")
        m = re.fullmatch(r"/api/sessions/([\w.:-]+)", self.path.split("?")[0])
        if not m:
            return self._error(404, "not found")
        s = self._find(m.group(1))
        if not s:
            return self._error(404, "no such session")
        body = self._body()
        with _lock:
            for key in ("title", "pinned", "archived"):
                if key in body:
                    s[key] = body[key]
        return self._send_json({"session": s})

    def do_DELETE(self):  # noqa: N802
        if not self._auth_ok():
            return self._error(401, "bad demo key")
        m = re.fullmatch(r"/api/sessions/([\w.:-]+)", self.path.split("?")[0])
        if not m:
            return self._error(404, "not found")
        with _lock:
            before = len(SESSIONS)
            SESSIONS[:] = [s for s in SESSIONS if s["id"] != m.group(1)]
            MESSAGES.pop(m.group(1), None)
            if len(SESSIONS) == before:
                return self._error(404, "no such session")
        return self._send_json({"deleted": m.group(1)})

    # ---------------------------------------------------------------- the run
    def _script_for(self, text: str) -> list[dict]:
        """The event script for one run. A prompt mentioning a shell command asks approval first."""
        wants_shell = any(w in text.lower() for w in ("run ", "shell", "command", "delete", "install"))
        script: list[dict] = []
        if wants_shell:
            script.append({"event": "approval.request", "request_id": _new_id(),
                           "command": "git status --short",
                           "description": "Approval demo: run a command on the PC",
                           "choices": ["once", "session", "always", "deny"]})
            script.append({"event": "approval.responded", "choice": "once"})
        script.append({"event": "tool.started", "tool": "read_file",
                       "preview": "bridge/hermes_remote_bridge/app.py"})
        script.append({"event": "tool.completed", "tool": "read_file",
                       "preview": "619 lines", "duration": 0.21})
        for chunk in REPLY:
            script.append({"event": "message.delta", "delta": chunk})
        script.append({"event": "run.completed", "output": "".join(REPLY)})
        return script

    def _perform(self, run_id: str, sid: str, text: str) -> None:
        """Push the script into the run's queue; the SSE handler drains it."""
        for frame in self._script_for(text):
            time.sleep((0.45 if frame["event"] == "message.delta" else 0.7) * SPEED)
            RUNS[run_id].setdefault("events", []).append(frame)
            if frame["event"] == "run.completed":
                RUNS[run_id]["status"] = "completed"
                with _lock:
                    MESSAGES.setdefault(sid, []).append(
                        {"id": _new_id(), "role": "assistant", "content": frame["output"]})
                    s = self._find(sid)
                    if s:
                        s["message_count"] = len(MESSAGES[sid])
                break

    def _stream_events(self, run_id: str):
        run = RUNS.get(run_id)
        if not run:
            return self._error(404, "unknown run")
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        sent = 0
        deadline = time.time() + 120
        try:
            while time.time() < deadline:
                for frame in list(run.get("events", []))[sent:]:
                    # The event name travels inside the JSON body; that is what the bridge reads.
                    self.wfile.write(f"data: {json.dumps(frame)}\n\n".encode())
                    sent += 1
                    if frame["event"].startswith("run."):
                        self.wfile.flush()
                        self.close_connection = True
                        return
                self.wfile.write(b": keepalive\n\n")
                self.wfile.flush()
                time.sleep(0.25 * SPEED)
        except (BrokenPipeError, ConnectionResetError):
            self.close_connection = True


def main() -> None:
    seed()
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"mock Hermes API on http://127.0.0.1:{PORT} ({len(SESSIONS)} synthetic sessions)", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
