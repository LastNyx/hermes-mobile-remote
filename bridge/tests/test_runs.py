import asyncio
import json

import pytest

from hermes_remote_bridge.runs import RunManager


class FakeUpstream:
    """Feeds SSE lines like Hermes' /v1/runs/{id}/events (no `event:` line, name inside JSON)."""

    def __init__(self):
        self.queue: asyncio.Queue = asyncio.Queue()
        self.status: dict | None = {"status": "running"}

    async def open_events(self, run_id):
        async def lines():
            while True:
                item = await self.queue.get()
                if item is None:
                    return
                yield f"data: {json.dumps(item)}"
                yield ""
        return lines()

    async def fetch_status(self, run_id):
        return self.status


async def collect(gen, n, timeout=2.0):
    out = []
    async def run():
        async for frame in gen:
            if frame.startswith(b":"):
                continue
            out.append(frame.decode())
            if len(out) >= n:
                return
    await asyncio.wait_for(run(), timeout)
    return out


def parse(frame):
    lines = dict(l.split(": ", 1) for l in frame.strip().splitlines())
    return int(lines["id"]), lines["event"], json.loads(lines["data"])


async def test_replay_after_reconnect_and_terminal():
    up = FakeUpstream()
    mgr = RunManager(up.open_events, up.fetch_status)
    run = mgr.track("run_1", "sess", "dev")
    for i in range(3):
        await up.queue.put({"event": "message.delta", "run_id": "run_1", "delta": f"d{i}"})
    first = await collect(mgr.subscribe(run, 0, keepalive=0.1), 2)
    assert [parse(f)[0] for f in first] == [1, 2]
    # client drops after seq 2, more events arrive while away
    await up.queue.put({"event": "tool.started", "run_id": "run_1", "tool": "terminal"})
    await up.queue.put({"event": "run.completed", "run_id": "run_1", "output": "ok"})
    rest = await collect(mgr.subscribe(run, 2, keepalive=0.1), 3)
    assert [parse(f)[:2] for f in rest] == [(3, "message.delta"), (4, "tool.started"), (5, "run.completed")]
    assert "run_id" not in parse(rest[0])[2]
    await asyncio.sleep(0.05)
    assert run.terminal and run.status == "completed"
    # a finished run replays fully and then the stream ends on its own
    frames = [f async for f in mgr.subscribe(run, 0) if not f.startswith(b":")]
    assert len(frames) == 5


async def test_approval_state_tracking():
    up = FakeUpstream()
    mgr = RunManager(up.open_events, up.fetch_status)
    run = mgr.track("run_2", "sess", "dev")
    await up.queue.put({"event": "approval.request", "run_id": "run_2", "command": "rm -rf x",
                        "choices": ["once", "deny"], "request_id": "req1"})
    await collect(mgr.subscribe(run, 0, keepalive=0.1), 1)
    assert run.status == "waiting_for_approval"
    assert run.pending_approval == {"request_id": "req1", "command": "rm -rf x", "choices": ["once", "deny"]}
    await up.queue.put({"event": "approval.responded", "run_id": "run_2", "choice": "deny"})
    await collect(mgr.subscribe(run, 1, keepalive=0.1), 1)
    assert run.status == "running" and run.pending_approval is None


async def test_upstream_drop_reconciles_from_status():
    up = FakeUpstream()
    up.status = {"status": "completed", "output": "final"}
    mgr = RunManager(up.open_events, up.fetch_status)
    run = mgr.track("run_3", "sess", "dev")
    await up.queue.put(None)  # upstream closes with no terminal event
    frames = await collect(mgr.subscribe(run, 0, keepalive=0.1), 1)
    seq, name, data = parse(frames[0])
    assert name == "run.completed" and data["output"] == "final" and data["source"] == "bridge.reconcile"


async def test_unknown_run_after_gateway_restart_is_interrupted():
    up = FakeUpstream()
    up.status = None
    mgr = RunManager(up.open_events, up.fetch_status)
    run = mgr.track("run_4", "sess", "dev")
    await up.queue.put(None)
    await collect(mgr.subscribe(run, 0, keepalive=0.1), 1)
    assert run.status == "interrupted"


async def test_buffer_eviction_emits_resync():
    up = FakeUpstream()
    mgr = RunManager(up.open_events, up.fetch_status, max_events=3)
    run = mgr.track("run_5", "sess", "dev")
    for i in range(5):
        await up.queue.put({"event": "message.delta", "run_id": "run_5", "delta": str(i)})
    await asyncio.sleep(0.05)
    frames = await collect(mgr.subscribe(run, 0, keepalive=0.1), 4)
    assert parse(frames[0])[1] == "bridge.resync"
    assert [parse(f)[0] for f in frames[1:]] == [3, 4, 5]
