# 08: Scheduled (cron) jobs work on a gateway host

Lane: quick
Branch: feature/gateway-login
Status: in-progress
Round: 0
Blocked by: None
Terms: gateway host, HostFeature, Scheduled page, cron job
Seam: a `CronSource` interface (jobs, runs, create, action) implemented by `BridgeClient` (unchanged behaviour) and `GatewayRestClient` (new, over the gateway's own `/api/cron/jobs` routes); `CronJobs` takes a `CronSource` provider instead of a `BridgeClient` provider.
Test command: cd android && ./build.sh test
Verify: Planner checks `HostFeature.SCHEDULED` is gone and the drawer shows Scheduled for a gateway host; the real list/create/pause/resume/run-now is the user's check on WorkLab (added to docs/gateway-manual-checks.md).

## What to build

The Scheduled page (cron jobs of the Hermes profile in use: list, recent runs, pause, resume, run now, create a job from a prompt and a schedule) is hidden for gateway hosts today (`HostFeature.SCHEDULED`) because it only talks to the bridge's `/v1/cron/*`. The bridge's routes are thin wrappers over the gateway's own `/api/cron/*` routes, so a gateway host can serve the same page directly. Make the page work for both host kinds and stop hiding it for gateways.

## Acceptance criteria

- [ ] `CronSource` interface with `cronJobs(profile)`, `cronRuns(jobId, profile, limit)`, `createCronJob(name, schedule, prompt, profile)`, `cronAction(jobId, action, profile)`; `BridgeClient` implements it with its existing methods (same requests as today); `GatewayRestClient` implements it as specified in Facts.
- [ ] JVM test (`GatewayRestTest` style, fake transport): `cronJobs` sends `GET /api/cron/jobs?profile=<name>` (the profile is ALWAYS sent, `default` when null) and parses both a bare JSON array and `{"jobs": [...]}`; `cronRuns` sends `GET /api/cron/jobs/<id>/runs?profile=&limit=` and parses `{"runs":[...]}` or a bare array; `createCronJob` sends `POST /api/cron/jobs?profile=` with body `{name, schedule, prompt, deliver:"local", paused:false}` and returns the job whether the reply is the job itself or `{"job": {...}}`; `cronAction` POSTs `/api/cron/jobs/<id>/<pause|resume|trigger>?profile=` and parses the job from the reply (or null when the reply is not a job). Job ids are percent-encoded with the shared `enc()`.
- [ ] JVM test: a 401 `session_expired` on a cron call refreshes once and retries (reuse the client's existing refresh path; do not duplicate it).
- [ ] `CronJobs` (data/CronJobs.kt) takes `source: () -> CronSource?`; its behaviour (profile-guarded answers, `unsupported` on a bridge 404 other than `job_not_found`, busy set) is unchanged. Existing CronJobs tests stay green with a fake `CronSource`.
- [ ] `AppViewModel` passes the active host's source (`source as? CronSource`: the bridge client or the gateway client) to `CronJobs`.
- [ ] `HostFeature.SCHEDULED` is removed, together with its use in `ui/Screens.kt` (the drawer header shows the Scheduled button for every host) and its entry in the gate test in `GatewayRestTest.kt` (gateway still hides PROJECT_IDEA, CHAT_FILE_DOWNLOAD, BRIDGE_STATUS; bridge hides none).
- [ ] UI: the Scheduled button in the drawer header is shown for a gateway host and opens the same page. An API without its screen fails this ticket.
- [ ] Bridge hosts behave exactly as before (every existing test green).

## Facts that hold for every ticket

- Design: `docs/GATEWAY-DESIGN.md` (read "Verified wire facts" and "Design corrections"). Branch `feature/gateway-login`. Never push.
- App code: `android/app/src/main/java/io/github/nideta231/hermesremote/` (`data/`, `ui/`, `AppViewModel.kt`); tests in `android/app/src/test/java/io/github/nideta231/hermesremote/`.
- Existing seams to reuse, not duplicate: `data/GatewayRest.kt` (`GatewayRestClient`: private `raw(method, path, query, body)` and `call(...)` already do Bearer, the one-shot `session_expired` refresh and BridgeException mapping; `enc()`), `data/CronJob.kt` (`parseCronJob`, `parseCronJobs`, `parseCronRuns`), `data/CronJobs.kt`, `data/BridgeClient.kt` (the existing cron methods around line 178-192 are the behaviour to keep).
- Tests: JVM unit tests only; no emulator. Compose screens have no test infra: UI criteria are checked by `./build.sh` (assembleDebug) compiling.
- Test command: `cd android && ./build.sh test` (JDK 17 on PATH via ~/.bashrc; if it prints "no JDK found", FAIL and say so). It prints nothing on success; read `android/app/build/test-results/testDebugUnitTest/*.xml`.
- Protected (planner edits only): `*.gradle.kts`, `gradle/`, `gradle.properties`, `local.properties`, `.github/`, `.team.json`.
- No Co-Authored-By trailer on any commit.
- Kotlin KDoc: never write a slash-star sequence such as `/api/*` in a comment.
- The app's one OkHttp client is `SharedHttp.client`; never construct `OkHttpClient()`.

### Gateway cron routes (read from Hermes `hermes_cli/web_routers/cron.py`; do not invent others)

- `GET /api/cron/jobs?profile=<name>`: `profile` defaults to `all` on the server, which lists EVERY profile's jobs, so always send one (`default` when the app has none). Reply is a bare JSON array of job objects or `{"jobs":[...]}`; accept both.
- `GET /api/cron/jobs/{id}/runs?profile=&limit=` (limit default 20): `{"runs":[...]}` or a bare array; accept both. Run objects carry `id, title, preview, source, started_at, ended_at, message_count, model`.
- `POST /api/cron/jobs?profile=` body fields `name, schedule, prompt, deliver, paused` (also `repeat, skills, model, provider, base_url, script`: do NOT send these; the phone only makes a prompt on a schedule delivered locally). Reply is the job, or `{"job": {...}}`.
- `POST /api/cron/jobs/{id}/pause`, `/resume`, `/trigger` with `?profile=`; reply is the job object.
- Job objects use the same keys `parseCronJob` already reads: `id, name, schedule_display, state, enabled, next_run_at, last_run_at, last_status, last_error, failure_streak, model`. Because the keys already match, the existing parsers work on the gateway's raw objects; wrap a bare array into `{"jobs": array}` (and a bare run array into `{"runs": array}`) before calling `parseCronJobs`/`parseCronRuns`.
- Per-job routes treat `profile` as a hint and fall back to searching every profile; that is fine for a gateway host (its login already grants every profile).

## Constraints

- Do not touch `bridge/`, `install.*`, `tray.*`, `docs/`, `CHANGELOG.md` or the `windows-support` branch (the planner updates docs after).
- Do not contact a real gateway from a test. Fake the transport.

## Result
