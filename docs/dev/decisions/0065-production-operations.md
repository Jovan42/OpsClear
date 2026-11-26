# ADR-0065: Production Operations

**Status:** Proposed
**Date:** 2026-11-26
**Author:** Jovan

## Context

Before real customers, production needs two things it doesn't have: backups we have actually
restored, and monitoring that tells us production broke before a customer does. Every customer's
data and account sits in one Docker volume on one droplet. There was no backup at all until
2026-11-26, and there's still no signal when production fails: no error tracker, no external uptime
check, no server alerts, and a health endpoint that never looks at the database. Docker logs that
never rotate, with no disk alert, are a real way for the single droplet to fall over. Paying
customers and the trial lock-out (JOB-419) mean we have to know first.

Promoted from JOB-427 (Future Consideration, PRJ-002) into PRJ-016 "Go-Live: Email, Operations,
Free Trial & Payments" / MIL-051 Production operations. It brings together the Maintenance chores
JOB-421 (backups) and JOB-422 (monitoring). This ADR is JOB-430.

### Facts from the existing system that shape this design

- **Backups, done (PR #614, JOB-421):**
  - `scripts/backup-db.sh` dumps both databases. It checks the whole archive with
    `pg_restore -f /dev/null`, uploads with the pinned `amazon/aws-cli` image (keys passed by env
    name) and pings healthchecks.io (start, success, fail).
  - `docs/dev/BACKUP.md` covers setup, the rename-based restore with guarded rollback, the Droplet
    Backup rebuild order, locks vs migrations and the `flock` cron line at 03:15.
  - `BACKUP_*` vars are in `.env.prod.example`.
  - Daily Droplet Backups are on (window 1–5am CET).
- **Backups, paused:** the dumps. DO Spaces ($5/month) was declined. The script and BACKUP.md are
  written for Spaces (endpoint examples, "Spaces key", console steps), but the script only needs
  `BACKUP_S3_ENDPOINT`, `BACKUP_S3_REGION` and `BACKUP_S3_BUCKET`, so any S3-compatible store
  works without code changes.
- **Health endpoint:** `HealthController` serves `GET /api/health` and always returns 200
  `{"status":"UP",…}` without touching the database. `SecurityConfig` permits it. nginx proxies it
  with no rate limit (ADR-0058). The backend Dockerfile already has
  `HEALTHCHECK … curl -f http://localhost:8080/api/health`. CI (`ci.yml`, `e2e-run` action),
  `scripts/seed.sh` and Postman call the same URL and only check the status code.
  `ApiPaths.HEALTH` is used in integration tests.
- **No Actuator, no Sentry:** neither is in `backend/build.gradle` (Spring Boot 3.4.1) or
  `frontend/package.json`.
- **Errors:** `GlobalExceptionHandler` maps known exceptions to 4xx. Its catch-all
  `@ExceptionHandler(Exception.class)` returns 500. Background work runs in `@Async` listeners
  (`InviteEmailListener`, `NotificationEventListener`) and `@Scheduled` jobs (`scheduler/**`), where
  exceptions never reach the handler. The frontend wraps the app in `components/ErrorBoundary.tsx`
  (class component, no reporting).
- **Compose:** `docker-compose.prod.yml` has no `logging:` on any service, so Docker's
  `json-file` driver grows without limit. Only `postgres` and `frontend` have compose
  healthchecks. The backend's comes from its Dockerfile.
- **Deploys:** `deploy.yml` runs `git pull` and `docker compose … up -d --build <service>` on the
  droplet, only for `backend/**` or `frontend/**` changes. Changes to root files, `scripts/**` and
  `docs/**` need a manual `git pull` (and `up -d` for compose changes). The frontend image is built
  on the droplet with `VITE_*` build args.
- **Privacy Policy:** draft PR #613 (MIL-053) lists sub-processors (DigitalOcean, Paddle, …).
  Sentry and the backup store will hold personal data, so they have to be added to that list.
- **Layers:** `docker-compose.prod.yml`, `.env.prod.example`, `.github/**`, `scripts/**` and
  `docs/**` are neutral (NAMING.md), so they can ride with the backend or frontend job, or make up
  a Chore on their own.

## Decision

1. **Database dumps go to Cloudflare R2**, in a bucket with EU jurisdiction and a 30-day lifecycle
   rule on `db/`. `backup-db.sh` stays as it is. BACKUP.md and `.env.prod.example` are rewritten
   from Spaces to R2. Spaces remains a drop-in option if we later want everything at one vendor.
2. **The backups are proven by a restore test** before go-live. The latest R2 dump of each database
   is restored into a scratch database on the prod Postgres, row counts are compared, the scratch
   databases are dropped and the result is logged in BACKUP.md.
3. **`/api/health` is served by Spring Boot Actuator.** Only the `health` endpoint is exposed,
   mapped to the same URL. It checks the database and disk space, returns 503 when either is down
   and never shows details. `HealthController` is removed. CI, seed.sh, nginx, Postman and the
   Docker healthcheck keep working unchanged.
4. **Sentry (EU region, free plan) reports errors from the backend and the frontend.** Errors only:
   no tracing, no session replay, no default PII. The only context is user ID, org ID, environment
   and release. Sentry is off when its DSN is empty, so dev, tests and CI send nothing.
5. **Frontend source maps are uploaded to Sentry during the Docker build** and removed from the
   served files. The auth token reaches the build as a BuildKit secret, not a build arg.
6. **Better Stack (free plan) checks uptime from outside:** the landing page, `/api/health`, the
   Keycloak realm, and TLS expiry.
7. **The DigitalOcean Monitoring agent** sends CPU, memory and disk alerts.
8. **Every service in `docker-compose.prod.yml` rotates its logs** (`json-file`, size-capped).
9. **All alerts go to `hello@opsclear.app`.** Downtime also pushes to the phone through the Better
   Stack app. The backup dead-man's switch (healthchecks.io) uses the same address.
10. **`docs/dev/DEPLOY.md` gets a Monitoring section:** every alert, where it goes, the dashboards,
    the env vars and the post-deploy checks.

## Product decisions

There are no user-facing changes. These are the operator's decisions.

| Topic | Decision |
|---|---|
| Dump storage | Cloudflare R2, EU jurisdiction, 10 GB free. Decided over Backblaze B2 and DO Spaces ($5/month). |
| Dump retention | 30 days (R2 lifecycle rule). Droplet Backups stay on as the copy root on the droplet can't delete. |
| Restore test | Dump into scratch DBs on prod, compare row counts, log in BACKUP.md. No full droplet rebuild for now. |
| Error tracking | Sentry, EU region, free plan (5k errors/month, 1 user). Backend and frontend. Errors only. |
| What counts as an error | Unhandled exceptions that become a 500, failures in `@Async` and `@Scheduled` work, React render crashes and unhandled promise rejections in the browser. 4xx responses (validation, not found, forbidden, rate-limited) are not reported. |
| Uptime | Better Stack free plan, 3-minute checks. Decided over UptimeRobot, whose free plan is non-commercial since 2024. |
| Server alerts | DO Monitoring: CPU > 80 % for 10 min, memory > 90 % for 10 min, disk > 80 %. |
| Alert inbox | `hello@opsclear.app` for every tool, plus Better Stack phone push for downtime. No Slack or Telegram. |
| Status page | Not now. Better Stack offers one for free if customers ask. |
| Privacy | Sentry and Cloudflare (R2) are added to the Privacy Policy sub-processor list (PR #613 / MIL-053). Better Stack and healthchecks.io get only URLs and timings, not personal data. |

## Technical design

### Database

No schema change.

### API

`GET /api/health`: same path and same use (status code only), new body.

- **200** `{"status":"UP"}` when the database and disk checks pass.
- **503** `{"status":"DOWN"}` when either fails.
- No components or details for anyone (`show-details=never`, `show-components=never`).

No other Actuator endpoint is exposed over HTTP.

### Backend

- **Actuator:** add `spring-boot-starter-actuator`. In `application.properties`:
  - `management.endpoints.web.base-path=/api`
  - `management.endpoints.web.exposure.include=health`
  - `management.endpoint.health.show-details=never`
  - `management.endpoint.health.show-components=never`
  - `management.health.mail.enabled=false`, so a Brevo hiccup doesn't make the app "down" and
    trigger the Docker healthcheck. Email failures reach Sentry instead.

  `/api/health` then comes from Actuator (`db` and `diskSpace` indicators). Delete
  `HealthController`. The `permitAll` rule in `SecurityConfig` stays. The Dockerfile `HEALTHCHECK`
  needs no change, but the job checks that `curl` exists in `eclipse-temurin:21-jre` and switches
  to `wget` if not.
- **Sentry SDK:** add `io.sentry:sentry-spring-boot-starter-jakarta` (current 8.x). Properties:
  - `sentry.dsn=${SENTRY_DSN:}`, empty means disabled
  - `sentry.environment=${SENTRY_ENVIRONMENT:production}`
  - `sentry.release=${SENTRY_RELEASE:}`
  - `sentry.send-default-pii=false`
  - `sentry.traces-sample-rate` unset (no tracing)
  - `sentry.max-request-body-size=never`
- **Only real errors reach Sentry:**
  - Turn off the starter's automatic MVC exception resolver, which would also report the 4xx
    exceptions `GlobalExceptionHandler` maps.
  - Call `Sentry.captureException(ex)` in the catch-all `@ExceptionHandler(Exception.class)`.
  - Add an `AsyncUncaughtExceptionHandler` in `AsyncConfig` and an `ErrorHandler` on the scheduler
    in `SchedulingConfig` that log and capture the exception.
- **Scrubbing:** a `BeforeSendCallback` bean removes the `Authorization`, `X-Api-Key` and `Cookie`
  headers and any query string, keeps `user.id` only (no email, username or IP), and adds the
  `org_id` tag when the request has an org context. User ID and org come from the existing security
  context set by `UserSyncFilter`.
- **Compose:** pass `SENTRY_DSN`, `SENTRY_ENVIRONMENT` and `SENTRY_RELEASE` to `backend` with empty
  defaults, and list them in `.env.prod.example`.
- **Release:** `deploy.yml` runs `export SENTRY_RELEASE=$(git rev-parse --short HEAD)` before
  `docker compose … up`, so backend and frontend events carry the deployed commit. The manual
  command in DEPLOY.md gets the same export.
- **Tests:**
  - Integration tests: `/api/health` returns 200 `{"status":"UP"}` with no `components` key and no
    auth, and 503 when the DB indicator is down (mocked `HealthIndicator` or a stopped datasource).
  - Unit tests: the `BeforeSendCallback` strips the headers, the query string and the user fields;
    the async and scheduler handlers capture; and the catch-all handler captures while a 4xx handler
    doesn't (mocked static `Sentry` or an injected `IScopes`).
  - Integration tests cover every changed backend line (BACKEND.md).

### Frontend

- **SDK:** add `@sentry/react`. Call `Sentry.init` in `main.tsx` before rendering, only when
  `import.meta.env.VITE_SENTRY_DSN` is set:
  - `environment` and `release` from `VITE_SENTRY_ENVIRONMENT` and `VITE_SENTRY_RELEASE`
  - `sendDefaultPii: false`
  - no `browserTracingIntegration`, no `replayIntegration`
  - `tracesSampleRate` unset
  - a `beforeSend` that drops query strings from URLs and breadcrumbs, and removes tokens from
    breadcrumb data
  - `ignoreErrors` for the usual browser noise (ResizeObserver loop, aborted fetches, chunk-load
    after a deploy, which the app already handles)
- **User context:** after login, `Sentry.setUser({ id })` and `Sentry.setTag('org_id', …)` from
  `AuthContext` / `OrgContext`. `Sentry.setUser(null)` on logout.
- **ErrorBoundary:** `componentDidCatch` calls `Sentry.captureException(error, { extra: { componentStack } })`.
  The existing fallback UI stays.
- **Source maps:** add `@sentry/vite-plugin` to `vite.config.ts`, active only when
  `SENTRY_AUTH_TOKEN` is set. It uses `build.sourcemap: 'hidden'`, uploads for the release and
  deletes the `.map` files from `dist` after upload, so nginx never serves them.
- **Dockerfile:**
  - new build args `VITE_SENTRY_DSN`, `VITE_SENTRY_ENVIRONMENT`, `VITE_SENTRY_RELEASE`, `SENTRY_ORG`
    and `SENTRY_PROJECT`
  - the token is mounted with `RUN --mount=type=secret,id=sentry_auth_token` on the build step, so
    it isn't in any image layer
  - `docker-compose.prod.yml` declares the secret from the `SENTRY_AUTH_TOKEN` env var (compose
    `secrets:` with `environment:`) and passes the args
- **Privacy:** in the Sentry project settings, turn on "Prevent storing of IP addresses" and the
  default server-side data scrubbing. This is manual and recorded in DEPLOY.md.
- **Tests:** a Vitest that `ErrorBoundary` calls `Sentry.captureException` (mocked module), and one
  that init is skipped without a DSN. No E2E: CI runs without a DSN, and there's nothing visible to
  the user.

### Uptime, server alerts and logs (Chore)

- **Log rotation:** a `x-logging: &default-logging` block (`driver: json-file`,
  `options: { max-size: "10m", max-file: "5" }`) at the top of `docker-compose.prod.yml`, with
  `logging: *default-logging` on every service. That's at most 50 MB per container. It applies to
  containers created after the change, so it needs a manual `up -d` (root file, no auto-deploy).
- **Better Stack monitors (manual):**

  | Monitor | URL / type | Alert when |
  |---|---|---|
  | Landing page | `https://opsclear.app/`, keyword "OpsClear" | down 2 checks in a row |
  | Backend health | `https://opsclear.app/api/health`, expect 200 | non-200 |
  | Keycloak | `https://opsclear.app/realms/opsclear/.well-known/openid-configuration`, expect 200 | non-200 |
  | TLS | `opsclear.app` certificate | expires in < 14 days |

  Email to `hello@opsclear.app`, plus push to the Better Stack phone app.
- **DO Monitoring (manual):** install the agent (`curl -sSL https://repos.insights.digitalocean.com/install.sh | sudo bash`,
  or the droplet's Monitoring toggle). Alert policies on the droplet: CPU > 80 % for 10 min,
  memory > 90 % for 10 min, disk > 80 % for 5 min, email to `hello@opsclear.app`.
- **Sentry alerts (manual):** the default "new issue" alert by email to the account. That account
  is created with `hello@opsclear.app`.
- **`docs/dev/DEPLOY.md` "Monitoring" section:**
  - table of every signal (Sentry backend and frontend, Better Stack monitors, DO alerts,
    healthchecks.io backup), what triggers it, where it goes and the dashboard URL
  - the env vars (`SENTRY_DSN`, `SENTRY_ENVIRONMENT`, `VITE_SENTRY_DSN`, `SENTRY_AUTH_TOKEN`,
    `SENTRY_ORG`, `SENTRY_PROJECT`; `SENTRY_RELEASE` set by deploy)
  - log rotation and how to read logs (`docker compose logs --since`)
  - **post-deploy checks:** `/api/health` is 200, Better Stack is green, no new Sentry issue for
    the release, `docker compose ps` shows healthy

### Backups (Chore, mostly manual)

- **Docs and examples:** in BACKUP.md and `.env.prod.example`, replace the Spaces steps with R2:
  - create the bucket with **EU jurisdiction**; the endpoint is
    `https://<account-id>.eu.r2.cloudflarestorage.com` and the region is `auto`
  - lifecycle rule on `db/` (delete after 30 days, abort multipart uploads after 1 day)
  - an R2 API token scoped to that bucket with "Object Read & Write"
  - in the threat model, swap "Spaces key" for "R2 token", which can also delete, so Droplet
    Backups stay as the copy root can't touch
  - in `backup-db.sh`, update the header comment and the `--help` example endpoint (text only)
- **Droplet (manual):** follow BACKUP.md:
  - healthchecks.io check `opsclear-db-backup` (period 1 day, grace 2 hours, email to
    `hello@opsclear.app`)
  - the `BACKUP_*` vars in `.env.prod`, then `chmod 600`
  - `git pull`, a first run, and an alert test (a bad bucket name gives a `/fail` ping)
  - the root cron line at 03:15 with `flock`
- **Restore test (manual):**
  - download the latest dump of each database and `pg_restore` it into `opsclear_restore_test`
    and `keycloak_restore_test` on the prod Postgres
  - compare row counts of the main tables (`organisations`, `users`, `projects`, `jobs`, `notes`;
    Keycloak's `user_entity`) against the live databases
  - drop the scratch databases
  - log the date, dump names, sizes, duration and result in BACKUP.md's restore test log
  - BACKUP.md gets this procedure as its own section, so it can be repeated (for example before
    each major upgrade)

### Constraints & edge cases

- **Free plan limits:** Sentry has 5k errors/month. The SDK's default rate limiting plus
  `ignoreErrors` and 4xx exclusion should keep us far below it. If an error loop burns the quota,
  Sentry drops events until the month resets, which is acceptable at this stage. The account has
  1 user, so add a second one only on a paid plan.
- **A health check that's too strict:** a slow DB makes `/api/health` 503, so Docker marks the
  backend unhealthy (it doesn't restart it, `restart: unless-stopped` only acts on exit) and Better
  Stack alerts. That's what we want. The mail indicator is off for that reason.
- **Health during startup:** the Dockerfile's `start-period=60s` covers Flyway and context startup.
- **Keycloak is not in Sentry:** it's a third-party server. The uptime check on the realm covers it.
- **Source maps without a token:** the plugin does nothing, `build.sourcemap` stays `hidden`, and
  the `.map` files are still deleted, so a local `docker compose` build matches today.
- **BuildKit:** `RUN --mount=type=secret` needs BuildKit, the default in Docker Engine 23+ and
  Compose v2 on the droplet. The frontend job checks the droplet's versions.
- **`pg_dump` locks:** don't merge migrations around 03:15 (BACKUP.md). Nothing here changes that.
- **Sub-processors:** the Privacy Policy must list Sentry and Cloudflare before the SDK DSNs and
  the R2 bucket go live with real customers. If PR #613 merges first, the backup Chore adds them.
  Otherwise PR #613 does.
- **Deploy gaps:** the Chore and the backup docs touch only neutral files, so nothing auto-deploys.
  Each job ends with the manual droplet steps.

## Alternatives considered

### Backblaze B2 for the dumps (decided against)

10 GB free, an EU region, and Object Lock, which would stop root on the droplet from deleting dumps.
Rejected in favour of R2 for its EU-jurisdiction buckets and no egress fees during a restore.
Droplet Backups already cover the "root deletes the dumps" case.

### DO Spaces for the dumps (decided against for now)

No new sub-processor, and the docs already describe it. Rejected for now over the $5/month. It stays
a drop-in swap of three env vars.

### UptimeRobot (decided against)

50 free monitors at 5-minute intervals, but the free plan has been non-commercial since 2024, which
rules it out for a paid product.

### Uptime from healthchecks.io or a droplet cron (decided against)

A cron on the droplet can't report that the droplet itself is down, and healthchecks.io only
receives pings. Probing has to come from outside.

### Keep `HealthController` and add a DB ping (decided against)

No new dependency, but hand-written code for what Actuator does in configuration. It also has no
disk check, the signal that matters most on a droplet whose logs used to grow forever.

### Actuator at `/actuator/health` (decided against)

The standard path, but it needs a new nginx location and changes in CI, seed.sh, the Dockerfile and
Postman, for no gain over mapping Actuator onto `/api/health`.

### Sentry tracing and session replay (decided against)

Performance data and replays are useful, but they add PII risk (URLs, screen content) and quota.
We can turn tracing on later at a low sample rate if we need it.

### Self-hosted error tracking (GlitchTip) (decided against)

No sub-processor, but it adds another service and Postgres load on the same 1 GB droplet it's meant
to watch, and it goes down with it.

### Public source maps or none (decided against)

Public maps expose the source. No maps leaves stack traces minified. Uploading at build time with a
BuildKit secret costs one token in `.env.prod`.

### Full droplet rebuild as the restore test (decided against for now)

It proves the whole-server path but costs an hour and a temporary droplet. The scratch-DB restore
proves the dumps, which are the main way back for data. A rebuild can be a later chore.

## Consequences

### Positive

- Data can come back: tested dumps in another jurisdiction, plus daily disk images.
- We hear about outages, 500s, front-end crashes, a full disk and a missed backup before customers
  do, all in one inbox.
- `/api/health` finally means something, and the Docker healthcheck reflects the database.
- Logs can no longer fill the disk.
- DEPLOY.md becomes the one place to find every alert and dashboard.

### Negative

- Two new sub-processors (Sentry, Cloudflare) to list and keep in the Privacy Policy.
- Four more accounts to keep: Cloudflare, Sentry, Better Stack, healthchecks.io. All are on
  `hello@opsclear.app`.
- A Sentry auth token lives in `.env.prod` on the droplet.
- Free plans have limits (Sentry quota, 3-minute checks, 1 Sentry user). Outgrowing them costs money.

### Neutral

- `/api/health` returns a smaller body (`{"status":"UP"}`). No caller reads the body.
- The restore test is manual and has to be repeated by hand.

## Implementation order

Each job is one layer (JOBS.md). Neutral paths go with the job that needs them. Jobs 1–4 are
independent of each other.

1. **Chore: Store database dumps in Cloudflare R2 and test a restore.** Rewrite BACKUP.md,
   `.env.prod.example` and the `backup-db.sh` comments from Spaces to R2, and add the repeatable
   restore test section. Also add Sentry and Cloudflare to the Privacy Policy sub-processors if
   PR #613 has merged (otherwise note it on PR #613). After merge (developer): R2 bucket, lifecycle
   rule and token; the healthchecks.io check; `.env.prod` vars; `git pull`; first run and alert
   test; cron line; the restore test logged in BACKUP.md.
2. **Backend: Report errors to Sentry and check the database in `/api/health`.** Actuator on
   `/api/health` (remove `HealthController`), the Sentry starter, scrubbing, and the capture in the
   catch-all, async and scheduler handlers, with tests. Also the `SENTRY_*` vars in compose and
   `.env.prod.example`, and the `SENTRY_RELEASE` export in `deploy.yml`. After merge (developer):
   create the Sentry org (EU) and backend project, turn on IP scrubbing, set `SENTRY_DSN` in
   `.env.prod`, `up -d backend`, and trigger a test error.
3. **Frontend: Report errors to Sentry with source maps.** `@sentry/react` init, user and org
   context, ErrorBoundary capture, `@sentry/vite-plugin`, the Dockerfile args and BuildKit secret,
   the compose args and secret, with Vitest tests. After merge (developer): create the frontend
   project, put `VITE_SENTRY_DSN`, `SENTRY_AUTH_TOKEN`, `SENTRY_ORG` and `SENTRY_PROJECT` in
   `.env.prod`, rebuild the frontend, and trigger a test error to check the stack trace is mapped.
4. **Chore: Uptime checks, server alerts, log rotation and the Monitoring docs.** Log rotation in
   `docker-compose.prod.yml` and the DEPLOY.md Monitoring section with the post-deploy checks. After
   merge (developer): Better Stack account and the four monitors, phone app, DO agent and alert
   policies, Sentry alert email, `git pull` and `up -d`, and a check that `docker inspect` shows
   the log options.

Once these merge, delete the Maintenance chores JOB-421 and JOB-422 (their scope is here).

## References

- JOB-427 (Future Consideration), JOB-430 (this ADR), PRJ-016 / MIL-051
- JOB-421 (backups) and JOB-422 (monitoring), Maintenance chores folded into this ADR
- PR #614 (backup script and BACKUP.md), PR #613 (legal pages and sub-processors, MIL-053)
- ADR-0058 API Rate Limiting (`/api/health` not limited), ADR-0059 Email Verification and
  Transactional Email (SMTP only via Brevo on 2525), ADR-0064 Domain Email and Support Contact
  (`hello@opsclear.app`)
- `scripts/backup-db.sh`, `docs/dev/BACKUP.md`, `docs/dev/DEPLOY.md`, `docker-compose.prod.yml`,
  `backend/Dockerfile`, `frontend/Dockerfile`, `.github/workflows/deploy.yml`,
  `backend/src/main/java/com/opsclear/controller/HealthController.java`,
  `backend/src/main/java/com/opsclear/exception/GlobalExceptionHandler.java`,
  `frontend/src/components/ErrorBoundary.tsx`
