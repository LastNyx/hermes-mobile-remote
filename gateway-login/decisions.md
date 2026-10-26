# Decisions: gateway-login

Chat-made rules the files cannot show, one line each, newest last.

- 2026-10-26 21:45 approved tickets + batches (1: 01-04, 2: 05-06, 3: 07); .team.json test = bridge pytest + android build.sh test
- 2026-10-26 21:50 user: when ALL tickets finish, send the dev (debug) APK to Telegram (final step, after review/docs)
- 2026-10-26 21:51 user: continue, run all tickets chained. 06: gateway images via /api/media (not placeholder); only 401 session_expired triggers refresh; 01 done
- 2026-10-26 21:55 02: dev FAILed on env (no JDK on PATH); planner set .team.json path + android/local.properties, ran suite green (GatewayAuthTest 6/6), committed worker files on user's yes
- 2026-10-26 22:00 03: dev FAILed on env (JDK not on worker PATH); planner verified suite green + GatewayStoreTest 4/4 + assembleDebug, committed on user's yes; user approved JAVA_HOME/PATH in ~/.bashrc
- 2026-10-26 22:03 04: planner added 2 tests for ticket-call SIGNED_OUT path (test-only, plain commit); GatewayTicketUrl still unwired until 05
- 2026-10-26 22:06 review 1.1 FAIL accepted: 2 fix tickets (remove-host clears gateway store incl. unpair; share keystore cipher). Declined: logout test (W15 unknown, note for 05), backoff dedupe (Possible, no behaviour). .team.json android cmd now 'bash build.sh test'; baseline recorded (bridge 2 Windows env failures)
- 2026-10-26 22:10 QUEUED after chain (not this feature): Windows bridge laggy (see skill windows-subprocess-cost; read audit log latency) and pair QR should never open Photos on Windows since tray already shows it -> check cli.py _show_qr/--qr-png path. Own branch from windows-support.
- 2026-10-26 22:12 batch 1 review PASS round 2; HEAD 6af1b15 = base for batch 2
- 2026-10-26 22:15 user: split 05; new ticket 05b (host activation: gateway host becomes live host, socket via GatewayTicketUrl, OkHttpTransport built in 05). Batch 2 = 05,05b,06; 06 and 07 blocked by 05b. Planner added androidx.browser dep.
- 2026-10-26 22:15 user: fix first: bridge pytest opens pairing-qr.png in Photos (test_terminal_qr_survives... uses the real Windows host). Planner test-only fix prepared (scratch qrfix.py); applied right after the running dev (05) exits so the tree stays clean for it. Rest of the Windows bridge work stays queued.
- 2026-10-26 22:19 05 verified (suite+APK build); bridge test fix ddcec02 (no more Photos popup from pytest, proven: 1 startfile call before, 0 across the full suite after)
- 2026-10-26 22:31 review 2.1 FAIL accepted: fix tickets 05-fix-share-okhttp-client, 06-fix-dedupe-enc-drop-handoff (refactor only, nothing folded). Untested UI criteria + live-fixture check -> ticket 07 manual checks (incl. bridge host path unchanged, switching bridge<->gateway, Add host chips, hidden features list, W10-W13 shapes vs real WorkLab). Possible vacuous state check not ticketed.
- 2026-10-26 22:35 batch 2 PASS; batch 3 = ticket 07 via docs worker with docs-facts.md; CHANGELOG heading 'Unreleased' (version = git tag, user decides)
- 2026-10-26 22:37 07 docs committed 6de07d6; docs RESULT FAIL = no glossary (CONTEXT.md absent, not created by rule); drift.md lists unchecked terms
- 2026-10-26 22:43 debug APK (sha256 65f002da...) sent to telegram (owner DM) (first TELEGRAM_ALLOWED_USERS id, user-approved); hermes send printed 'sent'
- 2026-10-26 22:51 PR opened: nideta231/hermes-mobile-remote#14 from LastNyx:feature/gateway-login. Merged origin/main (4 conflicts resolved: CHANGELOG, AppViewModel, MainActivity, Screens), suites green, .team.json untracked from the PR
- 2026-10-26 23:08 user: vault is Hermes RPC not bridge-only -> unhidden for gateway hosts (planner quick-lane, suite+APK green). Next: ticket 08 Scheduled (cron) on gateway hosts via /api/cron/jobs
- 2026-10-26 23:13 08 done; vault unhide + scheduled pushed to PR #14, PR body updated
