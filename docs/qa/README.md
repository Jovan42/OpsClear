# Manual test scenarios

Scenario files for a full manual pass through the UI on prod, played as several users with
different org and project roles, over several days. Design and reasons:
[ADR-0063](../dev/decisions/0063-manual-test-scenarios.md). The automated E2E suite protects
regressions; this pass judges whether screens make sense and covers behaviour that only shows up
over real calendar days (deadline alerts, schedules).

The files are reused as a regression checklist and kept current with every feature: the PR template, the feature job templates and `pr-reviewer`
enforce the upkeep rule (see [Keeping it current](#keeping-it-current)).

## Files

One file per functional area, each its own `Chore:` job in MIL-047. The ID prefix starts every
scenario ID in that file.

| # | File | ID prefix | Covers |
|---|------|-----------|--------|
| 01 | `01-onboarding.md` | `ONB` | Sign-up, email verification, onboarding, org creation; builds the test world |
| 02 | `02-org-admin.md` | `ORG` | Org members and roles, invites (email, Copy link, Resend, accept, expired), org settings, ID prefixes, can't join or create a second org |
| 03 | `03-projects.md` | `PRJ` | Create, edit, archive, project settings, project members and roles, section visibility, project links, project directory |
| 04 | `04-jobs.md` | `JOB` | Create, edit, assign, statuses, block reasons, delete, list filters and search, job links |
| 05 | `05-notes-history-relationships.md` | `NHR` | Notes (immutable), status history, job relationships |
| 06 | `06-approvals.md` | `APR` | Approval requests and decisions, the completion approval gate |
| 07 | `07-planning.md` | `PLN` | Milestones, job templates (project and org), job types |
| 08 | `08-schedules.md` | `SCH` | Recurring schedules: create, preview, runs, pause and resume, missed runs |
| 09 | `09-notifications.md` | `NTF` | Notification centre, real-time updates, notification settings, every notification type, deadline alerts |
| 10 | `10-dashboard-stats.md` | `DSH` | Project dashboard, overview, stats |
| 11 | `11-account.md` | `ACC` | User settings and preferences, language, user guide |
| 12 | `12-billing.md` | `BIL` | Subscription, Paddle checkout (sandbox), add-on gating, credits, past due |
| 13 | `13-api-keys.md` | `API` | API keys, rate limiting and the 429 toast |
| 14 | `14-feedback-admin.md` | `ADM` | Feedback, super-admin feedback triage, pricing, credits |
| 15 | `15-cross-cutting.md` | `XC` | Permissions per role across all areas, org isolation, mobile layout, the 3-clicks rule, empty states, errors, en/sr |

Files are added by Chores 3 to 17 of the ADR. Until a file exists, its row is the plan.

## How to run

1. **Check Paddle first.** Paddle must be in sandbox on prod. `ONB-01` in `01-onboarding.md` checks
   this before any purchase and stops the run if it's live. `12-billing.md` checks it again.
2. **Set up the users.** Six personas share one real mailbox through plus addressing
   (`you+ana@…`, `you+nikola@…`, `you+ivana@…`, `you+luka@…`, `you+sara@…`, `you+petra@…`). Email verification is
   live (ADR-0059), so every address must receive mail. Use one browser profile per persona so
   sessions don't mix.
3. **Build the world** with `01-onboarding.md` on day 1, then follow the day plan.
4. **Play each scenario as the persona in its `As:` line.** Tick a box only when that exact result
   is seen. Work through the steps in order; a scenario may rely on data an earlier one created
   (named in `Needs:`).
5. **Log what you find** (below), then commit the ticks on the PR branch.

Super-admin checks use the developer's real account. Test data stays out of the real OpsClear org:
personas never join it.

### Test world

All names are fictional. No real company or person is used. All data reads like real business data
(clients, projects, milestones, jobs, notes). Use "Tile bathroom at 14 Elm St", never "Test job 1".

**Brightline Renovations** (main org, becomes the demo org): a 20-person renovation and
maintenance company. Every add-on, bought through Paddle sandbox.

| Project | Settings |
|---------|----------|
| Residential Fit-outs | Completion approval gate **on**, all job sections visible |
| Office Refurbishments | Gate **off**, some job sections hidden |
| Spring Maintenance 2026 | Archived during the run |

Milestones read like real phases ("Kitchen phase", "Handover"). Clients are fictional customers.

**Pine & Pixel Studio** (second org): a small design agency with **no add-ons**, owned by Petra. Shows that gated
features are hidden or blocked. Deleted after the run. The app allows one org per user, so no
persona belongs to both orgs.

**Personas**

| Persona | Brightline | Pine & Pixel | Notes |
|---------|-----------|--------------|-------|
| Ana | Org owner; owner of Residential Fit-outs | none | |
| Nikola | Org admin; owner of Office Refurbishments, admin of Residential | none | |
| Ivana | Member; admin of Office Refurbishments, member of Residential | none | |
| Luka | Member; member of both projects | none | Uses the Serbian UI |
| Sara | Member; member of Residential | none | Checks that she can't create or join a second org |
| Petra | none | Org owner | Owns Pine & Pixel Studio; checks that no data leaks between orgs |

### Day plan

Day boundaries are UTC. Deadline alerts run daily at 06:00 UTC (07:00 CET in winter).

| Day | What happens |
|-----|--------------|
| 1 | `01-onboarding.md` builds the test world. Create jobs due on day 3 and day 4, and a job with no deadline. Create a daily and a weekly schedule. Start the single-sitting areas. |
| 2 | After 06:00 UTC: DEADLINE_SOON for jobs due day 3. Daily schedule created a job. Continue other areas. |
| 3 | DEADLINE_SOON for jobs due day 4. Complete one job due day 3 before its deadline day ends. |
| 4 | OVERDUE for open jobs due day 3, none for the completed one. No duplicate alerts. Schedules ran each day; pause one. |
| 5+ | Paused schedule created nothing. Remaining areas, then `15-cross-cutting.md`. |

A run spans roughly a working week. Single-sitting areas fit around the multi-day checks. A missed
schedule run is produced by pausing the daily schedule on day 4 and resuming it on day 7 after
07:00 UTC (`SCH-17` in `08-schedules.md`). The optional `SCH-20` stops the backend for more than an
hour. A normal deploy can't do it: schedules run at most once an hour, and a run is only missed
when the next one is also past.

## Scenario format

```md
# Approvals

Last run: 2026-11-25, Jovan
Guide: /guide/approvals

## APR-03 Member requests completion when the gate is on
**As:** Luka (member, Residential Fit-outs) · **Day:** 1 · **Needs:** NTF on, job in progress

1. Open "Tile bathroom at 14 Elm St", click Complete.
2. Add a reason, submit.

- [ ] Job stays In Progress with an "Awaiting approval" badge
- [ ] Ana gets APPROVAL_REQUESTED in the notification centre without refreshing
- [ ] Luka sees no Approve button on his own request — ❌ JOB-xxx

## Not manual
- Notification cleanup after 30/90 days: covered by `NotificationCleanupJobTest`.

## Findings
- JOB-xxx Bug: …
```

- **Scenario ID:** `<PREFIX>-<nn>`, stable once merged. Findings and later runs refer to it. Never
  renumber; retire a scenario by marking it removed, not by reusing its number.
- **Header:** `Last run: <date>, <who>` and, where one exists, `Guide:` with the matching user
  guide page.
- **As / Day / Needs:** the persona and role, the day of the run (see the day plan) and what must
  exist or be switched on first.
- **Steps** are numbered and say what to click or type, with the realistic data to use.
- **One checkbox per expected result**, so a failure points at one thing.
- **`## Not manual`:** things that can't be run by hand, each as `<thing>: covered by <test>`, so
  the file still covers 100% on paper. Examples: notification cleanup (30/90 days), Paddle webhook
  signature checks.
- **`## Findings`:** a list of jobs raised during the run, one line each.
- Scenario text is English only. The Serbian UI is covered by Luka's scenarios.

## Logging findings

| What you found | Where it goes |
|----------------|---------------|
| Defect | `Bug:` job in MIL-048 Bugs (PRJ-015) |
| Small UX fix | `UX:` job in MIL-049 UX improvements (PRJ-015), from the `UX improvement` template |
| Bigger idea | Future Consideration in PRJ-002 |
| The scenario itself is wrong | Fix the file in the same PR |
| Bug found outside a run | PRJ-001 MIL-001 |

A failed check stays **unticked** with the job ID inline (`— ❌ JOB-xxx`), and the job is also
listed under `## Findings`. `UX:` jobs follow [NAMING.md](../dev/process/NAMING.md#job-titles)
(branch `fix/<job>-<slug>`, commit `fix(frontend): …`) and `/implement` routes them to
`frontend-dev`. A UX fix that needs the backend is split into a `Backend:` job first.

## Re-running

The ticks in a file are the result of the last run. A new run happens in its own PR: reset every
box to `- [ ]`, update `Last run`, tick as you go, and clear the old findings. Git history keeps
each earlier run. The PR merges when every box is ticked or linked to a finding.

## Keeping it current

A PR that changes behaviour covered here updates the matching scenario file in the same PR. The PR
template has a QA line, the Backend feature and Frontend feature job templates have an `Update
docs/qa scenarios for changed behaviour` checkbox, and `pr-reviewer` raises a finding when a PR
touches a mapped route, page or controller and nothing under `docs/qa/` changed. It is a review
finding, not a CI failure.

## Area to routes map

Every frontend route in `frontend/src/router.tsx` and every controller in
`backend/src/main/java/com/opsclear/controller/` appears at least once. `pr-reviewer` uses this
map: a PR that changes a mapped route, page or controller without touching `docs/qa/` gets a
finding naming the area file. When you add a route or controller, add it here in the same PR.

### Frontend routes

| Route | Area file |
|-------|-----------|
| `/` (landing) | `01-onboarding.md` |
| `/features` | `01-onboarding.md` |
| `/onboarding` | `01-onboarding.md` |
| `/org/new` | `01-onboarding.md` |
| `/invite/:token` | `02-org-admin.md` |
| `/org/settings` | `02-org-admin.md` |
| `/org/members` | `02-org-admin.md` |
| `/org/invites` | `02-org-admin.md` |
| `/projects` | `03-projects.md` |
| `/projects/:projectFriendlyId` (redirect) | `03-projects.md` |
| `/projects/:projectFriendlyId/settings` | `03-projects.md` |
| `/projects/:projectFriendlyId/jobs` | `04-jobs.md` |
| `/projects/:projectFriendlyId/jobs/:jobFriendlyId` | `04-jobs.md`, `05-notes-history-relationships.md` |
| `/projects/:projectFriendlyId/approvals` | `06-approvals.md` |
| `/projects/:projectFriendlyId/milestones` | `07-planning.md` |
| `/projects/:projectFriendlyId/templates` | `07-planning.md` |
| `/projects/:projectFriendlyId/types` | `07-planning.md` |
| `/projects/:projectFriendlyId/schedules` | `08-schedules.md` |
| `/notifications` | `09-notifications.md` |
| `/projects/:projectFriendlyId/dashboard` | `10-dashboard-stats.md` |
| `/projects/:projectFriendlyId/stats` | `10-dashboard-stats.md` |
| `/overview` | `10-dashboard-stats.md` |
| `/settings` | `11-account.md` |
| `/feedback` | `14-feedback-admin.md` |
| `/admin` (redirect) | `14-feedback-admin.md` |
| `/admin/pricing` | `14-feedback-admin.md` |
| `/admin/feedback` | `14-feedback-admin.md` |
| `*` (unknown path redirects to `/projects`) | `15-cross-cutting.md` |
| `/design` | Not manual: developer-only design page |

Billing and API keys have no route of their own: they live inside `/org/settings` and `/settings`.
`12-billing.md` and `13-api-keys.md` cover those sections of the pages, so a change to either page
may touch more than one area file.

### Backend controllers

| Controller | Area file |
|------------|-----------|
| `OrganisationController` | `01-onboarding.md` |
| `UserController` | `01-onboarding.md` |
| `PublicSignupController` (public, no-auth early access sign-up and sign-up switch status) | `01-onboarding.md` |
| `OrganisationMemberController` | `02-org-admin.md` |
| `OrgInviteController` (incl. the public, no-auth `GET /api/invites/{token}`) | `02-org-admin.md` |
| `OrgSettingsController` | `02-org-admin.md` |
| `ProjectController` | `03-projects.md` |
| `ProjectMemberController` | `03-projects.md` |
| `ProjectLinkController` | `03-projects.md` |
| `ProjectDirectoryController` | `03-projects.md` |
| `JobController` | `04-jobs.md` |
| `BlockReasonController` | `04-jobs.md` |
| `JobLinkController` | `04-jobs.md` |
| `NoteController` | `05-notes-history-relationships.md` |
| `JobHistoryController` | `05-notes-history-relationships.md` |
| `JobRelationshipController` | `05-notes-history-relationships.md` |
| `ApprovalController` | `06-approvals.md` |
| `MilestoneController` | `07-planning.md` |
| `JobTemplateController` | `07-planning.md` |
| `OrgTemplateController` | `07-planning.md` |
| `JobTypeController` | `07-planning.md` |
| `RecurringScheduleController` | `08-schedules.md` |
| `SchedulePreviewController` | `08-schedules.md` |
| `ScheduleMissedRunController` | `08-schedules.md` (missed runs: see its `Not manual` section) |
| `NotificationController` | `09-notifications.md` |
| `NotificationSettingsController` | `09-notifications.md` |
| `DashboardController` | `10-dashboard-stats.md` |
| `StatsController` | `10-dashboard-stats.md` |
| `UserPreferencesController` | `11-account.md` |
| `SubscriptionController` | `12-billing.md` |
| `SubscriptionCatalogController` | `12-billing.md` |
| `PaddleSubscriptionController` | `12-billing.md` |
| `CreditController` | `12-billing.md` |
| `ApiKeyController` | `13-api-keys.md` |
| `FeedbackController` | `14-feedback-admin.md` |
| `SuperAdminFeedbackController` | `14-feedback-admin.md` |
| `SuperAdminPricingController` | `14-feedback-admin.md` |
| `SuperAdminCreditController` | `14-feedback-admin.md` |
| `SuperAdminSignupController` | `14-feedback-admin.md` |
| `PaddleWebhookController` | Not manual: called by Paddle, covered by its integration tests (`12-billing.md` checks the effects) |
| `HealthController` | Not manual: infra health check |
