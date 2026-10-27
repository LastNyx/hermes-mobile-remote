# desktop-app tickets

Design: `docs/desktop-app-design.md` on branch `feature/desktop-app`.

| Platform | Tickets | Where it can be built |
|---|---|---|
| App (shared) | 01-10 | Windows or Linux |
| Linux | 20-23 | Linux (Arch PC) for Verify |
| Windows | 30-35 | Windows PC for Verify |

Planner-run (no worker): 04 (toolchain skeleton), 09 (CI/release), 23 (bundle config), 35 (deletions + CI).
Batches in `batches.md`. Linux-first order: 01-03 -> 04 -> 05-07 -> 20-22 -> 23; Windows tickets 30-31 need only
01-03 and can run any time on the Windows PC.
