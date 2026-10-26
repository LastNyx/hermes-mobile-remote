# 03: Store gateway tokens and give each host a kind

Lane: full
Branch: feature/gateway-login
Status: in-progress
Round: 0
Blocked by: None
Terms: host kind (bridge|gateway), Profile, GatewayCredentialStore
Seam: `ProfileStore` kind field, `GatewayCredentialStore` with an injected cipher.
Test command: cd android && ./build.sh test
Verify: None.

## What to build

`Profile` gets a `kind` (`bridge` or `gateway`), defaulting to `bridge` so installs from before this change load unchanged. A `GatewayCredentialStore` keeps base URL, access token, refresh token and expiry for one gateway host, encrypted with the same Keystore scheme as `CredentialStore`, in its own per-host prefs file, excluded from backup like the bridge pairing. Removing a host removes its store.

Blocked-by note: (can start immediately; independent of the wire facts)

## Acceptance criteria

- [ ] JVM test: a ProfileStore saved by the old code (no `kind`) loads with every host as `bridge`
- [ ] JVM test: adding a gateway host persists `kind=gateway` and survives reload; renaming and removing keep working
- [ ] `GatewayCredentialStore` encrypts access and refresh tokens (no token string appears in plain prefs); the cipher is injected so the JVM test can fake the Keystore
- [ ] A corrupted blob clears the store and reports signed-out instead of throwing
- [ ] Backup rules (`data_extraction_rules`/`backup_rules`) cover the new prefs file name pattern; `assembleDebug` passes

## Facts that hold for every ticket


- Design: `docs/GATEWAY-DESIGN.md` (read it first). Branch `feature/gateway-login`, from `origin/main` v1.17.0. Never push.
- App code: `android/app/src/main/java/io/github/nideta231/hermesremote/` (`data/`, `ui/`, `AppViewModel.kt`); tests in `android/app/src/test/java/io/github/nideta231/hermesremote/`.
- Existing seams to reuse, not duplicate: `data/Gateway.kt` (JSON-RPC socket, reconnect, `client.capabilities`), `data/CredentialStore.kt` (Keystore AES-GCM), `data/ProfileStore.kt` (list of hosts, per-host prefs file), `data/BridgeClient.kt` (REST, `scoped(profile)`), `data/Pairing.kt` (`Tailnet` host rules).
- Bridge mode must behave exactly as today: every existing test in `android/app/src/test` stays green.
- Tests: JVM unit tests only; no emulator. Android-only glue (Custom Tab, Service, manifest) is acceptance criteria too, checked by `assembleDebug`.
- Test command: `cd android && ./build.sh test` (JDK 17 + Android SDK; on this PC `C:/home/tools/jdk-17`, `C:/home/tools/android-sdk`, pass `C:/` paths).
- Protected (planner edits only): `*.gradle.kts`, `gradle/`, `gradle.properties`, `local.properties`, `.github/`, `.team.json`.
- Wire protocol: `docs/GATEWAY-DESIGN.md` section "Verified wire facts" (W1-W16) is the source of truth; entries marked unknown must not be relied on. Do not invent payloads.
- No Co-Authored-By trailer on any commit.

## Constraints

- Do not touch `bridge/`, `install.*`, `tray.*`, or the `windows-support` branch.
- Do not contact a real gateway from a test. Fake the transport.

## Result
FAIL: `cd android && ./build.sh test` prints "error: no JDK found" (JAVA_HOME unset, java not on PATH); I cannot set env in a single-command call, so the tests never ran. Nothing committed.
Uncommitted (left in tree): `data/ProfileStore.kt` (HostKind, Profile.kind, gatewayFile), new `data/GatewayCredentialStore.kt` (TokenCipher, KeystoreTokenCipher), new `GatewayStoreTest.kt` (4 tests, one per first four criteria).
Backup criterion: `data_extraction_rules.xml` already excludes all `sharedpref` (path "."), and allowBackup=false, so `gateway_<id>` is covered; no change. `assembleDebug` not run.
Reuse: copied the Keystore AES-GCM scheme from CredentialStore (own alias); FakePrefs there is private so the test has its own MemPrefs.
Not checked: everything (compile, tests, assembleDebug).
