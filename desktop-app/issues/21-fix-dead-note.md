# 21-fix: remove the unused no-sandbox constant

Lane: full
Branch: feature/desktop-app
Platform: Linux
Status: todo
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
