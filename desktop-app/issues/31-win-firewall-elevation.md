# 31: Windows: firewall add/remove via UAC without PowerShell

Lane: quick
Branch: feature/desktop-app
Platform: Windows
Status: in-progress
Round: 0
Blocked by: 30
Terms: elevation, firewall rule
Seam: `bridge/hermes_remote_bridge/host/windows.py`, `bridge/hermes_remote_bridge/host/winapi.py`, `bridge/hermes_remote_bridge/firewall.py`, `bridge/hermes_remote_bridge/cli.py` (`firewall` subcommand): `run_elevated` replaced by `elevate_cli(args)` (ShellExecuteW 'runas' on the venv's bridge exe) and `firewall add|remove` CLI paths using FwPolicy2 through `winapi.py`.
Test command: cd bridge && uv run pytest -q
Verify: Planner on this PC (user clicks the UAC prompt): rule added (Private profile, private ranges, port 8650), visible in wf.msc; removed again.

**Blocked by:** 30
**Status:** todo

## What to build

The firewall fix keeps one UAC prompt (D9): the app or CLI relaunches `hermes-remote-bridge firewall add
--port N --elevated` through `ShellExecuteW(..., "runas", ...)`; the elevated process adds the rule with
`FwPolicy2` (same name `FIREWALL_RULE`, Private profile only, remote addresses = private ranges, TCP N).
`firewall remove` mirrors it. `--unattended` installs never create it (existing CI assertion stays true).

## Acceptance criteria

- [ ] Rule properties exact (fake FwPolicy2 records them)
- [ ] Elevation argv exact; no PowerShell
- [ ] Remove deletes only our rule

## Planner notes (2026-10-28)

- Today `firewall.open_port("windows", port)` joins the `New-NetFirewallRule` argv from `open_commands` and runs it
  through `WindowsHost.run_elevated` (elevated PowerShell). Replace that path: `WindowsHost.elevate_cli(args)` runs
  `ShellExecuteExW` with verb `runas` on the venv's own exe (`sys.executable`'s sibling
  `Scripts\hermes-remote-bridge.exe`, fall back to `sys.argv[0]`), `SEE_MASK_NOCLOSEPROCESS`, waits for it
  (`WaitForSingleObject`, then `GetExitCodeProcess`), returns True on exit 0. A cancelled UAC prompt
  (`ERROR_CANCELLED` 1223) returns False. ctypes goes in `winapi.py` behind the existing `_safe` style, never raises.
- CLI: `firewall add --port N --elevated` and `firewall remove --port N --elevated` (hidden flags) do the FwPolicy2
  write in the elevated process: rule Name = `FIREWALL_RULE` ("Hermes Mobile Remote"), Description = COMMENT,
  Protocol TCP (6), LocalPorts str(N), Direction inbound (1), Action allow (1), Profiles = Private (2),
  RemoteAddresses = ",".join(PRIVATE_RANGES), Enabled True; through a COM wrapper inside winapi's `_com()`.
  Remove: delete only rules named FIREWALL_RULE (loop `Rules.Remove(name)` while one exists).
- `open_commands("windows", port)` keeps showing the user what will happen, now as the elevated command line
  (`hermes-remote-bridge firewall add --port N --elevated`) instead of a PowerShell cmdlet.
- `run_elevated` is no longer called for the firewall; leave the method in place, unused (planner deletes with 35).
- Tests: a fake elevate (argv recorded) and a fake FwPolicy2 rule object (properties recorded); a fake shell32 that
  returns 1223 for the cancelled case. No real UAC, firewall or COM in tests.
- The app's Settings "Firewall fix" on Windows: `control.rs` WindowsControl `firewall_fix` runs the bridge CLI
  `firewall --yes` through `bridge::SystemRunner` (CREATE_NO_WINDOW), which then raises the single UAC prompt.

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
