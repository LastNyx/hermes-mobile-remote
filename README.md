# Hermes Mobile Remote

Android remote for [Hermes Agent](https://hermes-agent.nousresearch.com). Chat with your agent,
follow runs live, approve tool calls and switch models from your phone, while Hermes keeps running
on your own PC.

```
Android app ── trusted Wi-Fi: HTTPS, pinned certificate ──┐
           └── anywhere else: Tailscale (optional) ───────┴─► bridge :8650 ─ loopback ─► Hermes API :8642
```

The app never talks to Hermes directly. A small bridge runs next to Hermes on the PC, holds the
Hermes API key, and only lets in phones you paired.

## Features

- **Chat and sessions:** stream replies; browse, rename, fork and delete sessions. Drafts survive
  app restarts.
- **Pinned sessions:** pins are shared with the Hermes desktop app.
- **Follow your PC:** open a session that's running on the desktop, CLI or a messaging platform,
  and the app tails it ("Live on your PC") as new messages land.
- **Approvals:** approve or deny tool calls from the app. System tab → Command approvals switches
  Hermes' global `approvals.mode` (Manual, Smart, Off).
- **Model picker:** choose, per message, any model your configured providers can actually serve.
- **Reasoning effort:** Off, Low, Medium, High, Extra high or Max per message, or Hermes' default.
- **Slash commands:** type `/` for Hermes' commands and your skills, with suggestions. `/title`,
  `/compress`, `/status`, `/tools`, `/memory`, `/plan` and every skill run on the PC exactly as on
  the desktop. `/model`, `/reasoning`, `/new` and `/stop` map onto the app's own controls.
- **Auto-update:** the app checks GitHub Releases at startup and installs new versions in place
  (System tab → App version).
- **Notifications:** get notified when a run you started finishes or needs approval while the
  app is in the background.
- **Local network first:** on Wi-Fi you trust (home, office), the phone connects directly. Tailscale
  is optional and only used when you're away.
- **Tablet layout:** session list beside the chat.
- **Desktop tab (optional):** opens your PC's desktop in [aFreeRDP](https://f-droid.org/packages/com.freerdp.afreerdp/)
  with the connection pre-filled. It's built for KDE's KRdp, which Microsoft's Android RD client
  doesn't work with.

## Requirements

| PC | Phone |
|---|---|
| Linux with systemd (developed on KDE Plasma, Arch-based) | Android 8.0+ |
| [Hermes Agent](https://hermes-agent.nousresearch.com) installed | |
| [uv](https://docs.astral.sh/uv/) | |
| Optional: NetworkManager, Avahi (finds the PC when its IP changes), Tailscale | Optional: Tailscale |

## Install

On the PC:

```bash
git clone https://github.com/nideta231/hermes-mobile-remote.git
cd hermes-mobile-remote
./install.sh
```

The installer is safe to re-run. It:

1. Enables the Hermes API server on loopback with a random key, if it isn't already.
2. Installs the bridge into `bridge/.venv` and starts it as a systemd user service.
3. Asks whether the current network is yours (home or office) and should be trusted.
4. If a firewall (ufw or firewalld) blocks the bridge port, shows the exact rules and asks before
   adding them. That's the only step that needs your password.
5. Runs `doctor` to check everything.

On the phone:

1. Install the APK from [Releases](https://github.com/nideta231/hermes-mobile-remote/releases/latest),
   or [build it](#building-the-app). Later versions install from inside the app.
2. On the PC, run `bridge/.venv/bin/hermes-remote-bridge pair phone`.
3. In the app, tap **Scan pairing QR code**. Each QR code pairs exactly one device.

To remove the service: `./install.sh --uninstall`. Paired devices and settings stay in
`~/.config/hermes-remote` until you delete it.

## How the phone connects

| Where you are | Connection | Tailscale needed? |
|---|---|---|
| A Wi-Fi you trusted (home, office) | Direct, HTTPS with the PC's pinned certificate | No |
| Any other Wi-Fi (cafe, hotel) | Tailscale only. The bridge doesn't listen on that network. | Yes |
| Mobile data | Tailscale | Yes |

- **Trusting a network:** when the PC joins a network it hasn't seen, a desktop notification asks
  whether to trust it. You can also run `hermes-remote-bridge trust` while on it.
- **How networks are recognised:** by the NetworkManager connection profile *and* the router's MAC
  address, so a hotspot that copies your Wi-Fi name isn't trusted.
- **Switching is automatic:** the app re-picks the best path when the phone's network changes,
  every 20 s while open, and when you return to it. System tab → Connection shows the current
  path and has manual switch buttons.
- **Finding the PC:** if its IP changes, the app finds it again over mDNS without re-pairing. On
  the PC side, network changes are picked up within about 5 s.

## Bridge commands

```bash
B=bridge/.venv/bin/hermes-remote-bridge
$B doctor                  # explains what's wrong if the phone can't connect
$B pair phone              # pair a device (QR in the terminal; --qr-png file.png to save it)
$B devices                 # list paired devices
$B revoke phone            # takes effect immediately
$B trust                   # trust the current network;  trust --list / trust --remove ID
$B firewall                # allow the port from private networks on ufw/firewalld (asks first)
journalctl --user -u hermes-remote-bridge -f
```

- **Config (optional):** `~/.config/hermes-remote/config.toml`. All keys and their defaults are in
  [`bridge/hermes_remote_bridge/config.py`](bridge/hermes_remote_bridge/config.py).
- **Rotating the Hermes key:** change `API_SERVER_KEY` in `~/.hermes/.env` and restart Hermes. The
  bridge reads the new key automatically.

## Security model

- **Hermes API:** stays on `127.0.0.1`. Its key never leaves the PC.
- **What the bridge listens on:** loopback, the Tailscale addresses, and the Wi-Fi address only on
  trusted networks. It never binds `0.0.0.0`.
- **Device tokens:** every device has its own token. The PC stores only its SHA-256 hash, and each
  token can be revoked on its own. On the phone the token is encrypted with an Android Keystore key
  and excluded from backups.
- **Tailscale requests:** must come from your own Tailscale identity *and* carry a valid
  device token.
- **Local network requests:** must use HTTPS.
  - The bridge uses a self-signed certificate, and the pairing QR carries its SHA-256 fingerprint.
  - The app trusts only that certificate and checks it before sending the token, so a different
    device sitting on the PC's old IP gets nothing.
  - Plain HTTP from the LAN is refused.
- **Firewall changes:** only private address ranges are allowed, and only after you confirm.
- **Audit log:** `~/.local/state/hermes-remote/audit.log` records device, path, status and duration.
  It never contains tokens or prompts.
- **Updates:** the update check is the app's only traffic outside the bridge: HTTPS to GitHub, no
  token. The APK's SHA-256 is checked against the one GitHub reports, and Android installs it only
  if it's signed with the same key as the installed app.
- **Safeguards:** deleting a session needs a confirmation, and the bridge rejects a delete that
  doesn't repeat the session id. Resent messages are deduplicated. Each session can have only one
  active run at a time.

## Building the app

Requires JDK 17+ and the Android SDK (`sdk.dir` in `android/local.properties`, or `ANDROID_HOME`).

```bash
cd android
./build.sh            # unit tests + HermesRemote.apk
./build.sh install    # same, then adb install
./build.sh test       # unit tests only
```

Without `android/keystore.properties` you get a debug-signed APK. For release-signed builds, create
that file (it's gitignored) with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Keep the
keystore safe: Android only installs updates signed with the same key.

A self-built APK is signed with a different key than the official releases, so the in-app updater
can't replace it with a release (Android refuses). Uninstall once to switch.

## Releasing

Pushing a version tag builds, signs and publishes the APK; installed apps pick it up from there.

```bash
git tag v0.8.1 && git push origin v0.8.1
```

The version comes from the tag (`v0.8.1` → versionName `0.8.1`, versionCode `801`). Signing uses
three repository secrets: `ANDROID_KEYSTORE_BASE64` (the keystore, base64), `ANDROID_KEYSTORE_PASSWORD`
and `ANDROID_KEY_ALIAS`. Forks that publish their own builds need their own key and must change
`Updater.REPO` to their repository.

## Development

```bash
cd bridge && uv sync && uv run pytest     # bridge unit tests
cd android && ./build.sh test             # app unit tests, including real pinned-TLS handshakes
```

Live end-to-end tests run against your real Hermes and use a few model calls:

```bash
bridge/.venv/bin/hermes-remote-bridge pair e2e --token-file /tmp/e2e.json
(cd bridge && uv run python tests/e2e_live.py /tmp/e2e.json)
(cd android && HERMES_REMOTE_E2E=/tmp/e2e.json ./gradlew testDebugUnitTest --tests '*LiveBridgeTest*')
bridge/.venv/bin/hermes-remote-bridge revoke e2e
```

The bridge's HTTP API is documented in [`docs/bridge-api.md`](docs/bridge-api.md).

## Limitations

- **Live streaming:** only runs started from the app stream token by token. A run started on the
  PC shows up in the app as Hermes saves each step.
- **Notifications:** only runs started from the app trigger them, not runs started on the PC.
- **Battery savers:** some Android vendors (Xiaomi, Huawei and others) stop background apps
  aggressively. If notifications stop arriving, allow the app to run in the background.
- **Slash commands:** commands that need the agent loaded in memory on the PC (`/retry`, `/undo`,
  `/btw`, `/goal`, `/usage`, ...) aren't offered; they'd act on an empty agent from the bridge.
- **Platform:** the bridge is Linux-only for now.

## License

[MIT](LICENSE)
