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
     SMTP_FROM, SMTP_FROM_NAME (auth=true, starttls=true).
  3. List users with emailVerified=false (they will have to verify).
  4. Only with --enable-verification: set verifyEmail=true.
  5. Print the email settings back, password masked.

Two-run rollout:
  1. Run without the flag. Nothing user-facing changes.
  2. In the Keycloak admin console (Realm settings > Email) press "Test connection".
  3. Run again with --enable-verification to turn verification on.
Safe to run again at any point.

Environment:
  ENV_FILE            env file to read (default .env.prod)
  KEYCLOAK_CONTAINER  container name (default: the "keycloak" service of docker-compose.prod.yml)
  KEYCLOAK_REALM      realm (default opsclear)
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
for v in SMTP_HOST SMTP_PORT SMTP_USER SMTP_PASSWORD SMTP_FROM SMTP_FROM_NAME; do
  [ -n "${!v:-}" ] || { echo "$v is empty in $ENV_FILE" >&2; exit 1; }
done

COMPOSE_FILE=${COMPOSE_FILE:-docker-compose.prod.yml}
CONTAINER=${KEYCLOAK_CONTAINER:-$(docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps -q keycloak 2>/dev/null | head -n1)}
[ -n "$CONTAINER" ] || { echo "Keycloak container not found (set KEYCLOAK_CONTAINER)" >&2; exit 1; }

# Secrets reach the container as env vars (-e VAR takes the value from this shell), so they never
# appear in argv or in the output.
KC_ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD:-admin}
KC_SMTP_PASSWORD=$SMTP_PASSWORD
export KC_ADMIN_PASSWORD KC_SMTP_PASSWORD SMTP_HOST SMTP_PORT SMTP_USER SMTP_FROM SMTP_FROM_NAME REALM
KCADM=/opt/keycloak/bin/kcadm.sh
ENVS=(-e KC_ADMIN_PASSWORD -e KC_SMTP_PASSWORD -e SMTP_HOST -e SMTP_PORT -e SMTP_USER -e SMTP_FROM -e SMTP_FROM_NAME -e REALM)

kc() {
  docker exec -i "${ENVS[@]}" "$CONTAINER" "$KCADM" "$@"
}

echo "Logging in to Keycloak..."
docker exec -i "${ENVS[@]}" "$CONTAINER" sh -c \
  "$KCADM config credentials --server http://localhost:8080 --realm master --user admin --password \"\$KC_ADMIN_PASSWORD\"" >/dev/null

echo "Setting emailTheme and smtpServer on realm $REALM..."
# The realm update goes in as JSON on stdin (printf is a builtin, so the password never shows in
# any argv). The values come from the env file and must not contain double quotes or backslashes.
printf '{"emailTheme":"opsclear","smtpServer":{"host":"%s","port":"%s","user":"%s","password":"%s","from":"%s","fromDisplayName":"%s","auth":"true","starttls":"true","ssl":"false"}}' \
  "$SMTP_HOST" "$SMTP_PORT" "$SMTP_USER" "$SMTP_PASSWORD" "$SMTP_FROM" "$SMTP_FROM_NAME" \
  | kc update "realms/$REALM" -f -

echo
echo "Users with emailVerified=false (they will have to verify):"
kc get users -r "$REALM" -q emailVerified=false --fields username,email --format csv --noquotes || true

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
