#!/usr/bin/env bash
# Apply the email settings to the prod Keycloak realm (ADR-0059). Idempotent.
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/keycloak-apply-email.sh [--enable-verification] [--help]

Runs kcadm.sh inside the Keycloak container and reads SMTP settings from the env file.

Steps:
  1. Log in as the master admin (KEYCLOAK_ADMIN_PASSWORD).
  2. Set emailTheme=opsclear and smtpServer from SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD,
     SMTP_FROM, SMTP_FROM_NAME (auth=true, starttls=true), plus replyTo and replyToDisplayName
     (= SMTP_FROM_NAME) when SMTP_REPLY_TO is set (ADR-0064).
  3. List users with emailVerified=false (they will have to verify).
  4. Only with --enable-verification: set verifyEmail=true.
  5. Print the email settings back, password masked.

Two-run rollout:
  1. Run without the flag. Nothing user-facing changes.
  2. In the Keycloak admin console (Realm settings > Email) press "Test connection".
  3. Run again with --enable-verification to turn verification on.
Safe to run again at any point.

Fresh realm imports: keycloak/realm-export.json points SMTP at the dev host "mailpit". A fresh prod
import (new volume or database) therefore has a dead SMTP host until this script has run, so run it
right after any fresh import, before enabling verification.

Environment:
  ENV_FILE            env file to read (default .env.prod)
  COMPOSE_FILE        compose file used to find the container (default docker-compose.prod.yml)
  KEYCLOAK_CONTAINER  container name (default: the "keycloak" service of the compose file)
  KEYCLOAK_REALM      realm (default opsclear)
Required in the env file (no defaults): KEYCLOAK_ADMIN_PASSWORD, SMTP_HOST, SMTP_PORT, SMTP_USER,
SMTP_PASSWORD, SMTP_FROM, SMTP_FROM_NAME.
Optional in the env file: SMTP_REPLY_TO, where replies to Keycloak emails go (support@opsclear.app on
prod). Left out, no Reply-To is set; an existing one in the realm is cleared.
Values must not contain a double quote or a backslash.
USAGE
}

ENABLE=false
for arg in "$@"; do
  case "$arg" in
    --enable-verification) ENABLE=true ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $arg" >&2; usage >&2; exit 1 ;;
  esac
done

ENV_FILE=${ENV_FILE:-.env.prod}
[ -f "$ENV_FILE" ] || { echo "Env file $ENV_FILE not found" >&2; exit 1; }
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

REALM=${KEYCLOAK_REALM:-opsclear}
for v in KEYCLOAK_ADMIN_PASSWORD SMTP_HOST SMTP_PORT SMTP_USER SMTP_PASSWORD SMTP_FROM SMTP_FROM_NAME; do
  [ -n "${!v:-}" ] || { echo "$v is empty or unset in $ENV_FILE" >&2; exit 1; }
done
# The values go into a JSON document built by printf, so reject characters that would break it.
SMTP_REPLY_TO=${SMTP_REPLY_TO:-}
for v in REALM SMTP_HOST SMTP_PORT SMTP_USER SMTP_PASSWORD SMTP_FROM SMTP_FROM_NAME SMTP_REPLY_TO; do
  case "${!v}" in
    *\"*|*\\*) echo "$v must not contain a double quote or a backslash" >&2; exit 1 ;;
  esac
done

COMPOSE_FILE=${COMPOSE_FILE:-docker-compose.prod.yml}
CONTAINER=${KEYCLOAK_CONTAINER:-$(docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps -q keycloak 2>/dev/null | head -n1)}
[ -n "$CONTAINER" ] || { echo "Keycloak container not found (set KEYCLOAK_CONTAINER)" >&2; exit 1; }

KCADM=/opt/keycloak/bin/kcadm.sh

# Secrets never go in argv: kcadm reads the admin password from its stdin prompt, and the SMTP
# update goes in as JSON on stdin (printf is a shell builtin). Nothing secret is printed.
kc() {
  docker exec -i "$CONTAINER" "$KCADM" "$@"
}

echo "Logging in to Keycloak..."
printf '%s\n' "$KEYCLOAK_ADMIN_PASSWORD" \
  | kc config credentials --server http://localhost:8080 --realm master --user admin >/dev/null

# Reply-To is optional (ADR-0064). smtpServer is replaced as a whole, so leaving it out clears it.
REPLY_TO_JSON=""
if [ -n "$SMTP_REPLY_TO" ]; then
  REPLY_TO_JSON=$(printf ',"replyTo":"%s","replyToDisplayName":"%s"' "$SMTP_REPLY_TO" "$SMTP_FROM_NAME")
fi

echo "Setting emailTheme and smtpServer on realm $REALM..."
printf '{"emailTheme":"opsclear","smtpServer":{"host":"%s","port":"%s","user":"%s","password":"%s","from":"%s","fromDisplayName":"%s"%s,"auth":"true","starttls":"true","ssl":"false"}}' \
  "$SMTP_HOST" "$SMTP_PORT" "$SMTP_USER" "$SMTP_PASSWORD" "$SMTP_FROM" "$SMTP_FROM_NAME" "$REPLY_TO_JSON" \
  | kc update "realms/$REALM" -f -

echo
echo "Users with emailVerified=false (they will have to verify):"
# Paged, 100 at a time. A failing kcadm call stops the script (set -e).
offset=0
total=0
while :; do
  page=$(kc get users -r "$REALM" -q emailVerified=false --fields username,email \
    --format csv --noquotes --offset "$offset" --limit 100)
  [ -n "$page" ] || break
  printf '%s\n' "$page"
  n=$(printf '%s\n' "$page" | wc -l | tr -d ' ')
  total=$((total + n))
  [ "$n" -ge 100 ] || break
  offset=$((offset + 100))
done
echo "($total users)"

echo
if $ENABLE; then
  echo "Enabling verifyEmail..."
  kc update "realms/$REALM" -s verifyEmail=true
else
  echo "verifyEmail left unchanged. After Test connection works, rerun with --enable-verification."
fi

echo
echo "Current email settings (password masked):"
# --fields drops nested objects, so print the whole realm and keep the relevant lines.
# Keycloak already masks the stored SMTP password; the sed is a second guard.
kc get "realms/$REALM" \
  | awk '/"verifyEmail"|"emailTheme"/ {print} /"smtpServer"/ {p=1} p {print} p && /}/ {p=0}' \
  | sed -E 's/("password" *: *")[^"]*"/\1**********"/'
