---
name: ship
description: Ship work done in the main session. Commit in CONTRIBUTING format, push the branch, open the PR, link it to the OpsClear job and move the job to In Progress.
argument-hint: "[JOB-123]"
disable-model-invocation: true
---

# /ship

Turn the current changes into a PR that follows the process.

**Input:** `$ARGUMENTS`: the job ID. If empty, take it from the branch name (`…/JOB-123-…`).

**Read first:** [WORKFLOW.md](../../../docs/dev/process/WORKFLOW.md) (branches, commit scopes),
[CONTRIBUTING.md](../../../CONTRIBUTING.md) (commit and PR format), and the layer's process doc for
the checks to run.

## Steps

1. **Find the job.** `scripts/opsclear job get <ID>`. If there's no job ID anywhere, ask for it.
2. **Branch.** If on `main`, create the branch WORKFLOW.md prescribes (`feature/`, `fix/`, `e2e/`,
   `chore/` + job ID + short description) and move the changes onto it.
3. **Check the change.** `git status` and `git diff`. Make sure it stays within the job (one layer)
   and contains no secrets or stray files.
4. **Run the checks** for what changed: `cd backend && ./gradlew test`, `cd frontend && npm run lint`
   (and Vitest/Cypress per FRONTEND.md/E2E.md), `python3 scripts/opsclear_test.py` for `scripts/`.
   If a check fails, stop and report it; don't ship.
5. **Commit** as CONTRIBUTING.md says: `<type>(<scope>): <summary> (<JOB>)`, scope from WORKFLOW.md's
   fixed list, `-` bullets. No AI attribution: no `Co-Authored-By` trailer for Claude or any AI tool, and no "Generated with Claude Code" line.
6. **Push** with `git push -u origin <branch>`. Never push to `main` and never force-push.
7. **Open the PR** with `gh pr create`: title = commit subject. The body uses CONTRIBUTING.md's
   template: Summary, References (job and ADR), and a Test plan listing what you actually ran.
8. **Update OpsClear:**
   - `scripts/opsclear link add <JOB> <PR url> --label "PR #N"`
   - `scripts/opsclear job status <JOB> IN_PROGRESS` (skip if already In Progress)
9. **Report** the PR link and the check results.

## Stop conditions

- No job, failing checks, or changes spanning several layers/jobs: stop and ask.
- Never merge the PR or complete the job. After the user merges it, the job is completed separately.
