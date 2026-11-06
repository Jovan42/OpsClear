---
name: write-adr
description: Write the ADR for an `ADR:` job. Discuss product then technical decisions with the user, write docs/dev/decisions/NNNN-*.md, then commit, open the PR and link it to the job.
argument-hint: "JOB-123"
disable-model-invocation: true
---

# /write-adr

Produce the milestone's Architecture Decision Record (WORKFLOW.md §3).

**Input:** `$ARGUMENTS`: the ADR job's ID.

**Read first:** [WORKFLOW.md](../../../docs/dev/process/WORKFLOW.md),
[CONTRIBUTING.md](../../../CONTRIBUTING.md),
[decisions/README.md](../../../docs/dev/decisions/README.md) and
[0000-template.md](../../../docs/dev/decisions/0000-template.md).
For section layout and depth, follow the most recent ADRs (e.g. 0052–0054).

## Steps

1. **Read the job.** `scripts/opsclear job get <ID>`. Its title must start with `ADR:`. If its
   description says `Promoted from JOB-x`, also read that idea (`job get` + `note list`).
2. **Gather facts.** Read the code and the earlier ADRs this touches. Write down facts that shape
   the design (what exists today, constraints, dependencies on other milestones' jobs).
3. **Product discussion first.** What exactly are we building, why, and how it behaves for the
   user. Ask the open questions in small batches, each with options and a recommendation. Record
   decisions as you go.
4. **Then technical design:** schema, endpoints, components, constraints and edge cases. Same
   question style.
5. **Write the ADR.**
   - Number: one higher than the highest in `docs/dev/decisions/`. File `NNNN-short-title.md`.
   - Header: `**Status:** Proposed`, today's date, `**Author:**` from `git config user.name`.
   - Sections as in recent ADRs: Context (with "Promoted from …" and "This ADR is JOB-x"), Decision,
     Product decisions, Technical design, Alternatives considered (mark which were decided against),
     Consequences, Implementation order (layered jobs, one layer each, per JOBS.md), References.
   - Add its row to the index in `docs/dev/decisions/README.md`.
6. **Review.** Summarise the decisions for the user and apply their feedback before committing.
7. **Ship.** From the latest `main`, on branch `feature/<JOB>-adr-<slug>`:
   - commit `docs(adr): add ADR-NNNN for <title> (<JOB>)` with `-` bullets (CONTRIBUTING.md)
   - `git push -u origin <branch>`, then `gh pr create` with a `Closes JOB-x` line, Summary, References (job, source idea)
     and a short Test plan (e.g. facts checked against code). No AI attribution: no `Co-Authored-By` trailer for Claude or any AI tool, and no "Generated with Claude Code" line.
   - `scripts/opsclear link add <JOB> <PR url> --label "PR #N: ADR-NNNN"`
   - `scripts/opsclear job status <JOB> IN_PROGRESS`
8. **Report** the PR link. After it merges: `/finish-adr <JOB>`, then `/break-down <JOB>`.

## Stop conditions

- The job isn't an `ADR:` job, or already has a merged ADR PR: say so and stop.
- Don't invent decisions the user hasn't made. Leave them as open questions and ask.
- Never push to `main`, force-push or merge (AGENTS.md permissions).
