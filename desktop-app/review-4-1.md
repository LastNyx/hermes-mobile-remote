# Review batch 4, round 1 (tickets 30, 30-fix-winapi-com, 31, 32, 33)

Tests: `TEST PASS | bridge PASS | desktop PASS | no baseline`.

## Spec

- **31** "`firewall add --port N`": `cmd_firewall` computes `port = args.port or cfg.port` (cli.py:453) but the
  non-elevated add path still uses `cfg.port` for state, commands and `open_port` (cli.py:462-485). `--port` is ignored
  unless `--elevated`. Fix ticket 31-fix-firewall-port.
- **33** "First untrusted network: a notification that opens the flyout": the notification calls `show_main`
  (tray.rs, install poll closure), a normal window, not the flyout. Self-reported deviation, criterion not met on
  Windows. Fix ticket 33-fix-flyout-edge-cases.
- **33** "kept inside the monitor work area ... fallback: the monitor size": when `monitor_from_point` fails,
  `toggle_flyout` uses a `i32::MAX/2` work area (tray.rs, `toggle_flyout`), which puts a bottom-taskbar flyout far off screen.
- **33** possible: clicking the tray icon while the flyout is open first fires `Focused(false)` (hides it, clears
  `FLYOUT_OPEN`), then the Click toggles it open again, so the icon cannot close it.
- **32** possible: Settings still shows the Linux run-mode select on Windows (self-reported, Settings.vue); the app has
  no run mode there (D34). Untested criterion: the `CREATE_NO_WINDOW`/stdin/stdout spawn flags in `win_child.rs`
  `OsSpawner` are only asserted as a constant (cfg(windows), not run here).
- 30, 30-fix: criteria covered by tests; no breaking call sites found (`service_state` "inactive" read by doctor.py per Result).

## Standards

- windows.py module docstring (line 2) still says "PowerShell only in run_elevated (until ticket 31)"; 31 landed and
  `run_elevated` is now unused. Stale comment (fold into 31-fix).
- possible: `firewall remove` runs elevation without the confirmation prompt `firewall add` has (cli.py:458).
- No reuse misses found: `ProcessSupervisor`, `OsStep`, `Control`, `MENU_ITEMS`, `poll_once` reused.

Not checked: real Windows ctypes/COM/Win32/Tauri behaviour; the `cfg(windows)` Rust (not compiled here); migration.rs and
installer hooks (ticket 34, outside this batch).
