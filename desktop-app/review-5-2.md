# Review batch 5, round 2 (tickets 08, 34, 34-fix-uninstall-stops-running-bridge)

Tests: `suites.py --feature desktop-app` -> TEST PASS (bridge PASS, desktop PASS, no baseline).

## Spec
No findings.
- 08: all four criteria have a test (`bundle_type_picks_updater_or_release_check`, `banner_only_for_a_newer_desktop_release_by_semver`, the vitest UpdateBanner tests). The resync marker has a test. A command with no screen is not the case: `UpdateBanner.vue` is shown by `App.vue`.
- 34: order, argv, path-shaped match and the data dirs each have a test. The data-dir test is weak by construction (`Migration` has no data access), but it does run the criterion.
- 34-fix: `uninstall_ends_this_installs_bridge_before_reg_delete_and_spares_others` covers criteria 1 and 2 (kill 5 and 6 before `reg delete`; the other path, an empty exe and the own pid are spared). `uninstall_without_a_running_bridge_continues` covers criterion 3. The Result has a `Cause:` line.
- Callers of the changed `Uninstall` struct and `ProcInfo` (new `exe` field): only `lib.rs:26` and the tests in `migration.rs`, all updated.

## Standards
No findings. No existing helper was missed: Grep finds the only `sysinfo` use in `migration.rs`, and `ProcessList` is reused by the fix.
- Possible, not worth a ticket: `updates.rs:166-195` builds the same `UpdateCheck` twice (`update_check`, `update_install`).

Not checked: real Windows behaviour of `SysProcesses` `with_exe` and process kill, the real GitHub request and Tauri updater download, the NSIS hook (all reported as unchecked by the developer; no Windows host here).

RESULT: PASS
