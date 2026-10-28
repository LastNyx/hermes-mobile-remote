# 33-fix: leaving flyout mode restores the normal window

Lane: quick
Branch: feature/desktop-app
Platform: Windows
Status: done
Round: 2
Blocked by: 33
Terms: flyout, tray
Seam: `desktop/src-tauri/src/tray.rs` (`show_main`, `on_window_event` focus-loss hide, `toggle_flyout`) plus a new pure helper in `flyout.rs` that decides whether the window is in flyout style; tested on Linux through that helper.
Test command: cd desktop && bash test.sh
Verify: Planner: `cargo xwin check --tests --target x86_64-pc-windows-msvc` clean; real Windows run is a manual check.

## What to build

The flyout style (no decorations, always on top, hidden from the taskbar, 360x520) must be undone the next time the window is shown as a normal window, even when the flyout was closed by focus loss or by a second tray click. Track "window currently has flyout style" separately from "flyout currently open", clear it only when the style is restored, and restore the normal size as well. The decision "restore style on normal show" is a pure function with a test.

## Acceptance criteria

- [ ] Open flyout, lose focus (hide), then menu Open: normal window style is restored (pure decision tested)
- [ ] Same after a tray-click hide
- [ ] Opening the flyout again after a normal show still applies the flyout style

## Result

Planner Verify: PASS planner fixed directly (413aba2, trivial lane)
