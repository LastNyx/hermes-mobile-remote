# 04: Gateway socket takes a fresh URL and credential on every connect

Lane: full
Branch: feature/gateway-login
Status: in-progress
Round: 0
Blocked by: 02,03
Terms: Gateway socket, URL provider, ws-ticket, UNAUTHORIZED
Seam: the URL/credential provider interface on `Gateway`.
Test command: cd android && ./build.sh test
Verify: None here; real socket against WorkLab in ticket 07.

## What to build

`Gateway` stops taking one fixed URL. It takes a provider that is asked on every (re)connect attempt: bridge mode returns the same fixed URL as today; gateway mode returns a `ws(s)://<base>/api/ws?ticket=<fresh>` from `GatewayAuth`. When the upgrade fails or the ticket call returns 401 `session_expired` (see docs/GATEWAY-DESIGN.md "Verified wire facts", W7-W9), it refreshes tokens once and retries; if that fails it goes to `UNAUTHORIZED` and stops, with a message that says to sign in again (not "pair again"). 503 from refresh keeps retrying with the existing backoff. `client.capabilities` is still sent after open.

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
- Wire protocol: `docs/GATEWAY-DESIGN.md` section "Verified wire facts" (W1-W16) is the source of truth; entries marked unknown must not be relied on. Do not invent payloads.
- No Co-Authored-By trailer on any commit.
- Landed by tickets 02/03 (use, do not redo): `data/GatewayAuth.kt` has `GatewayAuth(baseUrl, http: HttpTransport, tokens)` with `suspend wsTicket(): String` (fresh each call; throws `GatewayAuthException` with `error` = `SIGNED_OUT` on 401 `session_expired`, `RETRY_LATER` on 503, `FAILED` otherwise), `suspend refresh(): GatewayTokens` (coalesced; same exception kinds), `tokens`, `logout()`. `ProfileStore` has `Profile.kind` (`HostKind`); `GatewayCredentialStore` holds tokens. A ticket call that throws `SIGNED_OUT` means: call `refresh()` once, then ticket again; `refresh()` throwing `SIGNED_OUT` means `UNAUTHORIZED`.
- `./build.sh test` needs JDK 17 on PATH; it is now exported in `~/.bashrc`. If it still prints "no JDK found", FAIL and say so.

## Constraints

- Do not touch `bridge/`, `install.*`, `tray.*`, or the `windows-support` branch.
- Do not contact a real gateway from a test. Fake the transport.

## Result
Commit c111be6. `./build.sh test`: all JVM tests pass (158 incl. 5 new in GatewayUrlProviderTest).
- Criterion 1: bridgeModeConnectsToTheFixedUrl (+ existing BridgeRequestsTest unchanged, green)
- Criterion 2: gatewayModeAsksTheProviderOnEveryConnect; 3: a401RefreshesOnceThenRetriesAndASecond401IsUnauthorized
- Criterion 4: aRefreshThatIsUnavailableDoesNotEndInUnauthorized; 5: unauthorizedMessageSaysSignInForGatewayAndPairForBridge
- Tests were written after the code (same commit); all pass.
- Reuse: GatewayAuth.wsTicket/refresh via new `GatewayTicketUrl` provider; `SocketUrlProvider`/`FixedUrl` added in Gateway.kt; old `Gateway(http, url)` constructor kept for bridge.
- Notes added to .scratch/dev-notes.md.
Not checked: GatewayTicketUrl against a real/faked GatewayAuth (not wired yet; ticket 07 covers a real socket), the ticket-call-401 path in Gateway.nextUrl has no dedicated test, bridge 401 text is overwritten by the existing catch handler (pre-existing).
