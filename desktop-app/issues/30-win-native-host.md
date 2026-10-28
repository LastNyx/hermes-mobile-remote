# 30: Windows: native host calls (no PowerShell)

Lane: full
Branch: feature/desktop-app
Platform: Windows
Status: done
Round: 0
Blocked by: None
Terms: default route, router MAC, connection profile, comtypes
Seam: `host/windows.py` methods `default_route`, `gateway_mac`, `network_profile`, `firewall_active`, `firewall_rule_present`, `service_state` keep their signatures; the Win32/COM calls go through a small `winapi.py` module that tests replace with a fake.
Test command: cd bridge && uv run pytest -q
Verify: Planner on this PC: old (PowerShell) and new answers identical for route, MAC and profile GUID; `current_network(fresh=True)` and `/v1/me` timed before/after. CI side-by-side step added by the planner (ticket 35).

**Blocked by:** None
**Status:** todo

## What to build

The bridge answers OS questions on Windows without spawning PowerShell (D9).

- Default route: `GetIpForwardTable2` (or `GetBestRoute2` to 0.0.0.0) + `GetIpInterfaceEntry` for the
  interface metric; pick the lowest route+interface metric, skip next hop 0.0.0.0, return `(alias, gateway)`
  exactly as `parse_default_routes` does today.
- Router MAC: `GetIpNetTable2` / `ResolveIpNetEntry2`; output through the existing `normalise_mac`.
- Connection profile: `INetworkListManager` via comtypes (`CLSID {DCB00C01-570F-4A9B-8D69-199FDBA5723B}`):
  map the interface's adapter GUID to its network, return `(network id GUID, name)` in the same format
  `Get-NetConnectionProfile` gave (InstanceID / Name), so stored trusted networks still match.
- Firewall: `HNetCfg.FwPolicy2` via comtypes for "any profile enabled" and "our rule present with port N".
- `service_state`: report from the supervisor/app (bridge reachable), not the Scheduled Task.
- `ps()`/`ps_json()` stay in the file but unused (the planner deletes them with the PowerShell cleanup).
comtypes is added to `pyproject.toml` (Windows marker) by the planner before dispatch.

## Acceptance criteria

- [ ] Each method returns the same tuple shapes and values as before for recorded fixture data (fake winapi)
- [ ] No `subprocess`/`powershell` call on any path (assert with a fake that fails if called)
- [ ] Profile id format identical to the old `Get-NetConnectionProfile` InstanceID (fixture)
- [ ] Existing `test_host.py` / `test_network_cache.py` stay green

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

Commit: 48b6754. Tests: `cd bridge && uv run pytest -q` -> 179 passed.
- AC1 `test_windows_methods_return_the_old_shapes_from_fixture_data` (route, MAC, firewall, service_state; fake winapi) + `test_windows_service_state_unknown_when_bridge_unreachable`.
- AC2 same fixture fails any `subprocess.run/Popen`; used by all three new tests.
- AC3 `test_connection_profile_id_keeps_the_instance_id_format` (`winapi.instance_id` gives `{UPPER-GUID}`).
- AC4 whole suite green. Three old PowerShell-mocking tests (network_profile, gateway_mac, service_state) were replaced as they asserted the removed behaviour.
- Reuse: `parse_default_routes`, `normalise_mac` reused; no existing Win32 helper found. Notes: 1 added.
- `ps`, `ps_json`, `powershell`, `encode_script`, `run_elevated` left in windows.py (unused except `run_elevated`; planner deletes).
- service_state probes 127.0.0.1:8650 (`winapi.BRIDGE_PORT`), not the configured port.
Not checked: real ctypes/COM code in winapi.py (struct layouts, comtypes enumeration) never ran, as there is no Windows here; needs the planner's side-by-side Verify.

Planner Verify: SKIP no Windows host here (side-by-side PowerShell vs native, timings need a Windows PC/CI); fakes cover parsing; Sonnet subagent reviewing winapi.py struct layouts/COM
