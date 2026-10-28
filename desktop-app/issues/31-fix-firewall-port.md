# 31-fix: firewall `--port` honoured on the non-elevated path

Lane: quick
Branch: feature/desktop-app
Platform: App
Status: todo
Round: 1
Blocked by: 31
Terms: firewall rule, elevation
Seam: `bridge/hermes_remote_bridge/cli.py` `cmd_firewall` and `host/windows.py` module docstring; tests in `bridge/tests/` (existing cli firewall tests).
Test command: cd bridge && uv run pytest -q
Verify: Planner: suite green.

## What to build

- In `firewall add` without `--elevated`, use the `--port` value (falling back to the configured port) for the state
  check, the shown command, and the elevation, instead of always the configured port.
- Update the stale `windows.py` module docstring: PowerShell is no longer used for the firewall; `run_elevated` is unused.

- (planner, review 4-1 possible) `firewall remove` without `--yes` asks the same confirmation as `firewall add`.

## Acceptance criteria

- [ ] `firewall add --port 9999 --yes` on a fake Windows host elevates `firewall add --port 9999 --elevated`
- [ ] Without `--port` the configured port is used (existing tests green)

## Result
