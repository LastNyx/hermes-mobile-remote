# 01: Spike: record the real gateway auth and API responses

Lane: full
Branch: feature/gateway-login
Status: in-progress
Round: 0
Blocked by: None
Terms: gateway, native auth, ws-ticket, wire facts
Seam: None (planner-run: research, docs only)
Test command: None (docs/research only, planner-run)
Verify: Planner reads the new section against the Hermes source with `rg`. WorkLab must be reachable over Tailscale first; if not, the entries that need it stay `unknown` and the planner asks the user.

## What to build

Probe the WorkLab gateway and the Hermes source, and write what is actually true into `docs/GATEWAY-DESIGN.md` (new section "Verified wire facts"): the responses of `/api/status` and `/api/auth/providers`, the authorize/token/refresh/logout request and response shapes, the ws-ticket call, the real `/api/*` routes and response shapes for sessions list, session patch, profiles and status, whether `/api/ws` needs anything beyond the ticket, and whether the gateway serves chat images itself. Mark each claim in the design doc as verified (with how) or still unknown. This is research: no app code.

Blocked-by note: (can start immediately)

## Acceptance criteria

- [ ] Design doc has a "Verified wire facts" section with one entry per item above, each marked verified or unknown
- [ ] Every verified entry names its source (curl output summary or `file:line` in the Hermes source)
- [ ] The four "Unknowns" in the design doc are each resolved or restated as a concrete open question
- [ ] Anything that contradicts the design's Android-changes table is listed under "Design corrections"

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
