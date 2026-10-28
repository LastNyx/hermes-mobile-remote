# Review batch 2, round 2 (tickets 05, 06, 07, 06-fix, 07-fix)

Tests: `suites.py --feature desktop-app` -> TEST PASS (bridge PASS, desktop PASS, no baseline).

## Spec
No findings.
- 05: all four criteria have tests (fake HTTP server, fallback, exact argv + CREATE_NO_WINDOW, per-OS paths).
- 06: each UI criterion has a vitest case in `desktop/src/ui.test.js`; every api.js call has a screen.
- 07: change list, uv on PATH vs download + SHA-256, exact `uv sync` argv/env, update ordering, Setup screen are all tested (`setup.rs`, `ui.test.js`).
- 06-fix: one `config_value` parser (`bridge.rs`) used by port and terminal shell (`control.rs`). Existing tests unchanged.
- 07-fix: `XDG_DATA_HOME` honoured, empty treated as unset (`bridge.rs:85,102`); stale files removed on re-copy and nested `tests` kept (`setup.rs:270-305`); warning uses `self.wait`; Setup.vue releases the old listener.
- Callers of the changed names (`settings_in`, `read_terminal_shell`, `copy_dir`, `run_with`) are all inside the diff and compile.

Noted, not a finding (no criterion): step 5 "after every app update" is not triggered automatically. The UI only offers "Run setup again" (07 Result says so). The `settings_in` start_at_logon change has no test, since the backend always reports off.

## Standards
No findings. No existing helper was left unused. `config_value` removed the duplicated parser. Glossary terms are respected.

Not checked: Windows build, real GitHub download, real `uv sync`, `RealFs::which`.

RESULT: PASS
