# 05-fix: Gateway transport and socket share the app's OkHttp client

Lane: full
Branch: feature/gateway-login
Status: done
Round: 1
Blocked by: 05
Terms: gateway host, OkHttpTransport, LiveLink
Seam: refactor only, existing tests are the check (`OkHttpTransport` in data/GatewaySignIn.kt, `AppViewModel.enterActiveHost`/`signInGateway`, `LiveLink.connectGateway`).
Test command: cd android && ./build.sh test
Verify: existing GatewaySignInTest and HostConnectionTest stay green.

## What to build

Ticket 05 asked for the transport "over the app's existing OkHttp client". Today every gateway activation and sign-in builds new `OkHttpClient()` instances (two transports per activation, one more per sign-in, one more in `LiveLink.connectGateway`). Make them all use the one client the bridge code already shares (find where `BridgeClient` gets its `http`), so connections and the thread pool are shared. Bridge behaviour unchanged.

## Acceptance criteria

- [ ] No `OkHttpClient()` is constructed in the gateway activation, sign-in or `LiveLink.connectGateway` paths; they take the shared client.
- [ ] Existing tests stay green.

## Result
Commit 5d13c81. `./build.sh test` exited clean (silent success). Refactor only, so existing tests are the check; no new test.
- Criterion 1: no shared client existed (BridgeClient defaulted to `OkHttpClient()`), so added `SharedHttp.client` (data/SharedHttp.kt). `OkHttpTransport` and `BridgeClient` default to it, `LiveLink.connectGateway` uses it. AppViewModel's `OkHttpTransport()` calls (activation, sign-in) now take it through the default.
- Criterion 2: existing suite green.
Reuse: nothing shared existed to reuse; BridgeClient derives via newBuilder so pool and dispatcher are now shared.
Notes added: 1.
Not checked: tests that still build their own `OkHttpClient()` (test code, left alone); on-device behaviour.

Planner Verify: PASS 5d13c81; suite green; SharedHttp.client replaces fresh OkHttpClient() in transport/LiveLink/BridgeClient default (BridgeClient derives per-pin clients from baseClient, pinning unaffected per read)

Planner Verify: PASS batch 2 review round 2 PASS
