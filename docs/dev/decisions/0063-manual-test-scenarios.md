# ADR-0063: Manual Test Scenarios

**Status:** Proposed
**Date:** 2026-11-18
**Author:** Jovan

## Context

Day to day, OpsClear is used to run its own development. That only exercises one role (project owner), in one org, with every add-on on. Customers will take paths nobody has walked: members without rights, orgs without add-ons, invite and billing states, and deadline and schedule behaviour that only shows up over several days. The automated E2E suite (ADR-0049, ADR-0057) protects regressions, but it can't judge whether a screen makes sense, and it doesn't run on prod over real calendar days.

This ADR defines a full manual test pass through the UI on prod, played as several users with different org and project roles, across orgs and projects with different settings, over several days. It leaves a set of scenario files behind that are reused as a regression checklist and kept up to date with every feature.

Promoted from JOB-364 (Future Consideration, PRJ-002) into PRJ-015 "Launch QA: Customer Path Testing" / MIL-047 Test scenarios. This ADR is JOB-365.

### Facts from the existing system that shape this design

- **App surface:** 30 routes in `frontend/src/router.tsx` and 37 controllers in `backend/src/main/java/com/opsclear/controller/`. These are the checklist of screens and actions to cover.
- **Add-ons** (`AddonCode`): DASHBOARD, APPROVALS, NOTES, JOB_STATUS_HISTORY, MILESTONES, JOB_RELATIONSHIPS, API_KEYS, JOB_TEMPLATES, RECURRING_SCHEDULING, JOB_LINKS, JOB_TYPES. Each one is gated in the UI and the API (`@RequiresAddon`), so an org without add-ons has to be part of the matrix.
- **Notification types** (`NotificationType`): 16 types, from APPROVAL_REQUESTED to SUBSCRIPTION_PAST_DUE. Each needs at least one scenario that triggers it. Notifications are **in-app only**: `EmailService` only sends invite emails.
- **Deadline alerts** (`DeadlineAlertJob`) run daily at 06:00 UTC. They send DEADLINE_SOON for open jobs due tomorrow and OVERDUE for open jobs whose deadline day has passed (`DeadlineRules`), each once per job and kind (`alertOnce`). Testing them takes 3–4 calendar days.
- **Notification cleanup** (`NotificationCleanupJob`) deletes read notifications after 30 days and unread ones after 90. It can't be tested in a run of a few days.
- **Missed runs** (`SchedulerPoller`, every minute) are only recorded when a scheduled run time has already passed when the poller sees it, which in practice means the app was down, for example during a deploy. You can't trigger one from the UI.
- **Billing:** Paddle runs in sandbox on prod. A super-admin can also grant credits (`POST /credits/grant`), and orgs can be deleted (`DELETE /organisations/{id}`).
- **Email verification** is live on prod (ADR-0059), so every test user needs a real inbox.
- **The app is bilingual** (en/sr, ADR-0037), with the language stored in user preferences.
- **Job titles aren't checked by CI.** `scripts/opsclear pr check` checks the PR title type and scope, the branch and the body. `/implement` routes by job title prefix (`.claude/skills/implement/SKILL.md`, `^(DB|Backend|Frontend|E2E|Bug|Chore|Tech debt)( \d+)?:`).
- **Upkeep precedent:** the user guide (ADR-0062) keeps itself current with a coverage map, a PR template checkbox and a `pr-reviewer` check. The QA files follow the same pattern.
- **PRJ-014 is still in progress.** Org ID prefixes (JOB-348/349), user preferences (JOB-350–354) and the user guide (JOB-356–362) change screens this pass covers.

## Decision

- **Scenario files in `docs/qa/`**, one markdown file per functional area (15 areas), plus `docs/qa/README.md` with the format, the test world, the day plan, the rules for logging findings and an area → routes map.
- **Each area is one `Chore:` job in MIL-047.** An agent or the developer writes the file and opens the PR. The developer runs it on prod and ticks the boxes on the PR branch. The PR merges when every box is ticked or linked to a finding.
- **Results are stored in the file:** ticked boxes plus a `Last run: <date>, <who>` line. The next run resets the ticks in its own PR, and git history keeps each run.
- **A realistic test world on prod:** two fictional companies, five users with plus-addressed emails, and projects with different settings. All data (clients, projects, milestones, jobs, notes) looks like real business data. After the run, the main org is cleaned up and kept as a demo org for potential customers.
- **Findings go to the project's own milestones:** defects to MIL-048 Bugs as `Bug:` jobs, and small UX fixes to MIL-049 UX improvements as `UX:` jobs using a new `UX improvement` template. Bigger ideas go to PRJ-002.
- **Upkeep is a process rule,** like the guide: a template checkbox, a PR template line and a `pr-reviewer` check using the area → routes map.

## Product decisions (settled in JOB-364 and JOB-365)

| Question | Decision |
|----------|----------|
| Deliverable | Markdown scenario files in `docs/qa/`, one per functional area, run on prod before merge. |
| Structure | MIL-047 Test scenarios (one job per area), MIL-048 Bugs, MIL-049 UX improvements. Only MIL-047 has an ADR. |
| File split | By functional area, not by user guide section. The guide is too coarse for role and permission checks. Files can link the matching guide pages. |
| Where results live | In the file: ticks plus `Last run`. The next run resets them in its PR. |
| Test world | Two orgs, five users, three projects in the main org (below). Realistic data everywhere. |
| What can't be run by hand | Listed in the file as `Not manual: covered by <test>`, so the file still covers 100% on paper. |
| Logging findings | Defect → `Bug:` in MIL-048. Small UX fix → `UX:` in MIL-049. Bigger idea → Future Consideration in PRJ-002. A wrong scenario → fix the file in the same PR. Bugs found later, outside a run, → PRJ-001 MIL-001. |
| UX jobs | Title prefix `UX:`, template `UX improvement`, routed to `frontend-dev`. |
| Billing | Paddle sandbox. Credits can be granted by the super-admin if needed. |
| Timing | Start now. Areas PRJ-014 changes run after the matching jobs ship. Multi-day areas start first. |
| Demo org | After the run, the main test org is tidied up and kept as a demo. The second org is deleted. |
| Upkeep | Kept up to date with every feature: a checkbox in the Backend feature and Frontend feature job templates, a PR template line, and a `pr-reviewer` check. |

### Areas and files

| # | File | ID prefix | Covers |
|---|------|-----------|--------|
| 01 | `01-onboarding.md` | `ONB` | Sign-up, email verification, onboarding, org creation; builds the test world |
| 02 | `02-org-admin.md` | `ORG` | Org members and roles, invites (email, Copy link, Resend, accept, expired), org settings, ID prefixes, org switch |
| 03 | `03-projects.md` | `PRJ` | Create, edit, archive, project settings, project members and roles, section visibility, project links, project directory |
| 04 | `04-jobs.md` | `JOB` | Create, edit, assign, statuses New → In Progress → Blocked → Completed, block reasons, delete, list filters and search, job links |
| 05 | `05-notes-history-relationships.md` | `NHR` | Notes (immutable), status history, job relationships |
| 06 | `06-approvals.md` | `APR` | Approval requests and decisions, the completion approval gate |
| 07 | `07-planning.md` | `PLN` | Milestones, job templates (project and org), job types |
| 08 | `08-schedules.md` | `SCH` | Recurring schedules: create, preview, runs, pause and resume, missed runs |
| 09 | `09-notifications.md` | `NTF` | Notification centre, real-time updates, notification settings, every notification type, deadline soon and overdue alerts |
| 10 | `10-dashboard-stats.md` | `DSH` | Project dashboard, overview, stats |
| 11 | `11-account.md` | `ACC` | User settings and preferences, language, user guide |
| 12 | `12-billing.md` | `BIL` | Subscription, Paddle checkout (sandbox), add-on gating, credits, past due |
| 13 | `13-api-keys.md` | `API` | API keys, rate limiting and the 429 toast |
| 14 | `14-feedback-admin.md` | `ADM` | Feedback, super-admin feedback triage, pricing, credits |
| 15 | `15-cross-cutting.md` | `XC` | Permissions per role across all areas, mobile layout, the 3-clicks rule, empty states, errors, en/sr |

### Test world

All names are fictional. No real company or person is used.

**Brightline Renovations** (main org, becomes the demo): a 20-person renovation and maintenance company. Every add-on, through Paddle sandbox.

| Project | Settings |
|---------|----------|
| Residential Fit-outs | Completion approval gate **on**, all job sections visible |
| Office Refurbishments | Gate **off**, some job sections hidden |
| Spring Maintenance 2026 | Archived during the run |

Clients, milestones (for example "Kitchen phase", "Handover"), jobs ("Tile bathroom at 14 Elm St") and notes read like real work.

**Pine & Pixel Studio** (second org): a small design agency with **no add-ons**. Shows that gated features are hidden or blocked. Deleted after the run.

**Users** (plus-addressed on one mailbox, separate browser profiles):

| Persona | Brightline | Pine & Pixel | Notes |
|---------|-----------|--------------|-------|
| Ana | Org owner; owner of Residential Fit-outs | — | |
| Nikola | Org admin; owner of Office Refurbishments, admin of Residential | — | |
| Ivana | Member; admin of Office Refurbishments, member of Residential | — | |
| Luka | Member; member of both projects | — | Uses the Serbian UI |
| Sara | Member; member of Residential | Org owner | Checks org switching and that no data leaks between orgs |

Super-admin checks use the developer's real account.

### Day plan

| Day | What happens |
|-----|--------------|
| 1 | `01-onboarding.md` builds the test world. Create jobs due on day 3 and day 4, and a job with no deadline. Create a daily and a weekly schedule. Start the single-sitting areas. |
| 2 | After 06:00 UTC: DEADLINE_SOON for jobs due day 3. Daily schedule created a job. Continue other areas. |
| 3 | DEADLINE_SOON for jobs due day 4. Complete one job due day 3 before its deadline day ends. |
| 4 | OVERDUE for open jobs due day 3, none for the completed one. No duplicate alerts (`alertOnce`). Schedules ran each day; pause one. |
| 5+ | Paused schedule created nothing. Remaining areas, then `15-cross-cutting.md`. |

A run spans roughly a working week. Single-sitting areas fit around the multi-day checks.

## Technical design

### Database, API, Backend, Frontend

None. This ADR changes docs, job templates and process only.

### `docs/qa/README.md`

- How to run: test world, personas, browser profiles, plus-addressed emails, the day plan.
- Scenario format (below) and how to log findings.
- **Area → routes map:** for each file, the frontend routes and controllers it covers. `pr-reviewer` uses it for the upkeep check.

### Scenario format

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

- One checkbox per expected result, so a failure points at one thing.
- A failed check stays unticked with the job ID inline. Each file ends with a Findings list.
- Scenario IDs are stable once merged, so findings and future runs can refer to them.

### UX improvement jobs

- **NAMING.md:** a `UX:` row in Job titles. Branch `fix/<job>-<slug>`, commit `fix(frontend): …`.
- **`/implement`:** add `UX` to the routing regex and route it to `frontend-dev`. A fix that needs the backend gets split into a `Backend:` job first, as usual.
- **Template `UX improvement`** (org template), sections:
  - Where (screen or URL, role)
  - What's confusing now (screenshot optional)
  - Proposed change
  - Found in (scenario ID)
  - Acceptance (checklist)

### Keeping it current (process)

- **Job templates:** a checkbox `Update docs/qa scenarios for changed behaviour` in the Backend feature and Frontend feature templates.
- **PR template:** a checkbox "QA: this PR doesn't change behaviour covered in `docs/qa/`, or the scenarios are updated."
- **`pr-reviewer`:** for backend and frontend PRs, check changed files against the area → routes map. If any match and no file under `docs/qa/` changed, raise a finding naming the area file. It's a finding, not a CI failure.
- **FRONTEND.md and BACKEND.md:** a short "QA scenarios" section pointing to the rule.

### Constraints & edge cases

- **Test data stays out of the real OpsClear org.** Personas never join it.
- **Paddle must still be in sandbox at run time.** `12-billing.md` checks this first and stops if it's live.
- **Deploys during the run** can restart the app. That's fine and is the only realistic way to see a missed run: an optional check with a per-minute schedule created just before a deploy.
- **PRJ-014 jobs that haven't shipped:** areas 02 and 11 wait for them, and 04 and 10 recheck the screens preferences change (density, due-soon, Mine filter, stats range) once JOB-353/354 land.
- **Time zone:** alerts fire at 06:00 UTC (07:00 CET in winter). Day boundaries in the day plan are UTC.
- **Personas can't be removed easily.** They stay as demo users after the run.
- **Scenario text is English only.** The Serbian UI is covered by Luka's scenarios, not by translated files.

## Alternatives considered

### One milestone per area (decided against)

Each milestone needs its own ADR, which would be 15 near-empty ADRs. One job per area in a single milestone gives the same tracking.

### Split files by user guide section (decided against)

The guide's five sections are too coarse for checks by role and permission, and they don't cover billing, API keys or super-admin.

### A separate run log per run (decided against)

`docs/qa/runs/<date>.md` keeps the scenario files clean, but it means two files to keep in sync. Ticks in the file plus git history are enough.

### Log all findings in PRJ-001 (decided against)

Keeping the run's findings in this project makes launch triage easier. PRJ-001 still gets bugs found outside a run.

### Placeholder test data, then a separate demo org (decided against)

Realistic data from the start means the test org becomes the demo with a small tidy-up instead of a second setup.

### Automated E2E only (decided against)

It complements this, doesn't replace it. It can't judge UX, and it doesn't run against prod over real days.

## Consequences

### Positive

- Every customer path is walked at least once before launch, with a written record.
- A reusable regression checklist for future releases.
- UX friction gets logged in one place, separate from defects.
- A realistic demo org for potential customers at the end.

### Negative

- Ongoing cost: scenario files must be updated with every feature.
- A run takes about a working week of calendar time.
- Test data lives on prod.

### Neutral

- A new `UX:` job prefix and template in the process docs.
- `pr-reviewer` gains a second upkeep check, next to the guide's.

## Implementation order

1. **Chore 1: QA framework and UX job type.**
   - `docs/qa/README.md`: how to run, test world, personas, day plan, scenario format, logging findings, area → routes map
   - NAMING.md `UX:` row, `/implement` routing to `frontend-dev`
   - the `UX improvement` org template in OpsClear

   No blockers.
2. **Chore 2: QA upkeep rule.** Checkbox in the Backend feature and Frontend feature templates, PR template line, `pr-reviewer` check, FRONTEND.md and BACKEND.md sections. *Blocked by Chore 1.*
3. **Chore 3: QA scenarios: onboarding** (`01`, builds the test world). *Blocked by Chore 1.*
4. **Chore 4: QA scenarios: notifications** (`09`, multi-day). *Blocked by Chore 3.*
5. **Chore 5: QA scenarios: recurring schedules** (`08`, multi-day). *Blocked by Chore 3.*
6. **Chore 6: QA scenarios: projects** (`03`). *Blocked by Chore 3.*
7. **Chore 7: QA scenarios: jobs** (`04`). *Blocked by Chore 3.*
8. **Chore 8: QA scenarios: notes, history and relationships** (`05`). *Blocked by Chore 3.*
9. **Chore 9: QA scenarios: approvals** (`06`). *Blocked by Chore 3.*
10. **Chore 10: QA scenarios: milestones, templates and types** (`07`). *Blocked by Chore 3.*
11. **Chore 11: QA scenarios: dashboard and stats** (`10`). *Blocked by Chore 3.*
12. **Chore 12: QA scenarios: billing and add-ons** (`12`). *Blocked by Chore 3.*
13. **Chore 13: QA scenarios: API keys and rate limiting** (`13`). *Blocked by Chore 3.*
14. **Chore 14: QA scenarios: feedback and super-admin** (`14`). *Blocked by Chore 3.*
15. **Chore 15: QA scenarios: org admin and ID prefixes** (`02`). *Blocked by Chore 3 and JOB-349.*
16. **Chore 16: QA scenarios: account, preferences and guide** (`11`). *Blocked by Chore 3, JOB-351 to JOB-354 and JOB-356 to JOB-361.*
17. **Chore 17: QA scenarios: cross-cutting** (`15`). *Blocked by Chores 4 to 16.*
18. **Chore 18: turn Brightline Renovations into the demo org.** Tidy data, delete leftovers, delete Pine & Pixel Studio. *Blocked by Chore 17.*

Each `QA scenarios` job writes one file and opens the PR. The developer runs it on prod, ticks the boxes on the branch and logs findings in MIL-048 and MIL-049, then the PR merges. Jobs 4 to 14 can run in parallel once Chore 3 is done.

## API Changes Checklist

No API changes.

## References

- JOB-364: source Future Consideration
- ADR-0037: Localization (en/sr)
- ADR-0049: E2E coverage plan (feature-area taxonomy)
- ADR-0051: In-app notification centre (types, retention)
- ADR-0052: Gated completion requiring approval
- ADR-0053: Per-project job section visibility
- ADR-0054: Claude Code agents and skills (`/implement`, `pr-reviewer`)
- ADR-0057: Frontend E2E code coverage
- ADR-0059: Email verification and transactional email
- ADR-0060: Configurable ID prefixes
- ADR-0061: Additional user preferences
- ADR-0062: User guide (upkeep pattern)
