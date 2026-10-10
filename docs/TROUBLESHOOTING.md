# Troubleshooting

Start with `bridge/.venv/bin/hermes-remote-bridge doctor`. It checks the things that are actually
worth checking and explains what is wrong. Everything below is a symptom it may not name.

## The phone cannot connect at all

On **Windows**, the same checks apply with different commands: `Get-ScheduledTask -TaskName
'Hermes Mobile Remote'` instead of `systemctl`, `%LOCALAPPDATA%\hermes-remote\state\bridge.log`
instead of `journalctl`, and `hermes-remote-bridge firewall` opens Windows Defender Firewall
(one UAC prompt). There is no "trust this network?" popup: run `hermes-remote-bridge trust`.
WSL2 cannot work; see [PLATFORMS.md](PLATFORMS.md).

**Windows: "running scripts is disabled on this system".** Run `install.cmd` instead of
`install.ps1`. It relaxes the policy for that one run only; do not change the policy yourself.

**Windows: a window flashed "the following arguments are required: command".** That is the bridge
`.exe` double-clicked with nothing after its name. Use `pair.cmd` to pair, or open a prompt and run
`hermes-remote-bridge doctor`. Current versions show a help screen instead of that error.

**Windows: "uv is required".** Older copies of the installer stopped here. Pull the latest, or run
`winget install --id=astral-sh.uv`, open a *new* PowerShell window (the old one does not see the
new PATH), and run the installer again.

**`forbidden_network` / "The PC doesn't serve this network."**

The bridge only serves loopback, Tailscale, and a Wi-Fi network you marked trusted. On the PC:

```bash
bridge/.venv/bin/hermes-remote-bridge trust          # trust the network you are on now
bridge/.venv/bin/hermes-remote-bridge trust --list   # what is trusted
```

You are trusting the network you *control*. On someone else's Wi-Fi, use Tailscale instead — the
LAN listener is not supposed to exist there.

**It worked yesterday on the same Wi-Fi.**

The PC moved to a different network, or the router's MAC changed, or Tailscale went down. Check
`journalctl --user -u hermes-remote-bridge -f`; the bridge logs every listen-set change. It rebinds
within about 5 seconds of a change, so a phone that cannot connect afterwards is usually an IP
change the app has not found yet — leave the app open for a moment, or check that Avahi is running
if you rely on mDNS.

**Connection timeouts on the LAN.**

Check the firewall:

```bash
bridge/.venv/bin/hermes-remote-bridge firewall
```

It prints the exact rules for ufw or firewalld and asks before applying them. Only private ranges
are opened.

**`forbidden_peer` / "Bridge refused this Tailscale identity."**

The request came from a tailnet address that is not your login. Either someone else on your tailnet
is trying to connect, or `allowed_logins` in `config.toml` is too narrow. Empty means "the owner of
this node".

## The app is paired but shows nothing

**"This device was revoked or the token is wrong. Pair again."**

The device was revoked, or `~/.config/hermes-remote/devices.json` was edited or replaced. Re-pair.

**The connection flips between LAN and Tailscale.**

The System tab shows the current path with manual switches. Automatic re-selection happens on a
network change, every 20 s while the app is open, and when you return to the app. If both paths are
reachable it prefers the LAN.

**"Hermes is not answering" / the app keeps reconnecting.**

The bridge could not reach a Hermes backend. `hermes-remote-bridge doctor` says which: no backend
in the spawn ledger and `start_hermes = false`, or `hermes serve` failed to start (its log is
`~/.local/state/hermes-remote/hermes-serve.log`). Opening the Hermes desktop app also starts one,
and the bridge attaches to it on the next connect.

**Nothing loads after updating only the PC or only the phone.**

1.0 changed the protocol. Update both: `./install.sh` on the PC, the app from its System tab.

## Turns misbehave

**Typing while a turn runs steers it instead of sending a new message.**

By design, as on the desktop: one turn at a time per session. Stop the turn, or open a new chat.

**The phone lost the connection mid-turn.**

The turn keeps running on the PC. When the phone reconnects it re-opens the chat with
`session.resume`, which returns the transcript so far, the turn still in flight and any open
question, then continues streaming.

**"Too many requests".**

The per-device limits are 240 requests/min and, on a live connection, 600 frames/min. Usually a
reconnect loop on a network that drops every few seconds. Fix the network, not the limit.

## Slash commands

**A command is not in the list.**

The list comes from Hermes' own completer. Commands that only work in a terminal or open a desktop
window (`/voice`, `/skin`, `/pet`, `/config`, …) are hidden, the same set the desktop app hides.

**"Method not available from the phone".**

The bridge's relay refused a call that the desktop's chat view does not make. If the app made it,
that is a bug: report it with the bridge log line.

## Notifications

**No notification when a turn finishes.**

Notifications cover chats opened on the phone, while the app keeps its connection in the
background (a silent "watching" notification shows while it does). Check that the app is allowed
to run in the background: Xiaomi, Huawei and some other vendors kill background apps aggressively, and the
setting is per-vendor and easy to miss.

## Updates

**The in-app update never appears.**

The app checks GitHub Releases at startup. Behind a captive network or with no internet, the check
fails silently by design — it is the only traffic outside the bridge and it carries no token.

**Android refuses to install the update.**

A self-built APK is signed with a different key than the releases, and Android only replaces an app
signed with the same key. Uninstall, then install the release.

## The bridge itself

**The service will not start.**

```bash
systemctl --user status hermes-remote-bridge
journalctl --user -u hermes-remote-bridge -n 50
```

The most common cause: a saved network went away and the bridge is trying to rebind an address it
no longer has. It exits with status 75
when the listen set changed, and systemd restarts it.

**Editing the unit file changed nothing.**

The installed unit is a copy, not a symlink. Re-run `./install.sh`.

**I want to see everything the bridge is doing.**

`hermes-remote-bridge doctor`, then the audit log:

```bash
tail -f ~/.local/state/hermes-remote/audit.log
```

It records device, path, status and duration. It never contains tokens or prompts.

## Reporting a bug

Include `doctor` output, the relevant bridge log lines, the app's Android version, and how to
reproduce it. Redact anything from a screenshot that you would not post publicly.
