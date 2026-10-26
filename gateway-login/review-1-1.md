# Review batch 1, round 1 (tickets 01-04)

Tests: suites.py ended `TEST FAIL`. bridge: 2 failures (test_media_serves_only_images..., test_project_idea_reads_and_writes...) are Windows environment (symlink privilege WinError 1314, CRLF); the diff does not touch `bridge/`, so not caused by this branch (no baseline file, so the runner counts them new). android: `./build.sh` is "not recognized" under the runner, so the android suite did NOT run here; planner reports it green (158 tests).

## Spec
- 03 "Removing a host removes its store": removal at AppViewModel.kt:656-657 clears only pairing and drafts; `gateway_<id>` tokens stay. Fix: 03-fix-remove-host-clears-gateway-store.
- 02 `GatewayAuth.logout` (GatewayAuth.kt:275) posts to `/auth/logout` with a Bearer, but W15 marks that as unknown; also no test. Possible: keep best effort, add a test that local tokens are wiped even when the call fails (covered by fix 03-fix-remove-host... only partly; note for ticket 05/06).
- 01, 04: criteria met and tested (GatewayUrlProviderTest covers each). `GatewayTicketUrl` itself (URL building, ws/wss swap, encoding) has no test, not a listed criterion.

## Standards
- Duplicated logic: `KeystoreTokenCipher` (GatewayCredentialStore.kt:21-51) reimplements the Keystore AES-GCM scheme of `CredentialStore` (CredentialStore.kt:112). Fix: 03-fix-share-keystore-cipher.
- Possible: the "backoff, delay, double" block is repeated three times in Gateway.kt `start()` (lines ~72-76, ~106-110, ~119-122).

Not checked: android suite run by me (runner could not launch build.sh); UI call sites of `Gateway(` in AppViewModel (constructor kept, old signature compatible).

RESULT: FAIL 1 spec, 1 standards: removing a gateway host leaves its tokens stored (AppViewModel.kt:656)
