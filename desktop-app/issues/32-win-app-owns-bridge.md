# 32: Windows: app owns the bridge (Job Object) and autostart

Lane: full
Branch: feature/desktop-app
Platform: Windows
Status: review
Round: 0
Blocked by: 01, 05, 07
Terms: Job Object, autostart
Seam: new `desktop/src-tauri/src/win_child.rs` (and the cfg(windows) wiring in `control.rs`, `run_mode.rs`, `setup.rs`, `lib.rs`): Rust `ChildSupervisor` Windows implementation: spawn `supervise` with CREATE_NO_WINDOW, assign it to a Job Object with JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE (Win32 calls behind a trait for tests); autostart through the Tauri autostart plugin behind a trait.
Test command: cd desktop && bash test.sh
Verify: Planner on this PC: start app -> bridge answers; quit -> bridge gone and port free; `taskkill /F` the app -> bridge gone; Run key present under HKCU\...\Run and visible in Task Manager > Startup.

**Blocked by:** 01, 05, 07
**Status:** todo

## What to build

On Windows the app owns the bridge (D5, D6, D7, D34): on start it runs `hermes-remote-bridge supervise` in
a Job Object so the bridge dies with the app even on a hard kill; Quit stops it cleanly first. The "start at
logon" setting is the HKCU Run key via the autostart plugin (setup turns it on). Nothing restarts a crashed
app; the settings page says so.

## Acceptance criteria

- [ ] Spawn flags and Job Object limit set (fake Win32 trait)
- [ ] Quit stops the child before exiting
- [ ] Autostart on/off maps to the plugin
- [ ] UI: start at logon toggle and the 'crash = bridge down' note
- [ ] An API without its screen fails this ticket

## Planner notes (2026-10-28)

- Reuse, do not duplicate: `run_mode::ProcessSupervisor<S: Spawner>` already implements spawn/stop (close stdin,
  wait, kill after grace)/exit_code over the `Spawner`/`Proc` traits; today `OsSpawner` is `cfg(unix)` only. Add a
  `cfg(windows)` `OsSpawner` in `desktop/src-tauri/src/win_child.rs` (re-exported so `ProcessSupervisor<OsSpawner>`
  works on both) that spawns with `CREATE_NO_WINDOW` (`std::os::windows::process::CommandExt::creation_flags`),
  stdin piped, stdout/stderr null, then assigns the child to a Job Object. Make `app_child()` available on
  Windows too (it is `cfg(target_os = "linux")` now) and call `on_app_start()` behavior on Windows: always spawn
  (no run mode on Windows, D34).
- Job Object through a `JobApi` trait (fake in tests): `CreateJobObjectW(null, null)`,
  `SetInformationJobObject(JobObjectExtendedLimitInformation, JOBOBJECT_EXTENDED_LIMIT_INFORMATION with
  BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE)`, `AssignProcessToJobObject(job,
  child handle)` (`std::os::windows::io::AsRawHandle`). Keep the job handle open for the app's lifetime (store it in
  a static); the OS closes it when the app dies, which kills the bridge. `windows-sys` 0.61 with the needed features
  (Win32_Foundation, Win32_System_JobObjects, Win32_System_Threading, Win32_Security) is already in Cargo.toml.
- Quit: lib.rs gets an `.on_window_event` / `RunEvent::ExitRequested` handler (or the tray Quit from ticket 22)
  that calls `app_child().stop()` before exit on Windows.
- Control on Windows: `control.rs` `backend()` for `cfg(windows)` = a `WindowsControl` whose start/stop act on
  `app_child()`, start_at_logon / set_start_at_logon go through an `Autostart` trait implemented with
  `tauri_plugin_autostart::ManagerExt` (`app.autolaunch().enable()/disable()/is_enabled()`); since `backend()` has
  no AppHandle, store the AppHandle in a `OnceLock` during `.setup` and read it there. firewall_fix/open_logs stay
  Unsupported (ticket 31 / later).
- Setup OS step on Windows: `WindowsOsStep` (describe: "Start at logon: HKCU Run key 'Hermes Remote'" ;
  apply = autostart enable + spawn the child; stop_bridge/start_bridge = app_child stop/spawn).
- UI: Settings already has the start-at-logon toggle; add the Windows-only note "If the app crashes the bridge
  stops too; start Hermes Remote from the Start menu again." (show it when `get_settings` reports
  `platform: "windows"`; add that field).
- This PC is Linux: `bash test.sh` must stay green here (cfg(windows) code is not compiled by it). The planner also
  runs `cargo xwin check --tests --target x86_64-pc-windows-msvc`; keep the Windows code compiling. Put pure logic
  (flags, call order) in code that compiles on both so tests run on Linux with the fake `JobApi`.

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

Commit d166661. `bash test.sh`: cargo 37 passed, vitest 18 passed, vite build ok.
- Spawn flags + Job Object limit: `the_job_is_created_limited_to_kill_on_close_then_the_child_is_assigned` (fake JobApi).
- Quit stops child first: `quit_stops_the_child_before_exiting`.
- Autostart on/off -> plugin (and start/stop, OS step): `start_at_logon_on_and_off_map_to_the_autostart_plugin`.
- UI toggle + crash note: ui.test.js "on Windows the start at logon toggle ... crash note shows" (mounted Settings).
- Reuse: `ProcessSupervisor`/`Spawner`/`Proc`, `OsStep`, `Control`; nothing existing for Job Objects. Notes added: 1.
- Tests were written after the code in one pass (not strictly red first).
Not checked: the `cfg(windows)` code (Win32 calls, autostart plugin, ExitRequested handler) was not compiled (no `cargo xwin` run); real Windows behaviour. Settings still shows the Linux run-mode select on Windows (not in scope).

Planner Verify: SKIP no Windows host (Job Object kill, Run key, taskkill test need Windows/CI). Checked: suite green, cargo xwin check --tests clean. Planner fixed directly 412b634: quit handler prevented ExitRequested th
