# Review batch 2, round 1 (tickets 05, 05b, 06; base 6af1b15)

Tests: `TEST PASS`; bridge 2 baseline failures (Windows), 0 new; android PASS.

## Spec
1. Untested criteria (no Compose test infra, accepted by planner, listed so they are not lost):
   - 05 "UI: Add host screen shows two choices", "after success the new gateway host appears...", "after cancel or failure ... no tokens stored" (AppViewModel.signInGateway, Screens.kt:1184-1260): no test.
   - 05b "UI: with a gateway host active the root shows the normal app" and "host switcher lists both kinds and switching tears down the previous socket" (AppViewModel.kt switchPc): only `rootScreen` helper is tested, which counts as none.
   - 06 "UI: ... drawer has no Scheduled entry, Settings has no Vault entry, chat has no file-download action" (Screens.kt:218, 797-805; ChatScreen.kt:1338): only `hiddenFeatures` tested.
   - 05b "for a bridge host the old `client.gateway()` path": no test (HostConnectionTest covers the gateway side only).
   No fix ticket: needs UI test infra; move to the manual checks in ticket 07.
2. 06 "fixtures committed from ticket 01's recorded samples": fixtures in GatewayRestTest are inline, written from Hermes source (W10-W13), not recorded. Cannot be fixed in code; the real-WorkLab check in 07 must confirm the shapes.
3. 05 "implementation over the app's existing OkHttp client": `OkHttpTransport()` is built with its own fresh `OkHttpClient()` (GatewaySignIn.kt:93), created twice per activation (AppViewModel.enterActiveHost) and once more in signInGateway; `LiveLink.connectGateway` also uses `OkHttpClient()` (LiveLink.kt). Fix ticket 05-fix.

## Standards
1. Reuse: `GatewayRestClient.enc` (GatewayRest.kt:50) duplicates the private `BridgeClient.enc` (BridgeClient.kt:190), and `call`'s error-to-`BridgeException` mapping repeats it. Fix ticket 06-fix.
2. Dead/speculative: `HostFeature.DESKTOP_HANDOFF` (GatewayRest.kt:20) is hidden-set only; no control reads it (the app has no desktop handoff). Remove it (and its assertion in GatewayRestTest:84). Fix ticket 06-fix.
3. Possible: the sign-in `state` is passed as both returned and expected to `GatewayAuth.signIn` (AppViewModel.signInGateway), so that check is vacuous; the real check is the loopback listener's. Not worth a ticket.

Not checked: on-device Custom Tab / sign-in-again flow, `assembleDebug` (planner ran it), call sites beyond AppViewModel/MainActivity/SystemSettings/LiveLink.
