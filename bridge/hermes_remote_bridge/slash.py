"""Slash commands, run the way the Hermes desktop app runs them.

Hermes executes slash commands in its TUI gateway (``python -m tui_gateway.entry``, newline-delimited
JSON-RPC over stdio), not in the HTTP API server the rest of the bridge uses. The bridge keeps one
such child process, resumes the target session in it, runs the command, and closes it again.

Only commands that act on persisted state are offered. Anything that needs a live agent in that
process (/retry, /btw, /goal, /usage, ...) would run against an empty agent there and report
misleading results, so those stay out. Skills are always offered: they expand to a prompt that the
app sends as a normal run.
"""
from __future__ import annotations

import asyncio
import contextlib
import json
import os
import time
from pathlib import Path

# Commands that read or change persisted state and so behave the same from any surface.
OUTPUT_COMMANDS = frozenset({
    "title", "compress", "status", "version", "profile", "whoami", "approvals", "tools", "memory",
    "bundles", "insights", "help", "diff", "reload-skills", "reload-mcp", "suggestions",
})
# Commands that expand into a prompt the app sends as a run.
PROMPT_COMMANDS = frozenset({"plan", "learn", "init"})
OFFERED = OUTPUT_COMMANDS | PROMPT_COMMANDS
# The app handles these itself: they map onto its own controls.
APP_COMMANDS = (
    ("new", "Start a new chat", ""),
    ("model", "Choose the model for your next message", "[name]"),
    ("reasoning", "Set reasoning effort for this chat", "[off|low|medium|high|xhigh|max|default]"),
    ("stop", "Stop the running reply", ""),
)

IDLE_SECONDS = 600
CATALOG_TTL = 300


class SlashError(Exception):
    def __init__(self, status: int, code: str, message: str):
        super().__init__(message)
        self.status, self.code, self.message = status, code, message


class TuiGateway:
    """One lazily started ``tui_gateway`` child, used by one request at a time."""

    def __init__(self, hermes_root: Path, python: Path):
        self._root, self._python = hermes_root, python
        self._proc: asyncio.subprocess.Process | None = None
        self._lock = asyncio.Lock()
        self._next_id = 0
        self._last_used = 0.0
        self._catalog: tuple[float, dict] | None = None

    async def _start(self) -> asyncio.subprocess.Process:
        if self._proc and self._proc.returncode is None:
            return self._proc
        if not self._python.exists():
            raise SlashError(503, "commands_unavailable", f"Hermes Python not found at {self._python}")
        env = {**os.environ, "PYTHONPATH": str(self._root), "HERMES_PYTHON_SRC_ROOT": str(self._root)}
        self._proc = await asyncio.create_subprocess_exec(
            str(self._python), "-m", "tui_gateway.entry", cwd=str(self._root), env=env,
            stdin=asyncio.subprocess.PIPE, stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.DEVNULL, limit=32 * 1024 * 1024)
        return self._proc

    async def _call(self, method: str, params: dict, timeout: float = 90) -> dict:
        proc = await self._start()
        assert proc.stdin and proc.stdout
        self._next_id += 1
        rid = self._next_id
        proc.stdin.write((json.dumps({"jsonrpc": "2.0", "id": rid, "method": method, "params": params}) + "\n").encode())
        await proc.stdin.drain()

        async def response() -> dict:
            while True:
                line = await proc.stdout.readline()
                if not line:
                    raise SlashError(502, "commands_unavailable", "Hermes command process exited")
                with contextlib.suppress(ValueError):
                    msg = json.loads(line)
                    if msg.get("id") == rid:  # anything else is an event notification
                        return msg

        try:
            msg = await asyncio.wait_for(response(), timeout)
        except asyncio.TimeoutError:
            await self.close()
            raise SlashError(504, "command_timeout", f"Hermes did not answer {method} in time")
        self._last_used = time.monotonic()
        if "error" in msg:
            err = msg["error"] or {}
            raise SlashError(400, "command_failed", str(err.get("message") or err))
        return msg.get("result") or {}

    async def close(self) -> None:
        proc, self._proc = self._proc, None
        if proc and proc.returncode is None:
            proc.terminate()
            with contextlib.suppress(asyncio.TimeoutError, ProcessLookupError):
                await asyncio.wait_for(proc.wait(), 5)

    async def close_if_idle(self) -> None:
        if self._proc and not self._lock.locked() and time.monotonic() - self._last_used > IDLE_SECONDS:
            await self.close()

    async def catalog(self) -> list[dict]:
        if self._catalog and time.monotonic() - self._catalog[0] < CATALOG_TTL:
            return self._catalog[1]["commands"]
        async with self._lock:
            raw = await self._call("commands.catalog", {})
        result = {"commands": build_catalog(raw)}
        self._catalog = (time.monotonic(), result)
        return result["commands"]

    async def run(self, session_id: str, command: str) -> dict:
        name, _, arg = command.strip().lstrip("/").partition(" ")
        name, arg = name.lower(), arg.strip()
        skills = {c["name"] for c in await self.catalog() if c["kind"] == "skill"}
        if name not in OFFERED and name not in skills:
            raise SlashError(400, "command_not_available", f"/{name} isn't available in the app")
        async with self._lock:
            resumed = await self._call("session.resume", {"session_id": session_id, "omit_messages": True, "lazy": True})
            runtime_id = resumed["session_id"]
            try:
                if name in skills:
                    result = await self._call("command.dispatch", {"session_id": runtime_id, "name": name, "arg": arg})
                else:
                    result = await self._call("slash.exec", {"session_id": runtime_id, "command": f"{name} {arg}".strip()})
            finally:
                with contextlib.suppress(SlashError):
                    await self._call("session.close", {"session_id": runtime_id}, timeout=15)
        return to_reply(name, arg, result)


def build_catalog(raw: dict) -> list[dict]:
    """App-facing command list: offered built-ins, the app's own commands, then skills."""
    described = dict(raw.get("pairs") or [])
    out = [{"name": n, "description": d, "args": a, "kind": "app"} for n, d, a in APP_COMMANDS]
    for name in sorted(OFFERED):
        desc = described.get(f"/{name}")
        if desc:
            text, _, usage = desc.partition(" (usage: ")
            args = usage.rstrip(")").removeprefix(f"/{name}").strip()
            out.append({"name": name, "description": text, "args": args,
                        "kind": "prompt" if name in PROMPT_COMMANDS else "output"})
    for key in sorted(raw.get("skills") or {}):
        name = key.lstrip("/")
        out.append({"name": name, "description": described.get(key, "Skill"), "args": "[instruction]",
                    "kind": "skill"})
    return out


def to_reply(name: str, arg: str, result: dict) -> dict:
    """``{"type": "output", "text"}`` to show, or ``{"type": "send", "message", "display"}`` to run."""
    kind = result.get("type")
    if kind in ("send", "skill"):
        message = (result.get("message") or "").strip()
        if not message:
            raise SlashError(400, "command_failed", f"/{name} produced nothing to send")
        display = (result.get("display") or f"/{name} {arg}").strip()
        return {"type": "send", "message": message, "display": display, "notice": result.get("notice") or ""}
    if kind == "prefill":
        return {"type": "output", "text": result.get("message") or ""}
    text = result.get("output") or result.get("notice") or "(no output)"
    if result.get("warning"):
        text = f"warning: {result['warning']}\n{text}"
    return {"type": "output", "text": text.strip("\n")}
