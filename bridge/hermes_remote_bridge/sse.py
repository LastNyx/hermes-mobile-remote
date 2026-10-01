"""Minimal SSE parsing/encoding (WHATWG event-stream subset used by Hermes)."""
from __future__ import annotations

import json
from typing import AsyncIterator


async def iter_sse(lines: AsyncIterator[str]) -> AsyncIterator[tuple[str | None, str]]:
    """Yield (event_name, data) per dispatched event; comments/keepalives are skipped."""
    event: str | None = None
    data: list[str] = []
    async for line in lines:
        if line == "":
            if data:
                yield event, "\n".join(data)
            event, data = None, []
            continue
        if line.startswith(":"):
            continue
        name, _, value = line.partition(":")
        value = value[1:] if value.startswith(" ") else value
        if name == "event":
            event = value
        elif name == "data":
            data.append(value)
    if data:
        yield event, "\n".join(data)


def encode(seq: int, name: str, payload: dict) -> bytes:
    return f"id: {seq}\nevent: {name}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n".encode()


KEEPALIVE = b": keepalive\n\n"
