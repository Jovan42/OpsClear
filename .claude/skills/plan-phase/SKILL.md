---
name: plan-phase
description: Plan the next phase from open Future Considerations. Propose a project with one idea per milestone, then (after approval) create the project, milestones and ADR jobs and close the promoted ideas.
disable-model-invocation: true
---

# /plan-phase

Group backlog ideas into a phase project, as described in
[WORKFLOW.md](../../../docs/dev/process/WORKFLOW.md) §1–3.

**Read first:** WORKFLOW.md, [JOBS.md](../../../docs/dev/process/JOBS.md),
[AGENTS.md](../../../docs/dev/process/AGENTS.md) (permissions, `scripts/opsclear`).

## Steps

1. **Collect.** `scripts/opsclear job list PRJ-002` (open Future Considerations), then
   `scripts/opsclear job get <ID>` for each candidate. `scripts/opsclear project list --all` shows
   earlier phases and naming.
2. **Propose** a phase, with reasons:
   - project name that says what the phase delivers, and a one-paragraph description
   - milestones: one Future Consideration each (two only if very closely related), in build order
   - one `ADR: <milestone name>` job per milestone, deadlines chained one day apart starting from
     the next working day
   - which ideas stay in the backlog and why
3. **Iterate** until the user approves. Then show the full list of commands you'll run.
4. **Create**, in this order. Each command prints a plan first. Run it with `--yes` only after the
   user approved this whole set:
   1. `scripts/opsclear project create --name "<name>" --description-file <file>`
   2. `scripts/opsclear milestone create <PRJ> --name "<milestone>"` for each milestone
   3. ADR jobs: `scripts/opsclear template show <PRJ> ADR`, then
      `scripts/opsclear job create <PRJ> --template ADR --title "<milestone name>" --milestone <MIL> --deadline <date> --assignee <user's email> --description-file <file>`.
      Start the description with `Promoted from JOB-x (Future Consideration, PRJ-002).` and carry
      the idea's what/why/approach/open questions into the template's sections.
   4. Close each promoted idea:
      - `scripts/opsclear note add <FC> --text 'Promoted to <PRJ> "<project>" as milestone <MIL> "<milestone>". ADR job: <JOB>.'`
      - `scripts/opsclear job status <FC> IN_PROGRESS`, then `scripts/opsclear job status <FC> COMPLETED`.
        NEW → COMPLETED isn't a legal transition.
5. **Verify.** `scripts/opsclear job list <PRJ> --status ALL` and `scripts/opsclear milestone list <PRJ>`.
   Report the project, milestones and ADR jobs with links. Suggest `/write-adr <first ADR job>`.

## Stop conditions

- Nothing is created before the user approves the whole plan.
- If a Future Consideration is too vague to become a milestone, suggest `/new-idea <ID>` first
  instead of promoting it.
- Never delete projects, milestones or jobs.
