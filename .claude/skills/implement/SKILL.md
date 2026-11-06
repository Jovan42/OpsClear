---
name: implement
description: Implement one or more OpsClear jobs by running the matching implementation agent for each in its own worktree. Checks blockers, routes by title prefix, runs independent jobs in parallel and reports the PRs.
argument-hint: "JOB-123 [JOB-124 …]"
disable-model-invocation: true
---

# /implement

Hand implementation jobs to the per-layer agents in `.claude/agents/`
([AGENTS.md](../../../docs/dev/process/AGENTS.md#agents-implementation-own-worktree)).

**Input:** `$ARGUMENTS`: one or more job IDs.

## Steps

1. **Read each job:** `scripts/opsclear job get <JOB>` (its project is found automatically).
2. **Check it can start.** Skip it, with the reason, when:
   - its status is COMPLETED
   - any **"Blocked by"** job isn't COMPLETED (name the blocker and its status)
   - the title doesn't route (below), e.g. `ADR:` jobs, which need `/write-adr`
3. **Route by title prefix.** Match the title against
   `^(DB|Backend|Frontend|E2E|Bug|Chore|Tech debt)( \d+)?:` (numbered titles like `Backend 2:` count):

   | Prefix | Agent |
   |---|---|
   | `DB` | `db-migration` |
   | `Backend` | `backend-dev` |
   | `Frontend` | `frontend-dev` |
   | `E2E` | `e2e-dev` |
   | `Bug` | `bug-fixer` |
   | `Chore`, `Tech debt` | `chore-dev` |

4. **Plan the run** and show it in one line per job (job, agent, run now / waits / skipped):
   - jobs linked to each other by BLOCKED_BY never run at the same time. Run the blocker first;
     the other one waits for a later `/implement` after the blocker is merged and Completed.
   - at most **one `db-migration`** at a time (migration numbers and the local database are shared).
   - everything else runs in parallel.
5. **Launch** each job that can run with the Agent tool: `subagent_type` = the agent's name,
   `isolation: "worktree"` (also set in the agent files), in the background, all independent ones
   in the same message. Prompt:
   `Implement <JOB> (<PRJ>, <milestone>). Title: <title>. Follow your instructions and the implementation loop in docs/dev/process/AGENTS.md.`
   Add anything the user said about the job.

   Right after launching, move each launched job to In Progress (routine, no `--yes`), so OpsClear
   shows the work as started before the PR exists:
   `scripts/opsclear job status <JOB> IN_PROGRESS`. If an agent later stops without a PR, say in the
   report that the job is still In Progress so the user can move it back.
6. **Review.** When an agent opens a PR, run the `pr-reviewer` agent on it (prompt:
   `Review PR #<N> for <JOB>.`). It posts one comment on the PR and returns a summary.
7. **Report** a table: job, agent, outcome (PR link, or stopped with the reason), check results,
   reviewer verdict and blockers, open questions from the agent. Then list what to do next (review/merge PRs; jobs that were
   waiting).

## Stop conditions

- Never set a job to Completed, merge a PR, or push to `main`.
- If an agent stopped, don't retry it with guesses. Report its reason and let the user decide
  (often a job or ADR needs updating first).
