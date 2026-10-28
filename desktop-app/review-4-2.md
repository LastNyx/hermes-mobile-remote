# Review batch 4, round 2 (tickets 30, 30-fix, 31, 31-fix, 32, 33, 33-fix)

Tests: `TEST PASS | bridge PASS | desktop PASS | no baseline`.

## Spec

1. **Open from the tray after the flyout leaves the window in flyout style** (33: "Right click: Open ..."; 33-fix item 3). `tray.rs` `show_main` restores decorations / taskbar / always-on-top only `if FLYOUT_OPEN.swap(false)`, but both the focus-loss hide (`on_window_event`, Focused(false)) and the toggle-hide in `toggle_flyout` clear `FLYOUT_OPEN` first. After any flyout use, menu "Open" (and the trust path elsewhere) shows a borderless, always-on-top, skip-taskbar 360x520 window. Looks done, is wrong. Fix ticket 33-fix-flyout-restore-window.
2. Untested criterion (32): "Spawn flags ... set (fake Win32 trait)". `win_child.rs` tests only assert `CREATE_NO_WINDOW == 0x0800_0000`; the spawn itself is `cfg(windows)` and uses the constant, so a wrong flag at the call site would pass. Possible, accepted as not testable on Linux; no ticket.

Checked, no finding: 30/30-fix criteria (struct offsets, fake comtypes, never-raise, service_state port), 31/31-fix criteria (exact rule properties, argv, remove only our rule, `--port` honoured), 32 quit/autostart/UI note, 33-fix routing/fallback/click-grace/Settings. Callers of changed names (`open_port`, `service_state` in doctor.py, `firewall.state`) still fit.

## Standards

1. Possible, untested and leaky: `App.vue:21` registers a `navigate` listener that is never removed and has no vitest, while the tray "Pair a device" action depends on it. Fix ticket 33-fix-navigate-listener-test.
2. Possible, dead code: `flyout.rs` `place`, Bottom (and Right) arm wraps `work.y + work.h - h` in a `clamp` with that same value as upper bound; it is a no-op. `host/windows.py` module docstring ends in a dangling fragment ("Scheduled Task, Windows Defender Firewall."). Fix ticket 30-fix-windows-docstring-and-clamp (refactor only).
3. Reuse check: no existing helper was bypassed (`parse_default_routes`, `normalise_mac`, `ProcessSupervisor`, `show_main`, `place` reused).

Not checked: real Windows ctypes/COM/Win32/tray run; `cfg(windows)` code in `tray.rs` and `win_child.rs` (not compiled by the test command); migration.rs / hooks.nsh (ticket 34, outside this batch).
