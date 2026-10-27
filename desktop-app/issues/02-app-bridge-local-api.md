# 02: Bridge: loopback `/v1/local/*` control API with a secret file

Lane: full
Branch: feature/desktop-app
Platform: App
Status: todo
Round: 0
Blocked by: None
Terms: local client, local secret, loopback peer
Seam: FastAPI routes under `/v1/local/` in `app.py` (or a router module it includes), tested with the existing test client; the secret helpers in a small module (`local_secret.py`).
Test command: cd bridge && uv run pytest -q
Verify: Planner curls `/v1/local/status` on this PC with and without the secret header, and from the tailnet address (must be 403).

**Blocked by:** None
**Status:** todo

## What to build

The desktop app controls the bridge fast, without a device token, and nothing else can.

- On first start the bridge creates `<config_dir>/local-secret` (32 random bytes, hex), readable only by the user
  (POSIX 0600; on Windows it lives in the per-user `%LOCALAPPDATA%` dir, which is already user-only). If the
  file is missing it is re-created; deleting it revokes every local client (D29).
- Requests to `/v1/local/*` must come from a loopback peer **and** carry `X-Hermes-Local: <secret>` (compared
  in constant time). Otherwise 403 `forbidden_local`. A tailnet/LAN peer gets 403 even with the right secret.
  Loopback alone is not trusted: `tailscale serve`, other Windows accounts and local programs reach 127.0.0.1.
- Routes (JSON, same shapes as the CLI's `--json` output, built on the same functions so they cannot drift):
  `GET status` (running, version, listen addresses, current network + trusted?, firewall ok?),
  `GET devices`, `POST pair {name, replace?}` -> `{code, qr_png_base64, ...}`, `POST revoke {name}`,
  `GET trust` / `POST trust` / `POST untrust {id}`, `GET doctor`.
- Not in the relay's `ALLOWED_METHODS`, not counted against device rate limits, audited like other requests.

## Acceptance criteria

- [ ] Secret file created on first start with the right permissions; re-created when deleted; value changes then
- [ ] Loopback + correct secret -> 200 for every route; missing/wrong secret -> 403 `forbidden_local`
- [ ] Non-loopback peer with the right secret -> 403 (test the tailnet and LAN peer paths)
- [ ] Each route's JSON equals the corresponding CLI `--json` output for the same state (shared function, one test per route)
- [ ] `/v1/local` appears in no relay allowlist and needs no device token
- [ ] Existing `test_app.py` / `test_cli_json.py` stay green

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
