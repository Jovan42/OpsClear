# ADR-0054: Claude Code Agents and Skills for the Dev Process

**Status:** Proposed
**Date:** 2026-11-05
**Author:** Jovan Manojlovic

## Context

All OpsClear development runs through one general-purpose Claude Code session. The process in `docs/dev/process/` (WORKFLOW, JOBS, BACKEND, FRONTEND, E2E) is detailed, but each session re-reads and rediscovers it. Building ADR-0050 to ADR-0053 showed the cost: every OpsClear API call (create job, add PR link, relabel it "merged", change status, add a closing note) was hand-written, templates were skipped once, the job-breakdown prompt was rewritten per ADR, and conventions drifted (e.g. the `adr` commit scope isn't in WORKFLOW.md's list).

Promoted from JOB-269 (Future Consideration, PRJ-002) into PRJ-013 / MIL-038. This ADR is JOB-293.

### Facts that shape this design

- **Subagents can't talk to the user mid-run.** A Claude Code subagent works on its own and returns one final report. Interactive steps (ADR questions, approving a breakdown) must therefore run in the main session; subagents suit self-contained execution.
- **Claude Code reads project config from `.claude/`**: subagents from `.claude/agents/*.md` (frontmatter: name, description, tools, model), skills from `.claude/skills/<name>/SKILL.md` (invoked as `/<name>`), permissions from `.claude/settings.json`. Checked in, it's shared by every session on the repo.
- **The OpsClear API accepts friendly IDs in paths** (`/api/projects/PRJ-012/jobs/JOB-245`, via `FriendlyIdResolver`), but a job can only be fetched under its own project. There's no lookup by job friendly ID alone.
- **Templates** exist at org level (ADR, DB migration, Backend feature, Frontend feature) and per project (Future Consideration in PRJ-002; Bug (backend), Bug (frontend), Chore, Tech debt in PRJ-001).
- **Integration tests need Docker** (Testcontainers). Locally Docker comes from OrbStack, and Java 21 needs `JAVA_HOME` set. That's being documented in JOB-297.
- **The `main` ruleset doesn't currently protect `main`.** It lists `non_fast_forward`, `pull_request` and `required_status_checks`, but its branch-include list is empty; a force-push to `main` succeeded on 2026-11-04. Repo-side protection is what actually stops a misbehaving agent, so this is worth fixing (see Consequences).
- **Prerequisite:** JOB-297 (Maintenance) aligns the process docs first, because agents follow whatever the docs say.

## Decision

Check in a `.claude/` folder with **skills for interactive steps** (run in the main session, can ask the user) and **subagents for execution steps** (each in its own git worktree, reporting back once), a **`scripts/opsclear` CLI** that every skill and agent uses for OpsClear API work, and **`.claude/settings.json`** allow/deny rules. Process rules stay in `docs/dev/process/`; skills and agents reference those docs instead of copying them.

## Product decisions

### Skills (main session, interactive)

| Skill | What it does |
|---|---|
| `/plan-phase` | Reviews open Future Considerations, proposes a phase (project name, one idea per milestone), and after approval creates the project, milestones and `ADR:` jobs (ADR template), then closes the promoted ideas with a note. |
| `/write-adr JOB-123` | Reads the ADR job and code, asks the open questions (with recommendations), writes `docs/dev/decisions/NNNN-*.md` from the template, then commits, opens the PR and links it to the job. |
| `/break-down JOB-123` | After an ADR merges: proposes implementation jobs (templates, `<Layer>:` titles, one layer per job, BLOCKED_BY, chained deadlines), and creates them after approval. |
| `/implement JOB-123` | Checks the job's blockers are Completed, picks the subagent from the title prefix, runs it in a worktree, then runs `pr-reviewer` on the resulting PR and reports both. Accepts several job IDs; unblocked jobs run in parallel. |
| `/ship` | For work done in the main session: commit (CONTRIBUTING format), push, open the PR, link it to the job, move the job to In Progress. |
| `/finish-adr JOB-123` | After an ADR PR merges: relabels the job's PR link "(merged)", adds the closing note on the source Future Consideration, and (with confirmation) completes the ADR job. |

### Subagents (own worktree, report back once)

| Agent | Handles | Model |
|---|---|---|
| `db-migration` | `DB:` jobs: Flyway migration + jOOQ regeneration | Sonnet |
| `backend-dev` | `Backend:` jobs incl. unit + integration tests | Opus |
| `frontend-dev` | `Frontend:` jobs incl. manual test checklist + E2E | Opus |
| `e2e-dev` | `E2E:` jobs | Sonnet |
| `bug-fixer` | `Bug:` jobs: reproduce, fix, regression test | Opus |
| `chore-dev` | `Chore:` and `Tech debt:` jobs | Sonnet |
| `pr-reviewer` | Reviews a PR against the process and for correctness | Opus |

Every implementation agent follows the same loop:
1. Read the job (`scripts/opsclear job get`), its ADR, and the matching process doc.
2. Create `feature/JOB-123-…` / `fix/JOB-123-…` / `chore/…` from the latest `main` in its worktree.
3. Implement only that job's scope (one layer).
4. Run the relevant checks (`./gradlew test`, `npm run lint`, Vitest, Cypress where required).
5. Commit, push, open the PR (title = commit subject; body with summary, job + ADR references, test plan; no AI attribution).
6. Link the PR to the job and move the job to In Progress.
7. Report back: what changed, check results, PR link, open questions.

**Stop instead of guessing**: if blockers aren't Completed, the job's scope contradicts its ADR, a check fails in a way unrelated to the job, or a decision isn't covered by the ADR, the agent stops and reports back without opening a PR.

`pr-reviewer` posts **one** review comment on the PR, never approves, requests changes or merges. The comment has a process checklist (one layer, tests included, commit/PR format, job + ADR referenced, migration numbering, template followed) plus code findings. It also reports a summary to the session.

### What agents may do

| Area | Allowed without asking | Needs confirmation (skill shows a plan first) | Never |
|---|---|---|---|
| **Git / GitHub** | Branch, commit, push their feature branch, open a PR, comment on a PR | — | Push to `main`, force-push, merge, delete remote branches, rewrite history |
| **OpsClear (prod)** | Read anything; add/relabel PR links; move their own job to In Progress | Create, close or delete jobs; change deadlines; set Completed; add notes (immutable) | Delete projects/milestones, change members or org settings |

### Model choice

Tiered per agent in its frontmatter: Opus for work that needs judgement (backend, frontend, bug fixing, review), Sonnet for well-specified mechanical work (migrations, E2E, chores). Skills run in the main session's model.

## Technical design

### Files

```
.claude/
  settings.json
  skills/
    plan-phase/SKILL.md
    write-adr/SKILL.md
    break-down/SKILL.md
    implement/SKILL.md
    ship/SKILL.md
    finish-adr/SKILL.md
  agents/
    db-migration.md
    backend-dev.md
    frontend-dev.md
    e2e-dev.md
    bug-fixer.md
    chore-dev.md
    pr-reviewer.md
scripts/
  opsclear                 # CLI (Python 3, stdlib only)
  opsclear_test.py         # unit tests for parsing/plan output
docs/dev/process/
  AGENTS.md                # what each skill/agent does, how to run them, permissions
```

### Skill and agent content

- **Short and referential.** Each file states its purpose, inputs, the steps above, stop conditions and permissions, and links to the relevant `docs/dev/process/*.md` and templates. No copied rules, so updating a process doc updates every agent's behaviour.
- **Agent frontmatter**: `name`, `description` (what it handles, so `/implement` and Claude Code can route to it), `model` (`opus` / `sonnet`), and `tools` limited to what the job needs (e.g. `pr-reviewer` gets read tools, `gh` and `scripts/opsclear`, no file editing).
- **`/implement` routing**: by job title prefix (`DB:` → `db-migration`, `Backend:` → `backend-dev`, `Frontend:` → `frontend-dev`, `E2E:` → `e2e-dev`, `Bug:` → `bug-fixer`, `Chore:` / `Tech debt:` → `chore-dev`). It always launches agents with worktree isolation and runs independent (unblocked) jobs in parallel.
- **Local environment**: agents rely on the setup documented by JOB-297 (OrbStack Docker on PATH, `JAVA_HOME` for `openjdk@21`) so integration tests run.

### `scripts/opsclear` CLI

- Python 3, standard library only, executable. Config from env: `OPSCLEAR_API_KEY` (required), `OPSCLEAR_URL` (default `https://46-101-205-77.sslip.io:8443`). Never prints the key.
- Commands (friendly IDs everywhere):

```
opsclear job get JOB-123                 # job + project friendly ID, links, relationships
opsclear job list PRJ-012 [--milestone MIL-034] [--status ALL]
opsclear job create PRJ-012 --template "Backend feature" --title "Backend: …" \
                   --milestone MIL-034 --deadline 2026-11-12 --description-file f.md
opsclear job update JOB-123 --deadline 2026-11-13      # full-PUT safe: re-fetches and keeps other fields
opsclear job status JOB-123 IN_PROGRESS
opsclear job block JOB-124 --by JOB-123                 # BLOCKED_BY, direction handled once, correctly
opsclear link add JOB-123 <url> --label "PR #480"
opsclear link merged JOB-123 <url>                      # relabels "… (merged)"
opsclear note add JOB-123 --file note.md
opsclear template show PRJ-002 "Future Consideration"
opsclear project create / milestone create …
```

- **Job lookup without a project**: `job get JOB-123` finds the project by listing the org's projects and checking each (fine at SME scale). A direct `GET /api/jobs/{friendlyId}` endpoint can be added later if the GitHub sync (MIL-039) needs it.
- **Safety**: commands in the "needs confirmation" column print the planned change and exit unless `--yes` is passed. Skills show that plan to the user, then re-run with `--yes` after approval.
- **Full-PUT safety**: `job update` always re-fetches the job and sends every field back, changing only what was asked. It was easy to get wrong by hand.
- Unit tests cover argument parsing, template filling and plan output (no network). A `--dry-run` flag for every write command helps manual testing.

### `.claude/settings.json`

```json
{
  "permissions": {
    "allow": [
      "Bash(./gradlew test:*)", "Bash(./gradlew build:*)", "Bash(./gradlew checkstyleMain:*)",
      "Bash(npm run lint:*)", "Bash(npm run test:*)", "Bash(npm run build:*)", "Bash(npx cypress run:*)",
      "Bash(gh pr view:*)", "Bash(gh pr list:*)", "Bash(gh pr create:*)", "Bash(gh pr comment:*)", "Bash(gh pr diff:*)",
      "Bash(scripts/opsclear job get:*)", "Bash(scripts/opsclear job list:*)", "Bash(scripts/opsclear template show:*)",
      "Bash(scripts/opsclear link add:*)", "Bash(scripts/opsclear link merged:*)"
    ],
    "deny": [
      "Bash(git push --force:*)", "Bash(git push -f:*)", "Bash(git push --force-with-lease:*)",
      "Bash(git push origin main:*)", "Bash(git push origin HEAD:main:*)",
      "Bash(git push origin --delete:*)", "Bash(git push --delete:*)",
      "Bash(gh pr merge:*)", "Bash(git reset --hard origin/main:*)"
    ]
  }
}
```

- Deny rules are prefix matches. They catch the common commands, not every possible spelling (e.g. an unusual refspec). The real guarantee for `main` must come from GitHub, which is why fixing the ruleset matters.
- Personal overrides go in the untracked `.claude/settings.local.json`.

### Constraints & edge cases

- **API key**: `OPSCLEAR_API_KEY` must hold a working key in the shell that runs Claude Code. The value currently in `launchctl` is stale. `AGENTS.md` documents where to set it (e.g. `~/.zshrc`).
- **Parallel agents** each get their own worktree and branch, so they don't collide. Jobs linked by BLOCKED_BY are never run in parallel.
- **Gated projects (ADR-0052)**: agents never set Completed, so the completion gate doesn't interact with them.
- **Migration numbering races**: two parallel `DB:` jobs could pick the same `V0xx`. `/implement` runs at most one `db-migration` agent at a time, and `pr-reviewer` checks numbering against `main`.
- **Process doc changes** take effect immediately because agents read them at run time. Changing `AGENTS.md`/skills is a normal reviewed PR.

## Alternatives considered

### Subagents for every step, including ADR and planning
Rejected (decided). Subagents can't ask questions mid-run. Interactive steps would bounce back with questions and need re-running.

### Skills only, no subagents
Rejected (decided). Implementation output would fill the main conversation, and jobs couldn't run in parallel or in isolated worktrees.

### Fewer, broader agents (planner / implementer / reviewer)
Considered. A single implementer would carry backend, frontend and E2E conventions at once. Per-layer agents mirror the one-layer-per-job rule and keep each prompt focused. The cost is seven small files instead of three.

### Agents only commit; a human pushes and opens PRs
Rejected (decided). It adds a manual step to every job, while the risky operations (push to `main`, force-push, merge) are denied anyway.

### Raw API calls instead of a CLI
Rejected (decided). Every skill and agent would re-implement the same calls and repeat the same mistakes (full-PUT field loss, relationship direction, template lookup).

### Read-only OpsClear access for agents
Rejected (decided). Linking PRs and moving a job to In Progress are routine and safe. Requiring confirmation for them adds friction without real protection.

### Same model for every agent
Rejected (decided). Mechanical jobs don't need the most capable model, and per-agent choice costs one frontmatter line.

### Enforce rules with hooks now
Deferred. Automatic enforcement (format checks, layer checks) belongs to MIL-040 (CI process checks), where it protects humans and agents alike.

## Consequences

### Positive
- Each job type has a repeatable, documented path from job to PR, and `/implement` can run independent jobs in parallel.
- OpsClear bookkeeping (links, statuses, closing notes) stops being manual and error-prone.
- Process docs stay the single source of truth. Agents become a forcing function to keep them correct.
- `scripts/opsclear` is reusable by the GitHub sync (MIL-039) and by humans.

### Negative
- Seven agents and six skills to maintain alongside the process docs.
- Agents with push access raise the stakes of branch protection: **the `main` ruleset must actually target `main`** (include `~DEFAULT_BRANCH`), otherwise nothing stops a bad push. Add as a Maintenance chore or include in MIL-040.
- Worse instructions now cost more, because agents follow them consistently.

### Neutral
- No product, schema or API changes.
- Depends on JOB-297 (docs alignment) landing first. The skills reference WORKFLOW.md's branch naming and the corrected scope list.

## Implementation order

Tooling only: no DB, backend or frontend jobs. Chore-sized jobs in MIL-038:

1. **Chore: `scripts/opsclear` CLI + tests**: commands above, confirmation/`--yes`, dry-run, full-PUT safety, friendly-ID job lookup, unit tests.
2. **Chore: `.claude/settings.json` + `docs/dev/process/AGENTS.md`**: allow/deny rules, setup instructions (API key, Docker, Java), overview of skills and agents. *Blocked by 1.*
3. **Chore: interactive skills**: `/plan-phase`, `/write-adr`, `/break-down`, `/ship`, `/finish-adr`. *Blocked by 1.*
4. **Chore: implementation agents + `/implement`**: `db-migration`, `backend-dev`, `frontend-dev`, `e2e-dev`, `bug-fixer`, `chore-dev`, routing, worktrees, parallel runs. *Blocked by 1.*
5. **Chore: `pr-reviewer`**: process checklist + code review, single PR comment, wired into `/implement`. *Blocked by 4.*
6. **Pilot**: run `/break-down` on ADR-0052 or 0053 and `/implement` on one small job end to end; fix the instructions based on what goes wrong. *Blocked by 2–5.*

Separately (Maintenance): fix the `main` ruleset so it targets `main`.

## References

- `docs/dev/process/` (WORKFLOW, JOBS, BACKEND, FRONTEND, E2E), CONTRIBUTING.md
- ADR-0025: API key authentication (used by `scripts/opsclear`)
- ADR-0027: Human-friendly IDs (friendly IDs in API paths)
- ADR-0052: Gated completion (agents never set Completed)
- JOB-269 (Future Consideration, PRJ-002): original idea
- JOB-293 (PRJ-013 / MIL-038): this ADR's job
- JOB-297 (Maintenance): process docs alignment (prerequisite)
- JOB-294 (MIL-039 GitHub sync) and JOB-295 (MIL-040 CI checks): related milestones
