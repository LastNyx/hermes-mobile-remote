# Review batch 2 (tickets 05, 06, 07), round 1

Tests: `TEST PASS | bridge PASS | desktop PASS | no baseline`.

## Spec

1. **Linux data dir ignores `XDG_DATA_HOME`** (07 planner notes: "Linux `$XDG_DATA_HOME/hermes-remote` (default `~/.local/share/hermes-remote`)"). `bridge.rs:102` hardcodes `home/.local/share/hermes-remote/venv`; `setup.rs:145` derives `data_dir()` from it. uv, bridge source and venv land in the wrong place when the variable is set. No test sets `XDG_DATA_HOME` (untested criterion). The Result admits it. Fix ticket: `07-fix-xdg-data-home.md`. (Ticket 05 text lists only the default path; the default stays the same.)
2. `control.rs:79` `settings_in` returns `start_at_logon: false` unconditionally, so the Settings checkbox (`Settings.vue`) always shows "off" whatever the real state. Not a listed criterion, but the screen shows a wrong value once a backend exists (possible; folded into fix 2 below).

## Standards

1. **Duplicated logic**: `key = value` line parsing exists twice: `bridge.rs:121-130` (`port()`) and `control.rs:59-64` (`read_terminal_shell`, plus line replace in `with_terminal_shell`). One shared config-line reader should serve both. Fix ticket: `06-fix-reuse-config-line.md`.
2. Possible: `Setup.vue` `cont()` subscribes to `setup-progress` on every Continue/Try again without releasing the previous listener (`stop` is overwritten), so retries leave duplicate listeners until unmount.
3. Possible: `copy_tree` (`setup.rs:269`) never removes files deleted from the bundled source, so an app update can leave stale bridge files in `<data>/bridge`. Also, it skips any directory named `tests` at any depth.

Not checked: real GitHub download, real `uv sync`, Windows build, Tauri event delivery, `RealFs::copy_dir`/`which` (no tests reach them), real `tauri dev` window.

RESULT: FAIL 1 spec, 1 standards: Linux data dir ignores XDG_DATA_HOME (ticket 07)
