# Review batch 1, round 2 (tickets 01-04 + two fixes)

Tests: suites.py `TEST PASS | bridge FAIL (2 baseline, 0 new) | android PASS`. The 2 bridge failures are the Windows environment ones recorded in baseline.md (pre-existing, not counted).

## Spec
- Finding 03 "Removing a host removes its store": resolved. `unpair()` (AppViewModel.kt:633) and `forgetPc()` (AppViewModel.kt:656) both call the shared `clearHostData`, which calls `ProfileStore.clearHostFiles` (ProfileStore.kt:79). That clears the pairing, drafts and `gateway_<id>` files. `GatewayStoreTest.kt:91,98` covers a gateway host and a bridge host.

## Standards
- Finding duplicated Keystore cipher: resolved. `CredentialStore.kt:14` now uses the shared `KeystoreTokenCipher` (GatewayCredentialStore.kt:21); the AndroidKeyStore code is no longer duplicated.

Not checked: the ViewModel wiring on a device (verified by reading the code only). The round-1 notes that were not findings (the `/auth/logout` test and the repeated backoff block) were left alone, as instructed.

RESULT: PASS
