# 06-fix: One reader for `key = value` config lines

Lane: quick
Branch: feature/desktop-app
Platform: App
Status: done
Round: 1
Blocked by: 06-app-ui.md
Terms: bridge paths, terminal shell
Seam: refactor only, existing tests are the check: `BridgePaths::port` in `desktop/src-tauri/src/bridge.rs` and `read_terminal_shell` / `with_terminal_shell` in `desktop/src-tauri/src/control.rs`.
Test command: cd desktop && bash test.sh
Verify: None (unit tests are the check).

## What to build

The port lookup and the terminal-shell lookup each parse `key = value` lines of the bridge `config.toml` (strip a trailing comment, trim, unquote). Make one shared helper in `bridge.rs` and use it from both places. Also make the Settings "Start at logon" value stop being a hard-coded false: `settings_in` should take it from the control backend (the `Unsupported` backend reports off), so a real backend can fill it later.

## Acceptance criteria

- [ ] Only one place parses a `key = value` config line; port and terminal shell both use it
- [ ] Existing port and terminal-shell tests pass unchanged

## Result
Commit: c80e94c
Tests: `bash test.sh` passes (Rust 12 passed, vitest 12 passed, build ok).
Refactor ticket, so no new tests. The existing port and terminal-shell tests cover both criteria and pass unchanged.
- Criterion 1: `bridge::config_value` is now the only `key = value` parser. `BridgePaths::port` and `read_terminal_shell` both call it.
- Criterion 2: the existing tests pass unchanged.
- Also done: `Control::start_at_logon()` was added (`Unsupported` returns false). `settings_in(dir, &dyn Control)` uses it instead of a hard-coded false.
Reuse: no existing helper to reuse. I merged the two parsers into one.
Notes added: 1 line in `.scratch/dev-notes.md`.
Not checked: the `settings_in` start_at_logon change has no test. It is covered only by compiling, because the backend always reports off.

Planner Verify: SKIP refactor; suite green (worker)
