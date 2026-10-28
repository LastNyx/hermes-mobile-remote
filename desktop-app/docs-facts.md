# Docs facts: desktop app (for the docs worker, ticket 10)

Source of truth: `docs/desktop-app-design.md` (D1-D45). Everything below is built on `feature/desktop-app`.

## What users get now

- **Desktop app "Hermes Remote"** (Tauri: Rust + Vue) in `desktop/`, for Windows and Linux.
  - Windows: NSIS installer, per user, no admin, bundles the WebView2 bootstrapper. Unsigned: SmartScreen shows
    "More info > Run anyway" (D24). Tray icon; left click opens a flyout at the taskbar corner, focus loss hides it;
    right click: Open, Start/Stop bridge, Pair a device, Quit. The app owns the bridge: it runs
    `hermes-remote-bridge supervise` in a Job Object, so quitting or killing the app stops the bridge (D6, D7).
    If the app crashes, nothing restarts it; start Hermes Remote from the Start menu (D6). No "always on" mode on
    Windows (D34). Start at logon = HKCU Run key "Hermes Remote" (visible in Task Manager > Startup, D5).
  - Linux: AppImage and .deb. Normal window from the launcher; tray icon only when the desktop has a
    StatusNotifier host (KDE yes; GNOME needs the AppIndicator extension); menu items Open / Start / Stop / Quit,
    no left-click action on Linux (D20, D43). Run mode setting (D31, D41):
    - "Always on" (default): systemd user service `hermes-remote-bridge.service`; closing the app leaves the bridge
      running. "Start at login" = `systemctl --user enable/disable` (D32). "Keep running after logout" =
      `loginctl enable-linger`, only shown when start at login is on; if polkit refuses, the app says to run
      `loginctl enable-linger` in a terminal (D33).
    - "Only while the app is open": the app stops and disables the unit and runs `supervise` as its child; the
      bridge dies with the app. No systemd sandboxing in this mode (the settings page says so). A failed switch
      puts the service back and stays "always on".
  - Same UI on both: on/off switch, status line (network trusted?, firewall, LAN, tailnet), devices with re-pair /
    revoke (revoke confirms), "Pair a device" with the QR inline and a Copy button, Settings (doctor, firewall
    fix, start at logon, terminal shell, run mode on Linux, "what this install changed" list).
  - When the bridge is not running the app still shows devices/doctor through the CLI and says
    "bridge not running, showing saved data" (D21).
  - First untrusted network: an OS notification "Trust this network?" that opens the window (Linux: the window
    opens when the notification fires; the notification cannot report a click there). Asked once per network
    (`<config_dir>/desktop-asked.json`).
- **First-run setup** (D11, D16, D39, D40): shows the change list first and saves it to `<config_dir>/INSTALLED.txt`;
  finds `uv` on PATH or downloads uv 0.13.6 from GitHub Releases and checks its SHA-256; copies the bundled bridge
  source to `<data_dir>/bridge`; `uv sync --frozen --no-dev` into `<data_dir>/venv`; then the OS step (Linux:
  write + enable the systemd user unit; Windows: autostart on + start the bridge); waits up to 20 s for the bridge.
  Data dir: Linux `$XDG_DATA_HOME/hermes-remote` (default `~/.local/share/hermes-remote`), Windows
  `%LOCALAPPDATA%\hermes-remote`. Config dir unchanged (Linux `~/.config/hermes-remote`, Windows
  `%LOCALAPPDATA%\hermes-remote\config`). Hermes' own config and `.env` are never touched.
- **Upgrade from a script install on Windows** (D12): setup kills the old PowerShell tray / run-bridge (matched by
  command line), deletes the "Hermes Mobile Remote" Scheduled Task, then turns autostart on. Paired devices and
  trusted networks stay. On Linux the app takes over the existing unit of the same name; `bridge/linux/tray.py`
  keeps working until a follow-up removes it (D23). `install.sh` stays for source installs on Linux.
- **Uninstall on Windows** (Settings > Apps): stops the bridge, removes autostart and the old task, removes the
  firewall rule (one UAC prompt), keeps config/devices unless "Delete the application data" is ticked.
- **Updates** (D22, D30, D35-D37): AppImage and NSIS builds use the Tauri updater ("Restart to update" banner);
  after an update the app re-syncs the venv on next start. `.deb` (and dev builds) show "New version X available"
  with a link to the release page. Desktop releases are tagged `desktop-v<version>` (the APK keeps `v<version>`).
- **Removed** (D1, D12): `install.ps1`, `install.cmd`, `tray.cmd`, `pair.cmd`, `bridge/windows/*.ps1`. The bridge
  spawns no PowerShell. The phone's Terminal still opens the user's shell: Windows `pwsh` > `powershell` > `cmd`,
  overridable with `terminal_shell` in `config.toml` (D13), e.g. `terminal_shell = "\"C:\\Program Files\\Git\\bin\\bash.exe\" -l"`.
  Running `hermes-remote-bridge` with no command on Windows now says to pair from the Hermes Remote app.

## Bridge changes (Python)

- `hermes-remote-bridge supervise` (D28, D42): runs `serve`; exit 75 (network changed) -> restart after 2 s;
  crash -> back off `max(3, min(60, 5*quick_failures))` s (quick = under 20 s); `serve` exits **76** on a port
  conflict and the supervisor stops instead of retrying; stops (child too, exit 0) on SIGTERM/Ctrl+C or when its
  stdin is closed by the parent app (a TTY or `/dev/null` stdin is ignored). Log: `<state_dir>/bridge.log`, UTF-8,
  rotated at 2 MB. Also the headless path (`hermes-remote-bridge supervise` in a console / RDP session).
- `/v1/local/*` control API (D8, D29) for the desktop app: `GET status`, `GET devices`, `POST pair {name, replace?}`,
  `POST revoke {name}`, `GET trust`, `POST trust`, `POST untrust {id}`, `GET doctor`. Only from a loopback peer
  **and** with header `X-Hermes-Local: <secret>`; otherwise 403 `forbidden_local` (a tailnet/LAN peer gets 403
  even with the secret). The secret is `<config_dir>/local-secret`, 32 random bytes hex, mode 0600, created on first
  start, re-created if deleted (deleting it revokes every local client). Never in the relay's allowed methods, no
  device token, no device rate limit. JSON shapes equal the CLI's `--json` output (same functions).
- Windows host (D9): routes, router MAC, connection profile and firewall state through ctypes (iphlpapi) and
  comtypes (INetworkListManager, HNetCfg.FwPolicy2); no subprocess. The network identity is the same as before, so
  trusted networks stay trusted (CI compares with the cmdlets). `comtypes` is a Windows-only dependency.
- Windows firewall (D9): `hermes-remote-bridge firewall` relaunches itself elevated (one UAC prompt,
  `ShellExecuteExW runas`) as `firewall add --port N --elevated`; the rule "Hermes Mobile Remote" allows TCP N
  inbound, Private profile only, remote addresses = private ranges. `firewall remove` mirrors it and deletes only
  that rule. Cancelling UAC = "Not applied".
- Windows `service_state` for doctor: "active" when the bridge answers on the configured port, else "inactive".

## Open / known limits (say them plainly)

- D44: on Linux the bridge's own `notify-send` trust prompt still runs under `serve`, so a desktop can get two
  prompts (the app's and the bridge's) until the follow-up removes the bridge's.
- Linux notification click cannot be detected; the window opens when the notification fires.
- Code signing deferred (D24); the updater needs nideta's signing key (repo secret `TAURI_SIGNING_PRIVATE_KEY`)
  and the public key in `tauri.conf.json` before the first desktop release.
- The Windows code was written and unit-tested on Linux; CI's Windows jobs are the first real run.

## Files to edit (ticket 10)

`docs/PLATFORMS.md`, `docs/INSTALL.md`, `docs/TROUBLESHOOTING.md`, `docs/SECURITY.md` (the `/v1/local` surface and
its secret), `docs/ARCHITECTURE.md`, `docs/BRIDGE_API.md` (the `/v1/local/*` routes), `README.md` (install lines
still name install.cmd / pair.cmd), a "Replaced by docs/desktop-app-design.md" line at the top of
`docs/windows-tray-design.md`, `CHANGELOG.md` under `## Unreleased`, and the new `docs/desktop-app-manual-checks.md`
(checkbox list: flyout look and position on bottom/top/side taskbars; UAC prompt for the firewall; pairing a real
phone; upgrade from a script install; Windows app crash = bridge down; Linux run modes both ways; linger on/off
and the polkit message; GNOME without a tray; SmartScreen on the unsigned installer; AppImage and .deb install;
tray menu clicks on KDE; click through every tab; real update from a `desktop-v*` release).
Do not edit `docs/desktop-app-design.md` or `docs/fix-background-work-dies-on-app-close.md` (not ours).
