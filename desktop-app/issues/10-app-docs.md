# 10: Docs and manual checks

Lane: full
Branch: feature/desktop-app
Platform: App
Status: todo
Round: 0
Blocked by: 06, 07, 08, 20, 21, 22, 23, 30, 31, 32, 33, 34
Terms: docs
Seam: None (docs worker)
Test command: None (docs only)
Verify: Planner reads the docs against the decisions table; the user fills `docs/desktop-app-manual-checks.md` on both PCs.

**Blocked by:** 06, 07, 08, 20, 21, 22, 23, 30, 31, 32, 33, 34
**Status:** todo

## What to build

Docs worker with `.scratch/desktop-app/docs-facts.md`: update `PLATFORMS.md`, `INSTALL.md`,
`TROUBLESHOOTING.md`, `SECURITY.md` (the `/v1/local` surface and its secret), `ARCHITECTURE.md`, `BRIDGE_API.md`;
add a "replaced by docs/desktop-app-design.md" line atop `docs/windows-tray-design.md`; `CHANGELOG.md` under
`## Unreleased`; write `docs/desktop-app-manual-checks.md` (flyout look and position on bottom/top/side
taskbars, UAC prompt, pairing a real phone, upgrade from a script install, app crash = bridge down on Windows,
Linux run modes, linger, GNOME without tray, SmartScreen on the unsigned installer).

## Acceptance criteria

- [ ] Every D-number that changes user-visible behaviour appears in the docs
- [ ] Manual checks file exists as a checkbox list

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
