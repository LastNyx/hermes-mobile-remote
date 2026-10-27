# 03-fix: Quoted Windows path in `terminal_shell` resolves

Lane: quick
Branch: feature/desktop-app
Platform: App
Status: done
Round: 1
Blocked by: 03-app-terminal-shell.md
Terms: terminal shell
Seam: in `bridge/hermes_remote_bridge/terminal.py` (`default_shell(cfg)`), tested with `os.name` faked to `nt`.
Test command: cd bridge && uv run pytest -q
Verify: None (unit tests are the check).

## What to build

On Windows a `terminal_shell` such as `"C:\Program Files\Git\bin\bash.exe" -l` must be split into the program path without its quotes and the argument `-l`, then resolved on PATH. Today the quotes stay in the first element, so the override is rejected and the default shell is used. Keep backslashes in the path intact. POSIX behaviour is unchanged.

## Acceptance criteria

- [ ] With `os.name` faked to `nt`, a quoted path containing a space and a backslash plus one argument gives argv `[path without quotes, arg]`
- [ ] An unquoted override and the POSIX path behave as before

## Result
Commit: f88b683. Tests: `178 passed` (cd bridge && uv run pytest -q).
- Criterion 1: test_windows_quoted_override_path_loses_quotes_keeps_backslashes (red before fix: fell back to cmd.exe).
- Criterion 2: test_windows_unquoted_override_unchanged (nt) plus existing test_override_used_verbatim_when_it_resolves and test_non_windows_default_unchanged (POSIX).
- Reuse: kept existing `shlex.split`; no existing helper for stripping quotes, added one line in `default_shell`.
- Notes added: none.
Cause: terminal.py `default_shell` used `shlex.split(posix=False)` on nt, which keeps surrounding quotes in tokens, so `shutil.which` failed on the quoted path.
Not checked: real Windows run.

Planner Verify: SKIP Verify None; unit tests (os.name=nt fake) are the check
