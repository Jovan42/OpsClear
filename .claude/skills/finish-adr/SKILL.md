---
name: finish-adr
description: Close out an ADR job after its PR merges. Relabel the PR link "(merged)", add the closing note on the source Future Consideration, and complete the ADR job after confirmation.
argument-hint: "JOB-123"
disable-model-invocation: true
---

# /finish-adr

Bookkeeping after an ADR PR merges. The GitHub job sync (ADR-0055) usually relabels the link and
completes the job on merge already, so this skill **checks first and only does what's missing**.

**Input:** `$ARGUMENTS`: the ADR job's ID.

**Read first:** [AGENTS.md](../../../docs/dev/process/AGENTS.md) (permissions, `scripts/opsclear`).

## Steps

1. **Check the merge.** `scripts/opsclear job get <ID>` and find its ADR PR link
   (`PR #N: ADR-NNNN`). Run `gh pr view <N> --json state,mergedAt`. If it isn't `MERGED`, stop.
2. **Relabel the link** unless it already ends in `(merged)` (the sync did it; say so and skip):
   `scripts/opsclear link merged <ID> <PR url>`. Its label becomes
   `PR #N: ADR-NNNN (merged)`.
3. **Note the source idea.** If the description says `Promoted from JOB-x`, run
   `scripts/opsclear note list <JOB-x>`. If no note mentions this ADR yet, propose:
   `ADR-NNNN merged (PR #N). Implementation is tracked in <PRJ> / <MIL>.`
   Notes are immutable, so show it, and run `scripts/opsclear note add <JOB-x> --text '…' --yes`
   only after the user approves. If the idea isn't Completed yet, offer to close it too
   (`job status IN_PROGRESS`, then `COMPLETED --yes`).
4. **Complete the ADR job** unless it is already COMPLETED (skip and say so; if the sync didn't,
   e.g. no `Closes` line, do it): `scripts/opsclear job status <ID> COMPLETED`. This prints the plan.
   Re-run with `--yes` after the user confirms, or directly if they've said to complete jobs once
   their PR is merged.
5. **Sync locally:** `git checkout main && git pull`, then delete the merged local branch.
6. **Next:** suggest `/break-down <ID>`.

## Stop conditions

- PR not merged, or no ADR PR link on the job: report and stop.
- Never delete links, notes or jobs.
