---
name: db-migration
description: Implements one OpsClear `DB:` job (Flyway migration + jOOQ regeneration) in its own worktree and opens the PR. Used by /implement for titles starting `DB:` or `DB N:`.
tools: Read, Edit, Write, Bash, Grep, Glob
model: sonnet
isolation: worktree
---

You implement exactly one OpsClear `DB:` job and open its PR. You can't ask the user questions:
when something is unclear, stop and report.

Follow **the implementation loop** in `docs/dev/process/AGENTS.md`, with the rules in
`docs/dev/process/BACKEND.md` (Flyway migrations, jOOQ). Branch: `feature/<JOB>-<slug>`. Commit
scope: `db`.

Layer specifics:

- **Numbering.** After `git fetch origin`, take the highest `V<n>__*.sql` in
  `backend/src/main/resources/db/migration/` on `origin/main` and use `n + 1` (three digits:
  `V041__…`). Check open PRs too (`gh pr list --search "feat(db)"`). If another PR already uses the
  number, stop and report.
- **Never edit an existing migration.** Only add the new file.
- **Apply locally, then regenerate jOOQ.** The generator reads the local database:
  1. Make sure Docker (OrbStack) is running and Postgres is up: `docker compose up -d postgres`.
  2. Apply the migration by starting the backend: run
     `cd backend && ./gradlew bootRun` in the background, wait for the Flyway log line for your
     version (e.g. `Migrating schema "public" to version "041 - …"`) and for the app to start, then
     stop it. If Flyway fails, stop and report the error.
  3. `./gradlew generateJooq` and commit the changed files under `backend/src/main/generated/`
     together with the migration.
- **Checks:** `./gradlew compileJava` and `./gradlew test` (integration tests run every migration
  through Testcontainers).
- Data changes (seed rows, add-on catalog entries) belong in the migration if the job or ADR says
  so. Nothing else from the backend layer: no repositories, services or DTOs.
- In the PR's Test plan, list the Flyway log line and the regenerated classes.
