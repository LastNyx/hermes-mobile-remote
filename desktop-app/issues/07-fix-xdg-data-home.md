# 07-fix: Linux data dir honours `XDG_DATA_HOME`

Lane: quick
Branch: feature/desktop-app
Platform: App
Status: todo
Round: 1
Blocked by: 07-app-first-run-setup.md
Terms: per-user venv, bridge paths
Seam: `BridgePaths::resolve` in `desktop/src-tauri/src/bridge.rs` (Linux branch), `copy_tree` / the wait message in `desktop/src-tauri/src/setup.rs`, and `desktop/src/Setup.vue`; tested with a fake env lookup, a temp dir and vitest.
Test command: cd desktop && bash test.sh
Verify: None (unit tests are the check).

## What to build

On Linux the data directory (venv, uv, bridge source) is `$XDG_DATA_HOME/hermes-remote`, defaulting to `~/.local/share/hermes-remote` when the variable is unset or empty. Today the variable is ignored. Windows is unchanged.

Planner adds (review 2-1 possible items, confirmed in code):
- Update path: before copying the bundled bridge source, remove `<data_dir>/bridge` (never the venv), so files
  deleted from the bundle do not linger. Skip `tests` only at the top level of the bridge source.
- The wait warning says the configured wait (`self.wait`) in seconds, not a hardcoded "20 s".
- `Setup.vue`: release the previous `setup-progress` listener before subscribing again on Continue / Try again.

## Acceptance criteria

- [ ] With `XDG_DATA_HOME` set, the venv, the bridge exe and setup's uv and bridge-source directories sit under it
- [ ] Without it, the paths are exactly as before
- [ ] A re-copy removes a file that is no longer in the source; a nested folder named `tests` is copied (temp dir test)
- [ ] Warning text uses the configured wait
- [ ] UI: Try again twice leaves one listener (vitest counts the mocked listen/unlisten)

## Result
