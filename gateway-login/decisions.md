# Decisions: gateway-login

Chat-made rules the files cannot show, one line each, newest last.

- 2026-10-26 21:45 approved tickets + batches (1: 01-04, 2: 05-06, 3: 07); .team.json test = bridge pytest + android build.sh test
- 2026-10-26 21:50 user: when ALL tickets finish, send the dev (debug) APK to Telegram (final step, after review/docs)
- 2026-10-26 21:51 user: continue, run all tickets chained. 06: gateway images via /api/media (not placeholder); only 401 session_expired triggers refresh; 01 done
- 2026-10-26 21:55 02: dev FAILed on env (no JDK on PATH); planner set .team.json path + android/local.properties, ran suite green (GatewayAuthTest 6/6), committed worker files on user's yes
