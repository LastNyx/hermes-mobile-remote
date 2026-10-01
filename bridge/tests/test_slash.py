import asyncio

import pytest

from hermes_remote_bridge import slash
from hermes_remote_bridge.app import create_app
from hermes_remote_bridge.config import Config
from hermes_remote_bridge.devices import DeviceStore
from test_app import FakeHermes, FakeTailnet, client

RAW_CATALOG = {
    "pairs": [["/title", "Set a title for the current session (usage: /title [name])"],
              ["/retry", "Retry the last message (resend to agent)"],
              ["/plan", "Write a plan (usage: /plan [request])"],
              ["/humanizer", "Humanize text"]],
    "skills": {"/humanizer": {"origin": "bundled"}},
}


class FakeGateway(slash.TuiGateway):
    """The real TuiGateway logic over a scripted JSON-RPC peer."""

    def __init__(self, replies):
        super().__init__(slash.Path("/nonexistent"), slash.Path("/nonexistent"))
        self.replies, self.calls = replies, []

    async def _call(self, method, params, timeout=90):
        self.calls.append((method, params))
        reply = self.replies.get(method, {})
        if isinstance(reply, Exception):
            raise reply
        return reply(params) if callable(reply) else reply


def gateway(**replies):
    return FakeGateway({"commands.catalog": RAW_CATALOG, "session.resume": {"session_id": "rt1"},
                        "session.close": {"closed": True}, **replies})


def test_catalog_offers_only_state_commands_app_commands_and_skills():
    names = {c["name"]: c for c in asyncio.run(gateway().catalog())}
    assert names["title"]["kind"] == "output" and names["title"]["args"] == "[name]"
    assert names["plan"]["kind"] == "prompt"
    assert names["humanizer"]["kind"] == "skill"
    assert {"new", "model", "reasoning", "stop"} <= {n for n, c in names.items() if c["kind"] == "app"}
    assert "retry" not in names  # needs a live agent in the command process


def test_output_command_runs_in_the_resumed_session_and_closes_it():
    gw = gateway(**{"slash.exec": {"output": "  Session title set: Hi"}})
    assert asyncio.run(gw.run("s1", "/title Hi")) == {"type": "output", "text": "  Session title set: Hi"}
    methods = [m for m, _ in gw.calls]
    assert methods[-3:] == ["session.resume", "slash.exec", "session.close"]
    assert gw.calls[-2][1] == {"session_id": "rt1", "command": "title Hi"}


def test_skill_becomes_a_message_to_send():
    gw = gateway(**{"command.dispatch": {"type": "skill", "message": "[IMPORTANT: ...] body",
                                         "display": "/humanizer fix this"}})
    reply = asyncio.run(gw.run("s1", "/humanizer fix this"))
    assert reply["type"] == "send" and reply["message"].startswith("[IMPORTANT")
    assert reply["display"] == "/humanizer fix this"


def test_session_is_closed_even_when_the_command_fails():
    gw = gateway(**{"slash.exec": slash.SlashError(400, "command_failed", "boom")})
    with pytest.raises(slash.SlashError):
        asyncio.run(gw.run("s1", "/title x"))
    assert gw.calls[-1][0] == "session.close"


def test_unoffered_command_is_refused_without_touching_the_session():
    gw = gateway()
    with pytest.raises(slash.SlashError) as e:
        asyncio.run(gw.run("s1", "/retry"))
    assert e.value.code == "command_not_available"
    assert "session.resume" not in [m for m, _ in gw.calls]


def _app(tmp_path, gw, hermes=None):
    cfg = Config(devices_file=tmp_path / "d.json", audit_log=tmp_path / "a.log")
    store = DeviceStore(cfg.devices_file)
    _, token = store.pair("phone")
    app = create_app(cfg, hermes=hermes or FakeHermes(), tailnet=FakeTailnet({"100.64.0.10": "me@example.com"}),
                     devices=store, owner_login="me@example.com", slash=gw,
                     hermes_cli=lambda *a: asyncio.sleep(0, result="high\n"))
    return client(app, "100.64.0.10"), {"Authorization": f"Bearer {token}"}


def test_command_endpoint_and_catalog(tmp_path):
    c, h = _app(tmp_path, gateway(**{"slash.exec": {"output": "ok"}}))
    assert any(x["name"] == "title" for x in c.get("/v1/commands", headers=h).json()["data"])
    r = c.post("/v1/sessions/s1/command", headers=h, json={"command": "/title Hi"})
    assert r.status_code == 200 and r.json() == {"type": "output", "text": "ok"}
    bad = c.post("/v1/sessions/s1/command", headers=h, json={"command": "/retry"})
    assert bad.status_code == 400 and bad.json()["error"]["code"] == "command_not_available"


def test_run_forwards_reasoning_effort_and_rejects_unknown_levels(tmp_path):
    hermes = FakeHermes({("POST", "/v1/runs"): {"run_id": "run_1", "status": "started"}})
    c, h = _app(tmp_path, gateway(), hermes)
    c.post("/v1/runs", headers=h, json={"session_id": "s1", "input": "hi", "client_request_id": "req000001",
                                        "reasoning_effort": "high"})
    assert hermes.sent_json("POST", "/v1/runs")["model_options"] == {"reasoning_effort": "high"}
    bad = c.post("/v1/runs", headers=h, json={"session_id": "s2", "input": "hi", "client_request_id": "req000002",
                                              "reasoning_effort": "turbo"})
    assert bad.status_code == 422


def test_models_report_the_configured_reasoning_default(tmp_path):
    hermes = FakeHermes({("GET", "/api/model/options"): {"model": "m", "provider": "p", "providers": []}})
    c, h = _app(tmp_path, gateway(), hermes)
    assert c.get("/v1/models", headers=h).json()["reasoning"]["default"] == "high"
