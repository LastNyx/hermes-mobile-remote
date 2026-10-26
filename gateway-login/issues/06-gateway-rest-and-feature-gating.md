# 06: Gateway hosts list sessions and hide bridge-only features

Lane: full
Branch: feature/gateway-login
Status: review
Round: 0
Blocked by: 01,03,04,05,05b
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
- Landed by 05/05b (9befcfb): for a gateway host `AppViewModel.client` (a `BridgeClient?`) is NULL and REST screens do `client ?: return`, so session list, profiles and status are simply empty today. `data/HostConnection.kt` has `decideConnection`/`rootScreen`; `gatewayAuthFor(...)`/`GatewayAuth(onRefreshed=...)` rebuilds tokens and writes rotated ones back to `GatewayCredentialStore`; `GatewayCredentials(baseUrl, accessToken, refreshToken, expiresAt, provider)`. `HttpTransport` (data/GatewayAuth.kt) only has `post`; add GET/PATCH to it (a new method with a default, or a second interface) without breaking `OkHttpTransport` or the fakes in existing tests.
- Wire shapes: design doc W10-W13 (sessions list is `{sessions,total,limit,offset,storage}` with `pinned`/`archived` booleans; PATCH body `{title|pinned|archived, profile?}`; `/api/profiles` -> `{profiles:[{name,display_name,description,model,provider,is_default}]}`; `/api/media?path=` -> `{data_url}`). A bearer 401 with `error: "session_expired"` -> one `GatewayAuth.refresh()` then retry; anything else is a plain failure.
- Where the existing UI reads the bridge: `AppViewModel` functions that use `client` (`refreshSessions`, `refreshProfiles`, pin/rename, status). Route them through one small seam (`SessionsSource` or similar) with a bridge implementation (today's `BridgeClient` calls, unchanged) and a gateway implementation (`GatewayRestClient`), chosen by the active host kind; do not scatter `if (kind == GATEWAY)` through the ViewModel.

## Constraints

- Do not touch `bridge/`, `install.*`, `tray.*`, or the `windows-support` branch.
- Do not contact a real gateway from a test. Fake the transport.

## Result

Commit 8c13fdd. `./build.sh test` green (GatewayRestTest 7/7, all existing suites pass).
- Parsers criterion: `sessionsProfilesAndStatusParseTheVerifiedGatewayShapes` + `pinAndRenameSendPatchWithProfile` (fixtures are inline, built from design W10-W13/W1; no recorded samples from ticket 01 exist in the repo).
- Gate criterion: `gatewayHidesBridgeOnlyFeaturesAndBridgeHidesNone`. Media criterion: `mediaDecodesTheDataUrlAndFallsBackOnRefusals` (403/404/413/415 -> null = placeholder).
- 401 criterion: `sessionExpiredRefreshesOnceThenRetries...`, `unauthenticatedIsNotARefreshCase`, `refreshRejectedAsksToSignInAgain` (onSignedOut flips the host to the sign-in screen).
- UI criteria (gateway/bridge): no Compose test infra, so covered by compile only: `LocalHiddenFeatures` hides Scheduled icon (Screens.kt), Vault/approvals/default model/Connection card (SettingsScreen), file cards (ChatScreen), project description field (ProjectDialogs); bridge gets an empty set. Status shows the single "Hermes gateway" row from /api/status. No desktop-handoff control exists in the app, so nothing to hide there.
- Reused: parseSession/parseProfiles/BridgeException/profileParam, GatewayAuth.refresh, gatewayAuthFor. Added `HttpTransport.send` (default throws) + OkHttpTransport override. Notes added: 2 lines.
Not checked: running app against a stand-in gateway (planner's Verify), `assembleDebug`, real WorkLab, project/IDEA path on gateway beyond hiding the field.

Planner Verify: SKIP stand-in gateway run needs a device/emulator (none here); PASS part: 8c13fdd, suite green, ./build.sh builds APK; fixtures are from Hermes source (W10-W13), not live-recorded: real WorkLab check in ch
