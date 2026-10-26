# 03-fix: Reuse the existing Keystore AES-GCM code

Lane: full
Branch: feature/gateway-login
Status: in-progress
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
