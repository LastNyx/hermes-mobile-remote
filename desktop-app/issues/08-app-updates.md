# 08: Desktop: updater and .deb release check

Lane: quick
Branch: feature/desktop-app
Platform: App
Status: review
Round: 0
Blocked by: 07
Terms: updater, release check
Seam: new `desktop/src-tauri/src/updates.rs`: Rust `UpdateCheck` with an injected GitHub Releases client; the Tauri updater plugin for NSIS/AppImage.
Test command: cd desktop && bash test.sh
Verify: Planner points the check at a test release JSON; the user sees the banner. Real update is a manual check after the first release.

**Blocked by:** 07
**Status:** todo

## What to build

Updates (D22, D30): Windows NSIS and AppImage builds use the Tauri updater plugin (endpoint: the
`latest.json` asset of the newest GitHub release of nideta231/hermes-mobile-remote). `.deb` installs (detect via
`/usr/share/doc/hermes-remote` or the `TAURI_BUNDLE_TYPE`/package marker) instead check the latest release
version and show "New version X available" with a link to the release page. After any update, ticket 07's
update path re-syncs the venv.

## Acceptance criteria

- [ ] Updater used on NSIS/AppImage, release check on .deb (fake bundle type)
- [ ] Banner shows only when the release version is newer (semver compare)
- [ ] UI: banner with link on .deb; 'Restart to update' on updater builds
- [ ] An API without its screen fails this ticket

## Planner notes (2026-10-28)

- New module `desktop/src-tauri/src/updates.rs` (logic + tests), `pub mod` in `lib.rs`; commands
  `update_check` and `update_install` registered in `lib.rs`; a banner component (e.g. `desktop/src/UpdateBanner.vue`)
  shown by `App.vue`; calls added to `desktop/src/api.js`. Use `semver` (already in Cargo.toml) for the compare
  and `ureq` for the GitHub request. Do not edit Cargo.toml, package.json or tauri.conf.json.
- Bundle type: `tauri::utils::platform::bundle_type()` returns `Option<BundleType>` (`Deb`, `AppImage`, `Nsis`,
  ...; None in a dev build). Map Deb -> release check, AppImage/Nsis -> updater, None -> release check. Inject it.
- The repo's GitHub releases also carry the Android app: tags look like `v1.25.0` with `.apk` assets only. Desktop
  releases will use tags `desktop-v<semver>` (planner decision; ticket 09 makes the CI produce them). The release
  check lists `https://api.github.com/repos/nideta231/hermes-mobile-remote/releases?per_page=20`, keeps tags
  starting with `desktop-v`, takes the highest semver above the app version, and links its `html_url`.
  Send a `User-Agent` header (GitHub requires one). No such release yet is the normal case: no banner, no error.
- Updater builds: use `tauri_plugin_updater::UpdaterExt` (`app.updater()?.check().await`) behind a trait so tests
  use a fake; "Restart to update" downloads + installs then calls `app.restart()`. Before restarting, call the
  existing setup update path (`setup` with `update: true`) is NOT possible in-process; instead write a marker
  `<config_dir>/needs-resync` and on next start App.vue runs `setup_run {update: true}` when the marker exists
  (then the command removes it). Put the marker logic in Rust with a test.
- Network errors in the check are logged and show nothing (no banner, no error) to keep the UI calm.

## Facts that hold for every ticket

- Design: `docs/desktop-app-design.md` (decisions D1-D34). Read it first. Branch `feature/desktop-app`, from
  `origin/main` v1.23.1. Never push, never touch `main`.
- Platform tags: **App** = shared code, buildable and testable on Windows or Linux. **Windows** / **Linux** =
  needs that OS for its Verify (unit tests still run anywhere with fakes).
- Bridge (Python, FastAPI): `bridge/hermes_remote_bridge/`. `app.py` = the HTTP API; the `guard` middleware
  (`app.py:266`) classifies the peer with `peer_verdict` (`app.py:247`: loopback -> "local", tailnet -> whois,
  private LAN over https). `cli.py` = `serve`, `pair`, `revoke`, `devices --json`, `trust [--list] --json`,
  `doctor --json`, `firewall` (`cli.py:500-520`); exit 75 = network changed, restart (`cli.py:160`);
  `_port_in_use`/`_bind` (`cli.py:51-90`). `host/` is the OS seam (`base.py`, `linux.py`, `windows.py`);
  nothing outside `host/` may branch on `sys.platform`.
- Bridge tests: `bridge/tests/` (pytest). Tests never touch the real OS for anything that opens UI, starts a
  process, or changes the firewall/registry/systemd: fake `cli.host` / the host object and assert the fake saw
  the call. Two tests (symlink privilege WinError 1314, CRLF) already fail on the Windows dev PC; that is the
  baseline, not this ticket.
- Desktop app (Tauri v2, Rust + Vue 3 + Vite): top-level `desktop/` (`desktop/src-tauri/` Rust, `desktop/src/`
  Vue). Created by ticket 04. Rust logic sits behind traits so `cargo test` runs without a window, a real
  bridge, systemd or Win32; Vue components are tested with vitest and a mocked `@tauri-apps/api` `invoke`.
- Protected (planner edits only; ask the planner in your Result if you need a change): `bridge/pyproject.toml`,
  `bridge/uv.lock`, `desktop/package.json`, `desktop/package-lock.json`, `desktop/src-tauri/Cargo.toml`,
  `Cargo.lock`, `desktop/src-tauri/tauri.conf.json`, `desktop/src-tauri/capabilities/`, `desktop/vite.config.*`,
  `desktop/test.sh`, `.github/`, `.team.json`.
- Security invariants (`docs/SECURITY.md`): the bridge never binds `0.0.0.0`/`::`; LAN is HTTPS only; the
  relay forwards only `ALLOWED_METHODS`. `/v1/local/*` (ticket 02) is never added to the relay allowlist and
  never served to a non-loopback peer.
- No PowerShell in plumbing (D1): no new `.ps1`, no `powershell.exe` spawned by the bridge or the app.
- No Co-Authored-By trailer on any commit.

## Constraints

- Do not touch `android/`, `bridge/linux/tray.py` (kept until a follow-up PR, D23), or other branches.
- Do not delete files: if a file becomes unused, leave it and say so in the Result (the planner deletes it).
- Never start the real bridge on port 8650, never change the real firewall, registry, Scheduled Tasks or
  systemd units from a test.

## Result

Commit: a10b151. `cd desktop && bash test.sh`: cargo 34 passed, vitest 17 passed, vite build ok.
- AC1 (updater vs release check by bundle type, fake bundle): Rust `bundle_type_picks_updater_or_release_check`.
- AC2 (newer only, semver; other tags, errors, no release = no banner): Rust `banner_only_for_a_newer_desktop_release_by_semver`.
- AC3/AC4 (UI banner link on .deb, 'Restart to update' on updater builds, error inline, nothing without update): vitest `UpdateBanner` mounts App.
- Resync marker: Rust `install_leaves_the_resync_marker_then_installs`; vitest "re-syncs the venv on start" (setup_state `resync` -> `setup_run {update:true}`; setup_run clears the marker on success).
- Reuse: semver, ureq, `BridgePaths::from_env().config_dir`, `setup_run`/`setup_state`, api.js `invoke` pattern; no existing update code found.
- Notes added: 1 line to .scratch/dev-notes.md.
Not checked: the real GitHub request, the real Tauri updater download/restart (needs a signed release; updater pubkey/endpoint in tauri.conf.json not touched or verified), and that a `target=_blank` link opens in the system browser inside the Tauri webview (shell-open may be needed).

Planner Verify: PASS real UpdateCheck (release-check mode) with a test release JSON: app 0.1.0 -> 0.2.0 link (desktop-v tags only, APK tag and broken tag ignored, highest newer wins); app 0.2.0/0.3.0 -> none; live GitHub 
