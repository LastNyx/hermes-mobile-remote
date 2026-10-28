# Review batch 3 (tickets 20, 21, 22 + 21 fixes), round 2

Tests: `TEST PASS | bridge PASS | desktop PASS | no baseline`.
Per planner note, round-1 item 1 (spawn deadlock) is disproved; the production code
(`run_mode.rs:199`) is fine and `a_second_spawn_with_a_live_child...` guards it. Not re-raised.

## Spec

1. **21-fix-spawn-relock**: both criteria met and tested (`run_mode.rs:483`, `:522`). No finding.
2. **21-fix-failed-switch-restores**: exit 76, other exit code and spawn error all restore (`run_mode.rs:89-94`, tests `:418`, `:440`). But the same ticket's idea ("after the service was stopped and disabled") is not applied to the earlier error returns in the same arm: `run_mode.rs:74-78`. If `status()` says still active, or `stop`/`disable`/`status` fail via `?`, the service stays disabled while the saved mode is always-on. Untested (the Result admits it). Fix ticket 21-fix-still-running-restores.

## Standards

1. **21-fix-dead-note**: `NO_SANDBOX_NOTE` is gone; no remaining uses. No finding.
2. possible: restore path discards `enable_now` errors (`let _ =`, `run_mode.rs:92`), so a failed restore is invisible to the user. Not ticketed.

Not checked: real systemd, tray, notifications, Windows cfg branches; tickets 20 and 22 code unchanged since round 1.
