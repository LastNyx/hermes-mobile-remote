# Security model

What the bridge protects, from whom, and where it deliberately refuses to help. Read
[ARCHITECTURE.md](ARCHITECTURE.md) first for how the pieces fit together.

The short version: the thing worth stealing is the Hermes API key, and it never leaves the PC.
The thing worth attacking is the bridge, and it will only talk to devices you paired, from
networks you chose.

## Assets

| Asset | Where it lives | Who can reach it |
|---|---|---|
| Hermes API key | `~/.hermes/.env`, read by the bridge at runtime | The bridge process only |
| Device tokens | Shown once at pairing; only their SHA-256 is stored | The paired phone, in its Keystore |
| Session content | Hermes' own store | Anything that can talk to Hermes |
| TLS private key | `~/.config/hermes-remote/tls/`, mode 600 | The PC user |

## Guarantees

**Hermes stays on loopback.** The API server binds `127.0.0.1`. Its key is read from Hermes' own
`.env`, held in one module, and never logged, never sent to a device, never included in an error
message. Rotating the key in `~/.hermes/.env` is picked up on the next request, without restarting
the bridge.

**The bridge never binds a wildcard.** It refuses to start on `0.0.0.0` or `::` and lists explicit
addresses instead. What it serves depends on where you are:

| Address | Served when | Transport |
|---|---|---|
| `127.0.0.1` | always | plain HTTP |
| Tailscale addresses | Tailscale is up | plain HTTP inside WireGuard |
| Wi-Fi address | LAN enabled **and** the network is trusted | HTTPS, pinned certificate |

On a network you have not trusted, the LAN listener does not exist. There is no setting that
serves an untrusted network.

**Networks are recognised, not named.** A network counts as yours when the NetworkManager
connection profile *and* the router's MAC address match a trusted entry. A hotspot that copies
your Wi-Fi name is a different network, and stays untrusted.

**Each device is separate.** Every device gets its own token, stored on the PC only as a SHA-256
hash. Revoking one device takes effect on its next request and does not affect the others. On the
phone the token is encrypted with an Android Keystore key and excluded from backup.

**Tailscale requests need both.** A request from a tailnet address must come from your own
Tailscale identity *and* carry a valid device token. Being on the tailnet is not enough.

**LAN requests must be pinned.** The bridge serves a self-signed certificate; its SHA-256 travels
in the pairing QR code, and the app trusts only that certificate, checking it before sending the
token. A different device that took over the PC's old IP cannot present that certificate, so it
gets nothing. The app additionally refuses, in an interceptor on every request, to send
credentials to a LAN host that is not on pinned TLS.

**Bodies are bounded before authentication.** Chunked bodies are refused (`411`) and bodies over
1 MB are rejected (`413`), so an oversized upload costs nothing. Per-device limits: 240 requests
per minute, 20 runs per minute, prompts up to 100k characters.

**Destructive actions are awkward on purpose.** Deleting a session needs a confirmation in the app
*and* the session id repeated in `?confirm=`. Resent messages are deduplicated by a client-generated
id, so a retry on a flaky network cannot start the same run twice. A session can have only one
active run.

**The audit log records access, not content.** `~/.local/state/hermes-remote/audit.log` (mode 600)
holds timestamp, peer, device, method, path, status and duration. Never tokens, never prompts,
never message text.

**Updates are verified twice.** The app's only traffic outside the bridge is the GitHub update
check, and it carries no token. The APK's SHA-256 is compared with the digest GitHub publishes,
and Android itself refuses to install an update signed with a different key. A self-built APK
therefore cannot be replaced by a release build, by design.

**Firewall changes are narrow and confirmed.** Only private address ranges are opened, only on
request, and never automatically.

## What this does not protect against

Stated plainly, because a security document that only lists wins is not useful.

- **A compromised PC.** The bridge runs as your user. Anything that can read your files can read
  `~/.hermes/.env`. Use full-disk encryption.
- **A rooted phone.** Keystore-backed storage raises the cost of token theft; it does not make it
  impossible.
- **Your Hermes agent's own judgement.** Approvals are a speed bump for a *runaway* command, not a
  sandbox. An agent that is talked into running something harmful will still be talked into it.
  `approvals.mode = manual` and a sensible prompt are your real controls.
- **Traffic analysis.** The bridge refuses untrusted networks, but it cannot tell you that someone
  on a network you *did* trust is watching. Use Tailscale when that matters.
- **A stolen PC while it is unlocked and on.** The token store and the key are readable by your
  user account.
- **Hermes' own API.** The bridge is a gate in front of it, not a sandbox around it. Anything that
  can reach `127.0.0.1:8642` with the key has the same access the bridge has.

## Reporting a vulnerability

See [SECURITY.md](../SECURITY.md).
