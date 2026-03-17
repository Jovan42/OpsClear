# Development Process

This document describes how features go from idea to production at OpsClear.

---

## Overview

```
Product Project (backlog)
    ↓  phase planning — pick most important items
Phase Project (milestone per consideration)
    ↓  one ADR per milestone
Architecture Decision Record
    ↓  jobs created from ADR (tracked in OpsClear)
OpsClear Jobs (inside milestone)
    ↓  one PR per job
Pull Request → main → auto-deploy
```

---

## 1. Product Project — Backlog

The **Product project** is a living backlog of considerations, ideas, and improvements.

Each ticket is a **spike** — it exists to capture research before any code is written:

- What is the problem / need?
- What options exist?
- What are the trade-offs and constraints?
- Any findings from prototyping or research?

Tickets are added continuously. The backlog is only reviewed at **phase boundaries** — not mid-phase.

---

## 2. Phase Planning

At the end of each phase, pick the most important tickets from the Product project to form the next phase.

Rules:
- Don't start a new phase until the current one is fully done
- Pick only what is clearly most important — keep the phase focused
- Tickets moved to a phase should already have enough research done to write an ADR

Each consideration becomes a **milestone** in the new phase project. Tightly related considerations that must ship together can be grouped into a single milestone.

---

## 3. Architecture Decision Records (ADRs)

**One ADR per milestone**, written before any code.

The ADR is based on the research in the Product ticket. It documents:
- Context and problem
- Options considered
- Decision and rationale
- Consequences

ADRs live in `docs/dev/decisions/`. See `0000-template.md` for the format.

> Start coding only after the ADR is written and agreed on.

---

## 4. Jobs (OpsClear)

Once the ADR is written, create **jobs inside OpsClear** — one per logical unit of work. GitHub Issues are no longer used for task tracking.

Each job must:
- Reference the ADR it implements (in the description)
- Be assigned to the relevant milestone
- Have a clear, scoped title

---

## 5. Pull Requests

One PR per job. Every PR must:
- Reference the OpsClear job in the description
- Link the ADR
- Pass CI (tests + lint) before merging

See `CONTRIBUTING.md` for branch naming and commit message format.

---

## Definition of Done

- **Job**: code merged to main (auto-deployed)
- **Milestone**: all jobs merged + the milestone's manual testing job passes
- **Phase**: all milestones complete

### Manual Testing Job

Every milestone must have one **"Manual testing"** job created alongside the other jobs (not after). It contains a checklist of what to verify in production once all other jobs in the milestone are merged — written based on the ADR so nothing is missed.

---

## Hotfix Process

If something breaks in production mid-phase:

1. Create a **Bug job** in the Maintenance project
2. Branch off `main` — `fix/<description>`
3. Fix, PR, merge to main — auto-deployed
4. Continue the current phase normally

No special flow, no phase interruption — the hotfix is just a Bug job like any other.

---

## ADR Naming

ADRs follow the format: `XXXX-descriptive-title.md`

The number prefix (`0001`, `0002`, ...) is for **sorting only** — it has no semantic meaning. The title is what matters and should clearly describe the decision.

Examples:
- `0021-milestone-grouping-for-jobs.md`
- `0022-notification-delivery-strategy.md`

Increment the number by 1 from the last ADR in `docs/dev/decisions/`.

---

## Phase Naming

Phases use **descriptive names** tied to the theme of the work, not numbers.

Good examples: `jobs-core`, `dashboard`, `notifications`, `auth-ui`, `approvals`

This makes it easier to reference past work and understand what a phase contained at a glance.

---

## Phase Lifecycle Summary

| Step | Output |
|------|--------|
| Backlog grooming (Product project) | Researched consideration tickets |
| Phase planning | New project with milestones |
| ADR | `docs/dev/decisions/XXXX-name.md` |
| Job creation | Jobs in OpsClear inside milestone (incl. manual testing job) |
| Development | PRs → merged to main → auto-deployed |
| Manual testing | Verify milestone in production |
| Phase complete | Review Product project, start next phase |

---

## Maintenance Project

Ad-hoc work that doesn't belong to a feature phase lives in the **Maintenance** project in OpsClear. It has three permanent milestones:

| Milestone | What goes here |
|-----------|---------------|
| `Bugs` | Defects and incorrect behaviour found during testing or use |
| `Chores` | Config, dependencies, CI, tooling, docs, refactors with no behaviour change |
| `Tech Debt` | Larger structural improvements needed but not urgent enough for a phase |

**Rules:**
- Bugs are picked up as they are found — no planning needed
- Chores are done opportunistically alongside feature work
- Tech Debt items that grow large enough to need an ADR graduate to a feature phase
- When `Chores` gets too large to manage, split off a `Dev Experience` milestone for DX/tooling/docs
