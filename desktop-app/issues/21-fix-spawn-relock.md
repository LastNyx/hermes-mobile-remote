# 21-fix: spawning twice must not hang; test ModeControl

Lane: full
Branch: feature/desktop-app
Platform: Linux
Status: review
Round: 1
Blocked by: 21
Terms: app-only mode
Seam: `run_mode.rs` `ProcessSupervisor::spawn` and `ModeControl`, tested through the injected `Spawner` (fake proc) and a fake `ChildSupervisor`.
Test command: cd desktop && bash test.sh

## What to build

Calling `spawn` on a supervisor that already has a running child must return Ok at once without starting a
second bridge and without blocking: take the "is there a live child" decision without holding the state lock
while asking for the exit code. Add a test that spawns twice with a fake proc and sees one spawn call, and a
test that `ModeControl` in app-only mode sends start/stop to the child, refuses start at login, and in
always-on mode sends them to the inner control.

## Acceptance criteria

- [ ] Second `spawn` with a live child returns Ok, spawner called once, test does not hang
- [ ] `ModeControl` start/stop/start-at-logon behaviour tested for both modes

## Result
Commit d00d696. `bash test.sh`: 28 Rust tests pass, 15 UI tests pass, build ok.
- Criterion 1: `a_second_spawn_with_a_live_child_returns_ok_without_a_second_bridge` (thread + 5s timeout, spawner called once).
- Criterion 2: `mode_control_routes_start_stop_and_start_at_logon_by_mode` (both modes, fake ChildSupervisor and fake inner Control).
- Reuse: no new code; the existing fakes (FakeSpawner, FakeChild) were reused; nothing else to reuse for FakeInner.
- Both tests passed before any code change: no red. The spawn code in `run_mode.rs` already does not hold the lock across `exit_code()`, because the lock guard in a lazy `&&` operand is dropped before the next operand runs. I left the production code alone. The tests now guard against a regression.
- Notes added: 1 line in .scratch/dev-notes.md.
Cause: UNCONFIRMED. The hang from the ticket could not be reproduced with the fake proc on the current code.
Not checked: the real OsSpawner double spawn; the ticket's `Verify` line.

Planner Verify: SKIP unit tests (spawn twice, ModeControl) are the check
