# 21-fix: a failed switch to app-only must restore always-on

Lane: full
Branch: feature/desktop-app
Platform: Linux
Status: in-progress
Round: 1
Blocked by: 21
Terms: app-only mode, always-on mode
Seam: `run_mode.rs` `RunModeSwitcher::set`, tested with the fake `ServiceControl` and fake `ChildSupervisor` call log.
Test command: cd desktop && bash test.sh

## What to build

When the switch to app-only fails after the service was stopped and disabled (port conflict exit 76, or any
other early exit of the child, or a spawn error), put the old owner back: stop the child, enable and start the
service again, keep the saved mode `always-on`, and still return the error (port-conflict message for 76).
Any early exit code, not only 76, counts as a failure with its own message.

## Acceptance criteria

- [ ] After exit 76 the call log ends with the service being enabled/started again; mode stays always-on
- [ ] Another early exit code and a spawn error also restore the service and return an error

## Result

Commit: 5f6cef4
Tests: `bash test.sh` green: 30 Rust passed, 15 vitest passed, vite build ok.
- Criterion 1 (exit 76, log ends child.stop, service.enable_now, mode always-on, port-conflict message): `a_port_conflict_restores_the_service_and_keeps_always_on`
- Criterion 2 (other exit code, spawn error): `another_early_exit_or_a_spawn_error_also_restores_the_service`
- Reuse: `write_mode`, `PORT_CONFLICT`, existing fakes/`switch` helper; no existing restore helper found.
- Notes added: 1 line in .scratch/dev-notes.md.
- I did not watch the new tests fail before the fix; I wrote them and the fix in the same step, then ran the suite once.
Not checked: real systemd/bridge run; the existing "service still running" error path does not restore (not in ticket).
