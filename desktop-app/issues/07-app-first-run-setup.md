# 07: Desktop: first-run setup (uv, venv, change list)

Lane: full
Branch: feature/desktop-app
Platform: App
Status: in-progress
Round: 0
Blocked by: 05
Terms: first run, uv, per-user venv, change list
Seam: Rust `Setup` with injected `Downloader`, `ProcessRunner` and `Fs`; steps reported to the UI as events; OS-specific last step behind a trait implemented by tickets 20 (Linux) and 32 (Windows).
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
