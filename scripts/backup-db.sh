#!/usr/bin/env bash
# Nightly production database backup (JOB-421): pg_dump of the opsclear and keycloak databases,
# checked, then uploaded to a private DigitalOcean Spaces bucket. Runbook: docs/dev/BACKUP.md.
set -euo pipefail

# Work from the repo root so the default .env.prod and compose file resolve from any cwd.
# (A custom ENV_FILE or COMPOSE_FILE should be an absolute path.)
cd "$(dirname "${BASH_SOURCE[0]}")/.."

usage() {
  cat <<'USAGE'
Usage: scripts/backup-db.sh [--help]

Steps:
  1. Ping the dead-man's switch "start" URL (if BACKUP_HEALTHCHECK_URL is set).
  2. For each database in BACKUP_DATABASES: pg_dump -Fc inside the postgres service to a private
     temp folder, then check the dump (not tiny, pg_restore reads the whole archive without
     error, it holds table data).
  3. Upload the dumps to s3://BACKUP_S3_BUCKET/db/<database>-<UTC timestamp>.dump with the
     amazon/aws-cli Docker image, then check each object's size in the bucket.
  4. Ping BACKUP_HEALTHCHECK_URL on success, or BACKUP_HEALTHCHECK_URL/fail (with the error text)
     on any failure. A missed ping is also an alert (set the check to period 1 day, grace 2 hours).
Nothing is deleted here: old dumps expire through the bucket's 30-day lifecycle rule.
Exits non-zero on any failure. pg_dump doesn't block INSERT/UPDATE/DELETE, but it holds ACCESS
SHARE locks, so a migration that needs ACCESS EXCLUSIVE waits for it (see docs/dev/BACKUP.md).

Environment:
  ENV_FILE            env file to read (default .env.prod)
  COMPOSE_FILE        compose file (default docker-compose.prod.yml)
  POSTGRES_CONTAINER  use this container instead of the compose "postgres" service (local tests)
  POSTGRES_USER       database user (default opsclear)
  BACKUP_DATABASES    space-separated database names (default "opsclear keycloak")
  BACKUP_AWS_IMAGE    CLI image (default amazon/aws-cli:2.42.3)
  BACKUP_MIN_BYTES    smallest believable dump (default 2048)
Required in the env file (no defaults): BACKUP_S3_ENDPOINT (https://<region>.digitaloceanspaces.com),
BACKUP_S3_BUCKET, BACKUP_S3_REGION, BACKUP_S3_ACCESS_KEY, BACKUP_S3_SECRET_KEY.
Optional: BACKUP_HEALTHCHECK_URL (full ping URL, e.g. https://hc-ping.com/<uuid>).
USAGE
}

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
  "") ;;
  *) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
esac

ENV_FILE=${ENV_FILE:-.env.prod}
[ -f "$ENV_FILE" ] || { echo "Env file $ENV_FILE not found" >&2; exit 1; }
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

HC_URL=${BACKUP_HEALTHCHECK_URL:-}

ping_hc() { # ping_hc [suffix] [body]
  [ -n "$HC_URL" ] || return 0
  curl -fsS -m 10 --retry 3 -o /dev/null --data-raw "${2:-}" "$HC_URL$1" \
    || echo "WARNING: could not reach the health check URL" >&2
}

# Private temp folder (0700), removed on exit. Any failure after this point pings /fail.
umask 077
WORKDIR=$(mktemp -d)
ERR_MSG="$WORKDIR/error.txt"
PG_ERR="$WORKDIR/pg_dump.err"

on_exit() {
  rc=$?
  if [ "$rc" -ne 0 ]; then
    msg=$(cat "$ERR_MSG" 2>/dev/null || echo "backup failed (exit $rc)")
    ping_hc /fail "$msg"
  fi
  rm -rf "$WORKDIR"
  exit "$rc"
}
trap on_exit EXIT

fail() {
  echo "ERROR: $*" >&2
  echo "$*" > "$ERR_MSG"
  exit 1
}

for v in BACKUP_S3_ENDPOINT BACKUP_S3_BUCKET BACKUP_S3_REGION BACKUP_S3_ACCESS_KEY BACKUP_S3_SECRET_KEY; do
  [ -n "${!v:-}" ] || fail "$v is empty or unset in $ENV_FILE"
done

COMPOSE_FILE=${COMPOSE_FILE:-docker-compose.prod.yml}
PG_USER=${POSTGRES_USER:-opsclear}
DATABASES=${BACKUP_DATABASES:-opsclear keycloak}
AWS_IMAGE=${BACKUP_AWS_IMAGE:-amazon/aws-cli:2.42.3}
MIN_BYTES=${BACKUP_MIN_BYTES:-2048}

pg() { # run a command in the postgres container, stdin passed through
  if [ -n "${POSTGRES_CONTAINER:-}" ]; then
    docker exec -i "$POSTGRES_CONTAINER" "$@"
  else
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres "$@"
  fi
}

# The keys reach the container by name (-e NAME): docker copies the values from its own
# environment, so they never appear in argv (ps). The checksum settings keep newer CLI versions
# compatible with S3-compatible stores such as Spaces.
aws_s3() {
  AWS_ACCESS_KEY_ID="$BACKUP_S3_ACCESS_KEY" AWS_SECRET_ACCESS_KEY="$BACKUP_S3_SECRET_KEY" \
  docker run --rm \
    -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY \
    -e AWS_DEFAULT_REGION="$BACKUP_S3_REGION" \
    -e AWS_EC2_METADATA_DISABLED=true \
    -e AWS_REQUEST_CHECKSUM_CALCULATION=when_required \
    -e AWS_RESPONSE_CHECKSUM_VALIDATION=when_required \
    -v "$WORKDIR:/data:ro" \
    "$AWS_IMAGE" --endpoint-url "$BACKUP_S3_ENDPOINT" "$@"
}

ping_hc /start
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
files=()

for db in $DATABASES; do
  file="$db-$STAMP.dump"
  echo "Dumping $db..."
  # -Fc is compressed (zlib). `if !` makes a failing pg_dump a handled failure, not a silent one.
  if ! pg pg_dump -Fc -Z 6 -U "$PG_USER" -d "$db" > "$WORKDIR/$file" 2> "$PG_ERR"; then
    fail "pg_dump of $db failed: $(tail -n 3 "$PG_ERR" | tr '\n' ' ')"
  fi
  size=$(wc -c < "$WORKDIR/$file" | tr -d ' ')
  [ "$size" -ge "$MIN_BYTES" ] || fail "dump of $db is only $size bytes (minimum $MIN_BYTES)"
  # --list reads only the header and table of contents, so first read the whole archive
  # (every data block is decompressed) to catch a dump cut off in the data section.
  pg pg_restore -f /dev/null < "$WORKDIR/$file" 2> "$PG_ERR" \
    || fail "dump of $db is not a complete archive: $(tail -n 2 "$PG_ERR" | tr '\n' ' ')"
  toc=$(pg pg_restore --list < "$WORKDIR/$file") || fail "dump of $db is not a readable archive"
  grep -q 'TABLE DATA' <<<"$toc" || fail "dump of $db contains no table data"
  echo "  $file: $size bytes, OK"
  files+=("$file")
done

for file in "${files[@]}"; do
  echo "Uploading $file..."
  aws_s3 s3 cp "/data/$file" "s3://$BACKUP_S3_BUCKET/db/$file" --only-show-errors \
    || fail "upload of $file failed"
  want=$(wc -c < "$WORKDIR/$file" | tr -d ' ')
  got=$(aws_s3 s3api head-object --bucket "$BACKUP_S3_BUCKET" --key "db/$file" \
    --query ContentLength --output text) || fail "could not read $file back from the bucket"
  [ "$got" = "$want" ] || fail "uploaded $file is $got bytes in the bucket, expected $want"
done

echo "Backup $STAMP done: ${files[*]}"
ping_hc "" "backup $STAMP ok: ${files[*]}"
