# 34-fix: Uninstall must stop the bridge the installed app is running

Lane: quick
Branch: feature/desktop-app
Platform: Windows
Status: done
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

## Result
Commit: 02638c8. `bash test.sh`: 53 rust tests + 20 UI tests passed.
Criteria 1 and 2: `uninstall_ends_this_installs_bridge_before_reg_delete_and_spares_others`. Criterion 3: `uninstall_without_a_running_bridge_continues`.
The tests were written together with the code, so I did not see them fail first.
Reused: the existing `ProcessList`/`SysProcesses` in migration.rs. I added `ProcInfo.exe` and `Uninstall.{procs,own_pid,app_exe}`, and lib.rs passes the real values.
Notes added: 1 line in `.scratch/dev-notes.md`.
Cause: `--uninstall` runs in a new process, so `app_child().stop()` has no child to stop. The bridge started by the installed app was never ended (migration.rs `Uninstall::run`).
Not checked: a real Windows uninstall. `SysProcesses` exe lookup (`with_exe`) was only compiled and not run against live processes.

Planner Verify: PASS tests with fake process list; xwin check clean. Note: killing the app exe closes its Job Object, which also ends serve (python.exe child not matched by path)
