# Decisions: desktop-app

Chat-made rules the files cannot show, one line each, newest last.

- 2026-10-27 22:55 user: nideta agreed to Rust+Node in the repo, per-OS release builds, updater key as repo secret (D25 closed)
- 2026-10-27 23:31 user: run all tickets to done autonomously; planner decides within the design contract; decisions outside it go into docs/desktop-app-design.md
- 2026-10-27 23:31 planner: this machine is Linux (CachyOS); webkit2gtk-4.1 installed rootless in ~/.local/tauri-deps; Windows Verify lines recorded SKIP (unit tests with fakes only)
- 2026-10-27 23:34 planner: batch 1 runs serially (01/02 share cli.py, 02/03 share app.py+config.py). Kit note: field() strips a Seam's leading backtick, so a Seam starting with a backticked path yields no paths
