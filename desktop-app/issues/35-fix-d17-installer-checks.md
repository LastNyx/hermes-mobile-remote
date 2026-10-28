# 35-fix: CI Windows job lacks the D17 installer checks

Lane: quick
Branch: feature/desktop-app
Platform: Windows
Status: done
Round: 1
Blocked by: 35
Terms: cleanup
Seam: `.github/workflows/ci.yml` (planner-run, protected); stale comment at line 20 is a refactor-only edit, existing tests are the check.
Test command: cd bridge && uv run pytest -q

## What to build

In the CI Windows job: add steps that install from the built NSIS bundle (silent), check that the app starts the bridge and the bridge stops when the app exits, then run the uninstaller and check that the autostart entry and the old Scheduled Task are gone (D17). Update the job comment at line 20 so it no longer says the host calls cmdlets or that the job runs the installer unless it now does. Optionally reword the `host/windows.py` docstring so it matches the real-Windows probe step.

## Acceptance criteria

- [ ] The Windows job installs from the NSIS bundle and checks that the bridge dies with the app
- [ ] The Windows job runs the uninstaller and checks that autostart and the old task are removed
- [ ] The comment at the top of the job matches what the job does

## Result

Planner Verify: PASS planner fixed directly (a3f094d): CI desktop job on Windows installs the debug NSIS silently, checks bridge up / dies on taskkill /F / uninstall removes Run key + old task; stale comments fixed; actio
