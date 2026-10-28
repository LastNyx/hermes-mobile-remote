# 33: Windows: tray icon and WARP-style flyout

Lane: full
Branch: feature/desktop-app
Platform: Windows
Status: review
Round: 0
Blocked by: 06, 32
Terms: flyout, tray
Seam: new `desktop/src-tauri/src/flyout.rs` plus the cfg(windows) branches in `desktop/src-tauri/src/tray.rs`: Rust `FlyoutPlacement` (pure function: taskbar edge + work area + tray click point -> window rect) plus the Tauri tray/window wiring.
Test command: cd desktop && bash test.sh
Verify: Planner on this PC: left click opens the flyout at the taskbar corner, focus loss closes it, right click shows the menu; screenshots. Top/side taskbars go to the manual checks.

**Blocked by:** 06, 32
**Status:** todo

## What to build

Windows tray (D3): left click toggles a borderless, rounded, always-on-top flyout (~360x520) placed at
the taskbar corner nearest the click (taskbar edge from `SHAppBarMessage(ABM_GETTASKBARPOS)`, kept inside the
monitor work area); focus loss hides it. Right click: Open, Start/Stop bridge, Pair a device, Quit. Icon shows
running / degraded / stopped / problem. First untrusted network: a notification that opens the flyout; a
decline is remembered.

## Acceptance criteria

- [ ] Placement correct for bottom, top, left, right taskbars and multi-monitor work areas (pure tests)
- [ ] Menu items and actions (fake app handle)
- [ ] Icon state mapping from status
- [ ] Trust notification once per network; decline remembered

## Planner notes (2026-10-28)

- `tray.rs` (ticket 22) already builds the tray, the menu (`MENU_ITEMS`, `menu_action`), close-to-tray and the trust
  notification poll (`poll_once`, `desktop-asked.json`). Reuse all of it. Today `install()` decides the tray with
  `has_status_notifier` (dbus-send), which is false on Windows, so Windows gets no tray: make the probe
  `cfg(windows)` -> always true. Add "Pair a device" to the menu (it shows the window and emits an event
  `navigate` with `"pair"`; `App.vue` listens and switches tab).
- Left click (Windows only; Tauri's `TrayIconEvent::Click { button: Left, button_state: Up, position, .. }`):
  toggle the `main` window as a flyout: when showing, `set_decorations(false)`, `set_always_on_top(true)`,
  `set_skip_taskbar(true)`, size 360x520 logical, position from `flyout::place(...)`; `WindowEvent::Focused(false)`
  hides it (Windows only). Linux keeps the ticket-22 behaviour (no left-click handling).
- `flyout::place(edge, work_area, click, size) -> Rect` is a pure function (all four edges, multi-monitor work
  areas with negative origins, clamped inside the work area); tests cover each edge. The taskbar edge comes from
  `SHAppBarMessage(ABM_GETTASKBARPOS)` (`windows-sys` features `Win32_UI_Shell` already enabled) behind a
  `Taskbar` trait; the work area from Tauri's `monitor_from_point(...)` / `work_area()` (fallback: the monitor size).
- Icon states (running / degraded / stopped / problem) from the status answer: pure `icon_state(&Value) -> IconState`
  (running && trusted && firewall_ok -> running; running otherwise -> degraded; not running -> stopped; error ->
  problem). Generate the four icons at runtime by tinting the default window icon (simple RGBA tint, no new files,
  no new crates) and update the tray icon from the existing 30 s poll.
- Keep `bash test.sh` green on Linux and the Windows code compiling (`cargo xwin check --tests`).

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

Commit d66008c. `cd desktop && bash test.sh`: 44 cargo tests + 18 vitest pass, vite build ok.
- Placement (all 4 edges, negative-origin monitor, tiny area): flyout.rs tests (5). Menu items/actions incl. Pair: `menu_items_and_their_actions`. Icon states + tint: `icon_state_follows_the_status_answer`. Trust once/remembered: existing `trust_notification_fires_once...` (unchanged, still passes; poll_once now wraps `poll_with_icon`).
- Reused: MENU_ITEMS/menu_action/poll_once/check_trust, `show_main`. Nothing existing for rect placement or icon tint.
- Windows glue (WinTaskbar via SHAppBarMessage, toggle_flyout, Focused(false) hide, left-click, tray on Windows) is cfg(windows), not compiled here. Pair navigation uses a DOM CustomEvent via `eval`, App.vue listens (no UI test added; no criterion).
- Trust notification on Windows calls `show_main` (normal window), not the flyout (no click point).
Not checked: `cargo xwin check --tests`, any real Windows run, App.vue navigate listener, top/side taskbars on a real PC.

Planner Verify: SKIP no Windows host (flyout position/focus-loss/right-click need Windows). Checked: xwin check --tests clean; Linux regression: tray still registered, trust notification recorded, window opens
