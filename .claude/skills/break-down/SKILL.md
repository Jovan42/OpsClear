---
name: break-down
description: After an ADR merges, propose the milestone's implementation jobs (one layer each, templates, BLOCKED_BY, chained deadlines) and create them in OpsClear after the user approves.
argument-hint: "JOB-123"
disable-model-invocation: true
---

# /break-down

Split a milestone into implementation jobs (WORKFLOW.md §4).

**Input:** `$ARGUMENTS`: the milestone's ADR job.

**Read first:** [WORKFLOW.md](../../../docs/dev/process/WORKFLOW.md) §4,
[JOBS.md](../../../docs/dev/process/JOBS.md) (titles, splitting, dependencies), and the done
criteria in [BACKEND.md](../../../docs/dev/process/BACKEND.md),
[FRONTEND.md](../../../docs/dev/process/FRONTEND.md) and [E2E.md](../../../docs/dev/process/E2E.md).

## Steps

1. **Check the ADR has merged.** `scripts/opsclear job get <ID>`: its PR link should be labelled
   "(merged)", or check `gh pr view <N> --json state`. `git pull` on `main` and read the ADR file,
   especially Implementation order and anything that depends on other milestones.
2. **Check what exists.** `scripts/opsclear job list <PRJ> --status ALL --json`:
   - jobs already in this milestone: don't create duplicates; propose only what's missing
   - the latest deadline in the whole project: new deadlines continue from the day after it
   - jobs in other milestones this work depends on, or that depend on it (cross-milestone BLOCKED_BY)
3. **Propose** a table: title, template, deadline, blocked by, one-line scope.
   - One layer per job: `DB:` → `Backend:` → `Frontend:`. Number them when a layer has several
     (`Backend 1:`, `Backend 2:`). Tests live in the backend job; E2E coverage in the frontend job
     that adds the behaviour (E2E.md).
   - Templates: `DB migration`, `Backend feature`, `Frontend feature` (`scripts/opsclear template list <PRJ>`).
   - Deadlines chained one day apart in build order.
   - If you split differently from the ADR's Implementation order, say where and why.
4. **Iterate** until the user approves the table.
5. **Write each description** from its template (`scripts/opsclear template show <PRJ> "<template>"`),
   concretely:
   - Goal referencing the ADR and the ADR job
   - Scope per the template's subsections, plus endpoints / schema from the ADR
   - Out of scope, naming the sibling job that covers each excluded part
   - Definition of done: the template's items plus job-specific checks
6. **Create** in build order with `scripts/opsclear job create <PRJ> --template … --title … --milestone <MIL> --deadline … --assignee <user's email> --description-file …`.
   Check the first plan, then use `--yes` (the user approved the table). Record the new IDs.
7. **Link dependencies** with `scripts/opsclear job block <blocked> --by <blocker>`. After the
   **first** link, run `scripts/opsclear job get <blocked>` and confirm it shows "Blocked by
   <blocker>" before adding the rest. Jobs in other projects can't be linked; mention them in the
   description instead.
8. **Verify and report.** `scripts/opsclear job list <PRJ> --milestone <MIL> --status ALL`, plus
   `job get` on a couple of jobs to check relationships. Report the table with IDs and links.
   Suggest `/implement` on the first unblocked job.

## Stop conditions

- The ADR hasn't merged, or the milestone already has a full breakdown: say so and stop.
- The ADR doesn't cover a decision the breakdown needs: ask; don't decide it in a job description.
- Never combine layers in one job.
