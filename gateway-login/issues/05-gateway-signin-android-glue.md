# 05: Sign in to a gateway from the phone (Custom Tab + loopback listener + Add host)

Lane: full
Branch: feature/gateway-login
Status: in-progress
Round: 0
Blocked by: 02,03
Terms: gateway host, loopback listener, Custom Tab, Add host
Seam: `LoopbackListener` (pure) plus a thin Activity/ViewModel glue.
Test command: cd android && ./build.sh test
Verify: Planner installs the debug APK on the phone and signs in to WorkLab (manual-checks file, ticket 07).

## What to build

A person can open Add host, choose "Sign in to a gateway", type the gateway URL, and get taken to the gateway's own login in a Chrome Custom Tab; after they finish, the app has tokens stored (`GatewayCredentialStore`) and a new `HostKind.GATEWAY` entry in the host list (`ProfileStore`). Making that host the live, connected one is ticket 05b, not this ticket: here the host only has to appear in the list and keep its tokens. The loopback listener is one-shot on `127.0.0.1` with a random port, accepts only the matching `state`, answers the browser with a small "you can return to the app" page, and stops. Cancelling, a timeout (600 s overall) or a wrong state ends cleanly with a message and no half-saved host. Add host still offers the existing QR pairing unchanged.

## Acceptance criteria

- [ ] UI: Add host screen shows two choices, *Pair a bridge* (existing flow, unchanged) and *Sign in to a gateway*
- [ ] UI: the gateway form validates the URL: `https` anywhere, plain `http` only for tailnet/LAN hosts (reuse the `Tailnet` rules); anything else is refused with a reason
- [ ] UI: after success the new gateway host appears in the host list with a name taken from the URL's host; after cancel or failure the host list is unchanged and no tokens are stored
- [ ] The loopback listener is a pure class with a JVM test: right state returns the code, wrong state does not, it closes after one request and after timeout
- [ ] Manifest: Custom Tabs dependency and any needed `queries` entry are present (planner adds the gradle dependency first); `assembleDebug` passes
- [ ] An API without its screen fails this ticket.

- The `HttpTransport` interface in `data/GatewayAuth.kt` needs a real implementation over the app's existing OkHttp client; write it here (`OkHttpTransport`, tiny, not unit-tested beyond a MockWebServer round trip of one POST). `GatewayAuth.logout()` hits `/auth/logout` with a Bearer; whether the gateway honours that is unknown (design W15), so treat it as best effort and make sure local tokens are wiped even when it fails (add a JVM test for that).
- Tokens come back from `GatewayAuth.signIn(code, verifier, returnedState, expectedState)`; the sign-in URL from `authorizeUrl(challenge, redirectUri, state)`; the verifier/challenge from `Pkce`. Do not reimplement.
- The Custom Tab must keep its cookies through the login (the gateway keeps the login state in a PKCE cookie): use a Custom Tab (`androidx.browser`, already a dependency), not a WebView.

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
Commit 56f6177. `./build.sh test`: all JVM tests pass (GatewaySignInTest 6/6, 0 failures anywhere); main code incl. Compose UI compiled.
- Loopback listener criterion: rightStateReturns..., wrongState..., timeoutEndsTheWait (GatewaySignInTest).
- URL validation criterion: httpsAnywhereAndPlainHttpOnlyOnTailnetOrLan (GatewayUrl, reuses Tailnet.isAllowedHost).
- OkHttpTransport MockWebServer round trip; logoutWipesLocalTokensEvenWhenTheServerFails.
- UI criteria (two choices chips, form, host list after success, unchanged on cancel/failure): no UI test infra, so only compiled, not tested on the component. Host list: refreshPcs now lists GATEWAY hosts; switchPc ignores them (05b).
- Manifest: `queries` entry for CustomTabsService added; androidx.browser dependency was already present. Cancel: "Cancel sign-in" button closes the listener.
- Reused: Pkce, GatewayAuth, GatewayCredentialStore, ProfileStore.add, Tailnet, describe(); found nothing for a loopback server or OkHttp transport.
Notes added: 2 lines in .scratch/dev-notes.md.
Not checked: assembleDebug, UI behaviour on a device/Custom Tab, real gateway sign-in, UI criteria on the component.
