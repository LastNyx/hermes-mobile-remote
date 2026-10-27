# 07: Desktop: first-run setup (uv, venv, change list)

Lane: full
Branch: feature/desktop-app
Platform: App
Status: in-progress
Round: 0
Blocked by: 05
Terms: first run, uv, per-user venv, change list
Seam: new `desktop/src-tauri/src/setup.rs`: Rust `Setup` with injected `Downloader`, `ProcessRunner` and `Fs`; steps reported to the UI as events; OS-specific last step behind a trait implemented by tickets 20 (Linux) and 32 (Windows).
Test command: cd desktop && bash test.sh
Verify: Planner runs setup on this PC into a temporary LOCALAPPDATA: uv downloaded and hash-checked, venv synced, bridge answers. The user does the same on the Arch PC.

**Blocked by:** 05
**Status:** todo

## What to build

After install (and after every app update) the app gets the bridge ready (D11, D16, D22).

1. Show the list of what will change (uv location, managed Python, the venv path, autostart, the Hermes API
   server setting, the optional firewall rule + UAC) and wait for "Continue". Save it to
   `<config_dir>/INSTALLED.txt`; the settings page shows it.
2. Find `uv` on PATH or in `<data_dir>/uv/`; else download the uv release archive for this OS/arch from
   GitHub Releases over HTTPS, verify its SHA-256 against the release's `.sha256` file, extract to
   `<data_dir>/uv/`. A hash mismatch stops setup with a clear error.
3. Copy the bundled bridge source (Tauri resources: `bridge/` incl. `uv.lock`) to `<data_dir>/bridge/` and run
   `uv sync --frozen` there with `UV_PROJECT_ENVIRONMENT=<venv>`.
4. Hand over to the OS step (autostart / systemd unit), then wait up to 20 s for `/v1/local/status`.
5. On an app update: stop the bridge first (Windows locks venv files), re-run 3-4, start it again.
Progress and errors go to the UI; nothing runs PowerShell.

## Acceptance criteria

- [ ] Change list shown before any change; saved to INSTALLED.txt
- [ ] uv found on PATH skips the download; otherwise download + SHA-256 check; mismatch aborts
- [ ] `uv sync --frozen` argv and env exact (fake runner)
- [ ] Update path stops the bridge before syncing and starts it after
- [ ] UI: setup screen shows each step and errors
- [ ] An API without its screen fails this ticket

## Planner notes (2026-10-28)

- Files: `desktop/src-tauri/src/setup.rs` (logic, tests in the same file), commands registered in `lib.rs`,
  a `desktop/src/Setup.vue` screen + calls added to `desktop/src/api.js` (plain JS; tests mock
  `@tauri-apps/api/core` as `desktop/src/ui.test.js` does). App.vue shows Setup when `setup_state` says setup is
  needed (no venv / bridge exe at `BridgePaths`) and offers it again from Settings.
- Reuse `bridge::BridgePaths` (config dir, venv, exe), `bridge::ProcessRunner`/`SystemRunner` (process spawning,
  CREATE_NO_WINDOW on Windows) and `bridge::LocalHttp` (the 20 s wait polls `status`). `Fs` and `Downloader`
  are new traits; the real Downloader uses `ureq` (already in Cargo.toml). Use `sha2` + `hex` for the hash,
  `flate2` + `tar` for .tar.gz, `zip` for .zip (all already in Cargo.toml; do not edit it).
- Data dir: Windows `%LOCALAPPDATA%\hermes-remote`, Linux `$XDG_DATA_HOME/hermes-remote` (default
  `~/.local/share/hermes-remote`). Venv = `<data_dir>/venv` (matches BridgePaths). uv goes to `<data_dir>/uv/`.
- uv download (pin the version as a const `UV_VERSION = "0.13.6"`): asset
  `https://github.com/astral-sh/uv/releases/download/<v>/uv-<target>.<ext>` with its hash file at the same URL plus
  `.sha256` (content: `<hex>  <file name>`). Targets: `x86_64-unknown-linux-gnu` / `aarch64-unknown-linux-gnu`
  (`.tar.gz`, binary at `uv-<target>/uv`), `x86_64-pc-windows-msvc` / `aarch64-pc-windows-msvc` (`.zip`,
  `uv.exe` at the archive root). Known value for tests: linux x86_64 0.13.6 hash
  `f29c3e94ffabd7e6f3ba521dba169a9b7ec12100bf3257087f0f24f6123f3645`. Set the exec bit on Unix.
- Bridge source: Tauri resource dir `bridge/` (bundle config is the planner's, ticket 23/34). The resource dir
  path is injected; in dev it is the repo's `bridge/` (`CARGO_MANIFEST_DIR/../../bridge`). Copy it skipping
  `.venv`, `__pycache__`, `.pytest_cache`, `tests`.
- `uv sync --frozen --no-dev` in `<data_dir>/bridge`, env `UV_PROJECT_ENVIRONMENT=<venv>`. Exact argv in a test.
- "Hermes API server setting" in the change list: the bridge needs nothing enabled (install.sh says so); list it as
  "Hermes Agent config: not changed".
- OS last step: trait `OsStep { fn describe(&self) -> Vec<String>; fn apply(&self) -> Result<(), String>;
  fn stop_bridge(&self) -> Result<(), String>; fn start_bridge(&self) -> Result<(), String>; }` with a
  `NoOsStep` used until tickets 20 (Linux systemd) and 32 (Windows) implement it. With `NoOsStep`, step 4 starts
  nothing; the 20 s wait then reports "the bridge did not answer" as a warning, not an error.
- Events: emit `setup-progress` `{step, state: "running"|"done"|"error", message}` through an injected reporter
  (Tauri `Emitter` in the real command, a Vec in tests).

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

Commit 608928e. `cd desktop && bash test.sh`: cargo 12 passed, vitest 12 passed, vite build ok. Tests and code were written together, so the red run was not observed separately.
- Change list shown before any change, saved to INSTALLED.txt: Rust `change_list_is_shown_before_any_change_and_saved_to_installed_txt`.
- uv on PATH skips the download; otherwise download, SHA-256 check, mismatch aborts: `uv_on_path_skips_the_download_otherwise_download_is_hash_checked`.
- `uv sync --frozen` argv and env exact: `uv_sync_argv_and_env_are_exact`.
- Update stops the bridge before sync and starts it after: `update_stops_the_bridge_before_syncing_and_starts_it_after`.
- UI: Setup screen steps and errors: vitest `Setup` in ui.test.js. The Setup screen has a Continue button, and Settings has "Run setup again".
- Reused: `bridge::BridgePaths`, `ProcessRunner`, `SystemRunner`, `LocalHttp`, `BridgeApi`, `installed_list_in` (already reads INSTALLED.txt). Nothing existed for download, hash, extract or copy.
- Changed `ProcessRunner`: `run_with(exe, args, env, cwd)` is now the required method and `run` a default over it, because uv needs env and cwd. `bridge.rs` FakeRunner and SystemRunner were updated.
- Data dir is `venv.parent()` from `BridgePaths`, so on Linux it ignores `XDG_DATA_HOME`, as `BridgePaths` does. Planner may want to align.
- The real command uses `NoOsStep` until tickets 20 and 32. Update mode is chosen by the UI as `!needed`; nothing triggers it automatically after an app update.
Not checked: real download from GitHub, real `uv sync`, Windows build, Tauri event delivery in a running app, `RealFs` copy skip list, `RealFs::which`.
