# 03-fix: Quoted Windows path in `terminal_shell` resolves

Lane: quick
Branch: feature/desktop-app
Platform: App
Status: in-progress
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
