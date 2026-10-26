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
