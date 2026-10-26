# 03-fix: Reuse the existing Keystore AES-GCM code

Lane: full
Branch: feature/gateway-login
Status: review
Round: 1
Blocked by: 03
Terms: GatewayCredentialStore, CredentialStore
Seam: refactor only, existing tests are the check (`data/GatewayCredentialStore.kt` `KeystoreTokenCipher`, `data/CredentialStore.kt`).
Test command: cd android && ./build.sh test
Verify: None.

## What to build

`KeystoreTokenCipher` copies the Keystore key creation, the AES/GCM transform and the iv-prefixed encrypt and decrypt from `CredentialStore` (CredentialStore.kt:112 and around). Pull the shared part into one helper that takes the key alias, used by both, so the scheme lives once. Keep the existing key alias of `CredentialStore` unchanged (installed apps must still decrypt) and keep the gateway alias as is.

## Acceptance criteria

- [ ] One implementation of the Keystore AES-GCM scheme, parameterised by alias
- [ ] `CredentialStore` behaviour and alias unchanged; all existing tests green

## Constraints

- Do not touch `bridge/`, `install.*`, `tray.*`.

## Result

Commit 6af1b15. `cd android && ./build.sh test` exited with no output (no failures shown).
Refactor ticket, no new tests: existing tests are the check.
- AC1: `KeystoreTokenCipher(alias)` is the single AES-GCM implementation; `CredentialStore` now uses it (alias "hermes_remote_token_key"), gateway passes "hermes_remote_gateway_key".
- AC2: CredentialStore blob format (iv + ciphertext, base64) and alias unchanged; existing tests green. The Keystore is now opened lazily rather than in the constructor.
Reuse: reused the existing KeystoreTokenCipher/TokenCipher; nothing else needed.
Notes added: 1.
Not checked: real Keystore decrypt of a blob written by an older install (no device/emulator run).

Planner Verify: PASS 6af1b15; suite green; CredentialStore alias hermes_remote_token_key unchanged, GCM 128/iv12 transform identical to removed code (read diff); real Keystore not testable on JVM (device check in manual l
