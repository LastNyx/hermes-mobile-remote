# 03-fix: Removing a host also removes its gateway credentials

Lane: full
Branch: feature/gateway-login
Status: done
Round: 1
Blocked by: 03
Terms: host kind, GatewayCredentialStore, Profile
Seam: the host-removal path in `AppViewModel` (around line 656, where the pairing and drafts files are cleared) plus `GatewayCredentialStore.clear`.
Test command: cd android && ./build.sh test
Verify: None.

## What to build

Ticket 03 says "Removing a host removes its store". Today removing a host clears only the bridge pairing and the drafts, so a removed gateway host leaves encrypted access and refresh tokens in `gateway_<id>`. When a host is removed, also clear that host's gateway credential store. The clear must be safe for a bridge host (nothing stored). Add a JVM test that saves credentials for a host, runs the removal step and finds the store empty; if the ViewModel cannot be tested on the JVM, move the "clear everything of host X" step into a small function that can.

## Acceptance criteria

- [ ] Removing a gateway host leaves no `gateway_<id>` data
- [ ] Forgetting the ACTIVE host (`unpair()`) clears the same data as `forgetPc` (grep both paths; one shared step)
- [ ] Removing a bridge host behaves as before
- [ ] JVM test covers the clearing

## Constraints

- Do not touch `bridge/`, `install.*`, `tray.*`.

## Result

Commit 1cbf750. `./build.sh test` printed nothing; GatewayStoreTest report: 5 tests, 0 failures.
- Criteria 1, 3, 4: `removingAHostClearsItsPairingDraftsAndGatewayStore` (gateway_<id>, pairing, drafts cleared; other host untouched; bridge id with no gateway data is harmless).
- Criterion 2: `unpair()` and `forgetPc()` both call the new `clearHostData(id)` -> `ProfileStore.clearHostFiles` (verified by code reading, not a ViewModel test).
Reuse: none existed for a shared clear; the new step reuses the file-name helpers in ProfileStore.
Notes added: none.
Not checked: the red run before the code (the new function did not exist, so it would not compile); the ViewModel wiring itself on a device.

Planner Verify: PASS 1cbf750; full suite green, GatewayStoreTest 5; old clear calls were prefs.clear() on same files, behaviour equal; unpair+forgetPc share clearHostData

Planner Verify: PASS batch 1 review round 2 PASS (review-1-2.md)
