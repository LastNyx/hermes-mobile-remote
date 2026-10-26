# 07: Docs, changelog and the real-phone checklist

Lane: full
Branch: feature/gateway-login
Status: in-progress
Round: 0
Blocked by: 04,05,05b,06
Terms: gateway host, manual checks
Seam: None (docs).
Test command: None (docs/research only, planner-run)
Verify: Planner walks the checklist on the real phone with the user; items not run stay unticked.

## What to build

User-facing docs say how to connect a phone straight to a gated gateway, what is hidden compared with a bridge, and what the security trade-off is (no bridge allowlist or rate limit; the gateway login and the network are the boundary). A checklist file lists what only a person on a real phone can judge.

## Acceptance criteria

- [ ] `docs/FEATURES.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY.md` and `CHANGELOG.md` describe direct gateway login accurately for what shipped
- [ ] `docs/gateway-manual-checks.md` is a checkbox list of action -> what you should see: sign in against WorkLab over Tailscale, chat and streaming, an approval answered on the phone, airplane-mode reconnect (fresh ticket), token expiry and refresh, wrong password, cancelling the browser, logout, switching between a bridge host and a gateway host
- [ ] No doc claims a feature the code does not have; the design doc's status line is updated from `draft, not built`
- [ ] Docs lines match the repo's existing doc style (read `docs/FEATURES.md` first)

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
