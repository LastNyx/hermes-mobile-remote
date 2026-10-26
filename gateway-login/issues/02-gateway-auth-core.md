# 02: Pure-Kotlin native sign-in: PKCE, authorize URL, token and refresh

Lane: full
Branch: feature/gateway-login
Status: in-progress
Round: 0
Blocked by: 01
Terms: gateway, PKCE, native auth, ws-ticket, coalesced refresh
Seam: `GatewayAuth` with an injected `HttpTransport` interface (no OkHttp in the tested path).
Test command: cd android && ./build.sh test
Verify: None (unit tests are the check; real flow is ticket 05/07).

## What to build

A `GatewayAuth` that, given a base URL and an injected HTTP interface, can: create a PKCE verifier and S256 challenge, build the `/auth/native/authorize` URL for a loopback `redirect_uri` and `state`, validate a returned `state`, exchange `code`+`verifier` at `/auth/native/token`, refresh at `/auth/native/refresh` with one coalesced in-flight refresh (parallel callers share the result), mint a ws-ticket via `/api/auth/ws-ticket`, and log out. Errors map to: signed out (401 `session_expired`), retry later (503), failed (anything else). Base URLs with a path prefix must keep it.

Blocked-by note: (needs the verified request/response shapes)

## Acceptance criteria

- [ ] JVM test: verifier `dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk` gives challenge `E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM` (RFC 7636 appendix B, recomputed with sha256+base64url)
- [ ] JVM test: authorize URL contains the S256 params, a 127.0.0.1 redirect and the state, and keeps a `/prefix` base path
- [ ] JVM test: a wrong `state` is rejected before any token call
- [ ] JVM test: 5 parallel refresh calls cause exactly 1 HTTP refresh and all get the same tokens
- [ ] JVM test: 503 on refresh is `retry later` and does NOT clear tokens; 401 `session_expired` is `signed out`
- [ ] JVM test: each ws-ticket call is a fresh request (a cached ticket is never reused)

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
