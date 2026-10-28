# 34-fix: Uninstall must stop the bridge the installed app is running

Lane: quick
Branch: feature/desktop-app
Platform: Windows
Status: in-progress
Round: 1
Blocked by: 34
Terms: uninstall, bridge
Seam: `desktop/src-tauri/src/migration.rs` `Uninstall` (its "stop the bridge" step) and the `--uninstall` branch in `desktop/src-tauri/src/lib.rs`; test in `migration.rs` tests with a fake process list.
Test command: cd desktop && bash test.sh

## What to build

`hermes-remote-desktop --uninstall` runs in a new process, so `run_mode::app_child().stop()` does nothing: that
process never started the bridge. The uninstall must really stop the bridge that the installed app started
(`hermes-remote-bridge supervise`) before it removes autostart, the old task and the firewall rule. Do it through
an injected seam, for example by finding and ending the running bridge and app processes of this install by
executable path (the `ProcessList` already used by migration), and keep the order step 1 = stop.

## Acceptance criteria

- [ ] With a fake process list holding a running bridge process of this install's exe, `Uninstall::run` ends it before the `reg delete` call.
- [ ] A process with another path (or the uninstall process itself) is never ended.
- [ ] No running bridge: uninstall continues without error.
