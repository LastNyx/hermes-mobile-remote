# 01-fix: Supervisor stops when its parent closes

Lane: quick
Branch: feature/desktop-app
Platform: App
Status: review
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

## Result

Commit 45c297d. `cd bridge && uv run pytest -q`: 176 passed.
- AC1: test_stdin_eof_stops_child_and_run_returns_0 (fake stdin gated on launch, blocking fake child).
- AC2: test_tty_or_missing_stdin_is_not_watched (tty fake that fails if read, and None).
- AC3: the existing supervise tests pass unchanged.
Reuse: used existing `Supervisor.stop()`; nothing else found for stdin watching. `cmd_supervise` calls `sup.watch_stdin(sys.stdin)`. A /dev/null stdin (a service) is also skipped.
Notes added: 1.
Not checked: a real parent closing a real pipe (the real `sys.stdin` path in `cmd_supervise`); Windows pipes.

Planner Verify: PASS real supervise with stdin pipe: closing it -> child gone, exit 0, log 'supervisor: stopped'
