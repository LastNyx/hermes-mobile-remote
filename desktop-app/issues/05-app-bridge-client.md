# 05: Desktop: bridge client (local API first, CLI fallback)

Lane: full
Branch: feature/desktop-app
Platform: App
Status: review
Round: 0
Blocked by: 02, 04
Terms: local client, CLI fallback, bridge paths
Seam: Rust trait `BridgeApi` (status, devices, pair, revoke, trust, untrust, doctor) with `LocalHttp` and `CliJson` implementations and a `Fallback` that tries the first then the second; `BridgePaths` resolving the venv's bridge exe and config dir per OS. Exposed to the UI as Tauri commands.
Test command: cd desktop && bash test.sh
Verify: Planner runs the app against the real bridge on this PC: status loads via HTTP; with the bridge stopped, doctor still answers via the CLI.

**Blocked by:** 02, 04
**Status:** todo

## What to build

The app always gets an answer about the bridge (D8, D21).

- `LocalHttp`: `http://127.0.0.1:<port>/v1/local/*` with `X-Hermes-Local` read from `<config_dir>/local-secret`
  (port from the bridge `config.toml`, default 8650). Timeout 2 s.
- `CliJson`: runs `<venv>/bin/hermes-remote-bridge` (`Scripts\hermes-remote-bridge.exe` on Windows) with
  `devices --json`, `trust --list --json`, `doctor --json`, `pair ...`, `revoke ...`; on Windows with
  `CREATE_NO_WINDOW` so no console flashes.
- `Fallback`: on connection refused / timeout / 403 from `LocalHttp`, use `CliJson` and mark the answer
  `via: "cli"` so the UI can say the bridge is not running.
- Paths: config dir Windows `%LOCALAPPDATA%\hermes-remote\config`, Linux `$XDG_CONFIG_HOME/hermes-remote`
  (default `~/.config/hermes-remote`); venv Windows `%LOCALAPPDATA%\hermes-remote\venv`, Linux
  `~/.local/share/hermes-remote/venv` (D11).

## Acceptance criteria

- [ ] `LocalHttp` sends the secret header and parses each route (fake HTTP server in the test)
- [ ] `Fallback` uses the CLI on refused/timeout/403 and tags `via: cli`
- [ ] CLI argv per command is exact (fake process runner); Windows spawn sets CREATE_NO_WINDOW
- [ ] Paths resolve per OS with env overrides (`XDG_CONFIG_HOME`, `LOCALAPPDATA`)

## Facts that hold for every ticket

- Design: `docs/desktop-app-design.md` (decisions D1-D34). Read it first. Branch `feature/desktop-app`, from
  `origin/main` v1.23.1. Never push, never touch `main`.
- Platform tags: **App** = shared code, buildable and testable on Windows or Linux. **Windows** / **Linux** =
  needs that OS for its Verify (unit tests still run anywhere with fakes).
- Bridge (Python, FastAPI): `bridge/hermes_remote_bridge/`. `app.py` = the HTTP API; the `guard` middleware
  (`app.py:266`) classifies the peer with `peer_verdict` (`app.py:247`: loopback -> "local", tailnet -> whois,
  private LAN over https). `cli.py` = `serve`, `pair`, `revoke`, `devices --json`, `trust [--list] --json`,
  `doctor --json`, `firewall` (`cli.py:500-520`); exit 75 = network changed, restart (`cli.py:160`);
  `_port_in_use`/`_bind` (`cli.py:51-90`). `host/` is the OS seam (`base.py`, `linux.py`, `windows.py`);
  nothing outside `host/` may branch on `sys.platform`.
- Bridge tests: `bridge/tests/` (pytest). Tests never touch the real OS for anything that opens UI, starts a
  process, or changes the firewall/registry/systemd: fake `cli.host` / the host object and assert the fake saw
  the call. Two tests (symlink privilege WinError 1314, CRLF) already fail on the Windows dev PC; that is the
  baseline, not this ticket.
- Desktop app (Tauri v2, Rust + Vue 3 + Vite): top-level `desktop/` (`desktop/src-tauri/` Rust, `desktop/src/`
  Vue). Created by ticket 04. Rust logic sits behind traits so `cargo test` runs without a window, a real
  bridge, systemd or Win32; Vue components are tested with vitest and a mocked `@tauri-apps/api` `invoke`.
- Protected (planner edits only; ask the planner in your Result if you need a change): `bridge/pyproject.toml`,
  `bridge/uv.lock`, `desktop/package.json`, `desktop/package-lock.json`, `desktop/src-tauri/Cargo.toml`,
  `Cargo.lock`, `desktop/src-tauri/tauri.conf.json`, `desktop/src-tauri/capabilities/`, `desktop/vite.config.*`,
  `desktop/test.sh`, `.github/`, `.team.json`.
- Security invariants (`docs/SECURITY.md`): the bridge never binds `0.0.0.0`/`::`; LAN is HTTPS only; the
  relay forwards only `ALLOWED_METHODS`. `/v1/local/*` (ticket 02) is never added to the relay allowlist and
  never served to a non-loopback peer.
- No PowerShell in plumbing (D1): no new `.ps1`, no `powershell.exe` spawned by the bridge or the app.
- No Co-Authored-By trailer on any commit.

## Constraints

- Do not touch `android/`, `bridge/linux/tray.py` (kept until a follow-up PR, D23), or other branches.
- Do not delete files: if a file becomes unused, leave it and say so in the Result (the planner deletes it).
- Never start the real bridge on port 8650, never change the real firewall, registry, Scheduled Tasks or
  systemd units from a test.

## Result
Commit 7834229. `cd desktop && bash test.sh`: cargo 5 passed (4 new), vitest 1 passed, vite build ok.
- AC1 `local_http_sends_secret_and_parses_each_route` (fake TcpListener server, all 8 routes, header + bodies).
- AC2 `fallback_uses_cli_on_refused_timeout_and_403` (also healthy -> `via: http`, CLI not run).
- AC3 `cli_argv_is_exact_and_windows_hides_the_console` (fake runner; `creation_flags(Windows)` = CREATE_NO_WINDOW; SystemRunner applies it under cfg(windows)).
- AC4 `paths_resolve_per_os_with_env_overrides`.
Reused: nothing existing (no prior bridge client); used installed ureq, serde_json, tempfile. Tauri commands `bridge_*` registered in lib.rs.
Notes: CLI has no status command, so `CliJson::status` returns `{"running": false}` via cli; CLI pair/revoke/trust/untrust return `{"output": <stdout>}` (not JSON). Port is read from a `port =` line in config.toml (no toml crate). Dev-notes: 1 line added.
Not checked: real bridge / real CLI (planner's Verify), Windows build and actual console suppression, `port` key name in real config.toml.

Planner Verify: PASS Fallback::real() via an example probe, isolated HOME with venv at D11 path, bridge :18650: running -> status/devices via=http; stopped -> status {running:false} via=cli, doctor checks via=cli. Windows
