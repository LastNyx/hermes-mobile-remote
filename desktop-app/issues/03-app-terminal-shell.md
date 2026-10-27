# 03: Bridge: configurable terminal shell

Lane: quick
Branch: feature/desktop-app
Platform: App
Status: done
Round: 0
Blocked by: None
Terms: terminal shell
Seam: `terminal.default_shell(cfg)` in `bridge/hermes_remote_bridge/terminal.py` (today `default_shell()` at line ~28).
Test command: cd bridge && uv run pytest -q
Verify: None (unit tests are the check).

**Blocked by:** None
**Status:** todo

## What to build

The phone's Terminal opens the user's preferred shell (D13). A new config key `terminal_shell` (a command
line, e.g. `"cmd.exe"` or `"/usr/bin/fish -l"`) overrides the default. Without it: on Windows `pwsh`, then
`powershell`, then `cmd.exe` (`%COMSPEC%`); elsewhere unchanged (`$SHELL -l`, then bash, then `/bin/sh`).
An override that does not exist on PATH falls back to the default and logs one warning.

## Acceptance criteria

- [ ] Override used verbatim when it resolves
- [ ] Windows order pwsh > powershell > cmd with fakes for `shutil.which`
- [ ] Missing override -> default + one warning
- [ ] Non-Windows default unchanged

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

Commit: 62a2da5. Tests: `cd bridge && uv run pytest -q` -> 174 passed (4 new red first, then green).
- Override verbatim -> test_override_used_verbatim_when_it_resolves
- Windows order -> test_windows_order_pwsh_powershell_cmd
- Missing override -> test_missing_override_falls_back_with_one_warning
- Non-Windows default -> test_non_windows_default_unchanged
Reused: `Config` key pattern, `shutil.which`, existing default logic; nothing existing for command-line splitting (used `shlex`).
Wired: `create_app` default shell factory passes `cfg` to `Shell`. Notes added: 1.
Not checked: a real Windows host, a real PTY session with an override.

Planner Verify: SKIP Verify None; unit tests are the check (suite green)
