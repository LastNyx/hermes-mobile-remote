# 03-fix: Removing a host also removes its gateway credentials

Lane: full
Branch: feature/gateway-login
Status: in-progress
Round: 1
Blocked by: 03
Terms: host kind, GatewayCredentialStore, Profile
Seam: the host-removal path in `AppViewModel` (around line 656, where the pairing and drafts files are cleared) plus `GatewayCredentialStore.clear`.
Test command: cd android && ./build.sh test
Verify: None.

## What to build

Ticket 03 says "Removing a host removes its store". Today removing a host clears only the bridge pairing and the drafts, so a removed gateway host leaves encrypted access and refresh tokens in `gateway_<id>`. When a host is removed, also clear that host's gateway credential store. The clear must be safe for a bridge host (nothing stored). Add a JVM test that saves credentials for a host, runs the removal step and finds the store empty; if the ViewModel cannot be tested on the JVM, move the "clear everything of host X" step into a small function that can.

## Acceptance criteria

- [ ] Removing a gateway host leaves no `gateway_<id>` data
- [ ] Forgetting the ACTIVE host (`unpair()`) clears the same data as `forgetPc` (grep both paths; one shared step)
- [ ] Removing a bridge host behaves as before
- [ ] JVM test covers the clearing

## Constraints

- Do not touch `bridge/`, `install.*`, `tray.*`.
