# 06-fix: Reuse the path-encoding helper, drop the unused DESKTOP_HANDOFF feature

Lane: full
Branch: feature/gateway-login
Status: review
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

## Result
Commit: 02c1914. `cd android && ./build.sh test` passed (no output, exit 0). Refactor only, no new tests.
- Criterion 1: one top-level `internal fun enc` in GatewayRest.kt; both private copies removed. Covered by GatewayRestTest/bridge tests staying green.
- Criterion 2: `DESKTOP_HANDOFF` removed; gate test `gatewayHidesBridgeOnlyFeaturesAndBridgeHidesNone` updated, still checks gateway hides the rest and bridge hides none.
- Criterion 3: whole suite green.
- Reused: BridgeClient's `enc` (made shared). Error mapping of non-2xx replies not merged (gateway `raw` has refresh logic, differs from bridge `call`); the ticket said "if it can be shared".
- Notes added: 1 line in .scratch/dev-notes.md.
Not checked: error-mapping dedupe; device run.

Planner Verify: PASS 02c1914; suite green; one internal enc() used by both clients, DESKTOP_HANDOFF removed (error-mapping dedupe not done; optional per ticket)
