# Architecture Decision Records (ADRs)

This directory contains Architecture Decision Records for OpsClear.

## What is an ADR?

An ADR documents a significant technical decision: the context, the decision itself, and its consequences. ADRs are **immutable** - once accepted, they don't change. If a decision is reversed, we create a new ADR.

## When to Write an ADR

Write an ADR before implementing:
- Database schema changes
- New dependencies or libraries
- API design decisions
- Security-related changes
- Infrastructure changes
- Significant refactoring

## ADR Statuses

| Status | Meaning |
|--------|---------|
| **Proposed** | Under discussion, not yet implemented |
| **Accepted** | Approved and ready to implement |
| **Implemented** | Code is in production |
| **Superseded** | Replaced by a newer ADR (link to it) |
| **Deprecated** | No longer relevant |

## File Naming

```
NNNN-short-title.md
```

Examples:
- `0001-use-postgresql-database.md`
- `0002-jwt-authentication.md`
- `0003-job-status-model.md`

## Template

See [0000-template.md](0000-template.md) for the ADR template.

## Index

| # | Title | Status | Date |
|---|-------|--------|------|
| 0001 | [Initial Tech Stack](0001-initial-tech-stack.md) | Implemented | 2026-02-03 |
