"""Bridge-owned run tracking.

Hermes' /v1/runs/{id}/events is single-consumer with no replay: if a subscriber drops, the
transport is discarded and missed events are gone. The bridge therefore owns that subscription
for the run's entire life, buffers every event with a monotonically increasing sequence number,
and lets any number of clients attach/re-attach with Last-Event-ID.
"""
from __future__ import annotations

import asyncio
import json
import logging
import time
from collections import deque
from dataclasses import dataclass, field
from typing import AsyncIterator, Awaitable, Callable

from . import sse

log = logging.getLogger("hermes_remote_bridge.runs")

TERMINAL_EVENTS = {"run.completed", "run.failed", "run.cancelled", "run.interrupted"}
TERMINAL_STATUSES = {"completed", "failed", "cancelled", "interrupted"}


@dataclass
class TrackedRun:
    run_id: str
    session_id: str | None
    device_id: str
    created_at: float = field(default_factory=time.time)
    status: str = "running"
    events: deque = field(default_factory=deque)  # (seq, name, payload)
    next_seq: int = 1
    dropped_through: int = 0  # highest seq evicted from the buffer
    pending_approval: dict | None = None
    finished_at: float | None = None
    pump_task: asyncio.Task | None = None
    cond: asyncio.Condition = field(default_factory=asyncio.Condition)

    @property
    def terminal(self) -> bool:
        return self.status in TERMINAL_STATUSES

    def snapshot(self) -> dict:
        return {
            "run_id": self.run_id,
            "session_id": self.session_id,
            "status": self.status,
            "created_at": self.created_at,
            "finished_at": self.finished_at,
            "last_seq": self.next_seq - 1,
            "pending_approval": self.pending_approval,
        }


# Upstream callables are injected so the manager is testable without a live Hermes.
OpenEvents = Callable[[str], Awaitable[AsyncIterator[str]]]
FetchStatus = Callable[[str], Awaitable[dict | None]]


class RunManager:
    def __init__(self, open_events: OpenEvents, fetch_status: FetchStatus, *,
                 max_events: int = 20_000, retention_seconds: int = 1800):
        self._open_events = open_events
        self._fetch_status = fetch_status
        self._max_events = max_events
        self._retention = retention_seconds
        self.runs: dict[str, TrackedRun] = {}

    def get(self, run_id: str) -> TrackedRun | None:
        return self.runs.get(run_id)

    def active_for_session(self, session_id: str) -> TrackedRun | None:
        for run in self.runs.values():
            if run.session_id == session_id and not run.terminal:
                return run
        return None

    def list(self, session_id: str | None = None, active_only: bool = False) -> list[TrackedRun]:
        runs = [r for r in self.runs.values()
                if (session_id is None or r.session_id == session_id) and not (active_only and r.terminal)]
        return sorted(runs, key=lambda r: r.created_at, reverse=True)

    def track(self, run_id: str, session_id: str | None, device_id: str) -> TrackedRun:
        existing = self.runs.get(run_id)
        if existing is not None:
            return existing
        run = TrackedRun(run_id=run_id, session_id=session_id, device_id=device_id)
        self.runs[run_id] = run
        run.pump_task = asyncio.create_task(self._pump(run), name=f"pump-{run_id}")
        return run

    async def _append(self, run: TrackedRun, name: str, payload: dict) -> None:
        async with run.cond:
            run.events.append((run.next_seq, name, payload))
            run.next_seq += 1
            while len(run.events) > self._max_events:
                run.dropped_through = run.events.popleft()[0]
            if name == "approval.request":
                run.status = "waiting_for_approval"
                run.pending_approval = {k: payload.get(k) for k in
                                        ("request_id", "command", "description", "choices", "pattern_key")
                                        if payload.get(k) is not None}
            elif name in ("approval.responded", "run.steered") or name.startswith("tool."):
                if run.status == "waiting_for_approval":
                    run.status = "running"
                run.pending_approval = None if name == "approval.responded" else run.pending_approval
            elif name == "run.stopping":
                run.status = "stopping"
            if name in TERMINAL_EVENTS:
                run.status = name.split(".", 1)[1]
                run.pending_approval = None
                run.finished_at = time.time()
            run.cond.notify_all()

    async def _pump(self, run: TrackedRun) -> None:
        try:
            lines = await self._open_events(run.run_id)
            async for _, data in sse.iter_sse(lines):
                try:
                    payload = json.loads(data)
                except ValueError:
                    continue
                name = payload.pop("event", None) or "message"
                payload.pop("run_id", None)
                await self._append(run, name, payload)
                if run.terminal:
                    return
        except asyncio.CancelledError:
            raise
        except Exception as exc:  # upstream stream broke; reconcile below
            log.warning("event stream for %s ended abnormally: %s", run.run_id, type(exc).__name__)
        await self._reconcile(run)

    async def _reconcile(self, run: TrackedRun) -> None:
        """Upstream stream closed without a terminal event: poll status until it settles."""
        for delay in (0, 1, 2, 4, 8, 15, 30, 30, 60, 60):
            if run.terminal:
                return
            await asyncio.sleep(delay)
            status = await self._fetch_status(run.run_id)
            if status is None:
                await self._append(run, "run.interrupted", {
                    "error": "Hermes no longer knows this run (gateway restarted?)", "source": "bridge"})
                return
            if status.get("status") in TERMINAL_STATUSES:
                fields = {k: status.get(k) for k in ("output", "error", "usage", "runtime") if status.get(k)}
                fields["source"] = "bridge.reconcile"
                await self._append(run, f"run.{status['status']}", fields)
                return
        await self._append(run, "run.interrupted", {
            "error": "Lost the Hermes event stream and the run never settled", "source": "bridge"})

    async def subscribe(self, run: TrackedRun, after: int, keepalive: float = 15.0) -> AsyncIterator[bytes]:
        """Replay buffered events with seq > after, then follow live until the terminal event."""
        cursor = after
        if cursor < run.dropped_through:
            yield sse.encode(cursor, "bridge.resync", {
                "reason": "events before this point were evicted; reload session history",
                "earliest_seq": run.dropped_through + 1})
            cursor = run.dropped_through
        while True:
            async with run.cond:
                pending = [e for e in run.events if e[0] > cursor]
                if not pending:
                    if run.terminal:
                        return
                    try:
                        await asyncio.wait_for(run.cond.wait(), timeout=keepalive)
                    except asyncio.TimeoutError:
                        pass
                    pending = [e for e in run.events if e[0] > cursor]
            if not pending:
                yield sse.KEEPALIVE
                continue
            for seq, name, payload in pending:
                yield sse.encode(seq, name, payload)
                cursor = seq

    def prune(self) -> None:
        now = time.time()
        for run_id, run in list(self.runs.items()):
            if run.terminal and run.finished_at and now - run.finished_at > self._retention:
                del self.runs[run_id]

    async def shutdown(self) -> None:
        for run in self.runs.values():
            if run.pump_task and not run.pump_task.done():
                run.pump_task.cancel()
