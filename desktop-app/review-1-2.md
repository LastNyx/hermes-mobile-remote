# Review batch 1, round 2 (tickets 01, 02, 03 and their fixes)

Tests: `TEST PASS | bridge PASS | desktop PASS | no baseline`.

## Spec

1. **01-fix, untested criterion.** "It must not trigger when stdin is ... absent at startup (... a service with no stdin)": `Supervisor.watch_stdin` (supervise.py:53-57) skips a character-device stdin (`/dev/null`) via `S_ISCHR`, but `test_tty_or_missing_stdin_is_not_watched` (test_supervise.py:148-152) covers only a TTY and `None`. That branch has no test. Fix: 01-fix-supervise-devnull-test.
2. 03-fix: quoted `nt` override now loses its quotes and keeps backslashes (terminal.py:36-37); both criteria have tests. Round-1 spec findings 1 and 3 are resolved (stdin EOF watcher with a stop-before-launch race already handled at supervise.py:85). No other finding.

## Standards

1. Round-1 reuse finding resolved: `cmd_pair` and `/v1/local/pair` both use `local_api.pairing_uri` and `qr_png`; `cmd_devices` builds one `DeviceStore`.
2. Possible (carried over, unchanged): `app.py` `local_pair` calls the private `cli._bridge_urls`; a neutral module would be cleaner.

Not checked: real parent closing a real pipe through `cmd_supervise` and `sys.stdin` (cli.py:175 has no test); Windows; the `desktop/` crates commit (172f77e) is outside these tickets.

RESULT: FAIL 1 spec, 0 standards: 01-fix `/dev/null` stdin skip in watch_stdin has no test
