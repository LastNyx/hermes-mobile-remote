# 05: Sign in to a gateway from the phone (Custom Tab + loopback listener + Add host)

Lane: full
Branch: feature/gateway-login
Status: todo
Round: 0
Blocked by: 02,03
Terms: gateway host, loopback listener, Custom Tab, Add host
Seam: `LoopbackListener` (pure) plus a thin Activity/ViewModel glue.
Test command: cd android && ./build.sh test
Verify: Planner installs the debug APK on the phone and signs in to WorkLab (manual-checks file, ticket 07).

## What to build

A person can open Add host, choose "Sign in to a gateway", type the gateway URL, and get taken to the gateway's own login in a Chrome Custom Tab; after they finish, the app has tokens stored and a new gateway host in the list. The loopback listener is one-shot on `127.0.0.1` with a random port, accepts only the matching `state`, answers the browser with a small "you can return to the app" page, and stops. Cancelling, a timeout (600 s overall) or a wrong state ends cleanly with a message and no half-saved host. Add host still offers the existing QR pairing unchanged.

## Acceptance criteria

- [ ] UI: Add host screen shows two choices, *Pair a bridge* (existing flow, unchanged) and *Sign in to a gateway*
- [ ] UI: the gateway form validates the URL: `https` anywhere, plain `http` only for tailnet/LAN hosts (reuse the `Tailnet` rules); anything else is refused with a reason
- [ ] UI: after success the new host is active and shows its name; after cancel or failure the host list is unchanged
- [ ] The loopback listener is a pure class with a JVM test: right state returns the code, wrong state does not, it closes after one request and after timeout
- [ ] Manifest: Custom Tabs dependency and any needed `queries` entry are present (planner adds the gradle dependency first); `assembleDebug` passes
- [ ] An API without its screen fails this ticket.

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
