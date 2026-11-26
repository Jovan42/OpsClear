# Production backups and restore

Two layers (JOB-421). Both are set up by hand on the droplet and in the DigitalOcean account.
Prod runs as **root** in `/root/OpsClear`, so every command below is run as root there.

| Layer | What | Restores | Kept |
|---|---|---|---|
| Droplet Backups | Whole-disk image, weekly or daily (DO console) | The whole server: OS, certbot certificates, `.env.prod`, the checkout, Docker volumes | DO's standard retention |
| Database dumps | `scripts/backup-db.sh`, nightly: `pg_dump -Fc` of `opsclear` and `keycloak` to a private Spaces bucket | One database, cleanly | 30 days (bucket lifecycle rule) |

A disk image of a running Postgres may need crash recovery and can't restore one database, so the
dumps are the main way back for data. The disk image is for losing the server.

**Threat model.** The Spaces key has to be able to delete (DO's limited keys are either read or
read/write/delete), so someone with root on the droplet can delete every dump. The Droplet Backups
are the copy the droplet can't touch, so keep them on even though the dumps exist.

## How the backup works

`scripts/backup-db.sh` (read `--help` for the details). It works from any folder: it changes to the
repo root first.

1. Runs `pg_dump -Fc -Z 6` for each database inside the `postgres` service (`docker compose exec`),
   into a private temp folder on the droplet.
2. Checks each dump: at least 2 KB, `pg_restore -f /dev/null` reads the **whole** archive without
   error (a dump cut off in the data section fails; `pg_restore --list` alone would not notice),
   and it has table data. A failed or empty dump stops the script (`set -euo pipefail`, every step
   is checked).
3. Uploads to `s3://<bucket>/db/<database>-<UTC timestamp>.dump` with the pinned `amazon/aws-cli`
   Docker image (nothing to install, Docker is already there), then reads each object's size back
   from the bucket and compares.
4. Pings the health check (see Alerting). The temp folder is always removed.

The Spaces keys are passed to the container by variable name, so they are not in the command line
(`ps`) and the script prints none of them.

### Locks and deploys

`pg_dump` doesn't block INSERT, UPDATE or DELETE: it holds `ACCESS SHARE` on every table for the
length of the dump. It does block anything that needs `ACCESS EXCLUSIVE`: Flyway's `ALTER TABLE` and
`DROP`, and Keycloak's Liquibase upgrades. Worse, a migration waiting for that lock queues every
later read and write on the table behind it, so a deploy with a migration that lands during the dump
stalls the site until the dump ends. At today's size the dump takes seconds, and 03:15 is a quiet
time: nobody works then and deploys only run when someone merges to `main`. **Don't merge
migrations around 03:15 server time.** If the dump grows to minutes, set a `lock_timeout` for
Flyway.

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
6. **Env vars** in `/root/OpsClear/.env.prod` (see `.env.prod.example`), then `chmod 600 .env.prod`:

   ```sh
   BACKUP_S3_ENDPOINT=https://<region>.digitaloceanspaces.com
   BACKUP_S3_REGION=<region>              # e.g. ams3
   BACKUP_S3_BUCKET=opsclear-backups
   BACKUP_S3_ACCESS_KEY=...
   BACKUP_S3_SECRET_KEY=...
   BACKUP_HEALTHCHECK_URL=https://hc-ping.com/<uuid>
   ```

   These variables are read only by the backup script. The compose services ignore them.
7. **Get the script and do the first manual run.** Merging this change does **not** deploy it
   (`deploy.yml` only runs for `backend/**` and `frontend/**`), so pull it on the droplet:

   ```sh
   cd /root/OpsClear && git pull && scripts/backup-db.sh
   ```

   Expect "Backup ... done", two new objects under `db/` in the bucket and a green check on
   healthchecks.io. Also test the alert once: temporarily set `BACKUP_S3_BUCKET=does-not-exist` in
   `.env.prod`, run the script (it fails at the upload), confirm the alert email arrives, and put
   the real bucket name back.
8. **Schedule** at a quiet hour (03:15 server time), in **root's** crontab (`.env.prod` is `600`,
   root-only). `flock -n` stops a hung run from overlapping the next night's run:

   ```sh
   sudo crontab -e
   # OpsClear nightly database backup (JOB-421, docs/dev/BACKUP.md)
   15 3 * * * flock -n /var/lock/opsclear-backup.lock /root/OpsClear/scripts/backup-db.sh >> /root/opsclear-backup.log 2>&1
   ```

   Check it ran the next morning: `tail /root/opsclear-backup.log`. The log grows slowly; trim it
   now and then.
9. **Prod restore test** (below), then fill in the test record at the bottom.

## Where the keys are

- Spaces key and health check URL: `/root/OpsClear/.env.prod` on the droplet (root-only, `600`,
  never in git). Keep a copy of the Spaces key and the DO login in the password manager. The key
  can be regenerated in the DO console (API > Spaces Keys), then update `.env.prod`.
- A Droplet Backup includes `.env.prod`, so a rebuilt droplet gets the keys back.

## Restore one database from a dump

Do this on the droplet, in `/root/OpsClear`. First list the dumps and pick one (the newest
`opsclear-` and `keycloak-` pair with the same timestamp is the usual choice).

```sh
cd /root/OpsClear
set -a; . ./.env.prod; set +a
export AWS_ACCESS_KEY_ID=$BACKUP_S3_ACCESS_KEY AWS_SECRET_ACCESS_KEY=$BACKUP_S3_SECRET_KEY
aws() { docker run --rm -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY \
  -e AWS_DEFAULT_REGION="$BACKUP_S3_REGION" \
  -e AWS_EC2_METADATA_DISABLED=true -e AWS_REQUEST_CHECKSUM_CALCULATION=when_required \
  -e AWS_RESPONSE_CHECKSUM_VALIDATION=when_required -v "$HOME/restore:/data" \
  amazon/aws-cli:2.42.3 --endpoint-url "$BACKUP_S3_ENDPOINT" "$@"; }
mkdir -p ~/restore
# newest dumps last (the date and time columns sort first)
aws s3 ls s3://$BACKUP_S3_BUCKET/db/ | sort -k1,2 | tail -n 6
aws s3 cp s3://$BACKUP_S3_BUCKET/db/opsclear-<timestamp>.dump /data/
aws s3 cp s3://$BACKUP_S3_BUCKET/db/keycloak-<timestamp>.dump /data/
```

Check the disk first, because two copies of the database exist until you drop the old one:
`df -h /` (the Docker volume lives there) and `docker system df`. You need room for the dump's
restored size on top of the current database.

Then swap the database. The old one is **renamed, not dropped**, so there's a way back until the
checks pass. Stop what uses the database first: `backend` for `opsclear`, `keycloak` for `keycloak`.
Repeat for each database you restore.

The steps are chained with `&&`, so if any of them fails nothing after it runs. The most likely
failure is the rename ("being accessed by other users"): then the live database keeps its name and
nothing was changed. **Don't run the rollback in that case**: fix the cause and start again, or give
up and start the service again with `$C start $USERSVC` (it stays stopped after a failed swap).

```sh
C="docker compose -f docker-compose.prod.yml --env-file .env.prod"
P="$C exec -T postgres psql -U opsclear -d postgres -v ON_ERROR_STOP=1"
DB=opsclear                      # or keycloak
USERSVC=backend                  # or keycloak
TS=<timestamp>                   # of the dump you downloaded
exists() { [ "$($P -Atc "select 1 from pg_database where datname='$1'")" = 1 ]; }

# Refuse to start if a leftover from an earlier swap is still around.
if exists ${DB}_pre_restore || exists ${DB}_failed_restore; then
  echo "leftover ${DB}_pre_restore or ${DB}_failed_restore exists, look at it and drop it first"
else
  $C stop $USERSVC \
  && $P -c "select pg_terminate_backend(pid, 5000) from pg_stat_activity where datname='$DB' and pid <> pg_backend_pid()" \
  && $P -c "ALTER DATABASE $DB RENAME TO ${DB}_pre_restore" \
  && $P -c "CREATE DATABASE $DB" \
  && $C exec -T postgres pg_restore -U opsclear -d $DB --no-owner --exit-on-error < ~/restore/$DB-$TS.dump \
  && $C start $USERSVC
fi
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

**If a check fails, or the restore command failed after the rename: roll back** with two renames,
then start the service again. It refuses to run unless `${DB}_pre_restore` exists, so it can't
leave prod without a database:

```sh
if exists ${DB}_pre_restore; then
  $C stop $USERSVC \
  && $P -c "select pg_terminate_backend(pid, 5000) from pg_stat_activity where datname='$DB' and pid <> pg_backend_pid()" \
  && $P -c "ALTER DATABASE $DB RENAME TO ${DB}_failed_restore" \
  && $P -c "ALTER DATABASE ${DB}_pre_restore RENAME TO $DB" \
  && $C start $USERSVC
else
  echo "no ${DB}_pre_restore: nothing was swapped, do NOT roll back"
fi
```

After the rollback, look at `${DB}_failed_restore` if you want to know what went wrong, then drop
it (`$P -c "DROP DATABASE ${DB}_failed_restore"`). The next swap refuses to start while it exists.

**If all checks pass**, drop the old copy and delete the downloaded dumps (they hold all customer
data):

```sh
$P -c "DROP DATABASE ${DB}_pre_restore"
rm -rf ~/restore
```

### Restore into a throwaway container (the test, no risk to prod)

With the dumps downloaded to `~/restore` as above:

```sh
docker run -d --rm --name restore-test -e POSTGRES_USER=opsclear -e POSTGRES_PASSWORD=x postgres:16
until docker exec restore-test pg_isready -h 127.0.0.1 -U opsclear; do sleep 1; done
docker exec -i restore-test pg_restore -U opsclear -d opsclear --no-owner --exit-on-error < ~/restore/opsclear-<timestamp>.dump
docker exec restore-test psql -U opsclear -d opsclear -Atc "select count(*) from jobs"
docker exec restore-test psql -U opsclear -d postgres -c "CREATE DATABASE keycloak"
docker exec -i restore-test pg_restore -U opsclear -d keycloak --no-owner --exit-on-error < ~/restore/keycloak-<timestamp>.dump
docker stop restore-test
rm -rf ~/restore
```

## Rebuild from a Droplet Backup

Use this when the server itself is lost or broken.

1. If the old droplet still runs and you will use **Create Droplet** (a new droplet), **power the
   old one off first**, so two hosts don't both run the nightly backup or serve the site during the
   cutover.
2. DO console > the droplet > Backups > pick the image > **Restore Droplet** (same droplet, replaces
   the disk) or **Create Droplet** from the image (new droplet, new IP).
3. For a new droplet: point the DNS `A @` and `A www` records (Namecheap) at the new IP, and update
   the `DEPLOY_HOST` secret (and `DEPLOY_SSH_KEY` if the key changed) used by
   `.github/workflows/deploy.yml`.
4. On the droplet start **only the database** from the image, so the site doesn't serve old data:
   `cd /root/OpsClear && docker compose -f docker-compose.prod.yml --env-file .env.prod up -d postgres`.
   The image holds the certificates (`/etc/letsencrypt`), `.env.prod`, the checkout and the
   `postgres_data` volume.
5. **Compare the image's date with the newest dump** in `db/` (`aws s3 ls`, see above). The image is
   up to a day old (daily backups) or a week (weekly), so the newest dump is usually newer. If it
   is, **restore both dumps (same timestamp) with the steps above** (nothing else is running, so
   the stop and start of the services in those steps can be skipped). The volume in an image was
   copied while Postgres ran, so Postgres may also do crash recovery on start; if the databases
   look wrong or don't start, restore the dumps the same way.
6. Bring up the rest: `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d`. Run
   the checks above. Confirm the cron entry exists (`sudo crontab -l`) and run
   `scripts/backup-db.sh` once.

## Restore tests

Fill in a row after every real test (do one after setup, then about every 6 months).

| Date | Tested by | Source (dump timestamp) | Result |
|---|---|---|---|
| 2026-11-25 | JOB-421 (local only: dev database, local S3-compatible store, throwaway postgres:16) | opsclear and keycloak dumps | OK: Flyway 44 = 44, 1649 jobs = 1649, 312 users = 312 |
| _fill in_ | | _latest prod dumps_ | _prod restore test, JOB-421 DoD_ |
