---
name: finish
description: Close out an implementation, bug or chore job after its PR merges. Relabel the PR link "(merged)", check the Definition of done, complete the job, sync main and suggest the jobs it unblocked.
argument-hint: "JOB-123"
disable-model-invocation: true
---

# /finish

Bookkeeping after a job's PR merges. The closing step for `/implement` and `/ship`.

**Input:** `$ARGUMENTS`: the job's ID.

**Read first:** [AGENTS.md](../../../docs/dev/process/AGENTS.md) (permissions, `scripts/opsclear`).

## Steps

1. **Read the job:** `scripts/opsclear job get <ID>`. If the title starts with `ADR:`, stop and
   suggest `/finish-adr <ID>` instead (it also notes the source Future Consideration).
2. **Check the merge.** Find the job's PR link (`PR #N`). Run `gh pr view <N> --json state,mergedAt`
   as its own command. If it isn't `MERGED`, stop. Don't relabel anything first.
3. **Relabel the link:** `scripts/opsclear link merged <ID> <PR url>`. Its label becomes
   `PR #N (merged)`.
4. **Check the Definition of done.** Go through each item in the job description against the PR
   (diff, checks, description). List any item that isn't met or can't be verified from here (e.g.
   a manual check), and ask the user about it before completing.
5. **Complete the job:** `scripts/opsclear job status <ID> COMPLETED`. This prints the plan.
   Re-run with `--yes` after the user confirms, or directly if they've said to complete jobs once
   their PR is merged and every done item is met. A job still NEW must go to IN_PROGRESS first.
6. **Sync locally:** `git checkout main`, `git pull`, then `git branch -d <branch>` for the merged
   local branch (one command each). If the branch is checked out in a worktree, leave it and say so.
7. **Next:** for each job under **"Blocks"** in step 1, run `scripts/opsclear job get <JOB>` and
   check whether all its "Blocked by" jobs are now COMPLETED. List the unblocked ones and suggest
   `/implement <JOB> …`. Mention review notes from the PR that affect them (e.g. a `pr-reviewer`
   heads-up).

## Stop conditions

- PR not merged, or no PR link on the job: report and stop.
- Never delete links, notes or jobs, and never delete remote branches.
