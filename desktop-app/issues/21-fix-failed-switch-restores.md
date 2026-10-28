# 21-fix: a failed switch to app-only must restore always-on

Lane: full
Branch: feature/desktop-app
Platform: Linux
Status: todo
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
