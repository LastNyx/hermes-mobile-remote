# 06-fix: One reader for `key = value` config lines

Lane: quick
Branch: feature/desktop-app
Platform: App
Status: in-progress
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
