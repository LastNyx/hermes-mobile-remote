# 34: Windows: NSIS installer, migration from the script install, uninstall

Lane: full
Branch: feature/desktop-app
Platform: Windows
Status: todo
Round: 0
Blocked by: 07, 31, 32
Terms: NSIS, migration, uninstall
Seam: Rust `Migration` (find old PowerShell tray / run-bridge by command line via sysinfo, `schtasks /Query|/Delete`) and `Uninstall` steps behind traits; NSIS bundle config by the planner.
Test command: cd desktop && bash test.sh
Verify: Planner on this PC (currently a script install): install the built NSIS -> old tray gone, old task deleted, Run key set, paired devices and trusted networks kept; uninstall from Settings > Apps removes autostart and the firewall rule after one UAC.

**Blocked by:** 07, 31, 32
**Status:** todo

## What to build

Installer and upgrade (D10, D12, D14): planner configures NSIS per-user with the WebView2 bootstrapper. On
first run the app migrates a script install: kills `powershell.exe` processes whose command line runs
`tray.ps1` or `run-bridge.ps1` (path-shaped match, as `install.ps1` does), deletes the "Hermes Mobile Remote"
Scheduled Task (`schtasks /Delete /TN ... /F`), then turns on autostart. Config, devices and trusted networks
stay where they are. Uninstall hook: stop the bridge, remove autostart and any old task, remove the firewall
rule (one UAC, ticket 31), keep data unless "remove my data" is ticked. Hermes' `.env` is never touched.

## Acceptance criteria

- [ ] Old processes matched by path-shaped command line only (not the app's own)
- [ ] Task deletion argv exact; absent task is fine
- [ ] Data dirs untouched by migration
- [ ] Uninstall order: bridge stopped before autostart/firewall removal

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
