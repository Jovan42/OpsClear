# Dev Guide: Working with Claude Code

How to do day-to-day work on OpsClear with the Claude Code skills and agents. One page; for
details see [AGENTS.md](AGENTS.md) (reference) and [WORKFLOW.md](WORKFLOW.md) (the process).

**The idea:** you decide, Claude does the typing. You approve plans and merge PRs. Claude writes
jobs, ADRs and code, opens PRs and keeps OpsClear up to date. Claude never merges, pushes to
`main` or completes a job without you.

## Setup (once)

- [ ] OpsClear API key in `~/.config/opsclear/api-key` (**Settings > API Keys**, `chmod 600`)
- [ ] `gh auth login`
- [ ] OrbStack running (`docker info` works)
- [ ] `JAVA_HOME` set to Homebrew `openjdk@21`
- [ ] `cd frontend && npm ci`

Step-by-step commands: [AGENTS.md § Setup](AGENTS.md#setup). Check it works:
`scripts/opsclear job get JOB-293`.

## The flow

| Step | You type | Claude does | You do |
|---|---|---|---|
| 1. Idea | `/new-idea` | Discusses it, checks for duplicates, files it (feature → PRJ-002, bug/chore → PRJ-001) | Answer questions, approve the job |
| 2. Phase | `/plan-phase` | Proposes a project from ideas, then creates milestones and `ADR:` jobs | Approve the plan |
| 3. ADR | `/write-adr JOB-x` | Asks product, then technical questions, writes the ADR, opens the PR | Decide, review, merge |
| 4. Close ADR | `/finish-adr JOB-x` | Marks the link merged, completes the ADR job | Confirm |
| 5. Break down | `/break-down JOB-x` | Proposes layered jobs (`DB:`, `Backend:`, `Frontend:`, `E2E:`) with blockers | Approve |
| 6. Build | `/implement JOB-a JOB-b` | Runs one agent per job in its own worktree, opens PRs, `pr-reviewer` comments | Wait for the report |
| 7. Review | — | — | Read the PR and the reviewer comment, merge |
| 8. Close | `/finish JOB-a` | Checks it's merged and the done list is met, completes the job, syncs `main`, says what's unblocked | Confirm anything unmet |

Bugs and chores skip steps 2–5: `/new-idea`, then `/implement`, review, `/finish`.

## Examples

```text
/new-idea                      # "users can't reset passwords from mobile"
/implement JOB-274             # one job
/implement JOB-275 JOB-276     # independent jobs run in parallel
/finish JOB-274                # after you merged its PR
```

## Doing it yourself

Prefer to code a change by hand, or with Claude in the main chat? Make the change, then
`/ship JOB-x`. It commits in the right format, pushes, opens the PR, links it and moves the job to
In Progress.
Close it with `/finish JOB-x` after merging.

## When something goes wrong

- **An agent stopped.** The report says why (job unclear, blocker not done, app bug found). Fix
  the cause, usually by updating the job or ADR, then run `/implement` again. Don't ask it to guess.
- **A job was skipped.** Its blocker isn't Completed, or the title has no layer prefix.
- **Checks fail on the PR.** Ask Claude in the main chat to fix it on that branch, then push.
- **Claude asks for permission.** Anything that writes to OpsClear or GitHub beyond the usual
  flow needs your OK ([AGENTS.md § Permissions](AGENTS.md#permissions)). That's on purpose.

## Rules to remember

- One job = one branch = one PR, one layer each ([JOBS.md](JOBS.md)).
- Nothing goes to `main` without a PR you merged.
- No AI attribution in commits or PRs.
