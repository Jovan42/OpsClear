# Development Process

This document describes how OpsClear features move from idea to shipped code.

---

## Overview

Work flows through five stages:

```
Future Considerations → Phase Design → Milestones → ADR → Jobs
```

---

## Stage 1: Future Considerations

All product ideas live as jobs in the **Future Considerations project** (currently PRJ-002).

Each job represents one potential feature or improvement. The job description should capture:
- What the feature does and why it matters
- High-level approach (not detailed design — that comes in the ADR)
- Any known constraints or dependencies

Nothing in this project is scheduled or committed to. It is purely a backlog.

---

## Stage 2: Phase Selection and Product Design

When it is time to start new work, one or more related items are picked from the Future Considerations backlog and grouped into a **phase**.

A phase has a clear product identity — it answers a single question or adds a coherent set of capabilities. Examples: "Organisation layer as top-level tenant boundary", "Human-friendly IDs for projects, jobs, and milestones", "Pricing model and monetization".

At this stage the discussion is product-level:
- What exactly does the phase add?
- How does it work from the user's perspective?
- What are the boundaries — what is in scope and what is explicitly deferred?
- What are the key decisions that need to be made?

No code, no tickets, no project yet — just a shared understanding.

---

## Stage 3: New Project and Milestones

Once the phase is well understood, a **new project** is created for it.

The project is then broken into **milestones**. Each milestone must satisfy this test:

> When this milestone is done, a complete, independently shippable feature has been added to the product.

Good milestone boundaries follow natural "layers" of the feature — for example:
- DB foundation → backend service + API → frontend screens

Bad milestone boundaries are arbitrary slices of one layer (e.g. "backend part 1", "backend part 2").

Milestones are named after what they deliver, not what they do (e.g. "Organisation CRUD" not "Backend endpoints").

---

## Stage 4: ADR per Milestone

The **first job of every milestone** is writing its Architecture Decision Record.

The ADR uses the Future Considerations job description as its primary input — that description was written at the right level of abstraction for this purpose.

The ADR captures:
- **Context** — why this decision is needed
- **Decision** — what exactly will be built and how it will work
- **Data model** — tables, columns, relationships (if applicable)
- **API shape** — endpoints, request/response structure (if applicable)
- **UI behaviour** — screens, interactions (if applicable)
- **Alternatives considered** — what was rejected and why
- **Consequences** — trade-offs, future implications

The ADR is reviewed before any implementation starts. It may be updated during implementation if something does not work as expected, but the goal is to catch design problems before coding begins.

ADRs live in `docs/dev/decisions/` and are numbered sequentially (`0001`, `0002`, …).

---

## Stage 5: Job Breakdown

Once the ADR is approved, the milestone's remaining work is broken into **jobs**.

One job = one pull request (or very close to it). Jobs should be:
- Small enough to review in one sitting
- Ordered so earlier jobs do not block later ones unnecessarily
- Named with a number prefix so order is clear: `01 — DB migration`, `02 — Backend service`, `03 — Frontend screens`

The job description is written from the ADR — it repeats the relevant slice of the ADR with enough detail for implementation, including the specific DB schema, endpoint signatures, or component structure that applies to that job.

---

## Summary

| Stage | Output |
|-------|--------|
| Future Considerations | Backlog of ideas as jobs in PRJ-002 |
| Phase design | Shared product understanding, no artifacts |
| Project + milestones | New project with milestone structure |
| ADR | One ADR job per milestone, reviewed before coding |
| Job breakdown | Ordered, PR-sized jobs derived from the ADR |
