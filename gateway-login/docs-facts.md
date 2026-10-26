# Docs facts: direct gateway login (what actually shipped on feature/gateway-login)

Read first: `docs/GATEWAY-DESIGN.md` (incl. "Verified wire facts" W1-W16 and "Design corrections"). Code is under
`android/app/src/main/java/io/github/nideta231/hermesremote/`. Only describe what the code does; where this file says
"not verified on a device", the docs must say the same.

## Shipped (all built, JVM-tested, APK builds)
- Add host offers two choices: pair a bridge (QR, unchanged) or "Sign in to a gateway" (URL field). `data/GatewaySignIn.kt`:
  `GatewayUrl.validate` (https anywhere; plain http only for tailnet/LAN hosts via `Tailnet` rules), one-shot `LoopbackListener`
  on 127.0.0.1 random port, 600 s overall timeout, wrong state refused, cancel button. Sign-in opens the gateway's own `/login` in a
  Chrome Custom Tab (RFC 8252 native flow with PKCE S256, `data/GatewayAuth.kt`); the password never enters the app.
- Tokens: `GatewayCredentialStore` (Keystore AES-GCM, own prefs file per host, key alias `hermes_remote_gateway_key`; the bridge
  pairing keeps `hermes_remote_token_key`; both share `KeystoreTokenCipher`). Refreshed tokens are written back. Removing a host
  (or unpairing the active one) clears pairing, drafts and the gateway store.
- A gateway host can be the active host: socket `ws(s)://<base>/api/ws?ticket=<fresh>`, a new single-use ticket on every
  (re)connect (`GatewayTicketUrl`); a 401 `session_expired` refreshes once then retries; a second failure -> "Sign in again"
  screen (host kept). 503 on refresh keeps retrying, never signs out. `Gateway` takes a `SocketUrlProvider`; bridge mode = `FixedUrl`.
- REST for gateway hosts (`data/GatewayRest.kt`): sessions list, pin/rename (PATCH), profiles, status, and chat pictures via
  `/api/media`; Bearer token; only `error: "session_expired"` triggers a refresh.
- Hidden for gateway hosts (`HostFeature` gate): Vault, Scheduled (cron), project IDEA.md description, chat file download,
  bridge status card (replaced by a single "Hermes gateway" status row), default-model/approvals settings that are bridge-only
  (see `LocalHiddenFeatures` uses in Screens.kt/ChatScreen.kt/ProjectDialogs.kt). There is no desktop-handoff control in the app.
- One shared OkHttp client (`SharedHttp.client`) for bridge and gateway code.
- Bridge hosts behave as before.

## Not shipped / not verified (say so)
- Notifications while the app is closed for a gateway host: follow-up (design table).
- Not verified on a real phone or against a real signed-in WorkLab: the Custom Tab + loopback redirect, sign-in, token refresh,
  sign-in-again, the gateway response shapes for sessions/profiles/status/media (fixtures are from the Hermes source, not recorded),
  logout (`/auth/logout` behaviour with a Bearer is unknown, W15; local tokens are always wiped), switching bridge<->gateway hosts.
- A phone browser that blocks the loopback redirect has no fallback yet (design unknown 4).
- No UI tests exist (no Compose test infra): the screens are compile-checked only.
- `/api/files/*` (file download) not used: hidden for gateway hosts.

## Security (state plainly)
- Direct mode has no bridge method allowlist, no rate limit, no device pin: the gateway's login plus the network (Tailscale/LAN)
  are the boundary. Plain http only on tailnet/LAN. Tokens in Keystore-backed prefs, excluded from backup (allowBackup=false and
  data_extraction_rules exclude sharedpref).

## Manual checklist items for `docs/gateway-manual-checks.md` (action -> what you should see; all unticked)
sign in against WorkLab over Tailscale (Custom Tab opens the gateway login, then the app returns with a host named after the URL);
chat and streaming; an approval answered on the phone; session list, pin, rename; a chat picture loads; airplane-mode and back
reconnects with a fresh ticket; token expiry and refresh (wait or revoke) -> keeps working or shows Sign in again; wrong password;
cancel in the browser -> host list unchanged; logout / remove host -> tokens gone, host gone; hidden features list (no Scheduled,
no Vault, no file download, no project description field for a gateway host); bridge host still shows everything and works after
adding a gateway host; switching bridge <-> gateway keeps one live socket; Add host still pairs a bridge by QR; pairing a bridge
after a gateway host exists; Keystore: an existing bridge pairing still works after updating the app (alias unchanged);
a browser that blocks loopback redirects; plain http to a public host is refused with a reason; real-WorkLab shapes of
sessions/profiles/status/media match the design doc W10-W13; the Telegram-delivered APK installs over the previous debug build.
