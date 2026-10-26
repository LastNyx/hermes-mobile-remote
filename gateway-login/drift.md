# Drift: gateway-login

Glossary: none found. Glob `**/CONTEXT.md`, `**/GLOSSARY.md`, `**/*ontext*.md`, `**/*lossary*.md` all empty; the prompt named no path. Glossary drift check NOT run.
Stand-in terms (ticket 07 "Terms:" line `gateway host`, `manual checks`): Grep `gateway host|bridge host|manual checks` in `*.md` = 26 hits in 7 files (README, CHANGELOG, docs/ARCHITECTURE, FEATURES, GATEWAY-DESIGN, SECURITY, gateway-manual-checks). Used consistently; no drift seen.
Renamed-term check on diff identifiers: unchecked (no glossary to compare against).
New-doc terms with no glossary entry: "Sign in to a gateway", "gateway host", "Sign in again", "Custom Tab": unchecked.
Identifiers cited in docs exist (Grep `hermes_remote_gateway_key|LoopbackListener|HostFeature|SharedHttp|GatewayTicketUrl|session_expired` in android/: 14 files).
Doubtful, unchecked:
- Sign-out revoking the gateway session (W15): docs say not confirmed.
- Wrong password, blocked loopback redirect, token expiry: on-device only.
- `https`/plain-http rule (`GatewayUrl.validate`) and "removing a host deletes drafts": taken from docs/.scratch docs-facts, not re-read in code.
- Chat pictures via `/api/media` (W13, source-only, WorkLab 0.22.2 vs source 0.22.5).
Docs changed: README, CHANGELOG (Unreleased), FEATURES, ARCHITECTURE, SECURITY, GATEWAY-DESIGN (status line), new gateway-manual-checks.md.
