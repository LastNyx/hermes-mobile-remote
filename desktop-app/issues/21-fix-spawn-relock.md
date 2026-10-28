# 21-fix: spawning twice must not hang; test ModeControl

Lane: full
Branch: feature/desktop-app
Platform: Linux
Status: todo
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
