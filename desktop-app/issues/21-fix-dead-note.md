# 21-fix: remove the unused no-sandbox constant

Lane: full
Branch: feature/desktop-app
Platform: Linux
Status: done
Round: 1
Blocked by: 21
Terms: app-only mode
Seam: refactor only, existing tests are the check: `run_mode.rs` constant `NO_SANDBOX_NOTE`.
Test command: cd desktop && bash test.sh

## What to build

`NO_SANDBOX_NOTE` in `run_mode.rs` is never used (the note lives in Settings.vue). Remove it.

## Acceptance criteria

- [ ] No unused no-sandbox constant remains; all tests still pass

## Result
Commit: eedf579
Tests: `bash test.sh` green: cargo 30 passed, vitest 15 passed, vite build ok.
Criterion 1 (no unused no-sandbox constant, tests pass): covered by the existing suite. This is a refactor-only ticket, so I wrote no new test. I removed `NO_SANDBOX_NOTE` from `run_mode.rs`; Grep showed it had no other uses.
Reuse: nothing to reuse, this was a deletion.
Notes added: none.
Not checked: nothing

Planner Verify: SKIP refactor; suite green
