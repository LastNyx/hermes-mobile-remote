# Review batch 3 (tickets 20, 21, 22), round 1

Tests: `TEST PASS | bridge PASS | desktop PASS | no baseline`.

## Spec

1. **Ticket 21, spawn deadlocks when a child already exists** (`run_mode.rs`, `ProcessSupervisor::spawn`, the first `if`).
   `self.state.lock().unwrap().child.is_some() && self.exit_code().is_none()`: the lock guard is a temporary
   that lives to the end of the whole condition, and `exit_code()` locks the same mutex. With a child present
   (app-only mode: app start spawns it, then the tray "Start bridge" or the switch calls `start`) this
   self-deadlocks. Untested: no test calls `spawn` twice. Fix ticket 21-fix-spawn-relock.
2. **Ticket 21, "old owner stopped before new one starts... if supervise exits 76, show it"**: on exit 76 the
   switcher returns the error but has already stopped and disabled the systemd service, and the mode stays
   `always-on` (`run_mode.rs` `RunModeSwitcher::set`, AppOnly arm). The bridge is left down while the app says
   always-on. Any other early exit code is also ignored. Fix ticket 21-fix-failed-switch-restores.
3. **Ticket 21, untested behaviour**: `ModeControl` (start/stop redirect, start-at-logon refused in app-only,
   `run_mode.rs`) has no test; the Result admits it. Covered by fix 1.
4. **Ticket 22, "untrusted network notification that opens the window"**: the window is shown when the
   notification fires, not when it is clicked (documented in Result, plugin limitation). Accepted, no ticket.

## Standards

1. `NO_SANDBOX_NOTE` (`run_mode.rs:17`) is never used; Settings.vue repeats the text. Dead code.
2. possible: `TrayPolicy` (`tray.rs`) only wraps one closure call, and `should_notify` wraps `contains`;
   speculative indirection. Not ticketed beyond item 1.

Not checked: real systemd, tray, notifications, Windows build of cfg branches.

Fix ticket for item 1: 21-fix-dead-note.
