# 33-fix: flyout trust notification, work-area fallback, tray-click close

Lane: quick
Branch: feature/desktop-app
Platform: Windows
Status: done
Round: 1
Blocked by: 33
Terms: flyout, tray
Seam: `desktop/src-tauri/src/tray.rs` (`toggle_flyout`, the trust notification closure, `FLYOUT_OPEN` handling) and `desktop/src-tauri/src/flyout.rs`, plus `desktop/src/Settings.vue` (run-mode select hidden on Windows); pure logic tested on Linux via `flyout::place` and a new pure helper.
Test command: cd desktop && bash test.sh
Verify: Planner: `cargo xwin check --tests --target x86_64-pc-windows-msvc` clean; real Windows run is a manual check.

## What to build

1. The "Trust this network?" notification on Windows opens the flyout (at the taskbar corner, using the tray icon's
   last known position or the work area corner of the primary monitor), not the normal window.
2. When no monitor is found for the click, fall back to the primary monitor's size/work area, never a huge sentinel,
   so the flyout stays on screen. Put the fallback choice in a pure function with a test.
3. Clicking the tray icon while the flyout is open must close it and not reopen it right after the focus-loss hide
   (e.g. ignore a tray click that arrives within a short window after a focus-loss hide); pure timing decision, tested.

4. (planner, review 4-1 possible on 32) Settings.vue hides the Linux run-mode select and its notes when
   `get_settings` reports `platform: "windows"` (D34); vitest.

## Acceptance criteria

- [ ] Windows trust notification routes to the flyout (pure decision tested)
- [ ] Missing monitor fallback yields a rect inside the primary work area (test)
- [ ] Tray click right after a focus-loss hide does not reopen (test)
- [ ] UI: no run-mode select on Windows (vitest)

## Result
Commit 03794b7. test.sh: cargo 51 passed, vitest 19 passed, vite build ok.
- Trust routes to flyout: `flyout::tests::windows_trust_notification_routes_to_the_flyout` (`trust_target`).
- Missing monitor fallback: `a_missing_monitor_falls_back_to_the_primary_work_area` (`work_area`, result inside rect via `place`).
- Tray click after focus-loss hide: `a_tray_click_right_after_a_focus_loss_hide_is_ignored` (`ignore_tray_click`, 300 ms).
- No run-mode select on Windows: ui.test.js "on Windows there is no run mode select" (Settings.vue).
Reused: `place`, `show_main`, existing Settings platform check; nothing existing for fallback/timing.
The cfg(windows) wiring in tray.rs (open_flyout, show_for_trust, FOCUS_HIDE_AT, LAST_TRAY_POS) is not compiled by test.sh.
Notes added: 1 line in .scratch/dev-notes.md.
Not checked: cargo xwin Windows compile of tray.rs, real Windows run.

Planner Verify: PASS unit tests green; cargo xwin check --tests clean after planner fix ce931a8 (tauri::monitor::Monitor is not public -> tauri::Monitor). Real flyout still a Windows check
