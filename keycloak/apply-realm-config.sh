#!/bin/sh
# Applies realm-level settings that `start-dev --import-realm` will NOT re-apply once the
# realm already exists — true for every environment after its first startup, local dev and
# production alike. Safe to run repeatedly: every -s value is set unconditionally.
#
# Usage:
#   ./keycloak/apply-realm-config.sh                                    # local dev
#   ./keycloak/apply-realm-config.sh docker-compose.prod.yml .env.prod  # production
set -e

COMPOSE_FILE="${1:-docker-compose.yml}"
ENV_FILE="$2"

if [ -n "$ENV_FILE" ]; then
  # POSIX `sh` (e.g. dash, /bin/sh on Debian/Ubuntu) only searches $PATH for bare
  # filenames passed to `.` — it won't check the current directory unless the path
  # has a "/" in it, unlike bash's more lenient `source`. Force an explicit path.
  case "$ENV_FILE" in
    /*) ENV_FILE_PATH="$ENV_FILE" ;;
    *) ENV_FILE_PATH="./$ENV_FILE" ;;
  esac
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE_PATH"
  set +a
fi

ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"

compose() {
  if [ -n "$ENV_FILE" ]; then
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
  else
    docker compose -f "$COMPOSE_FILE" "$@"
  fi
}

compose exec -T keycloak /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 --realm master --user "$ADMIN_USER" --password "$ADMIN_PASSWORD"

compose exec -T keycloak /opt/keycloak/bin/kcadm.sh update realms/opsclear \
  -s displayName=OpsClear \
  -s loginTheme=opsclear \
  -s internationalizationEnabled=true \
  -s 'supportedLocales=["en","sr"]' \
  -s defaultLocale=en

echo "Realm 'opsclear' updated: displayName=OpsClear, loginTheme=opsclear, internationalizationEnabled=true, supportedLocales=[en,sr]"
