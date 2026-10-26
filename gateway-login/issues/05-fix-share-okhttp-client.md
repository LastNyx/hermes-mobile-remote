# 05-fix: Gateway transport and socket share the app's OkHttp client

Lane: full
Branch: feature/gateway-login
Status: todo
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
