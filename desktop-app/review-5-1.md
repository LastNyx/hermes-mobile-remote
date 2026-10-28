# Review batch 5, round 1 (tickets 08, 34)

Tests: `TEST PASS | bridge PASS | desktop PASS | no baseline`.

## Spec

1. **34, "Uninstall order: bridge stopped before autostart/firewall removal" is satisfied only on paper.**
   `lib.rs` `run()` runs `--uninstall` in a fresh process and calls `run_mode::app_child().stop()`
   (`migration.rs` Uninstall::run, `run_mode.rs:225-227`). `ProcessSupervisor::stop` returns `Ok(())` at once
   when this process never spawned a child. In the uninstall process the supervisor is brand new, so the bridge
   run by the installed app is not stopped by this step. The test uses a fake `Child`, so it cannot see this. The
   bridge can keep running (holding the venv files) if the installer's own app-running check does not end the
   app first. Fix ticket: 34-fix-uninstall-stops-running-bridge.md.
2. 08: all criteria have tests (Rust `bundle_type_picks_updater_or_release_check`,
   `banner_only_for_a_newer_desktop_release_by_semver`; vitest UpdateBanner, including the link click via `openUrl`
   after 675368b). The `shell:default` capability and the registered shell plugin cover `plugin:shell|open`. No
   finding.
3. 34 criterion 3 ("Data dirs untouched") test is weak, as the Result admits: Migration has no data access, so
   the test cannot fail. Noted, not a finding.

## Standards

1. possible: `updates.rs` `update_check` and `update_install` (lines ~170-200) build the same `UpdateCheck { bundle,
   current, releases, updater }` twice. A small shared constructor would remove the duplicate. No fix ticket.
2. Reuse check: no existing helper for process matching, release listing or the marker was found. No finding.

Not checked: the real GitHub request, the real Tauri updater download and restart, NSIS hook behaviour on Windows
(including whether the Tauri uninstaller ends the running app first), `cargo xwin check`.

RESULT: FAIL 1 spec, 1 standards
