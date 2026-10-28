# 21-fix: a switch to app-only that finds the service still running must restore always-on

Lane: full
Branch: feature/desktop-app
Platform: Linux
Status: todo
Round: 2
Blocked by: 21
Terms: app-only mode, always-on mode
Seam: `run_mode.rs` `RunModeSwitcher::set` (AppOnly arm), tested with the fake `ServiceControl` (`active: true`) and fake `ChildSupervisor` call log.
Test command: cd desktop && bash test.sh

## What to build

In the switch to app-only, the service is stopped and disabled first. If the status check then says the service
is still running, or the status check or the disable step itself fails, the function returns an error with the
service left disabled while the saved mode stays always-on. Put the service back (enable and start it again)
before returning the error, the same way the child-failure path already does. The child is never started in
this case, so there is nothing to stop.

## Acceptance criteria

- [ ] Service still active after stop/disable: the call log ends with the service enabled/started again, the child is never spawned, mode stays always-on, the error is returned
- [ ] Disable or status failing also re-enables the service and returns that error

## Result
