#!/usr/bin/env bash
# seed.sh — wipe and repopulate OpsClear with realistic demo data
# Usage: ./scripts/seed.sh
# Requires: local dev stack running (docker-compose up) and the backend on :8080.
#
# Every step fails loudly: the first failed request prints its HTTP status and response body and
# stops the script. Prerequisites are checked before anything is deleted, so a stack that isn't
# ready leaves the existing data alone.
#
# Overridable for other environments (CI uses these):
#   API_URL        backend base URL                      (default http://localhost:8080)
#   KEYCLOAK_URL   Keycloak base URL                     (default http://localhost:8180)
#   DB_CONTAINER   Postgres container name or ID         (default opsclear-postgres)
#   DB_NAME        database name                         (default opsclear)
#   DB_USER        database user                         (default opsclear)
set -euo pipefail

API=${API_URL:-http://localhost:8080}
KEYCLOAK=${KEYCLOAK_URL:-http://localhost:8180}
DB_CONTAINER=${DB_CONTAINER:-opsclear-postgres}
DB_NAME=${DB_NAME:-opsclear}
DB_USER=${DB_USER:-opsclear}
PASSWORD=password123

# The demo organisation. Internal (ADR-0030): every add-on and limit is unlocked without Paddle.
ORG_NAME="OpsClear Demo"
ORG_SLUG="DEM"

die() {
  echo "" >&2
  echo "ERROR: $*" >&2
  exit 1
}

psql_exec() {
  docker exec -i "$DB_CONTAINER" psql -v ON_ERROR_STOP=1 -X -q -At -U "$DB_USER" -d "$DB_NAME" "$@"
}

# Date N days from today (UTC), YYYY-MM-DD. Keeps the demo data current instead of drifting
# into the past.
day() {
  python3 -c "import datetime,sys; print(datetime.datetime.now(datetime.timezone.utc).date() + datetime.timedelta(days=int(sys.argv[1])))" "$1"
}

json_field() {
  python3 -c "import sys,json; print(json.load(sys.stdin)['$1'])"
}

jid() { json_field id; }

# api METHOD PATH TOKEN [BODY] — prints the response body; on a non-2xx status prints the
# status and body to stderr and fails.
api() {
  local method="$1" path="$2" token="$3" body="${4:-}"
  local out status
  out=$(curl -sS -X "$method" "$API$path" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    ${body:+-d "$body"} \
    -w $'\n%{http_code}') || die "$method $path: request failed (is the backend running at $API?)"
  status=${out##*$'\n'}
  out=${out%$'\n'*}
  if [[ "$status" != 2* ]]; then
    echo "" >&2
    echo "ERROR: $method $path returned HTTP $status" >&2
    echo "$out" >&2
    exit 1
  fi
  printf '%s' "$out"
}

kc_admin_token() {
  curl -sSf -X POST "$KEYCLOAK/realms/master/protocol/openid-connect/token" \
    -d "client_id=admin-cli&username=admin&password=admin&grant_type=password" \
    | json_field access_token
}

get_token() {
  curl -sSf -X POST "$KEYCLOAK/realms/opsclear/protocol/openid-connect/token" \
    -d "client_id=opsclear-frontend&username=$1&password=$2&grant_type=password&scope=openid" \
    | json_field access_token
}

# Keycloak user ID for an exact email, or "" if there's no such user.
kc_user_id() {
  curl -sSf -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK/admin/realms/opsclear/users?email=$1&exact=true" \
    | python3 -c "import sys,json; u=json.load(sys.stdin); print(u[0]['id'] if u else '')"
}

echo "============================================"
echo "  OpsClear Demo Seed Script"
echo "============================================"
echo ""

# -----------------------------------------------
# 0. PREFLIGHT (nothing is deleted until all of these pass)
# -----------------------------------------------
echo "==> [0/6] Checking prerequisites..."

for cmd in curl python3 docker; do
  command -v "$cmd" > /dev/null || die "'$cmd' is required but not installed."
done

psql_exec -c "SELECT 1" > /dev/null \
  || die "Can't reach Postgres in container '$DB_CONTAINER'. Start the stack with 'docker-compose up -d'."

TIER_COUNT=$(psql_exec -c "SELECT count(*) FROM subscription_tiers WHERE max_members >= 10 AND max_projects IS NULL" 2>/dev/null) \
  || die "The database has no OpsClear schema. Start the backend once so Flyway migrates it."
[ "$TIER_COUNT" -gt 0 ] || die "subscription_tiers is empty. Is the database fully migrated?"

ADMIN_TOKEN=$(kc_admin_token) \
  || die "Can't get a Keycloak admin token from $KEYCLOAK (admin/admin). Is Keycloak running?"

[ -n "$(kc_user_id testuser@example.com)" ] \
  || die "testuser@example.com isn't in the 'opsclear' Keycloak realm. Import keycloak/realm-export.json."

get_token "testuser@example.com" "$PASSWORD" > /dev/null \
  || die "Can't log in as testuser@example.com via the opsclear-frontend client."

curl -sSf -o /dev/null "$API/api/health" \
  || die "The backend isn't healthy at $API/api/health. Start it with 'cd backend && ./gradlew bootRun'."

echo "  Postgres, Keycloak and the backend are ready."

# -----------------------------------------------
# 1. RESET
# -----------------------------------------------
echo ""
echo "==> [1/6] Resetting database..."
# Every table except the Flyway history and the subscription catalog that migrations seed.
psql_exec > /dev/null <<'SQL'
SET client_min_messages TO WARNING;
DO $$
DECLARE
  tables text;
BEGIN
  SELECT string_agg(format('%I', tablename), ', ')
    INTO tables
    FROM pg_tables
   WHERE schemaname = 'public'
     AND tablename NOT IN ('flyway_schema_history', 'subscription_tiers', 'subscription_addons');
  EXECUTE 'TRUNCATE ' || tables || ' RESTART IDENTITY CASCADE';
END $$;
SQL
echo "  Database cleared."

echo "==> [1/6] Removing existing seed users from Keycloak..."
for email in alice@example.com bob@example.com carol@example.com dave@example.com; do
  uid=$(kc_user_id "$email")
  if [ -n "$uid" ]; then
    curl -sSf -o /dev/null -X DELETE \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      "$KEYCLOAK/admin/realms/opsclear/users/$uid"
    echo "  Removed $email"
  fi
done

# -----------------------------------------------
# 2. KEYCLOAK USERS
# -----------------------------------------------
echo ""
echo "==> [2/6] Creating Keycloak users..."

create_kc_user() {
  local first="$1" last="$2" email="$3"
  curl -sSf -o /dev/null -X POST "$KEYCLOAK/admin/realms/opsclear/users" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"firstName\": \"$first\", \"lastName\": \"$last\",
      \"email\": \"$email\", \"username\": \"$email\",
      \"enabled\": true, \"emailVerified\": true,
      \"credentials\": [{\"type\":\"password\",\"value\":\"$PASSWORD\",\"temporary\":false}]
    }"
  echo "  Created $email"
}

create_kc_user "Alice"  "Webb"    "alice@example.com"
create_kc_user "Bob"    "Harris"  "bob@example.com"
create_kc_user "Carol"  "Marsh"   "carol@example.com"
create_kc_user "Dave"   "Santos"  "dave@example.com"

# -----------------------------------------------
# 3. SYNC USERS INTO OPSCLEAR
# -----------------------------------------------
echo ""
echo "==> [3/6] Syncing users into OpsClear..."

TOKEN_TEST=$(get_token  "testuser@example.com" "$PASSWORD")
TOKEN_ALICE=$(get_token "alice@example.com"    "$PASSWORD")
TOKEN_BOB=$(get_token   "bob@example.com"      "$PASSWORD")
TOKEN_CAROL=$(get_token "carol@example.com"    "$PASSWORD")
TOKEN_DAVE=$(get_token  "dave@example.com"     "$PASSWORD")

# Any authenticated request creates the user row (UserSyncFilter). /organisations/mine is one of the
# few endpoints that works before the user belongs to an organisation (204 = no org yet).
for TOKEN in "$TOKEN_TEST" "$TOKEN_ALICE" "$TOKEN_BOB" "$TOKEN_CAROL" "$TOKEN_DAVE"; do
  api GET "/api/organisations/mine" "$TOKEN" > /dev/null
done

ID_TEST=$(kc_user_id  "testuser@example.com")
ID_ALICE=$(kc_user_id "alice@example.com")
ID_BOB=$(kc_user_id   "bob@example.com")
ID_CAROL=$(kc_user_id "carol@example.com")
ID_DAVE=$(kc_user_id  "dave@example.com")
echo "  Users synced."

# -----------------------------------------------
# 4. ORGANISATION + MEMBERS + SUBSCRIPTION
# -----------------------------------------------
echo ""
echo "==> [4/6] Creating organisation..."

ORG=$(api POST "/api/organisations" "$TOKEN_TEST" "{\"name\":\"$ORG_NAME\",\"slug\":\"$ORG_SLUG\"}" | jid)
api POST "/api/organisations/$ORG/members" "$TOKEN_TEST" "{\"userId\":\"$ID_ALICE\",\"role\":\"ADMIN\"}"  > /dev/null
api POST "/api/organisations/$ORG/members" "$TOKEN_TEST" "{\"userId\":\"$ID_BOB\",\"role\":\"ADMIN\"}"    > /dev/null
api POST "/api/organisations/$ORG/members" "$TOKEN_TEST" "{\"userId\":\"$ID_CAROL\",\"role\":\"MEMBER\"}" > /dev/null
api POST "/api/organisations/$ORG/members" "$TOKEN_TEST" "{\"userId\":\"$ID_DAVE\",\"role\":\"MEMBER\"}"  > /dev/null
echo "  $ORG_NAME ($ORG_SLUG): testuser owner, alice + bob admins, carol + dave members."

# Add-on access needs real Paddle billing or an internal org. ADR-0030: is_internal is set in the
# database only (no API), and is meant for the owner's own org and demo accounts. The add-on rows are
# there so the subscription page lists them; internal orgs get every add-on regardless.
psql_exec -v org_id="$ORG" > /dev/null <<'SQL'
INSERT INTO org_subscriptions (org_id, tier_id, billing_cycle, is_internal)
SELECT :'org_id'::uuid, id, 'MONTHLY', TRUE
  FROM subscription_tiers
 WHERE max_members >= 10 AND max_projects IS NULL
 ORDER BY max_members
 LIMIT 1;
INSERT INTO org_subscription_addons (org_subscription_id, addon_id)
SELECT s.id, a.id
  FROM org_subscriptions s CROSS JOIN subscription_addons a
 WHERE s.org_id = :'org_id'::uuid AND a.available;
SQL
INTERNAL=$(api GET "/api/organisations/$ORG/subscription" "$TOKEN_TEST" | json_field internal)
[ "$INTERNAL" = "True" ] || die "The demo organisation's subscription isn't internal."
echo "  Internal subscription with all add-ons."

# -----------------------------------------------
# HELPERS
# -----------------------------------------------
create_job() {
  # args: token projectId title priority assignedTo(or "") deadlineOffsetDays(or "") milestoneId(or "") client(or "")
  local token="$1" pid="$2" title="$3" priority="$4" assigned="$5" deadline="$6" milestone="$7" client="${8:-}"
  local body="{\"title\":\"$title\",\"priority\":\"$priority\""
  [ -n "$assigned"  ] && body+=",\"assignedTo\":\"$assigned\""
  [ -n "$deadline"  ] && body+=",\"deadline\":\"$(day "$deadline")T00:00:00Z\""
  [ -n "$milestone" ] && body+=",\"milestoneId\":\"$milestone\""
  [ -n "$client"    ] && body+=",\"client\":\"$client\""
  body+="}"
  api POST "/api/projects/$pid/jobs" "$token" "$body" | jid
}

create_milestone() {
  # args: token projectId name description deadlineOffsetDays
  api POST "/api/projects/$2/milestones" "$1" \
    "{\"name\":\"$3\",\"description\":\"$4\",\"deadline\":\"$(day "$5")\"}" | jid
}

set_status() {
  # args: token pid jid status [reason-or-completion-message]
  local token="$1" pid="$2" jid="$3" status="$4" text="${5:-}"
  local body="{\"status\":\"$status\""
  if [ -n "$text" ]; then
    if [ "$status" = "BLOCKED" ]; then body+=",\"reason\":\"$text\""; else body+=",\"message\":\"$text\""; fi
  fi
  body+="}"
  api PATCH "/api/projects/$pid/jobs/$jid/status" "$token" "$body" > /dev/null
}

note() {
  api POST "/api/projects/$2/jobs/$3/notes" "$1" "{\"content\":\"$4\"}" > /dev/null
}

request_approval() {
  api POST "/api/projects/$2/jobs/$3/approvals" "$1" "{\"description\":\"$4\"}" | jid
}

decide_approval() {
  # args: token pid jid aid status comment
  api PATCH "/api/projects/$2/jobs/$3/approvals/$4/status" "$1" \
    "{\"status\":\"$5\",\"comment\":\"$6\"}" > /dev/null
}

blocked_by() {
  # args: token pid jobId blockingJobId
  api POST "/api/projects/$2/jobs/$3/relationships" "$1" \
    "{\"targetJobId\":\"$4\",\"type\":\"BLOCKED_BY\"}" > /dev/null
}

add_member() {
  # args: token pid userId role
  api POST "/api/projects/$2/members" "$1" "{\"userId\":\"$3\",\"role\":\"$4\"}" > /dev/null
}

# -----------------------------------------------
# 5. PROJECTS + MEMBERS + MILESTONES + JOBS
# -----------------------------------------------
echo ""
echo "==> [5/6] Creating projects, milestones, and jobs..."

# ===================================================================
# PROJECT 1: Website Redesign  (Alice owns, Bob admin, Carol+Dave members)
# Status: ACTIVE — mixed statuses across milestones
# ===================================================================
P1=$(api POST "/api/projects" "$TOKEN_ALICE" \
  '{"name":"Website Redesign","description":"Full redesign of the company marketing site for Acme Corp"}' | jid)
add_member "$TOKEN_ALICE" "$P1" "$ID_BOB"   "ADMIN"
add_member "$TOKEN_ALICE" "$P1" "$ID_CAROL" "MEMBER"
add_member "$TOKEN_ALICE" "$P1" "$ID_DAVE"  "MEMBER"

M1A=$(create_milestone "$TOKEN_ALICE" "$P1" "Discovery & Design" "Client research, wireframes, and design sign-off" 16)
M1B=$(create_milestone "$TOKEN_ALICE" "$P1" "Development"        "Frontend build and backend integration"          46)
M1C=$(create_milestone "$TOKEN_ALICE" "$P1" "Launch"             "Staging QA, client review, go-live"               66)

# Discovery & Design
J1=$(create_job "$TOKEN_ALICE" "$P1" "Kick-off meeting with client"          "MEDIUM"   "$ID_ALICE" -5  "$M1A" "Acme Corp")
J2=$(create_job "$TOKEN_ALICE" "$P1" "Competitor analysis"                   "LOW"      "$ID_CAROL" -1  "$M1A" "Acme Corp")
J3=$(create_job "$TOKEN_ALICE" "$P1" "Wireframes — homepage"                 "HIGH"     "$ID_CAROL" 6   "$M1A" "Acme Corp")
J4=$(create_job "$TOKEN_ALICE" "$P1" "Wireframes — interior pages"           "MEDIUM"   "$ID_DAVE"  9   "$M1A" "Acme Corp")

# Development
J5=$(create_job "$TOKEN_ALICE" "$P1" "Implement navigation component"        "HIGH"     "$ID_DAVE"  23  "$M1B" "Acme Corp")
J6=$(create_job "$TOKEN_ALICE" "$P1" "Build product listing page"            "HIGH"     "$ID_CAROL" 28  "$M1B" "Acme Corp")
J7=$(create_job "$TOKEN_ALICE" "$P1" "Fix critical login bug"                "CRITICAL" "$ID_BOB"   -2  "$M1B" "Acme Corp")
J8=$(create_job "$TOKEN_ALICE" "$P1" "Set up CI/CD pipeline"                 "HIGH"     "$ID_BOB"   2   "$M1B" "")

# Launch
J9=$(create_job  "$TOKEN_ALICE" "$P1" "Deploy to staging"                    "CRITICAL" "$ID_BOB"   44  "$M1C" "Acme Corp")
J10=$(create_job "$TOKEN_ALICE" "$P1" "Final QA pass"                        "HIGH"     "$ID_CAROL" 48  "$M1C" "Acme Corp")

# Transitions
set_status "$TOKEN_ALICE" "$P1" "$J1" "IN_PROGRESS"
set_status "$TOKEN_ALICE" "$P1" "$J1" "COMPLETED"
set_status "$TOKEN_ALICE" "$P1" "$J2" "IN_PROGRESS"
set_status "$TOKEN_ALICE" "$P1" "$J2" "COMPLETED"
set_status "$TOKEN_CAROL" "$P1" "$J3" "IN_PROGRESS"
set_status "$TOKEN_DAVE"  "$P1" "$J4" "IN_PROGRESS"
set_status "$TOKEN_DAVE"  "$P1" "$J4" "BLOCKED" "Client has not provided brand guidelines yet"
set_status "$TOKEN_BOB"   "$P1" "$J7" "IN_PROGRESS"
set_status "$TOKEN_BOB"   "$P1" "$J8" "IN_PROGRESS"
set_status "$TOKEN_BOB"   "$P1" "$J8" "BLOCKED" "Waiting for server credentials from IT department"

# Relationships
blocked_by "$TOKEN_ALICE" "$P1" "$J9"  "$J8"
blocked_by "$TOKEN_ALICE" "$P1" "$J10" "$J9"

# Notes
note "$TOKEN_ALICE" "$P1" "$J1" "Client confirmed mobile-first scope. Budget approved for 3 rounds of revisions."
note "$TOKEN_BOB"   "$P1" "$J7" "Root cause: session token not invalidated on password reset. Hotfix in review."
note "$TOKEN_BOB"   "$P1" "$J7" "Hotfix deployed to staging — monitoring for 24h before prod push."
note "$TOKEN_ALICE" "$P1" "$J8" "IT confirmed credentials will arrive Friday EOD."

# Approvals
A1=$(request_approval "$TOKEN_CAROL" "$P1" "$J6" "Product listing page complete — please review before I merge.")
decide_approval "$TOKEN_ALICE" "$P1" "$J6" "$A1" "APPROVED" "Looks good. Clean implementation, merge when ready."
request_approval "$TOKEN_DAVE" "$P1" "$J5" "Navigation component done and responsive. Ready for review." > /dev/null
# left pending intentionally

echo "  P1 (Website Redesign) done."

# ===================================================================
# PROJECT 2: Mobile App  (testuser owns, Alice admin, Carol member)
# Status: ACTIVE — sprint in progress with a blocker; completion needs approval (ADR-0052)
# ===================================================================
P2=$(api POST "/api/projects" "$TOKEN_TEST" \
  '{"name":"Mobile App","description":"Field technician app for iOS and Android — TechServ Ltd"}' | jid)
add_member "$TOKEN_TEST" "$P2" "$ID_ALICE" "ADMIN"
add_member "$TOKEN_TEST" "$P2" "$ID_CAROL" "MEMBER"
api PATCH "/api/projects/$P2/settings" "$TOKEN_TEST" '{"requireCompletionApproval":true}' > /dev/null

M2A=$(create_milestone "$TOKEN_TEST" "$P2" "MVP"          "Core job tracking screens and auth"                 31)
M2B=$(create_milestone "$TOKEN_TEST" "$P2" "Beta Release" "Push notifications, offline mode, store submission" 77)

JA=$(create_job "$TOKEN_TEST" "$P2" "Define REST API contracts"             "HIGH"     "$ID_ALICE" 7   "$M2A" "TechServ Ltd")
JB=$(create_job "$TOKEN_TEST" "$P2" "Build login & auth flow"               "CRITICAL" "$ID_CAROL" 12  "$M2A" "TechServ Ltd")
JC=$(create_job "$TOKEN_TEST" "$P2" "Job list screen"                       "HIGH"     "$ID_CAROL" 16  "$M2A" "TechServ Ltd")
JD=$(create_job "$TOKEN_TEST" "$P2" "Push notification integration"         "CRITICAL" "$ID_TEST"  3   "$M2A" "TechServ Ltd")
create_job "$TOKEN_TEST" "$P2" "Offline mode support"                       "MEDIUM"   "$ID_CAROL" 38  "$M2B" "TechServ Ltd" > /dev/null
JF=$(create_job "$TOKEN_TEST" "$P2" "App Store & Play Store submission"     "HIGH"     "$ID_TEST"  68  "$M2B" "TechServ Ltd")
JG=$(create_job "$TOKEN_TEST" "$P2" "Beta testing with 10 users"            "MEDIUM"   "$ID_ALICE" 58  "$M2B" "TechServ Ltd")

set_status "$TOKEN_TEST"  "$P2" "$JA" "IN_PROGRESS"
set_status "$TOKEN_TEST"  "$P2" "$JA" "COMPLETED"
set_status "$TOKEN_TEST"  "$P2" "$JB" "IN_PROGRESS"
set_status "$TOKEN_TEST"  "$P2" "$JC" "IN_PROGRESS"
set_status "$TOKEN_TEST"  "$P2" "$JD" "IN_PROGRESS"
set_status "$TOKEN_TEST"  "$P2" "$JD" "BLOCKED" "Third-party SDK licence not yet approved by legal"
# Carol is a Member, so with the gate on this creates a pending completion approval (HTTP 202)
# instead of completing the job.
set_status "$TOKEN_CAROL" "$P2" "$JB" "COMPLETED" "Login, logout and token refresh done and tested on both platforms."

blocked_by "$TOKEN_TEST" "$P2" "$JF" "$JG"

note "$TOKEN_ALICE" "$P2" "$JA" "API contracts reviewed with backend team — all endpoints confirmed."
note "$TOKEN_CAROL" "$P2" "$JB" "Using JWT stored in secure keychain — no plain-text storage."
note "$TOKEN_TEST"  "$P2" "$JD" "Legal says review takes 5–7 business days. Escalated to the VP yesterday."

request_approval "$TOKEN_CAROL" "$P2" "$JC" "Job list screen complete with filters and search. Screenshots in Figma." > /dev/null
# left pending intentionally

echo "  P2 (Mobile App) done."

# ===================================================================
# PROJECT 3: Infrastructure Upgrade  (Bob owns, testuser + Dave members)
# Status: COMPLETED — all jobs done, project closed
# ===================================================================
P3=$(api POST "/api/projects" "$TOKEN_BOB" \
  '{"name":"Infrastructure Upgrade","description":"Server migration and hardening — internal"}' | jid)
add_member "$TOKEN_BOB" "$P3" "$ID_TEST" "MEMBER"
add_member "$TOKEN_BOB" "$P3" "$ID_DAVE" "MEMBER"

M3A=$(create_milestone "$TOKEN_BOB" "$P3" "Preparation" "Audit, provisioning, SSL renewal"     -15)
M3B=$(create_milestone "$TOKEN_BOB" "$P3" "Cutover"     "Migration, monitoring, decommission"  -5)

JX=$(create_job "$TOKEN_BOB" "$P3" "Audit current server configuration"    "MEDIUM"   "$ID_DAVE"  -31 "$M3A" "")
JY=$(create_job "$TOKEN_BOB" "$P3" "Provision new VPS"                     "HIGH"     "$ID_BOB"   -26 "$M3A" "")
JZ=$(create_job "$TOKEN_BOB" "$P3" "Renew SSL certificates"                "CRITICAL" "$ID_BOB"   -22 "$M3A" "")
JW=$(create_job "$TOKEN_BOB" "$P3" "Migrate application to new VPS"        "HIGH"     "$ID_DAVE"  -10 "$M3B" "")
JV=$(create_job "$TOKEN_BOB" "$P3" "Set up monitoring and alerting"        "HIGH"     "$ID_TEST"  -7  "$M3B" "")
JU=$(create_job "$TOKEN_BOB" "$P3" "Decommission old server"               "LOW"      "$ID_BOB"   -5  "$M3B" "")

for JOB in "$JX" "$JY" "$JZ" "$JW" "$JV" "$JU"; do
  set_status "$TOKEN_BOB" "$P3" "$JOB" "IN_PROGRESS"
  set_status "$TOKEN_BOB" "$P3" "$JOB" "COMPLETED"
done

note "$TOKEN_BOB"  "$P3" "$JZ" "Renewed for 2 years via Let's Encrypt wildcard cert. Auto-renewal configured."
note "$TOKEN_DAVE" "$P3" "$JW" "Zero-downtime migration — DNS cut over at 02:00 UTC. All services healthy."
note "$TOKEN_TEST" "$P3" "$JV" "Grafana + PagerDuty alerts live. Runbook updated in Confluence."

A4=$(request_approval "$TOKEN_DAVE" "$P3" "$JW" "Migration complete, all services healthy for 48h. Ready to close.")
decide_approval "$TOKEN_BOB" "$P3" "$JW" "$A4" "APPROVED" "Confirmed. Excellent zero-downtime execution."

api PATCH "/api/projects/$P3/status" "$TOKEN_BOB" '{"status":"COMPLETED"}' > /dev/null
echo "  P3 (Infrastructure Upgrade) marked COMPLETED."

# -----------------------------------------------
# 6. VERIFY
# -----------------------------------------------
echo ""
echo "==> [6/6] Verifying..."

check() {
  # args: label expected actual
  if [ "$2" != "$3" ]; then
    die "$1: expected $2, got $3"
  fi
}

count() { psql_exec -c "SELECT count(*) FROM $1"; }

check "users"                5  "$(count users)"
check "organisations"        1  "$(count organisations)"
check "organisation members" 5  "$(count organisation_members)"
check "projects"             3  "$(count projects)"
check "milestones"           7  "$(count milestones)"
check "jobs"                 23 "$(count jobs)"
check "notes"                10 "$(count notes)"
check "approvals"            5  "$(count approvals)"
check "job relationships"    3  "$(count job_relationships)"
check "pending completion approvals" 1 \
  "$(psql_exec -c "SELECT count(*) FROM approvals WHERE kind = 'COMPLETION' AND status = 'PENDING'")"

EXPECTED_ROLES="alice@example.com|Mobile App|ADMIN
alice@example.com|Website Redesign|OWNER
bob@example.com|Infrastructure Upgrade|OWNER
bob@example.com|Website Redesign|ADMIN
carol@example.com|Mobile App|MEMBER
carol@example.com|Website Redesign|MEMBER
dave@example.com|Infrastructure Upgrade|MEMBER
dave@example.com|Website Redesign|MEMBER
testuser@example.com|Infrastructure Upgrade|MEMBER
testuser@example.com|Mobile App|OWNER"
ACTUAL_ROLES=$(psql_exec -c "SELECT u.email || '|' || p.name || '|' || pm.role
  FROM project_members pm JOIN users u ON u.id = pm.user_id JOIN projects p ON p.id = pm.project_id
  ORDER BY u.email, p.name")
check "project roles" "$EXPECTED_ROLES" "$ACTUAL_ROLES"

# Notifications are written asynchronously after each request commits; give them a moment.
NOTIFICATIONS=0
for _ in $(seq 1 10); do
  NOTIFICATIONS=$(count notifications)
  [ "$NOTIFICATIONS" -gt 0 ] && break
  sleep 1
done
[ "$NOTIFICATIONS" -gt 0 ] || die "notifications: expected some, got none"

# Every demo user can log in and use the API inside the organisation.
for TOKEN in "$TOKEN_TEST" "$TOKEN_ALICE" "$TOKEN_BOB" "$TOKEN_CAROL" "$TOKEN_DAVE"; do
  api GET "/api/projects" "$TOKEN" > /dev/null
done
echo "  Counts, roles and API access check out ($NOTIFICATIONS notifications so far)."

# -----------------------------------------------
# SUMMARY
# -----------------------------------------------
echo ""
echo "============================================"
echo "  Seed complete!"
echo "============================================"
echo ""
echo "Organisation: $ORG_NAME ($ORG_SLUG), internal subscription with every add-on"
printf "  %-36s  %s\n" "testuser@example.com" "owner"
printf "  %-36s  %s\n" "alice@example.com, bob@example.com" "admins"
printf "  %-36s  %s\n" "carol@example.com, dave@example.com" "members"
echo ""
echo "Projects:"
printf "  %-24s  %s\n" "Website Redesign"       "ACTIVE     — 10 jobs (2 done, 2 blocked, 6 open) — 3 milestones"
printf "  %-24s  %s\n" "Mobile App"             "ACTIVE     —  7 jobs (1 done, 1 blocked, 5 open) — 2 milestones, completion approval on"
printf "  %-24s  %s\n" "Infrastructure Upgrade" "COMPLETED  —  6 jobs (all done)                  — 2 milestones"
echo ""
echo "10 notes, 5 approvals (2 approved, 3 pending incl. 1 completion request), 3 job relationships"
echo ""
echo "Users (password: $PASSWORD for all):"
printf "  %-30s  %s\n" "testuser@example.com"  "owner: Mobile App | member: Infrastructure Upgrade"
printf "  %-30s  %s\n" "alice@example.com"     "owner: Website Redesign | admin: Mobile App"
printf "  %-30s  %s\n" "bob@example.com"       "admin: Website Redesign | owner: Infrastructure Upgrade"
printf "  %-30s  %s\n" "carol@example.com"     "member: Website Redesign, Mobile App"
printf "  %-30s  %s\n" "dave@example.com"      "member: Website Redesign, Infrastructure Upgrade"
echo ""
echo "Access:"
echo "  Frontend    http://localhost:5173"
echo "  Swagger UI  http://localhost:8080/swagger-ui.html"
echo "  pgAdmin     http://localhost:5050  (admin@admin.com / admin)"
echo "  Keycloak    http://localhost:8180/admin  (admin / admin)"
echo ""
