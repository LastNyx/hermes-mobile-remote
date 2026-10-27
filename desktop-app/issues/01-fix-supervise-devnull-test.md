# 01-fix: Test that a character-device stdin is not watched

Lane: quick
Branch: feature/desktop-app
Platform: App
Status: todo
Round: 2
Blocked by: 01-fix-supervise-parent-close.md
Terms: supervisor
Seam: in `bridge/tests/test_supervise.py`, a test of `Supervisor.watch_stdin` with a stream whose file descriptor is a character device (a real `/dev/null` opened for reading), no production change unless the test fails.
Test command: cd bridge && uv run pytest -q
Verify: None (unit tests are the check).

## What to build

Ticket 01-fix says the supervisor must not trigger when it runs as a service with no stdin. `watch_stdin` skips a character-device stdin (`/dev/null`) for this, but no test covers that branch. Add a test that passes an opened `/dev/null` and checks that it returns False, starts no watcher and does not stop the supervisor. Skip it where `/dev/null` does not exist.

## Acceptance criteria

- [ ] A character-device (`/dev/null`) stdin returns False and the supervisor is not stopped
- [ ] Existing supervise tests stay green

## Result
