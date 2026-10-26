# 04: Gateway socket takes a fresh URL and credential on every connect

Lane: full
Branch: feature/gateway-login
Status: todo
Round: 0
Blocked by: 02,03
Terms: Gateway socket, URL provider, ws-ticket, UNAUTHORIZED
Seam: the URL/credential provider interface on `Gateway`.
Test command: cd android && ./build.sh test
Verify: None here; real socket against WorkLab in ticket 07.

## What to build

`Gateway` stops taking one fixed URL. It takes a provider that is asked on every (re)connect attempt: bridge mode returns the same fixed URL as today; gateway mode returns a `ws(s)://<base>/api/ws?ticket=<fresh>` from `GatewayAuth`. When the upgrade fails with 401 or the ticket call says signed out, it refreshes tokens once and retries; if that fails it goes to `UNAUTHORIZED` and stops, with a message that says to sign in again (not "pair again"). 503 from refresh keeps retrying with the existing backoff. `client.capabilities` is still sent after open.

## Acceptance criteria

- [ ] JVM test with a fake socket/provider: bridge mode connects to the same URL as before and every existing Gateway behaviour test still passes
- [ ] JVM test: gateway mode asks the provider on each reconnect (3 reconnects = 3 provider calls, 3 different tickets)
- [ ] JVM test: a 401 triggers exactly one refresh then one retry; a second 401 ends in `UNAUTHORIZED`
- [ ] JVM test: a 503 on refresh does not reach `UNAUTHORIZED`
- [ ] The `UNAUTHORIZED` message for a gateway host reads as a sign-in problem; for a bridge host it is unchanged

## Facts that hold for every ticket


- Design: `docs/GATEWAY-DESIGN.md` (read it first). Branch `feature/gateway-login`, from `origin/main` v1.17.0. Never push.
- App code: `android/app/src/main/java/io/github/nideta231/hermesremote/` (`data/`, `ui/`, `AppViewModel.kt`); tests in `android/app/src/test/java/io/github/nideta231/hermesremote/`.
- Existing seams to reuse, not duplicate: `data/Gateway.kt` (JSON-RPC socket, reconnect, `client.capabilities`), `data/CredentialStore.kt` (Keystore AES-GCM), `data/ProfileStore.kt` (list of hosts, per-host prefs file), `data/BridgeClient.kt` (REST, `scoped(profile)`), `data/Pairing.kt` (`Tailnet` host rules).
- Bridge mode must behave exactly as today: every existing test in `android/app/src/test` stays green.
- Tests: JVM unit tests only; no emulator. Android-only glue (Custom Tab, Service, manifest) is acceptance criteria too, checked by `assembleDebug`.
- Test command: `cd android && ./build.sh test` (JDK 17 + Android SDK; on this PC `C:/home/tools/jdk-17`, `C:/home/tools/android-sdk`, pass `C:/` paths).
- Protected (planner edits only): `*.gradle.kts`, `gradle/`, `gradle.properties`, `local.properties`, `.github/`, `.team.json`.
- Wire protocol is in the design doc and is UNVERIFIED until ticket 01 records the real responses in it. Do not invent payloads.
- No Co-Authored-By trailer on any commit.

## Constraints

- Do not touch `bridge/`, `install.*`, `tray.*`, or the `windows-support` branch.
- Do not contact a real gateway from a test. Fake the transport.

## Result
