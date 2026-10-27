# Review batch 1, round 1 (tickets 01, 02, 03)

Tests: `TEST PASS | bridge PASS | desktop PASS | no baseline`.

## Spec

1. **01, partial requirement.** "SIGTERM / Ctrl+C / the parent closing stops the child and exits 0": `cmd_supervise` (cli.py:168-176) handles SIGINT/SIGTERM only. Nothing watches for the parent closing (stdin EOF), and the Result admits it. The desktop app relies on this to avoid an orphaned bridge. Fix: 01-fix-supervise-parent-close.
2. **03, wrong on Windows.** "Override used verbatim when it resolves": `terminal.py:35` uses `shlex.split(..., posix=False)` on Windows. That keeps the quote characters, so `"C:\Program Files\Git\bin\bash.exe" -l` gives an argv[0] that includes the quotes. `shutil.which` fails on it, so the override is ignored with a warning. Untested: the tests use only unquoted commands. Fix: 03-fix-shell-quoted-path.
3. Criteria for 02 (secret file, 403 paths, relay allowlist, CLI equality) each have tests; no other finding.

## Standards

1. **Duplicated logic (reuse).** `local_api.qr_png` (local_api.py:39-45) and the `hermesremote://pair?` URI building (app.py:441) repeat `cli.py:341-345` (`urlencode` plus `segno.make(...).save(kind="png", scale=8, ...)`). `local_api` already took over `pairing_payload` from `cmd_pair`, but `cmd_pair` still has its own copy. It should call `qr_png` and one shared pairing-URI helper. Fix: 02-fix-reuse-qr.
2. Possible: `cmd_devices` (cli.py:433-436) builds `DeviceStore` twice, once for `rows` and once inside `devices_doc`.
3. Possible: `app.py` imports `cli` and calls the private `cli._bridge_urls` (app.py `local_pair`). The web layer depends on the CLI module; moving it to a neutral module would be cleaner.

Not checked: real child process and signals via `popen_launcher`; Windows; the `desktop/` crates commit (172f77e) is outside tickets 01-03 and was not reviewed.

RESULT: FAIL 2 spec, 1 standards: 03 quoted Windows override path never resolves
