# 21: Linux: 'only while the app is open' run mode

Lane: full
Branch: feature/desktop-app
Platform: Linux
Status: in-progress
Round: 0
Blocked by: 01, 20
Terms: app-only mode, PDEATHSIG
Seam: new `desktop/src-tauri/src/run_mode.rs`: Rust `RunMode` switcher over `ServiceControl` and a `ChildSupervisor` that spawns `hermes-remote-bridge supervise` with `PR_SET_PDEATHSIG` (via `pre_exec`), injected for tests.
Test command: cd desktop && bash test.sh
Verify: User on the Arch PC: switch to app-only, `kill -9` the app, the bridge is gone; switch back, the service runs.

**Blocked by:** 01, 20
**Status:** todo

## What to build

Run mode setting (D31). Switching to "only while the app is open": stop and disable the user unit, then
spawn `supervise` as the app's child with `PR_SET_PDEATHSIG(SIGTERM)`, so a crashed app takes the bridge
with it. Switching back: stop the child, wait for it to exit, then `enable --now` the unit. The old owner is
always stopped before the new one starts; if `supervise` still exits 76 (port conflict), show it. The
settings page says app-only mode has no systemd sandboxing. The mode is saved in the app's config.

## Acceptance criteria

- [ ] Switch order: old owner stopped and confirmed before the new one starts (both directions)
- [ ] Child spawned with PDEATHSIG (assert the pre_exec hook is installed via the injected spawner)
- [ ] Exit 76 surfaces as a port-conflict message
- [ ] UI: run mode control with the no-sandbox note; start-at-login/linger disabled in app-only mode
- [ ] An API without its screen fails this ticket

## Planner notes (2026-10-28)

- New module `desktop/src-tauri/src/run_mode.rs`, `pub mod` in `lib.rs`. Reuse `linux_service::ServiceControl`
  (`stop`, `disable`, `enable_now`, `status`) and `bridge::BridgePaths` (venv exe). `libc` is already a unix
  dependency in Cargo.toml (`libc::prctl(libc::PR_SET_PDEATHSIG, libc::SIGTERM)` inside
  `std::os::unix::process::CommandExt::pre_exec`). Spawn `supervise` with stdin piped (ticket 01-fix: the
  supervisor stops when its stdin closes), stdout/stderr to null.
- `ChildSupervisor` trait: `spawn() -> Result<(), String>`, `stop() -> Result<(), String>` (close stdin, then
  wait up to 10 s, then kill), `exit_code() -> Option<i32>` (76 = port conflict). The real one keeps the
  `std::process::Child` in app state (`tauri::State<Mutex<..>>` or a `OnceLock<Mutex<..>>`).
- Mode saved as `run_mode = "always-on" | "app-only"` in `<app config dir>/desktop.toml`, where the app config dir
  is `BridgePaths::config_dir` (same folder as the bridge config; separate file so the bridge never reads it).
  Read it with `bridge::config_value`. Default `always-on`.
- In app-only mode, `control.rs`'s Linux backend start/stop act on the child instead of systemctl, and on app
  start the app spawns the child. Add commands `get_run_mode` / `set_run_mode {mode}` (register in lib.rs);
  Settings.vue gets the control, the no-sandbox note, and disables start-at-login / linger in app-only mode.
- Tests never spawn the real bridge: fake ServiceControl and fake ChildSupervisor record the call order.
  One test may spawn `/bin/sh -c 'exit 76'`-style children only through an injected command builder to check the
  pre_exec hook is set (assert via the fake that `pdeathsig` was requested).

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
