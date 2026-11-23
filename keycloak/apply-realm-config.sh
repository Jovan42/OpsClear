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
  -s defaultLocale=en \
  -s ssoSessionIdleTimeout=7200

echo "Realm 'opsclear' updated: displayName=OpsClear, loginTheme=opsclear, internationalizationEnabled=true, supportedLocales=[en,sr], ssoSessionIdleTimeout=7200 (120 min)"

# Production only: make sure the frontend client accepts logins from the app's public URL
# (APP_URL, or https://APP_HOST:APP_PORT as docker-compose.prod.yml builds it). realm-export.json
# is only imported on a realm's first start, so a new domain has to be added here. Adds the URL
# next to what's already there; never removes an entry.
if [ -n "$ENV_FILE" ]; then
  APP_ORIGIN="${APP_URL:-https://${APP_HOST:-localhost}:${APP_PORT:-8443}}"
  APP_ORIGIN="${APP_ORIGIN%/}"
  CLIENT_UUID=$(compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get clients -r opsclear \
    -q clientId=opsclear-frontend --fields id --format csv --noquotes | tr -d '\r')
  # The full client, not --fields: --fields drops nested attributes like post.logout.redirect.uris.
  CLIENT_JSON=$(compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get "clients/$CLIENT_UUID" -r opsclear)

  if ! printf '%s' "$CLIENT_JSON" | grep -q "\"$APP_ORIGIN/\*\""; then
    for suffix in '/*' '/*/*' '/*/*/*' '/*/*/*/*'; do
      compose exec -T keycloak /opt/keycloak/bin/kcadm.sh update "clients/$CLIENT_UUID" -r opsclear \
        -s "redirectUris+=$APP_ORIGIN$suffix"
    done
  fi
  if ! printf '%s' "$CLIENT_JSON" | grep -q "\"$APP_ORIGIN\""; then
    compose exec -T keycloak /opt/keycloak/bin/kcadm.sh update "clients/$CLIENT_UUID" -r opsclear \
      -s "webOrigins+=$APP_ORIGIN"
  fi
  LOGOUT_URIS=$(printf '%s' "$CLIENT_JSON" | sed -n 's/.*"post.logout.redirect.uris" *: *"\([^"]*\)".*/\1/p')
  case "$LOGOUT_URIS" in
    *"$APP_ORIGIN/*"*) ;;
    *)
      # "##"-separated list; keep every existing entry and append ours.
      case "$LOGOUT_URIS" in ''|*'##') SEP='' ;; *) SEP='##' ;; esac
      compose exec -T keycloak /opt/keycloak/bin/kcadm.sh update "clients/$CLIENT_UUID" -r opsclear \
        -s "attributes.\"post.logout.redirect.uris\"=${LOGOUT_URIS}${SEP}${APP_ORIGIN}/*##" ;;
  esac

  echo "Client 'opsclear-frontend': login, origin and logout URLs include $APP_ORIGIN"
fi
