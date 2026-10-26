# 06-fix: Reuse the path-encoding helper, drop the unused DESKTOP_HANDOFF feature

Lane: full
Branch: feature/gateway-login
Status: in-progress
Round: 1
Blocked by: 06
Terms: HostFeature gate, gateway host
Seam: refactor only, existing tests are the check (`GatewayRestClient` in data/GatewayRest.kt, `BridgeClient.enc`, `GatewayRestTest`).
Test command: cd android && ./build.sh test
Verify: GatewayRestTest stays green.

## What to build

1. `GatewayRestClient` has its own copy of the path-segment encoder that `BridgeClient` already has (private). Share one helper (make the existing one reachable) and use it from both; keep the error mapping of non-2xx replies in one place if it can be shared the same way.
2. `HostFeature.DESKTOP_HANDOFF` is hidden for gateways but nothing in the app reads it (there is no desktop handoff control). Remove it and its assertion in the gate test.

## Acceptance criteria

- [ ] One path-encoding helper used by both clients.
- [ ] `DESKTOP_HANDOFF` gone; gate test still checks gateway hides the rest and bridge hides none.
- [ ] Existing tests stay green.
