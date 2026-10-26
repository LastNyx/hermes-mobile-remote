# 05b: A signed-in gateway host becomes the live host

Lane: full
Branch: feature/gateway-login
Status: in-progress
Round: 0
Blocked by: 04,05
Terms: gateway host, HostKind, GatewayTicketUrl, LiveLink, Pairing
Seam: a small `HostConnection` decision function (kind -> how the app connects) that the ViewModel calls, plus `LiveLink.connectGateway`.
Test command: cd android && ./build.sh test
Verify: Planner installs the debug APK and uses the stand-in gateway (a stdlib shim in `.scratch/`) or WorkLab: sign in, then the chat screen shows connected.

## What to build

Today the ViewModel only knows bridges: `pairing: Pairing?` decides whether the pairing screen or the app shows (`MainActivity.App`), `start(Pairing)` builds a `BridgeClient`, and `useClient` -> `LiveLink.connect(app, BridgeClient)` opens the socket. Make a gateway host work as the active host too. When the active host is `HostKind.GATEWAY` and its `GatewayCredentialStore` holds tokens, the app shows the normal app (not the pairing screen), and `LiveLink` opens the socket with `Gateway(http, baseUrl, GatewayTicketUrl(baseUrl, auth))` where `auth` is a `GatewayAuth` built from the stored tokens and persisting refreshed tokens back to the store. Switching between a bridge host and a gateway host in the host list tears down the old socket and starts the right one; the bridge path is unchanged. Chat over the socket (open session, send, stream, approvals) works for a gateway host because it is the same JSON-RPC. REST-backed screens (session list, profiles, status) are ticket 06: until then they may show empty or an unavailable message for a gateway host, but must not crash or call `/v1/*`. When the socket ends in `UNAUTHORIZED` for a gateway host, the app shows the sign-in-again message and offers sign-in again for that host (it keeps the host entry).

## Acceptance criteria

- [ ] JVM test: the connection decision returns "bridge pairing" for a bridge host and "gateway tokens" for a gateway host with tokens, and "needs sign-in" for a gateway host without tokens
- [ ] JVM test: refreshed tokens from `GatewayAuth` are written back to `GatewayCredentialStore` (a fake cipher/prefs), so a restart still has the rotated refresh token
- [ ] JVM test: `LiveLink` (or the function it uses) given a gateway host builds a `Gateway` whose provider is `GatewayTicketUrl` (URL ends `/api/ws?ticket=` plus a ticket), and for a bridge host the old `client.gateway()` path
- [ ] UI: with a gateway host active the root shows the normal app, not `PairScreen`; with a gateway host that has no tokens it shows a sign-in-again state with a button, not a crash
- [ ] UI: the host switcher lists both kinds and switching tears down the previous socket (no two live sockets)
- [ ] Bridge hosts behave exactly as before (every existing test green)
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
- Existing code to read first: `AppViewModel.kt` (`start`, `useClient`, `activate`, `teardown`, `pair`, `startAddingPc`, `_pairing`), `MainActivity.kt` (`App()` decides PairScreen vs PairedApp from `pairing != null`), `data/LiveLink.kt` (`connect(context, BridgeClient)`; keeps ONE live `Gateway`; reuses it when `g.url` equals the fresh one), `data/Gateway.kt` (`Gateway(http, url, provider)`, `SocketUrlProvider`, `FixedUrl`, `GatewayTicketUrl`), `data/ProfileStore.kt` (`Profile.kind`, `gatewayFile`, `clearHostFiles`), `data/GatewayCredentialStore.kt`, `data/GatewayAuth.kt` (`GatewayAuth`, `HttpTransport`, `GatewayTokens`, `GatewayAuthException`), and the `OkHttpTransport` ticket 05 adds.
- `Gateway.url` is only an identity string used by `LiveLink` to decide reuse; for a gateway host pass its base URL, not a ticket URL (a ticket URL changes on every connect and would defeat reuse).
- A composition crash passes every unit test: the root `AnimatedContent` in `App()` switches on a Boolean; keep it a pure function of state and check `assembleDebug` with `./build.sh` (not only `test`).
- Do not put new gateway logic in `AppViewModel` when a small testable class can hold it; the ViewModel is hard to test on the JVM.
- Landed by ticket 05 (56f6177): `data/GatewaySignIn.kt` (`GatewayUrl.validate/hostName`, `LoopbackListener`, `OkHttpTransport()`); `AppViewModel.signInGateway` adds the host via `profiles.add(name, HostKind.GATEWAY)` and saves `GatewayCredentialStore(app, host.id).save(GatewayCredentials(baseUrl, accessToken, refreshToken, expiresAt))`; `load()` returns that or null. `refreshPcs` already lists gateway hosts; `switchPc`/`activate` currently RETURN EARLY for a gateway host (`// going live on a gateway is ticket 05b`): remove that early return and implement the activation.

## Constraints

- Do not touch `bridge/`, `install.*`, `tray.*`, or the `windows-support` branch.
- Do not contact a real gateway from a test. Fake the transport.

## Result
