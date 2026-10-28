# Review batch 6, round 1: ticket 35 (commit 0692a8f)

Tests: `TEST PASS | bridge PASS | desktop PASS | no baseline`.

## Spec

1. Untested criterion / missing D17 checks (`.github/workflows/ci.yml`, `bridge-windows` job, lines 20-140). Ticket: "replaces the PowerShell CI steps with the D17 checks"; Verify: "CI Windows job green with the new checks (native probes vs fixtures, NSIS install, bridge dies with app, uninstall)". Native probes, the cmdlet comparison, supervise (stdin close, exit 76) and the "No PowerShell" grep are present. No step installs from the built NSIS, and none runs the uninstaller to check that autostart and the old task are gone. The `desktop` job only builds the bundle. "bridge dies with app" is covered only at supervise level, not with the app. A Windows-only gap, so it cannot be run here.
2. Criteria "No `.ps1` left" and "no `powershell` spawn in bridge or app code" hold. `git ls-files` finds no `.ps1`/`.cmd`, and the remaining hits are `terminal.py` (the user's shell, D13), `migration.rs` (matching the old command line) and comments. No caller of the removed `ps`, `ps_json`, `encode_script`, `run_elevated` or `powershell` remains outside the diff, other than `winapi.run_elevated`, which is a separate function that is still used.

## Standards

1. Stale comment, `.github/workflows/ci.yml:20`: "the real PowerShell cmdlets the Windows host calls and the real installer". The host no longer calls cmdlets, and no installer runs in this job.
2. Possible: `bridge/hermes_remote_bridge/host/windows.py:6` now says the ctypes/COM calls "have not yet run on a real Windows machine". The step at ci.yml:45 runs them on a real runner. The wording is also inconsistent with the line removed.

Not checked: the Windows CI job (cannot run on Linux); the `README.md` references to `install.cmd` and `pair.cmd` (user docs, ticket 10).

## Fix tickets
- 35-fix-d17-installer-checks (Spec 1, Standards 1)

RESULT: FAIL 1 spec, 2 standards: CI lacks the D17 NSIS install and uninstall checks
