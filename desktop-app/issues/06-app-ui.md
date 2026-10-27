# 06: Desktop: the shared Vue UI

Lane: full
Branch: feature/desktop-app
Platform: App
Status: review
Round: 0
Blocked by: 05
Terms: flyout, status line, pairing
Seam: Vue components in `desktop/src/` calling Tauri commands through one `desktop/src/api.js` module (mocked in vitest); new Rust commands for controls in `desktop/src-tauri/src/control.rs`.
Test command: cd desktop && bash test.sh
Verify: Planner opens the app with `npm run tauri dev` against the real bridge, pairs a test device, revokes it, toggles the bridge; screenshots for the user. Look and feel go to the manual checks.

**Blocked by:** 05
**Status:** todo

## What to build

One UI used by the Windows flyout and the Linux window (D4).

- Home: a big on/off switch (bridge running); a status line (network trusted? phone reachable via firewall /
  LAN / tailnet?); "bridge not running, showing saved data" when an answer came `via: cli`.
- Devices: list with re-pair and revoke (revoke asks to confirm; re-pair warns the old code stops working).
- "Pair a device": name field, then the QR inline with the code and a Copy button.
- Settings page: doctor (results as a coloured list, runs without freezing the UI), open log folder,
  firewall check / fix, start at logon, terminal shell, and the "what this install changed" list (D16).
- Compact enough for a ~360x520 flyout; also fills a normal resizable window. Light/dark from the OS.

## Acceptance criteria

- [ ] UI: Home switch calls start/stop and reflects the status answer
- [ ] UI: status line shows trusted/untrusted network and each reachability path
- [ ] UI: devices list; revoke asks to confirm then calls revoke; re-pair shows the warning
- [ ] UI: pairing shows the QR image and copies the code
- [ ] UI: settings page lists doctor results without blocking (pending state shown)
- [ ] UI: `via: cli` answers show the bridge-not-running notice
- [ ] An API without its screen fails this ticket

## Planner notes (2026-10-28)

- The UI is plain JS, not TypeScript (planner decision for 04): the module is `desktop/src/api.js`, which exists
  already with `appVersion()`. Tests mock `@tauri-apps/api/core` as `desktop/src/App.test.js` does.
- Bridge data commands exist (ticket 05, `desktop/src-tauri/src/bridge.rs`): `bridge_status`, `bridge_devices`,
  `bridge_pair {name, replace}`, `bridge_revoke {name}`, `bridge_trusted`, `bridge_trust`, `bridge_untrust {id}`,
  `bridge_doctor`. Each returns `{via: "http"|"cli", data}`; CLI pair/revoke/trust return `data.output` text,
  HTTP pair returns `data.code` and `data.qr_png_base64`.
- Controls whose OS backend comes in later tickets (bridge start/stop: 20/21 Linux, 32 Windows; start at logon:
  20/32; firewall fix: 31; open log folder; terminal shell): add Tauri commands `bridge_start`, `bridge_stop`,
  `get_settings`, `set_start_at_logon {on}`, `firewall_fix`, `open_logs`, `set_terminal_shell {value}` in a new
  `control.rs`, behind a `Control` trait. Ship an `Unsupported` implementation that returns the error
  "not available yet on this platform"; the UI shows that error inline. Later tickets replace the implementation,
  not the commands. `set_terminal_shell` may write the `terminal_shell` line in `<config_dir>/config.toml`
  (bridge config key from ticket 03) through an injected file writer.
- The "what this install changed" list reads `<config_dir>/INSTALLED.txt` (written by ticket 07) via a
  `get_installed_list` command; when the file is missing it says "Setup has not run yet".
- Register every new command in `lib.rs` `generate_handler!`.

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

Commit: 01cf105. `bash test.sh`: cargo 8 passed (3 new in control.rs), vitest 11 passed (10 new in ui.test.js), vite build ok.
Covers: Home switch start/stop + status (Home tests); status line trusted/untrusted + firewall/LAN/tailnet; devices list + revoke confirm + re-pair warning (Devices tests); pairing QR + Copy; doctor pending then coloured list; `via: cli` notice; settings actions with inline errors, install list / "Setup has not run yet". Every API (api.js) has a screen.
Reuse: searched for existing helpers; only `BridgePaths`/`appVersion` reused (config_dir for settings, INSTALLED.txt). Nothing else found.
Red step: UI tests were written before the components but I ran the suite only once, after writing the code (all green first run; no separate red run).
New Rust commands in control.rs (Unsupported backend), registered in lib.rs. tauri.conf window size untouched (protected); CSS is fluid for ~360x520.
Unused files: none.
Notes added: 2 lines in .scratch/dev-notes.md.
Not checked: real `tauri dev` window, dark/light look, real bridge status shape beyond `listen`/`network`/`firewall_ok` (LAN/tailnet derived from `listen` IPs by prefix).

Planner Verify: PASS real app (debug, Linux webkit) against isolated bridge :18650: Home shows switch on + 'Network <ssid> (untrusted) · Firewall blocked · LAN not reachable · Tailnet 100.65.118.116' (screenshot ~/.hermes
