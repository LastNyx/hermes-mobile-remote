# 20: Linux: systemd service control, start at login, linger

Lane: full
Branch: feature/desktop-app
Platform: Linux
Status: review
Round: 0
Blocked by: 05, 07
Terms: always-on mode, user service, linger
Seam: new `desktop/src-tauri/src/linux_service.rs`: Rust trait `ServiceControl` (status, start, stop, enable, disable, linger_on, linger_off, write_unit) with a `Systemd` implementation over an injected `ProcessRunner`.
Test command: cd desktop && bash test.sh
Verify: User on the Arch PC: setup writes and enables the unit, the switch starts/stops it, closing the app leaves the bridge running, linger toggles (`loginctl show-user $USER -p Linger`).

**Blocked by:** 05, 07
**Status:** todo

## What to build

Always-on mode, the Linux default (D19, D32, D33):

- Setup's OS step writes `~/.config/systemd/user/hermes-remote-bridge.service` from the existing template
  `bridge/systemd/hermes-remote-bridge.service` (`@BIN@` -> the venv's `hermes-remote-bridge`), runs
  `systemctl --user daemon-reload`, `enable --now`. An existing unit from `install.sh` is taken over (same name).
- The app's switch runs `systemctl --user start|stop`; status from `systemctl --user is-active`.
- "Start at login": `enable` / `disable`. "Keep running after logout": `loginctl enable-linger|disable-linger
  $USER`, only offered when start at login is on; a polkit refusal is shown as "needs your password: run
  `loginctl enable-linger` in a terminal".
- Closing or crashing the app never stops the service.

## Acceptance criteria

- [ ] Unit text equals the template with @BIN@ replaced; daemon-reload + enable --now issued
- [ ] Switch maps to start/stop; status parsed from is-active
- [ ] Start-at-login and linger argv exact; linger hidden unless enabled; polkit failure message
- [ ] UI: settings shows start at login and keep running after logout with the rules above
- [ ] An API without its screen fails this ticket

## Planner notes (2026-10-28)

- New module `desktop/src-tauri/src/linux_service.rs` (tests in the same file), `pub mod` in `lib.rs`. Use the
  existing `bridge::ProcessRunner` (`run_with`) as the injected runner; `bridge::BridgePaths` gives the venv exe.
- Wire it in, do not add new commands: `control.rs` `backend()` returns a Linux `Control` built on `ServiceControl`
  under `cfg(target_os = "linux")` (start/stop = systemctl start/stop, `start_at_logon` = `is-enabled`,
  set = enable/disable; firewall_fix/open_logs stay as they are). `setup.rs` uses a Linux `OsStep` (describe:
  "systemd user service hermes-remote-bridge.service, enabled at login"; apply: write unit + daemon-reload +
  `enable --now`; stop_bridge/start_bridge: systemctl stop/start) instead of `NoOsStep` on Linux.
- Template: embed `bridge/systemd/hermes-remote-bridge.service` with `include_str!` (path relative to the crate:
  `../../../bridge/systemd/hermes-remote-bridge.service`); replace `@BIN@` with the venv exe path. Unit dir:
  `$XDG_CONFIG_HOME/systemd/user` (default `~/.config/systemd/user`), like install.sh line 13.
- Linger: add commands `get_linger` / `set_linger {on}` in `control.rs` (register them in `lib.rs`); status from
  `loginctl show-user $USER -p Linger` (`Linger=yes`). Settings.vue shows "Keep running after logout" only
  when start at login is on; a non-zero exit from enable-linger shows "needs your password: run
  `loginctl enable-linger` in a terminal".
- Never run real systemctl/loginctl in tests (fake runner only).

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

Commit 0196033. `cd desktop && bash test.sh`: cargo 19 passed, vitest 14 passed, vite build ok.
- AC1 `unit_is_the_template_with_bin_replaced_then_reloaded_and_enabled_now` (linux_service.rs)
- AC2 `switch_maps_to_start_stop_and_status_is_parsed_from_is_active`
- AC3 `start_at_login_and_linger_argv_are_exact_and_polkit_refusal_is_explained`
- AC4/5 ui.test.js "keep running after logout shows only with start at login..." (mounts App, Settings tab); API wired to screen via get_linger/set_linger commands registered in lib.rs.
- Reused: `bridge::ProcessRunner`/`SystemRunner`/`BridgePaths`, `Control`, `OsStep`, `guard` in Settings.vue; nothing existing for systemd.
- Tests written with the code in one pass, so no separate red run (new module).
- Added trait methods beyond the list (is_enabled, enable_now, linger) for the wiring the notes ask for. Notes added: 1.
Not checked: real systemctl/loginctl on the Arch PC, polkit prompt, install.sh takeover on a live unit; Windows build of the cfg branches.

Planner Verify: PASS real Systemd over SystemRunner on this PC: is-active/is-enabled parse ok, linger=true matches loginctl Linger=yes; write_unit into a temp dir = template with @BIN@ replaced, byte-identical, daemon-rel
