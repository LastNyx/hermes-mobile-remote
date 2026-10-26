# 06: Gateway hosts list sessions and hide bridge-only features

Lane: full
Branch: feature/gateway-login
Status: todo
Round: 0
Blocked by: 01,03,04,05
Terms: gateway host, HostFeatures gate, /api/* REST
Seam: `GatewayRestClient` with an injected transport, and a `HostFeatures` gate.
Test command: cd android && ./build.sh test
Verify: Planner runs the app against a stand-in gateway (stdlib shim in `.scratch/`) before the review; real WorkLab in ticket 07.

## What to build

With a gateway host active, the drawer lists sessions, pin/rename work, profile switching and the connection status work, using the gateway's own `/api/*` with the bearer token (shapes taken from ticket 01's verified section, not from the bridge's `/v1`). Screens and controls that only exist in the bridge are hidden for gateway hosts: Vault, cron/Scheduled, project IDEA.md, chat file download, the bridge status card, desktop handoff. Chat pictures for a gateway host load through the gateway's `GET /api/media?path=<abs path>` (Bearer; returns `{"data_url": "data:image/...;base64,..."}`; 403/404/413/415 show a placeholder). Bridge hosts show everything as today, including `/v1/media`.

## Acceptance criteria

- [ ] JVM test: the sessions/profile/status parsers accept the verified gateway responses (fixtures committed from ticket 01's recorded samples)
- [ ] JVM test: a feature-gate function returns the right set of hidden features for `gateway` and none for `bridge`
- [ ] UI: for a gateway host the drawer has no Scheduled entry, Settings has no Vault entry, the chat has no file-download action, and the status screen shows only gateway fields
- [ ] UI: for a bridge host nothing has changed (existing screens still show every entry)
- [ ] JVM test: the media loader decodes a `/api/media` data_url and falls back to a placeholder on 403/404/413/415
- [ ] A 401 whose body has `error: "session_expired"` from any `/api/*` call triggers the same one-shot refresh as the socket and then asks to sign in again; a 401 `unauthenticated` is not a refresh case (docs/GATEWAY-DESIGN.md W7)
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
