# 01-fix: Supervisor stops when its parent closes

Lane: quick
Branch: feature/desktop-app
Platform: App
Status: todo
Round: 1
Blocked by: 01-app-bridge-supervise.md
Terms: supervisor
Seam: in `bridge/hermes_remote_bridge/cli.py` (`cmd_supervise`) and `bridge/hermes_remote_bridge/supervise.py` (`Supervisor`): a watcher that calls `stop()` when stdin reaches EOF, tested with a fake stdin.
Test command: cd bridge && uv run pytest -q
Verify: None (unit tests are the check).

## What to build

Ticket 01 requires that the parent closing stops the child and exits 0. Add it: when the supervisor's standard input is closed by the parent (the desktop app), it stops the child and exits 0, the same as SIGTERM. It must not trigger when stdin is a terminal or absent at startup (a user running it by hand, or a service with no stdin).

## Acceptance criteria

- [ ] EOF on stdin stops the child and `run` returns 0 (fake stdin, fake child)
- [ ] A TTY or missing stdin does not stop the supervisor
- [ ] Existing supervise tests stay green
