# 22: Linux: app window, launcher entry, optional tray

Lane: full
Branch: feature/desktop-app
Platform: Linux
Status: todo
Round: 0
Blocked by: 06
Terms: app window, optional tray
Seam: Rust `TrayPolicy` deciding whether to create a tray (injected `has_status_notifier()` probe), plus the window/menu wiring in the Tauri setup.
Test command: cd desktop && bash test.sh
Verify: User on the Arch PC (KDE or GNOME): the app opens from the launcher as a normal window; with a tray, its menu 'Open Hermes Remote' shows the window; without a tray, nothing breaks; untrusted network notification opens the window.

**Blocked by:** 06
**Status:** todo

## What to build

On Linux the UI is a normal resizable window opened from the launcher (`.desktop` entry from the bundle)
(D20). A tray icon is created only when a StatusNotifier host exists on the session bus; its menu has "Open
Hermes Remote", start/stop, quit (Tauri's tray click events do not fire on Linux, so no left-click
behaviour). Closing the window hides to tray when there is one, otherwise quits the app (the service keeps
running in always-on mode). An untrusted network shows a desktop notification "Trust this network?" that
opens the window; a decline is remembered (same rule as `host/linux.py:prompt_trust`).

## Acceptance criteria

- [ ] Tray created only when the probe says a StatusNotifier host exists
- [ ] Menu items and their actions (fake app handle)
- [ ] Close-window behaviour with and without tray
- [ ] Trust notification fires once per untrusted network; decline remembered

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
