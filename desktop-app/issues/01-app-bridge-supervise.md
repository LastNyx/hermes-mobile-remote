# 01: Bridge: `supervise` command and a port-conflict exit code

Lane: full
Branch: feature/desktop-app
Platform: App
Status: done
Round: 0
Blocked by: None
Terms: supervisor, quick failure, exit 75, port conflict
Seam: `hermes_remote_bridge/supervise.py`: a `Supervisor` taking an injected child launcher, clock and sleep; `cli.py` `supervise` subcommand calls it.
Test command: cd bridge && uv run pytest -q
Verify: Planner runs `hermes-remote-bridge supervise` on this PC, kills the `serve` child, sees it restart and the log line; starts a second `supervise` and sees it stop on the conflict.

**Blocked by:** None
**Status:** todo

## What to build

A user (or the desktop app) runs `hermes-remote-bridge supervise` and the bridge stays up: it runs `serve`
as a child and applies the policy that `bridge/windows/supervise.ps1` has today, now in Python and the same on
every OS.

- Exit 75 (network changed): restart after 2 s, reset the quick-failure count.
- A port conflict: `serve` now exits with its own code (76) instead of the generic `SystemExit(message)` from
  `_bind`, printing the same sentence. The supervisor stops (does not retry) on 76 and logs why.
- Any other exit: a "quick failure" is a run shorter than 20 s; wait `max(3, min(60, 5 * quick_failures))`
  seconds (count capped at 12, reset to 0 after a run of 20 s or more), then restart.
- Log: the child's stdout and stderr, plus one `<ISO time> supervisor: <text>` line per decision, appended
  UTF-8 to `<state_dir>/bridge.log` (`host().state_dir()`), rotated to `bridge.log.1` above 2 MB.
- SIGTERM / Ctrl+C / the parent closing stops the child and exits 0.

## Acceptance criteria

- [ ] `serve` exits 76 with the existing sentence when the port is taken; other exits unchanged (75 still 75)
- [ ] Exit 75 -> restart after 2 s and quick-failure count reset (fake clock)
- [ ] Crash sequence of quick failures waits 5, 10, 15 ... capped at 60 s, minimum 3 s; a 20 s+ run resets it
- [ ] Exit 76 -> no restart, supervisor exits non-zero with a log line naming the port conflict
- [ ] Log lines are UTF-8, child output is captured, rotation at 2 MB keeps one `.1` file
- [ ] Stopping the supervisor terminates the child (fake child records it)
- [ ] `hermes-remote-bridge supervise --help` exists; existing CLI tests stay green

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

Commit fafdd80. `cd bridge && uv run pytest -q`: 152 passed.
- Tests in bridge/tests/test_supervise.py: 76 exit+sentence (criterion 1); 75 restart 2 s + reset (2); backoff 5/10/15..60 cap, 3 s min, 20 s reset (3); 76 no restart + log line (4); UTF-8 log, child output, 2 MB rotation (5); stop terminates child (6); `supervise --help` (7). Existing suite green.
- Reused: `host().state_dir()`, the ps1 policy numbers; nothing existing for log rotation. Notes added: 1.
- Red step not observed: the only allowed command is the whole suite, so tests and code were written together before the first run.
- `bridge/windows/supervise.ps1` is now superseded; left in place for the planner to delete.
Not checked: real child process via `popen_launcher` and real SIGTERM/Ctrl+C (Verify line); "parent closing" (stdin EOF) is not implemented; Windows.

Planner Verify: PASS isolated XDG port 18650: supervise spawned serve; kill -9 child -> 'exited with code -9 after 7s; restarting in 5s', new child; second supervise exit 76 + 'port conflict ... not restarting'; SIGTERM s
