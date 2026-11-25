# Production backups and restore

Two layers (JOB-421). Both are set up by hand on the droplet and in the DigitalOcean account.

| Layer | What | Restores | Kept |
|---|---|---|---|
| Droplet Backups | Whole-disk image, weekly or daily (DO console) | The whole server: OS, certbot certificates, `.env.prod`, the checkout, Docker volumes | DO's standard retention |
| Database dumps | `scripts/backup-db.sh`, nightly: `pg_dump -Fc` of `opsclear` and `keycloak` to a private Spaces bucket | One database, cleanly | 30 days (bucket lifecycle rule) |

A disk image of a running Postgres may need crash recovery and can't restore one database, so the
dumps are the main way back for data. The disk image is for losing the server.

## How the backup works

`scripts/backup-db.sh` (read `--help` for the details):

1. Runs `pg_dump -Fc -Z 6` for each database inside the `postgres` service (`docker compose exec`),
   into a private temp folder on the droplet. `pg_dump` takes a snapshot and doesn't block writes.
2. Checks each dump: at least 2 KB, `pg_restore --list` can read it, and it has table data. A failed
   or empty dump stops the script (`set -euo pipefail`, every step is checked).
3. Uploads to `s3://<bucket>/db/<database>-<UTC timestamp>.dump` with the pinned `amazon/aws-cli`
   Docker image (nothing to install, Docker is already there), then reads each object's size back
   from the bucket and compares.
4. Pings the health check (see Alerting). The temp folder is always removed.

The Spaces keys never appear in the command line (passed to the container as environment
variables) and the script prints none of them.

## Alerting: dead-man's switch

The script pings a [healthchecks.io](https://healthchecks.io) check (`BACKUP_HEALTHCHECK_URL`):
`/start` when it begins, the plain URL when everything worked, `/fail` with the error text when any
step failed. Why this and not an email from the droplet:

- It covers the case an in-script alert can't: the script never ran (cron broken, droplet down,
  Docker down, env file wrong). A missing ping is an alert, so "no good dump for about 26 hours" is
  just the check's schedule: **period 1 day, grace time 2 hours**.
- No SMTP from the droplet needed (only Brevo on port 2525 works there, ADR-0059) and no mail code
  to maintain. The check emails the owner on its own (add an email integration; Telegram or Slack
  work too).
- The free plan is enough (20 checks). If the monitoring chore later picks another channel, only
  the URL changes.

Without `BACKUP_HEALTHCHECK_URL` the script still exits non-zero on failure but nothing alerts, so
don't leave it empty in production.

## One-time setup (the developer, DO console and droplet)

1. **Droplet Backups.** DO console > Droplets > the production droplet > Backups > Enable backups.
   Choose daily if the plan offers it (about 30% of the droplet price; weekly is 20%).
2. **Spaces bucket.** Console > Spaces Object Storage > Create. Pick a **different region** from the
   droplet. Name it e.g. `opsclear-backups`. Keep **File Listing: Restrict** (private). Note the
   region and endpoint, `https://<region>.digitaloceanspaces.com`.
3. **Lifecycle rule.** The bucket > Settings > Lifecycle Rules > Add: prefix `db/`, expire current
   versions after **30 days**, and tick "delete incomplete multipart uploads".
4. **Access key.** Console > API > Spaces Keys > Generate. Use a **limited access** key, scoped to
   that one bucket with read/write/delete. Copy the secret at once, it is shown only once.
5. **Health check.** Create a free check on healthchecks.io named `opsclear-db-backup`, period
   1 day, grace 2 hours, and add an email integration. Copy its ping URL.
6. **Env vars** in `~/OpsClear/.env.prod` (see `.env.prod.example`), then `chmod 600 .env.prod`:

   ```sh
   BACKUP_S3_ENDPOINT=https://<region>.digitaloceanspaces.com
   BACKUP_S3_REGION=<region>              # e.g. ams3
   BACKUP_S3_BUCKET=opsclear-backups
   BACKUP_S3_ACCESS_KEY=...
   BACKUP_S3_SECRET_KEY=...
   BACKUP_HEALTHCHECK_URL=https://hc-ping.com/<uuid>
   ```

   These variables are read only by the backup script. The compose services ignore them.
7. **First manual run**, on the droplet, from the checkout (`git pull` first, or wait for the deploy):

   ```sh
   cd ~/OpsClear && scripts/backup-db.sh
   ```

   Expect "Backup ... done", two new objects under `db/` in the bucket and a green check on
   healthchecks.io. Also test the alert once: temporarily set `BACKUP_S3_BUCKET=does-not-exist` in
   `.env.prod`, run the script (it fails at the upload), confirm the alert email arrives, and put
   the real bucket name back.
8. **Schedule** at a quiet hour (03:15 server time). Cron, as the user that owns the checkout
   (it must be in the `docker` group):

   ```sh
   crontab -e
   # OpsClear nightly database backup (JOB-421, docs/dev/BACKUP.md)
   15 3 * * * cd $HOME/OpsClear && scripts/backup-db.sh >> $HOME/opsclear-backup.log 2>&1
   ```

   Check it ran the next morning: `tail ~/opsclear-backup.log`.
9. **Prod restore test** (below), then fill in the test record at the bottom.

## Where the keys are

- Spaces key and health check URL: `~/OpsClear/.env.prod` on the droplet (root-readable, `600`,
  never in git). Keep a copy of the Spaces key and the DO login in the password manager. The key
  can be regenerated in the DO console (API > Spaces Keys), then update `.env.prod`.
- A Droplet Backup includes `.env.prod`, so a rebuilt droplet gets the keys back.

## Restore one database from a dump

Do this on the droplet, in `~/OpsClear`. Use the dump you want: list them, newest last.

```sh
set -a; . ./.env.prod; set +a
aws() { docker run --rm -e AWS_ACCESS_KEY_ID="$BACKUP_S3_ACCESS_KEY" \
  -e AWS_SECRET_ACCESS_KEY="$BACKUP_S3_SECRET_KEY" -e AWS_DEFAULT_REGION="$BACKUP_S3_REGION" \
  -e AWS_EC2_METADATA_DISABLED=true -e AWS_REQUEST_CHECKSUM_CALCULATION=when_required \
  -e AWS_RESPONSE_CHECKSUM_VALIDATION=when_required -v "$HOME/restore:/data" \
  amazon/aws-cli:2.42.3 --endpoint-url "$BACKUP_S3_ENDPOINT" "$@"; }
mkdir -p ~/restore
aws s3 ls s3://$BACKUP_S3_BUCKET/db/ | sort -k4 | tail -n 6
aws s3 cp s3://$BACKUP_S3_BUCKET/db/opsclear-<timestamp>.dump /data/
```

Then replace the database (this **deletes the current data** in it, so take a fresh
`scripts/backup-db.sh` first if the stack still works). Stop what uses the database. For `opsclear`
that is the backend, for `keycloak` it is Keycloak:

```sh
C="docker compose -f docker-compose.prod.yml --env-file .env.prod"
DB=opsclear                      # or keycloak
USERSVC=backend                  # or keycloak
$C stop $USERSVC
$C exec -T postgres psql -U opsclear -d postgres -c "DROP DATABASE $DB WITH (FORCE)"
$C exec -T postgres psql -U opsclear -d postgres -c "CREATE DATABASE $DB"
$C exec -T postgres pg_restore -U opsclear -d $DB --no-owner --exit-on-error < ~/restore/$DB-<timestamp>.dump
$C start $USERSVC
```

Restoring `keycloak` and `opsclear` from different days works for the app, but accounts created
between the two dumps would be missing on one side. Prefer the pair with the same timestamp.

### Check after a restore

- Open https://opsclear.app and log in with a known account.
- Flyway is at the expected version. The latest should equal the highest `V<nnn>__` file in
  `backend/src/main/resources/db/migration/`:
  `$C exec -T postgres psql -U opsclear -d opsclear -Atc "select max(version::int) from flyway_schema_history where success"`
- Recent jobs and notes from just before the dump time are there, and the dashboard counts look
  right.
- `$C logs --tail 50 backend` shows no migration or connection errors.

### Restore into a throwaway container (the test, no risk to prod)

```sh
docker run -d --rm --name restore-test -e POSTGRES_USER=opsclear -e POSTGRES_PASSWORD=x postgres:16
sleep 6
docker exec -i restore-test pg_restore -U opsclear -d opsclear --no-owner --exit-on-error < ~/restore/opsclear-<timestamp>.dump
docker exec restore-test psql -U opsclear -d opsclear -Atc "select count(*) from jobs"
docker exec restore-test psql -U opsclear -d postgres -c "CREATE DATABASE keycloak"
docker exec -i restore-test pg_restore -U opsclear -d keycloak --no-owner --exit-on-error < ~/restore/keycloak-<timestamp>.dump
docker stop restore-test
```

## Rebuild from a Droplet Backup

Use this when the server itself is lost or broken.

1. DO console > the droplet > Backups > pick the image > **Restore Droplet** (same droplet, replaces
   the disk) or **Create Droplet** from the image (new droplet, new IP).
2. For a new droplet: point the DNS `A @` and `A www` records (Namecheap) at the new IP, and update
   the `DEPLOY_HOST` secret (and `DEPLOY_SSH_KEY` if the key changed) used by `.github/workflows/deploy.yml`.
3. On the droplet: `cd ~/OpsClear && docker compose -f docker-compose.prod.yml --env-file .env.prod up -d`.
   The image holds the certificates (`/etc/letsencrypt`), `.env.prod`, the checkout and the
   `postgres_data` volume.
4. The volume in a disk image was copied while Postgres ran, so Postgres may do crash recovery on
   start. If the databases look wrong or don't start, restore the latest dumps as above.
5. Run the checks above. Confirm the cron entry exists (`crontab -l`) and run
   `scripts/backup-db.sh` once.

## Restore tests

Fill in a row after every real test (do one after setup, then about every 6 months).

| Date | Tested by | Source (dump timestamp) | Result |
|---|---|---|---|
| 2026-11-25 | JOB-421 (local only: dev database, local S3-compatible store, throwaway postgres:16) | opsclear and keycloak dumps | OK: Flyway 44 = 44, 1649 jobs = 1649, 312 users = 312 |
| _fill in_ | | _latest prod dumps_ | _prod restore test, JOB-421 DoD_ |
