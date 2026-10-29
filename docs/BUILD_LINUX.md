# Building Hermes Remote on Linux

> **Status: untested.** Written from `docs/INSTALL.md` and the CI workflows before a local build was run. No `desktop-v*` release exists yet, so the desktop app must be built from source.

Hermes Remote lets you control Hermes Agent from an Android phone. It has two parts:
- **Bridge and desktop app (PC):** the bridge runs next to Hermes, and the desktop app installs and manages it.
- **Android app (phone):** the APK comes from GitHub Releases.

Repo: https://github.com/nideta231/hermes-mobile-remote

The project has no published desktop release yet (only phone APKs, `v1.18.0` to `v1.29.0`), so you have to build the desktop app yourself.

## 0. Requirements
- Linux with systemd (developed on Arch with KDE).
- Hermes Agent already installed and working (`hermes --version`).
- An Android 8.0+ phone.
- Optional: Tailscale on both PC and phone, for use away from home Wi-Fi.

## 1. Install build tools

**Arch / CachyOS / Manjaro**
```bash
sudo pacman -S --needed base-devel webkit2gtk-4.1 libayatana-appindicator librsvg patchelf xdotool openssl curl wget git nodejs npm
```

**Debian / Ubuntu**
```bash
sudo apt update
sudo apt install -y build-essential curl wget file git libwebkit2gtk-4.1-dev libayatana-appindicator3-dev librsvg2-dev libxdo-dev libssl-dev patchelf nodejs npm
```

**Fedora**
```bash
sudo dnf install -y webkit2gtk4.1-devel libappindicator-gtk3-devel librsvg2-devel libxdo-devel openssl-devel patchelf gcc gcc-c++ git nodejs npm
```

Install Rust (stable) if you don't have it:
```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source ~/.cargo/env
```

Use Node 22 or newer (`node -v`). Rust needs no particular version.

## 2. Get the source and build
```bash
git clone https://github.com/nideta231/hermes-mobile-remote.git
cd hermes-mobile-remote/desktop
npm ci
npm run tauri build -- --bundles appimage --config '{"bundle":{"createUpdaterArtifacts":false}}'
```
- The `--config` override skips updater signing. Without it the build asks for a signing key you don't have. The consequence is that your build won't auto-update.
- The first build compiles many Rust crates and takes several minutes.
- The AppImage is written to `src-tauri/target/release/bundle/appimage/`.
- To get a `.deb` instead, use `--bundles deb`. Prefer the AppImage on Arch.

## 3. Run it
```bash
chmod +x src-tauri/target/release/bundle/appimage/*.AppImage
./src-tauri/target/release/bundle/appimage/*.AppImage
```
For a launcher entry, copy the file somewhere permanent, for example `~/Applications/`. A tool like Gear Lever or AppImageLauncher can add the menu entry.

## 4. First-run setup
On first start the app lists the changes it will make, then does them. It saves the list to `~/.config/hermes-remote/INSTALLED.txt`. It:
1. Finds `uv`, or downloads it and checks its checksum.
2. Copies the bridge into `~/.local/share/hermes-remote`.
3. Creates the bridge's Python environment.
4. Enables the systemd user service `hermes-remote-bridge.service`.
5. Waits up to 20 seconds for the bridge to answer.

It never touches Hermes' own config.

**Tray icon:** KDE shows it. GNOME needs the *AppIndicator and KStatusNotifierItem Support* extension. Without a tray icon, use the app window.

**Run mode** (in Settings):
- **Always on** (default): the bridge keeps running after you close the app.
- **Only while the app is open:** the bridge stops when the app closes.
- To keep it running after logout, turn on **Keep running after logout**. This runs `loginctl enable-linger`.

## 5. Install the phone app
Download the newest `.apk` from https://github.com/nideta231/hermes-mobile-remote/releases (tags like `v1.29.0`, not `desktop-v…`). Allow installs from your browser or file manager when Android asks.

**The bridge and the app versions must match.** Always build the desktop app from the same release you install on the phone.

## 6. Pair the phone
1. In the desktop app, click **Pair a device**. A QR code appears.
2. Open the phone app, add a host, and scan the QR code.
3. The first time the PC joins a new network, a notification asks whether to trust it. Accept it for your home or office network.

Over local Wi-Fi the phone uses HTTPS on port 8650 with a pinned certificate. Away from home, install Tailscale on both devices.

## 7. Firewall
If the phone can't connect, the PC firewall may block port 8650. Use the **Firewall fix** in Settings, or run `hermes-remote-bridge doctor`. For `ufw`, run `sudo ufw allow 8650/tcp`.

## 8. Troubleshooting
- **Check the service:** `systemctl --user status hermes-remote-bridge`
- **Logs:** `~/.local/state/hermes-remote/`
- **AppImage won't start:** try `./Hermes*.AppImage --appimage-extract-and-run`. Install `fuse2` if it complains about FUSE.
- **Build fails on a missing `.pc` file:** a system library from step 1 is missing.
- **Docs:** `docs/INSTALL.md`, `docs/TROUBLESHOOTING.md` and `docs/PLATFORMS.md` in the repo.

## 9. Uninstall
```bash
systemctl --user disable --now hermes-remote-bridge
rm -rf ~/.config/hermes-remote ~/.local/share/hermes-remote ~/.local/state/hermes-remote
```
Then delete the AppImage and uninstall the phone app.

## Simpler alternative without the GUI
```bash
git clone https://github.com/nideta231/hermes-mobile-remote.git
cd hermes-mobile-remote && ./install.sh
bridge/.venv/bin/hermes-remote-bridge pair phone
```
This needs only `git` and `uv`.
