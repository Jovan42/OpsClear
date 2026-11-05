# Claude Code Agents and Skills

ADR-0054 (MIL-038) set up Claude Code to run the dev process in [WORKFLOW.md](WORKFLOW.md):
**skills** for the interactive steps, **subagents** for implementation, and a **`scripts/opsclear`
CLI** for all OpsClear API work. This doc explains what each one does, what they're allowed to do,
and how to set up a machine to use them.

> **Status (2026-11-05):** the CLI, `.claude/settings.json`, the interactive skills, `/implement`,
> the implementation agents and `pr-reviewer` exist.

**Process rules live in the other process docs, not here.** Skills and agents link to
[WORKFLOW.md](WORKFLOW.md), [JOBS.md](JOBS.md), [BACKEND.md](BACKEND.md), [FRONTEND.md](FRONTEND.md)
and [E2E.md](E2E.md) instead of copying them, and read them at run time. Changing a process doc
changes every agent's behaviour, so keep those docs correct.

---

## Skills (interactive, main session)

Skills run in your Claude Code session, so they can ask you questions. Invoke them as `/<name>`;
Claude doesn't start them on its own. They live in `.claude/skills/<name>/SKILL.md`.

| Skill | Use it when | What it does |
|---|---|---|
| `/new-idea [JOB-123]` | You have a new idea, bug or chore, or want to refine an existing idea | Discusses it, checks the backlog for duplicates and the code for facts, then routes it: features to Future Considerations (PRJ-002), bugs/chores/tech debt to Maintenance (PRJ-001). Creates or updates the job after you approve. |
| `/plan-phase` | Starting a new phase | Proposes a project from open Future Considerations (one idea per milestone). After approval, creates the project, milestones and `ADR:` jobs, and closes the promoted ideas. |
| `/write-adr JOB-123` | An `ADR:` job is next | Asks the open questions (with recommendations), writes `docs/dev/decisions/NNNN-*.md`, commits, opens the PR, links it to the job. |
| `/break-down JOB-123` | An ADR has merged | Proposes implementation jobs (templates, `<Layer>:` titles, one layer per job, BLOCKED_BY, deadlines) and creates them after approval. |
| `/implement JOB-123 [JOB-124 …]` | Implementation jobs are unblocked | Checks blockers are Completed, runs the matching agent in its own worktree, then runs `pr-reviewer` on the PR. Unblocked jobs run in parallel. |
| `/ship` | You did the work in the main session | Commits (CONTRIBUTING format), pushes, opens the PR, links it to the job, moves the job to In Progress. |
| `/finish-adr JOB-123` | An ADR PR has merged | Relabels the PR link "(merged)", adds the closing note on the source Future Consideration, and (after you confirm) completes the ADR job. |

## Agents (implementation, own worktree)

`/implement` picks the agent from the job title prefix. Each agent works in its own git worktree
and branch, can't ask questions mid-run, and reports back once.

| Agent | Handles | Model |
|---|---|---|
| `db-migration` | `DB:` jobs: Flyway migration + jOOQ regeneration | Sonnet |
| `backend-dev` | `Backend:` jobs, including unit and integration tests | Opus |
| `frontend-dev` | `Frontend:` jobs, including the manual test checklist and E2E | Opus |
| `e2e-dev` | `E2E:` jobs | Sonnet |
| `bug-fixer` | `Bug:` jobs: reproduce, fix, regression test | Opus |
| `chore-dev` | `Chore:` and `Tech debt:` jobs | Sonnet |
| `pr-reviewer` | Reviews a PR against the process and for correctness (one comment, never approves or merges) | Opus |

Agent files live in `.claude/agents/<name>.md`. `pr-reviewer` also works on its own: ask Claude to
run the `pr-reviewer` agent on any PR number.

### The implementation loop

Every implementation agent follows this loop. Its own file adds only the layer-specific parts.

1. **Read.** `scripts/opsclear job get <JOB>`, the ADR named in the job (`docs/dev/decisions/`),
   and the process doc for the layer: [BACKEND.md](BACKEND.md), [FRONTEND.md](FRONTEND.md),
   [E2E.md](E2E.md). Check every "Blocked by" job is COMPLETED.
2. **Branch.** The agent runs in its own git worktree. Start from the latest `main`:
   `git fetch origin && git checkout -b <branch> origin/main`, branch named per
   [WORKFLOW.md](WORKFLOW.md#branch-naming). A fresh worktree has no `frontend/node_modules`, so run
   `cd frontend && npm ci` before any frontend command.
3. **Implement** only this job's scope and layer. Anything the job lists as out of scope stays out.
4. **Check.** Backend: `cd backend && ./gradlew test checkstyleMain`. Frontend: `npm run lint` and
   `npm run type-check` (plus Cypress specs the job adds, see E2E.md). `scripts/`:
   `python3 scripts/opsclear_test.py`. All must pass.
5. **Commit, push, PR.** Commit per [CONTRIBUTING.md](../../../CONTRIBUTING.md#commit-messages)
   (`<type>(<scope>): <summary> (<JOB>)`), `git push -u origin <branch>`, `gh pr create` with
   Summary, References (job, ADR) and a Test plan listing what was actually run. No AI attribution: no `Co-Authored-By` trailer for Claude or any AI tool, and no "Generated with Claude Code" line.
6. **Update OpsClear.** `scripts/opsclear link add <JOB> <PR url> --label "PR #N"` and
   `scripts/opsclear job status <JOB> IN_PROGRESS`. Never set Completed.
7. **Report** (the agent's final message): what changed, check results, PR link, open questions.

**Stop instead of guessing.** No PR, report back instead, when:
- a blocker isn't COMPLETED
- the job contradicts its ADR, or needs a decision the ADR doesn't cover
- a check fails for a reason unrelated to the job
- the work would cross into another layer or job

`/implement` runs at most one `db-migration` agent at a time (migration numbers would collide, and
they share the local database) and never runs jobs linked by BLOCKED_BY in parallel.

---

## Permissions

| Area | Allowed without asking | Needs confirmation (skill shows the plan first) | Never |
|---|---|---|---|
| **Git / GitHub** | Branch, commit, push their feature branch, open a PR, comment on a PR | — | Push to `main`, force-push, merge, delete remote branches, rewrite history |
| **OpsClear (prod)** | Read anything; add/relabel PR links; move their own job to In Progress | Create, close or delete jobs; change deadlines; set Completed; add notes (immutable) | Delete projects/milestones, change members or org settings |

Agents never set a job to Completed. That's always you (or a skill, after you confirm).

### How it's enforced

- **`.claude/settings.json`** (checked in) holds shared `allow` rules, so routine commands (tests,
  lint, build, `gh pr view/create/comment`, read-only `scripts/opsclear` commands, PR links) don't
  prompt, and `deny` rules for force-push, pushing to `main`, deleting remote branches, `gh pr merge`
  and `git reset --hard origin/main`.
- **Deny rules are prefix matches.** They catch the usual spellings, not every possible one (e.g. an
  unusual refspec like `git push origin feature:main`). The real guarantee for `main` must come from
  the GitHub ruleset, which currently doesn't target `main` (fix tracked in Maintenance, PRJ-001).
- **`scripts/opsclear`** refuses confirmation-level changes unless `--yes` is passed: it prints the
  plan and exits with code 3. Skills show you that plan and re-run with `--yes` after you approve.
- Allow rules match the command as written, so call the CLI as `scripts/opsclear …` from the repo
  root (not `./scripts/opsclear` or `python3 scripts/opsclear`).
- **Personal overrides** go in `.claude/settings.local.json` (git-ignored), e.g. extra allow rules
  you're comfortable with on your machine. Don't loosen the shared deny rules there.

---

## Setup

On a fresh Mac, from the repo root:

1. **OpsClear API key.** In OpsClear, open **Settings > API Keys** and create a key (starts with
   `opck_`). Add it to `~/.zshrc`, then open a new terminal:
   ```bash
   export OPSCLEAR_API_KEY=opck_...
   # optional, defaults to prod:
   # export OPSCLEAR_URL=https://46-101-205-77.sslip.io:8443
   ```
   Use `~/.zshrc`, not `launchctl setenv`: the shell that starts Claude Code must have it. A Claude
   Code session started before you set it won't see it, so restart Claude Code. Check with
   `scripts/opsclear job get JOB-293`.
2. **GitHub CLI.** `brew install gh`, then `gh auth login` (HTTPS). Check with `gh pr list`.
3. **Docker** (integration tests use Testcontainers). Install OrbStack and start it
   (`open -a OrbStack`). Its installer usually links `docker` into `/usr/local/bin` and adds
   `~/.orbstack/shell/init.zsh` to `~/.zprofile`. Check with `docker info`. Only if `docker` isn't
   found, add to `~/.zshrc`:
   ```bash
   export PATH="$HOME/.orbstack/bin:$PATH"
   ```
4. **Java 21.** `brew install openjdk@21`. It isn't linked, so Gradle won't find it by itself. Add
   to `~/.zshrc`, replacing any older `JAVA_HOME` line:
   ```bash
   export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
   ```
   Check with `java -version` (in a new terminal), then `cd backend && ./gradlew test` (unit and
   integration tests, takes a few minutes).
5. **Frontend.** `cd frontend && npm ci`. Check with `npm run lint`.
6. **Python 3.9+** for `scripts/opsclear` (the macOS system `python3` is enough). Check with
   `python3 scripts/opsclear_test.py`.

Then start Claude Code from the repo root so it picks up `.claude/settings.json`.

---

## `scripts/opsclear` quick reference

Friendly IDs everywhere. `scripts/opsclear --help` and `scripts/opsclear <command> <action> --help`
have the details.

```bash
scripts/opsclear job get JOB-123                       # job + project, links, relationships
scripts/opsclear job list PRJ-013 --milestone MIL-038 --status ALL   # default: open jobs only
scripts/opsclear job create PRJ-013 --template "Backend feature" --title "org CRUD" \
    --milestone MIL-038 --deadline 2026-11-12 --assignee you@example.com --description-file f.md
scripts/opsclear job update JOB-123 --deadline 2026-11-13   # keeps every other field
scripts/opsclear job status JOB-123 IN_PROGRESS             # BLOCKED needs --reason
scripts/opsclear job block JOB-124 --by JOB-123             # JOB-124 is blocked by JOB-123
scripts/opsclear link add JOB-123 https://github.com/Jovan42/OpsClear/pull/480 --label "PR #480"
scripts/opsclear link merged JOB-123 https://github.com/Jovan42/OpsClear/pull/480
scripts/opsclear note list JOB-123
scripts/opsclear note add JOB-123 --file note.md
scripts/opsclear template list PRJ-002
scripts/opsclear template show PRJ-002 "Future Consideration"
scripts/opsclear project list [--all]
scripts/opsclear project create --name "…" --description-file f.md
scripts/opsclear milestone list PRJ-013
scripts/opsclear milestone create PRJ-013 --name "…" --deadline 2026-12-01
```

- **Needs `--yes`:** `job create`, `job status` to anything but IN_PROGRESS, `job update` that
  changes the deadline, `note add`, `project create`, `milestone create`. Without it they print the
  plan and exit 3.
- `--dry-run` on any command prints the requests a write would send and sends nothing. `--json` gives
  machine-readable output.
- `job create --template` adds the template's title prefix unless the title already has it, numbered
  or not (`Backend 2: …` stays as is).
- `job status` can't go NEW → COMPLETED (the API forbids it); move to IN_PROGRESS first.
- `job get` without `--project` searches your projects (active first). Pass `--project PRJ-013` to
  skip the search.
- Exit codes: 0 ok, 1 error, 2 usage error, 3 not applied (re-run with `--yes`).
