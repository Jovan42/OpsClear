#!/usr/bin/env bash
# seed.sh — wipe and repopulate OpsClear with realistic demo data
# Usage: ./scripts/seed.sh
# Requires: local dev stack running (docker-compose up)
set -euo pipefail

KEYCLOAK=http://localhost:8180
API=http://localhost:8080
DB_CONTAINER=opsclear-postgres
DB_NAME=opsclear

echo "============================================"
echo "  OpsClear Demo Seed Script"
echo "============================================"
echo ""

# -----------------------------------------------
# 1. RESET
# -----------------------------------------------
echo "==> [1/5] Resetting database..."
docker exec "$DB_CONTAINER" psql -U opsclear -d "$DB_NAME" -c \
  "TRUNCATE approvals, notes, project_block_reasons, jobs, milestones, project_members, projects, users RESTART IDENTITY CASCADE;" \
  > /dev/null
echo "  Database cleared."

echo "==> [1/5] Removing existing seed users from Keycloak..."
ADMIN_TOKEN=$(curl -sf -X POST "$KEYCLOAK/realms/master/protocol/openid-connect/token" \
  -d "client_id=admin-cli&username=admin&password=admin&grant_type=password" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

delete_kc_user() {
  local email="$1"
  local uid
  uid=$(curl -sf -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK/admin/realms/opsclear/users?email=$email" \
    | python3 -c "import sys,json; u=json.load(sys.stdin); print(u[0]['id'] if u else '')")
  if [ -n "$uid" ]; then
    curl -sf -o /dev/null -X DELETE \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      "$KEYCLOAK/admin/realms/opsclear/users/$uid"
    echo "  Removed $email"
  fi
}

for email in alice@example.com bob@example.com carol@example.com dave@example.com; do
  delete_kc_user "$email"
done

# -----------------------------------------------
# 2. KEYCLOAK USERS
# -----------------------------------------------
echo ""
echo "==> [2/5] Creating Keycloak users..."

create_kc_user() {
  local first="$1" last="$2" email="$3"
  curl -sf -o /dev/null -X POST "$KEYCLOAK/admin/realms/opsclear/users" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"firstName\": \"$first\", \"lastName\": \"$last\",
      \"email\": \"$email\", \"username\": \"$email\",
      \"enabled\": true, \"emailVerified\": true,
      \"credentials\": [{\"type\":\"password\",\"value\":\"password123\",\"temporary\":false}]
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
echo "==> [3/5] Syncing users into OpsClear..."

get_token() {
  curl -sf -X POST "$KEYCLOAK/realms/opsclear/protocol/openid-connect/token" \
    -d "client_id=opsclear-frontend&username=$1&password=$2&grant_type=password&scope=openid" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"
}

TOKEN_TEST=$(get_token  "testuser@example.com" "password123")
TOKEN_ALICE=$(get_token "alice@example.com"    "password123")
TOKEN_BOB=$(get_token   "bob@example.com"      "password123")
TOKEN_CAROL=$(get_token "carol@example.com"    "password123")
TOKEN_DAVE=$(get_token  "dave@example.com"     "password123")

for TOKEN in "$TOKEN_TEST" "$TOKEN_ALICE" "$TOKEN_BOB" "$TOKEN_CAROL" "$TOKEN_DAVE"; do
  curl -sf -o /dev/null -H "Authorization: Bearer $TOKEN" "$API/api/projects"
done

get_kc_id() {
  curl -sf -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK/admin/realms/opsclear/users?email=$1" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])"
}

ID_TEST=$(get_kc_id  "testuser@example.com")
ID_ALICE=$(get_kc_id "alice@example.com")
ID_BOB=$(get_kc_id   "bob@example.com")
ID_CAROL=$(get_kc_id "carol@example.com")
ID_DAVE=$(get_kc_id  "dave@example.com")
echo "  Users synced."

# -----------------------------------------------
# HELPERS
# -----------------------------------------------
jid() { python3 -c "import sys,json; print(json.load(sys.stdin)['id'])"; }

api() {
  local method="$1" path="$2" token="$3" body="${4:-}"
  curl -sf -X "$method" "$API$path" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    ${body:+-d "$body"}
}

create_job() {
  # args: token projectId title priority assignedTo(or "") deadline(or "") milestoneId(or "") client(or "")
  local token="$1" pid="$2" title="$3" priority="$4" assigned="$5" deadline="$6" milestone="$7" client="${8:-}"
  local body="{\"title\":\"$title\",\"priority\":\"$priority\""
  [ -n "$assigned"  ] && body+=",\"assignedTo\":\"$assigned\""
  [ -n "$deadline"  ] && body+=",\"deadline\":\"${deadline}T00:00:00Z\""
  [ -n "$milestone" ] && body+=",\"milestoneId\":\"$milestone\""
  [ -n "$client"    ] && body+=",\"client\":\"$client\""
  body+="}"
  api POST "/api/projects/$pid/jobs" "$token" "$body" | jid
}

set_status() {
  local token="$1" pid="$2" jid="$3" status="$4" reason="${5:-}"
  local body="{\"status\":\"$status\""
  [ -n "$reason" ] && body+=",\"reason\":\"$reason\""
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

# -----------------------------------------------
# 4. PROJECTS + MEMBERS + MILESTONES + JOBS
# -----------------------------------------------
echo ""
echo "==> [4/5] Creating projects, milestones, and jobs..."

# ===================================================================
# PROJECT 1: Website Redesign  (Alice owns, Bob admin, Carol+Dave members)
# Status: ACTIVE — mixed statuses across milestones
# ===================================================================
P1=$(api POST "/api/projects" "$TOKEN_ALICE" \
  '{"name":"Website Redesign","description":"Full redesign of the company marketing site for Acme Corp"}' | jid)
api POST "/api/projects/$P1/members" "$TOKEN_ALICE" "{\"userId\":\"$ID_BOB\",\"role\":\"ADMIN\"}"   > /dev/null
api POST "/api/projects/$P1/members" "$TOKEN_ALICE" "{\"userId\":\"$ID_CAROL\",\"role\":\"MEMBER\"}" > /dev/null
api POST "/api/projects/$P1/members" "$TOKEN_ALICE" "{\"userId\":\"$ID_DAVE\",\"role\":\"MEMBER\"}"  > /dev/null

M1A=$(api POST "/api/projects/$P1/milestones" "$TOKEN_ALICE" \
  '{"name":"Discovery & Design","description":"Client research, wireframes, and design sign-off","deadline":"2026-03-31"}' | jid)
M1B=$(api POST "/api/projects/$P1/milestones" "$TOKEN_ALICE" \
  '{"name":"Development","description":"Frontend build and backend integration","deadline":"2026-04-30"}' | jid)
M1C=$(api POST "/api/projects/$P1/milestones" "$TOKEN_ALICE" \
  '{"name":"Launch","description":"Staging QA, client review, go-live","deadline":"2026-05-20"}' | jid)

# Discovery & Design
J1=$(create_job "$TOKEN_ALICE" "$P1" "Kick-off meeting with client"          "MEDIUM"   "$ID_ALICE" "2026-03-10" "$M1A" "Acme Corp")
J2=$(create_job "$TOKEN_ALICE" "$P1" "Competitor analysis"                   "LOW"      "$ID_CAROL" "2026-03-14" "$M1A" "Acme Corp")
J3=$(create_job "$TOKEN_ALICE" "$P1" "Wireframes — homepage"                 "HIGH"     "$ID_CAROL" "2026-03-21" "$M1A" "Acme Corp")
J4=$(create_job "$TOKEN_ALICE" "$P1" "Wireframes — interior pages"           "MEDIUM"   "$ID_DAVE"  "2026-03-24" "$M1A" "Acme Corp")

# Development
J5=$(create_job "$TOKEN_ALICE" "$P1" "Implement navigation component"        "HIGH"     "$ID_DAVE"  "2026-04-07" "$M1B" "Acme Corp")
J6=$(create_job "$TOKEN_ALICE" "$P1" "Build product listing page"            "HIGH"     "$ID_CAROL" "2026-04-12" "$M1B" "Acme Corp")
J7=$(create_job "$TOKEN_ALICE" "$P1" "Fix critical login bug"                "CRITICAL" "$ID_BOB"   "2026-03-13" "$M1B" "Acme Corp")
J8=$(create_job "$TOKEN_ALICE" "$P1" "Set up CI/CD pipeline"                 "HIGH"     "$ID_BOB"   "2026-03-17" "$M1B" "")

# Launch
J9=$(create_job  "$TOKEN_ALICE" "$P1" "Deploy to staging"                    "CRITICAL" "$ID_BOB"   "2026-04-28" "$M1C" "Acme Corp")
J10=$(create_job "$TOKEN_ALICE" "$P1" "Final QA pass"                        "HIGH"     "$ID_CAROL" "2026-05-02" "$M1C" "Acme Corp")

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

# Notes
note "$TOKEN_ALICE" "$P1" "$J1" "Client confirmed mobile-first scope. Budget approved for 3 rounds of revisions."
note "$TOKEN_BOB"   "$P1" "$J7" "Root cause: session token not invalidated on password reset. Hotfix in review."
note "$TOKEN_BOB"   "$P1" "$J7" "Hotfix deployed to staging — monitoring for 24h before prod push."
note "$TOKEN_ALICE" "$P1" "$J8" "IT confirmed credentials will arrive Friday EOD."

# Approvals
A1=$(request_approval "$TOKEN_CAROL" "$P1" "$J6" "Product listing page complete — please review before I merge.")
decide_approval "$TOKEN_ALICE" "$P1" "$J6" "$A1" "APPROVED" "Looks good. Clean implementation, merge when ready."
A2=$(request_approval "$TOKEN_DAVE" "$P1" "$J5" "Navigation component done and responsive. Ready for review.")
# A2 left pending intentionally

echo "  P1 (Website Redesign) done."

# ===================================================================
# PROJECT 2: Mobile App  (testuser owns, Alice admin, Carol member)
# Status: ACTIVE — sprint in progress with a blocker
# ===================================================================
P2=$(api POST "/api/projects" "$TOKEN_TEST" \
  '{"name":"Mobile App","description":"Field technician app for iOS and Android — TechServ Ltd"}' | jid)
api POST "/api/projects/$P2/members" "$TOKEN_TEST" "{\"userId\":\"$ID_ALICE\",\"role\":\"ADMIN\"}"   > /dev/null
api POST "/api/projects/$P2/members" "$TOKEN_TEST" "{\"userId\":\"$ID_CAROL\",\"role\":\"MEMBER\"}"  > /dev/null

M2A=$(api POST "/api/projects/$P2/milestones" "$TOKEN_TEST" \
  '{"name":"MVP","description":"Core job tracking screens and auth","deadline":"2026-04-15"}' | jid)
M2B=$(api POST "/api/projects/$P2/milestones" "$TOKEN_TEST" \
  '{"name":"Beta Release","description":"Push notifications, offline mode, store submission","deadline":"2026-05-31"}' | jid)

JA=$(create_job "$TOKEN_TEST" "$P2" "Define REST API contracts"             "HIGH"     "$ID_ALICE" "2026-03-22" "$M2A" "TechServ Ltd")
JB=$(create_job "$TOKEN_TEST" "$P2" "Build login & auth flow"               "CRITICAL" "$ID_CAROL" "2026-03-27" "$M2A" "TechServ Ltd")
JC=$(create_job "$TOKEN_TEST" "$P2" "Job list screen"                       "HIGH"     "$ID_CAROL" "2026-03-31" "$M2A" "TechServ Ltd")
JD=$(create_job "$TOKEN_TEST" "$P2" "Push notification integration"         "CRITICAL" "$ID_TEST"  "2026-03-18" "$M2A" "TechServ Ltd")
JE=$(create_job "$TOKEN_TEST" "$P2" "Offline mode support"                  "MEDIUM"   "$ID_CAROL" "2026-04-22" "$M2B" "TechServ Ltd")
JF=$(create_job "$TOKEN_TEST" "$P2" "App Store & Play Store submission"     "HIGH"     "$ID_TEST"  "2026-05-22" "$M2B" "TechServ Ltd")
JG=$(create_job "$TOKEN_TEST" "$P2" "Beta testing with 10 users"            "MEDIUM"   "$ID_ALICE" "2026-05-12" "$M2B" "TechServ Ltd")

set_status "$TOKEN_TEST" "$P2" "$JA" "IN_PROGRESS"
set_status "$TOKEN_TEST" "$P2" "$JA" "COMPLETED"
set_status "$TOKEN_TEST" "$P2" "$JB" "IN_PROGRESS"
set_status "$TOKEN_TEST" "$P2" "$JC" "IN_PROGRESS"
set_status "$TOKEN_TEST" "$P2" "$JD" "IN_PROGRESS"
set_status "$TOKEN_TEST" "$P2" "$JD" "BLOCKED" "Third-party SDK licence not yet approved by legal"

note "$TOKEN_ALICE" "$P2" "$JA" "API contracts reviewed with backend team — all endpoints confirmed."
note "$TOKEN_CAROL" "$P2" "$JB" "Using JWT stored in secure keychain — no plain-text storage."
note "$TOKEN_TEST"  "$P2" "$JD" "Legal says review takes 5–7 business days. Escalated to VP on 2026-03-14."

A3=$(request_approval "$TOKEN_CAROL" "$P2" "$JC" "Job list screen complete with filters and search. Screenshots in Figma.")
# A3 left pending intentionally

echo "  P2 (Mobile App) done."

# ===================================================================
# PROJECT 3: Infrastructure Upgrade  (Bob owns, testuser + Dave members)
# Status: COMPLETED — all jobs done, project closed
# ===================================================================
P3=$(api POST "/api/projects" "$TOKEN_BOB" \
  '{"name":"Infrastructure Upgrade","description":"Server migration and hardening — Q1 2026 internal"}' | jid)
api POST "/api/projects/$P3/members" "$TOKEN_BOB" "{\"userId\":\"$ID_TEST\",\"role\":\"MEMBER\"}" > /dev/null
api POST "/api/projects/$P3/members" "$TOKEN_BOB" "{\"userId\":\"$ID_DAVE\",\"role\":\"MEMBER\"}" > /dev/null

M3A=$(api POST "/api/projects/$P3/milestones" "$TOKEN_BOB" \
  '{"name":"Preparation","description":"Audit, provisioning, SSL renewal","deadline":"2026-02-28"}' | jid)
M3B=$(api POST "/api/projects/$P3/milestones" "$TOKEN_BOB" \
  '{"name":"Cutover","description":"Migration, monitoring, decommission","deadline":"2026-03-10"}' | jid)

JX=$(create_job "$TOKEN_BOB" "$P3" "Audit current server configuration"    "MEDIUM"   "$ID_DAVE"  "2026-02-12" "$M3A" "")
JY=$(create_job "$TOKEN_BOB" "$P3" "Provision new VPS"                     "HIGH"     "$ID_BOB"   "2026-02-17" "$M3A" "")
JZ=$(create_job "$TOKEN_BOB" "$P3" "Renew SSL certificates"                "CRITICAL" "$ID_BOB"   "2026-02-21" "$M3A" "")
JW=$(create_job "$TOKEN_BOB" "$P3" "Migrate application to new VPS"        "HIGH"     "$ID_DAVE"  "2026-03-05" "$M3B" "")
JV=$(create_job "$TOKEN_BOB" "$P3" "Set up monitoring and alerting"        "HIGH"     "$ID_TEST"  "2026-03-08" "$M3B" "")
JU=$(create_job "$TOKEN_BOB" "$P3" "Decommission old server"               "LOW"      "$ID_BOB"   "2026-03-10" "$M3B" "")

for JOB in "$JX" "$JY" "$JZ" "$JW" "$JV" "$JU"; do
  set_status "$TOKEN_BOB" "$P3" "$JOB" "IN_PROGRESS"
  set_status "$TOKEN_BOB" "$P3" "$JOB" "COMPLETED"
done

note "$TOKEN_BOB"  "$P3" "$JZ" "Renewed for 2 years via Let's Encrypt wildcard cert. Auto-renewal configured."
note "$TOKEN_DAVE" "$P3" "$JW" "Zero-downtime migration — DNS cut over at 02:00 UTC 2026-03-04. All services healthy."
note "$TOKEN_TEST" "$P3" "$JV" "Grafana + PagerDuty alerts live. Runbook updated in Confluence."

A4=$(request_approval "$TOKEN_DAVE" "$P3" "$JW" "Migration complete, all services healthy for 48h. Ready to close.")
decide_approval "$TOKEN_BOB" "$P3" "$JW" "$A4" "APPROVED" "Confirmed. Excellent zero-downtime execution."

api PATCH "/api/projects/$P3/status" "$TOKEN_BOB" '{"status":"COMPLETED"}' > /dev/null
echo "  P3 (Infrastructure Upgrade) marked COMPLETED."

# -----------------------------------------------
# 5. SUMMARY
# -----------------------------------------------
echo ""
echo "============================================"
echo "  Seed complete!"
echo "============================================"
echo ""
echo "Projects:"
printf "  %-28s  %s\n" "Website Redesign"       "ACTIVE     — 10 jobs (2 done, 2 blocked, 6 open) — 3 milestones"
printf "  %-28s  %s\n" "Mobile App"             "ACTIVE     —  7 jobs (1 done, 1 blocked, 5 open) — 2 milestones"
printf "  %-28s  %s\n" "Infrastructure Upgrade" "COMPLETED  —  6 jobs (all done)                  — 2 milestones"
echo ""
echo "Users (password: password123 for all):"
printf "  %-30s  %s\n" "testuser@example.com"  "owner: Mobile App | member: Infrastructure Upgrade"
printf "  %-30s  %s\n" "alice@example.com"     "owner: Website Redesign | admin: Mobile App"
printf "  %-30s  %s\n" "bob@example.com"       "admin: Website Redesign | owner: Infrastructure Upgrade"
printf "  %-30s  %s\n" "carol@example.com"     "member: Website Redesign, Mobile App"
printf "  %-30s  %s\n" "dave@example.com"      "member: Website Redesign, Infrastructure Upgrade"
echo ""
echo "Access:"
echo "  Swagger UI  http://localhost:8080/swagger-ui.html"
echo "  pgAdmin     http://localhost:5050  (admin@admin.com / admin)"
echo "  Keycloak    http://localhost:8180/admin  (admin / admin)"
echo ""
