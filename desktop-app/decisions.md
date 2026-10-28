# Decisions: desktop-app

Chat-made rules the files cannot show, one line each, newest last.

- 2026-10-27 22:55 user: nideta agreed to Rust+Node in the repo, per-OS release builds, updater key as repo secret (D25 closed)
- 2026-10-27 23:31 user: run all tickets to done autonomously; planner decides within the design contract; decisions outside it go into docs/desktop-app-design.md
- 2026-10-27 23:31 planner: this machine is Linux (CachyOS); webkit2gtk-4.1 installed rootless in ~/.local/tauri-deps; Windows Verify lines recorded SKIP (unit tests with fakes only)
- 2026-10-27 23:34 planner: batch 1 runs serially (01/02 share cli.py, 02/03 share app.py+config.py). Kit note: field() strips a Seam's leading backtick, so a Seam starting with a backticked path yields no paths
- 2026-10-27 23:48 planner 04 (f957196): Tauri 2.12 / Vue 3.5 / Vite 8 / vitest 5, plain JS (no TS) for the UI; updater pubkey is a placeholder until 08; .team.json stays untracked (it never was in the repo), desktop suite added locally
- 2026-10-27 23:51 planner: pre-added crates (ureq3, sha2, hex, flate2, tar, zip, semver, sysinfo, libc, windows-sys 0.61, tempfile) so dev tickets 05-34 need no Cargo.toml edits
- 2026-10-27 23:54 review 1-1 FAIL: all 3 findings confirmed in code; fix tickets 01-fix (stdin EOF), 02-fix (shared QR/URI helper), 03-fix (Windows quoted path); possible items folded into 02-fix
- 2026-10-27 23:59 delegated: review rounds and small findings (user away: run all tickets to done, decide within the design)
- 2026-10-28 00:00 planner fixed directly: review 1-2 finding (/dev/null stdin test) in 6624d06; batch 1 closed after round 2 without a round 3 (only finding was test-only). Possible (app.py calls cli._bridge_urls) left as-is
- 2026-10-28 00:04 planner 06: control commands (start/stop, autostart, firewall fix, logs, shell) ship with an Unsupported Control impl; 20/21/31/32 replace the impl. Batch 2 runs serially (06/07 share App.vue, api.js, lib.rs)
- 2026-10-28 00:18 planner 07: uv pinned 0.13.6 from GitHub, .sha256 sidecar; OsStep trait with NoOsStep until 20/32; uv sync --frozen --no-dev
- 2026-10-28 00:34 planner: 07 worker killed by session switch after 61s (26-line partial edit to bridge.rs discarded, nothing committed); redispatched inside systemd-run so it survives
- 2026-10-28 06:55 user: max_workers 1 for this project (no parallel dispatch)
- 2026-10-28 09:18 review 2-1 FAIL: both findings confirmed; possible items (duplicate setup listener, stale files on update copy, top-level-only tests skip) + hardcoded 20 s text folded into 07-fix; start_at_logon possible folded into 06-fix by reviewer. Serial: 06-fix then 07-fix (both edit bridge.rs)
- 2026-10-28 09:18 planner: untracked docs/fix-background-work-dies-on-app-close.md (written 08:11 by another session/user, about Hermes gateway reaping) left in place, added to .git/info/exclude so workers see a clean tree; 06-fix redispatched
- 2026-10-28 09:24 planner 20: Linux Control + OsStep in linux_service.rs wired into control.rs backend()/setup.rs; linger via get_linger/set_linger commands
- 2026-10-28 09:28 planner 21: run mode in <config_dir>/desktop.toml (run_mode key), supervise child with piped stdin + PDEATHSIG; get_run_mode/set_run_mode commands
