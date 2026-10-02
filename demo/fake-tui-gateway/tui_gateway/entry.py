"""A stand-in for Hermes' ``tui_gateway.entry``, used only by the demo bridge.

The bridge runs slash commands by starting ``python -m tui_gateway.entry`` and speaking
newline-delimited JSON-RPC to it. In the demo there is no real Hermes, so this module answers the
four methods the bridge calls with a small, invented command list.

Protocol (see bridge/hermes_remote_bridge/slash.py):
  commands.catalog {}                  -> {"pairs": [[name, description, usage], ...], "skills": {...}}
  session.resume {session_id, lazy}    -> {"session_id": "..."}
  slash.exec {session_id, command}     -> {"type": "output", "output": "..."} | {"type": "send", ...}
  command.dispatch {session_id, name}  -> same shapes as slash.exec
  session.close {session_id}           -> {}
"""
from __future__ import annotations

import json
import sys
import uuid

# (name, description). The bridge parses an optional "(usage: /name <arg>)" suffix out of the
# description to learn the command's arguments, which is how the real catalog encodes them.
COMMANDS = [
    ("/status", "Show what Hermes is doing right now"),
    ("/tools", "List the tools this agent can use"),
    ("/memory", "Show the agent's memory directory"),
    ("/title", "Rename the current session (usage: /title <text>)"),
    ("/compress", "Summarise the conversation so far"),
    ("/approvals", "Show the current approval mode"),
]

SKILLS = {
    "/summarize": "Condense long text into a few bullet points",
    "/explain": "Explain a concept simply, with one example",
}


def catalog() -> dict:
    return {"pairs": [[n, d] for n, d in COMMANDS], "skills": SKILLS}


def exec_command(command: str) -> dict:
    name, _, arg = command.strip().lstrip("/").partition(" ")
    name, arg = name.lower(), arg.strip()
    if name == "title":
        return {"type": "output", "output": f"Session title set to {arg!r}." if arg
                else "Usage: /title <text>"}
    if name == "status":
        return {"type": "output", "output": "Demo backend: no run is active."}
    if name == "approvals":
        return {"type": "output", "output": "approvals.mode = manual"}
    if name in SKILLS:
        return {"type": "send", "message": f"{name} {arg}".strip(),
                "display": f"{name} {arg}".strip()}
    return {"type": "output", "output": f"Demo gateway: /{name} is not implemented."}


def handle(msg: dict) -> dict | None:
    method, params = msg.get("method"), msg.get("params") or {}
    if method == "commands.catalog":
        return catalog()
    if method == "session.resume":
        return {"session_id": params.get("session_id") or f"sess_{uuid.uuid4().hex[:8]}"}
    if method == "slash.exec":
        return exec_command(str(params.get("command", "")))
    if method == "command.dispatch":
        return exec_command(str(params.get("name", "")))
    if method == "session.close":
        return {}
    return None


def main() -> None:
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except ValueError:
            continue
        result = handle(msg)
        if result is not None and "id" in msg:
            print(json.dumps({"jsonrpc": "2.0", "id": msg["id"], "result": result}), flush=True)


if __name__ == "__main__":
    main()
