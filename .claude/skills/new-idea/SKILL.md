---
name: new-idea
description: Capture a new product idea, bug, chore or tech-debt item, or refine an existing one, and file it in OpsClear (Future Considerations or Maintenance) after the user approves.
argument-hint: "[JOB-123]"
disable-model-invocation: true
---

# /new-idea

Turn an idea into a well-formed OpsClear job, in the right place, without duplicates.

**Input:** `$ARGUMENTS`: empty for a new idea, or a job ID to refine an existing one.

**Read first:** [WORKFLOW.md](../../../docs/dev/process/WORKFLOW.md) (feature vs maintenance track),
[JOBS.md](../../../docs/dev/process/JOBS.md) (titles),
[AGENTS.md](../../../docs/dev/process/AGENTS.md) (permissions, `scripts/opsclear`).

## Steps

1. **Start.** With a job ID: `scripts/opsclear job get <ID>` and `scripts/opsclear note list <ID>`,
   then summarise what's there and what's missing. Without one: ask the user to describe the idea.
2. **Discuss.** Cover what it is, why it matters, a rough approach, known constraints and open
   questions. Ask one or two questions at a time and give a recommendation with each. Stay at idea
   level: no schema or endpoint design (that's the ADR's job).
3. **Check facts in the code.** Search the code for what the idea touches, and correct assumptions
   that are wrong. Cite file paths in the description where useful.
4. **Check for duplicates.** `scripts/opsclear job list PRJ-002 --status ALL`,
   `scripts/opsclear job list PRJ-001 --status ALL`, and the open phase projects
   (`scripts/opsclear project list`). If something overlaps, propose updating that job instead.
5. **Route it** (WORKFLOW.md):

   | Kind | Project | Milestone | Template | Title |
   |---|---|---|---|---|
   | Feature idea | PRJ-002 Future Considerations | none | `Future Consideration` | free |
   | Bug | PRJ-001 Maintenance | MIL-001 Bugs | `Bug (backend)` / `Bug (frontend)` | `Bug: …` |
   | Chore | PRJ-001 Maintenance | MIL-002 Chores | `Chore` | `Chore: …` |
   | Tech debt | PRJ-001 Maintenance | MIL-003 Tech Debt | `Tech debt` | `Tech debt: …` |

   Get the sections with `scripts/opsclear template show <PRJ> "<template>"` and fill every one.
6. **Show the final job** (project, milestone, template, title, priority, assignee, full
   description) and ask for approval. Iterate until approved.
7. **Write it.** Save the description to a file in the scratchpad, then:
   - new: `scripts/opsclear job create <PRJ> --template "<template>" --title "<title>" [--milestone <MIL>] --assignee <user's email> --description-file <file>`.
     Check the printed plan matches what was approved, then re-run with `--yes`.
   - refine: `scripts/opsclear job update <ID> --description-file <file>` (and `--title` if it changed).
8. **Report** the job ID and its link: `https://46-101-205-77.sslip.io:8443/projects/<PRJ>/jobs/<JOB>`.

## Stop conditions

- Don't create anything before the user approves the final version.
- Don't create projects, milestones or ADR jobs (that's `/plan-phase`), and don't start implementing.
- If it's really part of an existing phase milestone, say so and suggest adding it to that
  milestone's ADR or breakdown instead.
