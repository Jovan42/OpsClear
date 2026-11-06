# ADR-0055: GitHub ↔ OpsClear Job Sync

**Status:** Proposed
**Date:** 2026-11-06
**Author:** Jovan

## Context

Every PR needs the same OpsClear bookkeeping: add the PR as a link on its job, move the job to In Progress, then after the merge relabel the link "(merged)" and complete the job. ADR-0054's skills and agents do most of this through `scripts/opsclear`, but only when someone runs them. A PR opened or merged outside those flows leaves the job out of date, which is the exact problem OpsClear exists to solve. Automating it from GitHub also dogfoods the public API (ADR-0025 API keys) end to end.

Promoted from JOB-291 (Future Consideration, PRJ-002) into PRJ-013 / MIL-039. This ADR is JOB-294.

### Facts from the existing system that shape this design

- **`scripts/opsclear` already does every call we need** (ADR-0054): `job get` finds a job by friendly ID alone by listing the org's projects and checking each one (`find_job`), `link add` skips a URL that's already linked, `link merged` relabels `<label> (merged)` within the 100-character label limit, and `job status` is a no-op when the job is already in the target status. It's Python 3 stdlib only and has unit tests (`scripts/opsclear_test.py`) that CI runs on every `scripts/**` change.
- **There's no lookup by job friendly ID alone in the API.** Jobs are only reachable under their project (`/api/projects/{p}/jobs/{j}`). `FriendlyIdResolver` resolves friendly IDs within the caller's organisation.
- **Legal transitions** (`JobService.validateTransition`): `NEW → IN_PROGRESS`, `IN_PROGRESS → COMPLETED`, `IN_PROGRESS ↔ BLOCKED`, `COMPLETED → IN_PROGRESS`. Completing a NEW or Blocked job therefore takes two calls. Only the assignee or a project Owner/Admin can change status, and only an Owner/Admin can reopen.
- **API keys act as their user** (`ApiKeyAuthFilter`). They have no scopes, so a key has exactly its user's permissions.
- **Gated completion (ADR-0052) is designed but not built.** In its design Owners/Admins complete jobs directly (their action is the sign-off). Only Members get the completion-approval path (`PATCH …/status` returns 202).
- **One job can have several PRs.** JOB-308 had PR #484 merged and then a follow-up PR (#485). Completing on every merge would close such jobs too early.
- **Every merge to `main` triggers a deploy** (`deploy.yml`), so a sync that runs on merge may hit prod while the backend restarts.
- **The PR template** (`.github/PULL_REQUEST_TEMPLATE.md`) has a `## Closes` section meant for GitHub issues (`Closes #`). We track work in OpsClear, not GitHub issues.
- **MIL-040 (CI process checks, JOB-295)** will validate branch names and commit formats. This milestone only syncs and enforces nothing.

## Decision

Add a GitHub Actions workflow, `.github/workflows/job-sync.yml`, that runs on pull request events against `main` and calls a new `scripts/opsclear pr sync` command. The command reads the event, finds the jobs the PR references, adds or relabels the PR link on each one, moves NEW jobs to In Progress, and on merge **completes only the jobs the PR body names with `Closes JOB-x`**. It authenticates with a dedicated API key on the maintainer's account, stored as a repo secret. It retries while OpsClear is unreachable, reports what it did in the run summary, and never fails the PR. No DB, backend or frontend changes.

## Product decisions

### Which jobs a PR references

A PR references the union of:
- `JOB-x` in the **branch name** (e.g. `feature/JOB-294-adr-github-job-sync`),
- `JOB-x` in the **PR title** (e.g. `docs(adr): add ADR-0055 … (JOB-294)`),
- `Closes JOB-x` and `Part of JOB-x` lines in the **PR body**.

Other mentions in the body (e.g. "see JOB-123") are ignored, so a job mentioned in passing is never linked. Text inside HTML comments (`<!-- … -->`, as in the PR template) is ignored too.

### What closes a job

- **`Closes JOB-x`** in the body means "merging this PR finishes the job". Several jobs can be listed (`Closes JOB-1, JOB-2`) or put on separate lines.
- **`Part of JOB-x`** means "this PR is some of the work". The job gets the link and moves to In Progress, but a merge doesn't complete it.
- A job referenced only by branch or title behaves like `Part of`.
- Our tooling writes `Closes JOB-x` **by default**, since one job = one PR is the norm: the PR template, `/ship`, `/write-adr` and every implementation agent. For a partial PR, write `Part of JOB-x` instead.
- The body is read **at merge time**, so adding a `Closes` line after opening the PR works.

### What happens on each PR event

| PR event | Link on each referenced job | Job status |
|---|---|---|
| **Opened** | Added: `PR #N: <PR title>`, cut to 100 chars. An existing link with the same URL keeps its label | NEW → In Progress. Other statuses untouched |
| **Edited** (title/body) | Added for newly referenced jobs. Jobs no longer referenced keep their link | NEW → In Progress for newly referenced jobs |
| **Reopened** | Added if missing, `(closed)` suffix removed | NEW → In Progress |
| **Merged**, job in `Closes` | Relabelled `<label> (merged)` (replacing `(closed)` if present) | Completed. NEW goes NEW → In Progress → Completed. **Blocked is unblocked and completed** (Blocked → In Progress → Completed): the merge is taken as proof the blocker is gone. Already Completed: no-op |
| **Merged**, job not in `Closes` | Relabelled `<label> (merged)` | NEW → In Progress. Otherwise untouched |
| **Closed without merge** | Relabelled `<label> (closed)` | Untouched. A person decides whether the work is dropped |

A job that work should normally happen on is already In Progress before its PR opens (agents and `/ship` do this). The sync only catches up when it isn't.

### Whose name is on the changes

- A **separate API key named `github-actions`** on the maintainer's account, stored as repo secret `OPSCLEAR_API_KEY`. It can be revoked on its own without touching the key Claude Code uses.
- Status history and links show the maintainer as the actor. The Actions run summary shows which changes came from the sync.
- The maintainer is Owner of the OpsClear projects, so the key can complete any job, including ones assigned to someone else.
- **Gated projects (ADR-0052):** because the key's user is an Owner, completion goes through directly, the same as an Owner clicking Complete. Merging a PR that says `Closes` is the sign-off. If the key's user is ever only a Member of a gated project, `PATCH …/status` returns 202 and the summary reports "completion requested" instead of "completed".

### When something goes wrong

- **OpsClear unreachable or 5xx** (e.g. prod restarting during the deploy a merge triggers): retry with backoff for about 3 minutes, then give up with a warning.
- **Job not found, 403, or an illegal transition**: warn, skip that job, carry on with the others.
- **No job referenced** (e.g. Dependabot PRs): a short notice that nothing was synced.
- The workflow **always finishes green** and is **never a required check**, so an OpsClear outage or a typo in a job ID never blocks a merge. Warnings show up as annotations on the run and in its summary.

### Manual steps stay, and become idempotent

- Implementation agents and `/ship` still add the link and move the job to In Progress right away. The CLI already skips duplicates, so the sync and the agents don't fight.
- `/finish-adr` and the "complete on merge" routine **check instead of do**: if the link is already relabelled or the job already Completed, they skip that step; if the sync didn't do it (outage, missing `Closes`), they still do it. Notes on source Future Considerations stay manual (they're immutable and need judgement).

## Technical design

### Database

No changes.

### API

No changes. The sync uses existing endpoints through `scripts/opsclear`:
- `GET /api/projects?status=ALL`, `GET /api/projects/{p}/jobs/{j}`: find the job (`find_job`)
- `POST /api/projects/{p}/jobs/{j}/links`, `PUT …/links/{linkId}`: add and relabel the link
- `PATCH /api/projects/{p}/jobs/{j}/status`: move the job

Finding a job costs one call per project until it's found (about 15 calls today), which is fine at our size. A direct `GET /api/jobs/{friendlyId}` can be added later if this gets slow or other API users need it.

### `scripts/opsclear pr sync`

```
opsclear pr sync --event "$GITHUB_EVENT_PATH" [--summary "$GITHUB_STEP_SUMMARY"] [--yes] [--dry-run]
opsclear pr sync --action closed --merged --number 486 --url https://github.com/…/pull/486 \
                 --branch feature/JOB-294-adr-github-job-sync --title "…" --body-file body.md --dry-run
```

- **Input**: the GitHub event JSON (`pull_request.number`, `html_url`, `title`, `body`, `head.ref`, `merged`, plus `action`), or the same fields as flags so it can be run and tested locally.
- **Parsing** (pure functions, unit tested):
  - strip `<!-- … -->` from the body;
  - refs: `\bJOB-\d+\b` (case-insensitive, normalised to upper case) in branch and title;
  - body lines matching `^\s*(Closes|Part of)\s+JOB-\d+(\s*,\s*JOB-\d+)*` (case-insensitive);
  - `closes` = jobs on `Closes` lines; `refs` = union of all of the above.
- **Plan, then apply**: it builds the full list of planned changes per job from the event table above and prints it. `--dry-run` sends nothing. Completing a job needs confirmation like every other CLI command, so the workflow passes `--yes`.
- **Labels**: new helpers next to `merged_label`: `closed_label` (adds ` (closed)`) and `base_label` (removes either suffix), all kept within 100 characters.
- **Reuse**: `find_job`, the link and status helpers and the existing `Transport` are reused, not duplicated. The command applies multi-step transitions in order (NEW → IN_PROGRESS → COMPLETED, BLOCKED → IN_PROGRESS → COMPLETED) and stops that job's chain on the first error.
- **Retries**: connection errors and 5xx are retried with backoff (10s, 20s, 40s, 60s, 60s, about 3 minutes total). 4xx is not retried. The delay schedule is injectable so tests don't sleep.
- **Output**: one line per change on stdout, `::warning::` lines for skipped jobs, and a Markdown table (job, change, result) appended to `--summary`.
- **Exit code**: `pr sync` exits 0 even when some jobs were skipped with warnings. It exits non-zero only for bad usage (e.g. a missing event file).
- **Secrets**: the key is never printed (existing CLI rule). GitHub also masks it in logs.

### `.github/workflows/job-sync.yml`

```yaml
name: Job sync
on:
  pull_request:
    branches: [main]
    types: [opened, reopened, edited, closed]
permissions:
  contents: read
concurrency:
  group: job-sync-${{ github.event.pull_request.number }}
  cancel-in-progress: false
jobs:
  sync:
    if: github.event.pull_request.head.repo.full_name == github.repository
    runs-on: ubuntu-latest
    timeout-minutes: 10
    continue-on-error: true
    steps:
      - uses: actions/checkout@v7
        with:
          ref: main
          sparse-checkout: scripts
      - uses: actions/setup-python@v7
        with:
          python-version: '3.12'
      - name: Sync PR to OpsClear jobs
        env:
          OPSCLEAR_API_KEY: ${{ secrets.OPSCLEAR_API_KEY }}
        run: python scripts/opsclear pr sync --event "$GITHUB_EVENT_PATH" --summary "$GITHUB_STEP_SUMMARY" --yes
```

- **Base-branch script**: it checks out `main`'s `scripts/`, not the PR's, so a PR can't change the sync logic that runs with the secret on that same PR.
- **No PR text in the shell**: title, body and branch are read from the event file, never interpolated into `run:`, which prevents script injection through a crafted PR title.
- **Fork PRs are skipped** (`if:`), since they don't get secrets anyway. PRs from branches in this repo work normally.
- **Missing secret**: `pr sync` prints a notice and exits 0.
- **Concurrency per PR** keeps an `edited` and a `closed` run from racing on the same job.
- `continue-on-error` is a backstop on top of `pr sync`'s own exit-0 rule.

### Process docs and tooling

- **PR template**: replace the GitHub-issue `## Closes` section with `Closes JOB-x` (and a comment explaining `Part of JOB-x` for partial PRs).
- **CONTRIBUTING.md / WORKFLOW.md**: document `Closes` / `Part of`, and (once the workflow is live) what the sync does on each event.
- **Skills and agents**: `/ship`, `/write-adr` and every implementation agent write `Closes JOB-x` (or `Part of JOB-x` for a partial PR) in the PR body. This ships first, before the sync exists (see Implementation order).
- **AGENTS.md**: the sync workflow, the `github-actions` key and secret, and that agent bookkeeping stays as an idempotent fallback.
- **`/finish-adr`** checks whether the link is already relabelled and the job already Completed before doing either.

### Constraints & edge cases

- **Same PR, several jobs**: each job is handled independently. One failing doesn't stop the others.
- **Job in `Closes` but not in branch/title**: still referenced and completed. `Closes` lines count as references.
- **Reference removed by an edit**: the link stays. Unlinking is a manual action.
- **Completed job referenced by a newly opened PR**: link added, status left Completed (reopening needs judgement, and Owner/Admin rights). The summary says so.
- **PR merged into a branch other than `main`**: no run (`branches: [main]`).
- **A job's PR merges while another PR on it is still open**: only `Closes` decides, so a follow-up PR should say `Part of` until the last one.
- **Duplicate events / re-runs**: every step is idempotent (link exists, label already suffixed, status already reached), so re-running a workflow changes nothing.
- **Unblocking on merge** writes two status-history entries (Blocked → In Progress → Completed) attributed to the key's user. The original block reason stays in history (ADR-0028).
- **Key permissions**: the key's user must be Owner/Admin (or assignee) on the projects it touches. A 403 is a warning, not a failure.
- **Same-repo branches can edit workflow files**, and a PR's own `job-sync.yml` changes take effect on that PR. That's acceptable for a repo where only the maintainer and their agents push. Fork PRs never see the secret.

## Alternatives considered

### Merge completes every referenced job
Rejected (decided). Jobs with follow-up PRs (e.g. JOB-308) would be completed by the first merge. An explicit `Closes` keyword is clear and matches GitHub's own convention.

### Complete on merge unless another open PR references the job
Considered. It needs no keyword, but it's implicit and needs a GitHub API query on every merge. Rejected in favour of `Closes`.

### Leave Blocked jobs alone on merge
Rejected (decided). A merged PR that says it closes the job is taken as proof the blocker is gone. Leaving the job Blocked would need a manual unblock-and-complete every time.

### Remove or revert on PR close without merge
Rejected (decided). Deleting the link loses history and reverting the status guesses at intent. The job keeps a `(closed)` link and a person decides.

### New `GET /api/jobs/{friendlyId}` endpoint
Deferred (decided). The CLI's project scan works at our size. An endpoint adds a Backend job for no user-visible gain today, and can be added later without changing the workflow.

### Dedicated bot user for the key
Rejected for now (decided). It gives a clearer audit trail, but needs a Keycloak account and an org seat, and must be added as Admin to every new project. A separate, revocable key on the maintainer's account is enough for V1.

### Fail the workflow when OpsClear is down or a job isn't found
Rejected (decided). A red run on a PR for a bookkeeping problem is noise, and if the check were ever made required it would block merges. Warnings in the run summary are enough.

### Remove the manual bookkeeping from agents and skills
Rejected (decided). It's cheap, immediate and idempotent, and it's the fallback when the sync fails.

### A third-party Action or a JavaScript action
Rejected. `scripts/opsclear` already has the API logic and tests. A second implementation would drift.

### `pull_request_target` instead of `pull_request`
Rejected. It's only needed to give fork PRs secrets, which we don't want. With `pull_request` plus a base-branch checkout the script that runs is the reviewed one.

## Consequences

### Positive
- Jobs stay in sync with PRs even when nobody runs a skill: linked when the PR opens, completed when it merges.
- `Closes` / `Part of` makes the intent of each PR explicit and visible in the PR itself.
- No backend, DB or frontend change. The logic lives in the CLI that's already tested and used daily.
- Dogfoods API keys from a real automated client.

### Negative
- Status changes from the sync are attributed to the maintainer, not to "GitHub". The run summary is the only place that tells them apart.
- Forgetting `Closes` on the last PR leaves the job In Progress (the manual fallback catches it).
- The key's user must stay Owner/Admin on every project the repo's jobs live in.
- Finding a job scans projects, about 15 API calls per job, until a lookup endpoint exists.

### Neutral
- The PR template, CONTRIBUTING.md, WORKFLOW.md, AGENTS.md, `/ship`, `/write-adr`, `/finish-adr` and the implementation agents change to write and respect `Closes JOB-x`.
- A new repo secret `OPSCLEAR_API_KEY` and a new OpsClear API key `github-actions` must be created by hand (documented in AGENTS.md).

## Implementation order

The `Closes` / `Part of` convention has no effect until the workflow exists, so it ships first: PRs opened meanwhile already carry the lines and work as soon as the sync goes live.

1. **Chore 1: `Closes` / `Part of` convention and tooling**: PR template `Closes JOB-x` section (with a comment on `Part of JOB-x` for partial PRs), CONTRIBUTING.md and WORKFLOW.md explain both keywords, and `/ship`, `/write-adr` and every implementation agent write `Closes JOB-x` (or `Part of JOB-x` for a partial PR) in the PR body. *Not blocked.*
2. **Chore 2: `scripts/opsclear pr sync`**: event/flag input, reference and `Closes`/`Part of` parsing (HTML comments stripped), per-event plan, multi-step transitions, `closed_label`/`base_label`, retries with injectable backoff, run-summary output, exit-0 rule. Unit tests for parsing, every row of the event table, label edge cases, retries and partial failures (no network). *Not blocked.*
3. **Chore 3: job-sync workflow**: `.github/workflows/job-sync.yml` as above, the `github-actions` API key and `OPSCLEAR_API_KEY` secret, verified on a test PR (open → In Progress, edit, close → `(closed)`, reopen, merge with `Closes` → Completed). *Blocked by Chore 2.*
4. **Chore 4: wire the sync into the dev process**: AGENTS.md documents the sync, the key and the secret, and that agent bookkeeping stays as an idempotent fallback; WORKFLOW.md describes what the sync does on each event; `/finish-adr` checks whether the link is already relabelled and the job already Completed before doing either. *Blocked by Chore 3* (it describes behaviour that only exists once the workflow is live).

## References

- ADR-0025: API keys
- ADR-0028: Job status change history
- ADR-0052: Gated completion requiring approval
- ADR-0054: Claude Code agents and skills (`scripts/opsclear`, `/finish-adr`)
- JOB-291 (Future Consideration, PRJ-002): original idea
- JOB-294 (PRJ-013 / MIL-039): this ADR's job
- JOB-295 (MIL-040): CI process checks
