# 34: Windows: NSIS installer, migration from the script install, uninstall

Lane: full
Branch: feature/desktop-app
Platform: Windows
Status: done
Round: 0
Blocked by: 07, 31, 32
Terms: NSIS, migration, uninstall
Seam: new `desktop/src-tauri/src/migration.rs` (plus the cfg(windows) call in `setup.rs`'s Windows OS step and an `uninstall` CLI flag in `desktop/src-tauri/src/main.rs`/`lib.rs`): Rust `Migration` (find old PowerShell tray / run-bridge by command line via sysinfo, `schtasks /Query|/Delete`) and `Uninstall` steps behind traits; NSIS bundle config by the planner.
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

## Planner notes (2026-10-28)

- Process match: copy install.ps1:91 exactly: a `powershell.exe`/`pwsh.exe` process whose command line matches
  the regex `-File\s+(?:"[^"]*|\S*)[\\/](tray|run-bridge)\.ps1` (case-insensitive). Use `sysinfo` (already in
  Cargo.toml; `System::new()` + `refresh_processes_specifics(ProcessesToUpdate::All, true,
  ProcessRefreshKind::nothing().with_cmd(UpdateKind::Always))`, `Process::cmd()` joined with spaces) behind a
  `ProcessList` trait (fake in tests). Never match the app's own pid. Kill with `Process::kill()`. No new crates:
  write the match without the `regex` crate (a small hand matcher over the joined command line is fine) and test it
  against: `-File C:\x\bridge\windows\tray.ps1`, `-File "C:\Program Files\h\bridge\windows\run-bridge.ps1"`,
  `-Command "... tray.ps1 ..."` (no match), `install.ps1 -File x` (no match), `-File tray.ps1` without a path
  separator (no match, as the regex requires one).
- Old task: `schtasks /Query /TN "Hermes Mobile Remote"` (exit 0 = exists) then `schtasks /Delete /TN "Hermes Mobile
  Remote" /F` through `bridge::ProcessRunner` (CREATE_NO_WINDOW is already applied by SystemRunner on Windows). An
  absent task is fine.
- Migration runs once from the Windows OS step of setup (`win_child::WindowsOsStep::apply`, before autostart is
  turned on); data dirs (`%LOCALAPPDATA%\hermes-remote\config` and `state`) are never touched.
- Uninstall: `hermes-remote-desktop --uninstall [--remove-data]` (parsed in `run()` before the Tauri builder): stop
  the bridge child if any (`run_mode::app_child().stop()`), disable autostart (HKCU Run value via the autostart
  plugin is not available without an app; delete the value `Hermes Remote` under
  `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` with `reg delete ... /f` through ProcessRunner), delete the old
  task, run `<venv>\Scripts\hermes-remote-bridge.exe firewall remove --port <port> --elevated` through the bridge's
  own elevation (`firewall remove` CLI from ticket 31; one UAC), and with `--remove-data` delete
  `%LOCALAPPDATA%\hermes-remote`. Order is tested: bridge stopped first. Never touch Hermes' `.env`.
- The NSIS hook that calls `--uninstall` is the planner's (tauri.conf.json is protected); just provide the flag.
- Keep `bash test.sh` green on Linux and `cargo xwin check --tests` clean.

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

Commit: 8267209. `bash test.sh`: cargo 48 passed, vitest 18 passed, vite build ok.
- Criterion 1 (path-shaped match, own pid skipped): `old_script_processes_match_by_path_shaped_command_line_only`.
- Criterion 2 (task argv, absent ok): `task_deletion_argv_is_exact_and_an_absent_task_is_fine`.
- Criterion 3 (data dirs untouched): `migration_leaves_the_data_dirs_alone` (weak: Migration has no data access by construction).
- Criterion 4 (order): `uninstall_stops_the_bridge_before_removing_autostart_and_the_firewall_rule`; also covers `--uninstall`/`--remove-data` parsing. WindowsOsStep migrate-first order in the existing win_child test.
- Reuse: ProcessRunner/SystemRunner, BridgePaths, ChildSupervisor, sysinfo (already a dep); nothing existing for process matching.
- Deviation: the firewall step runs `firewall remove --port N` WITHOUT `--elevated` (the bridge CLI shows the UAC itself; `--elevated` is the relaunched child and would just fail unelevated).
- Notes added: 1. Uninstall `--remove-data` deletes the parent of config_dir (`%LOCALAPPDATA%\hermes-remote`).
Not checked: Windows build (`cargo xwin check`), real sysinfo kill/cmd behaviour, the NSIS hook (planner's).

Planner Verify: SKIP no Windows host (NSIS install over a script install, uninstall from Settings>Apps need Windows/CI; makensis not on this PC). Checked: suite green, xwin check --tests clean; planner added NSIS config (
