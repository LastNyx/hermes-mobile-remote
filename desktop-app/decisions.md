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
