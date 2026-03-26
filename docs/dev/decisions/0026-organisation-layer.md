# ADR-0026: Organisation layer as top-level tenant boundary

**Status:** Proposed
**Date:** 2026-03-26
**Author:** Jovan Manojlovic
**Supersedes:** ADR-0004 (partially — org layer was deferred there as Alternative 2)

## Context

The Project is currently the top-level entity in OpsClear. Any authenticated
user can create a project and invite anyone by email. This works for MVP but
breaks down as soon as a real company starts using the product:

- No concept of "our company's people"
- No shared billing boundary
- No way to scope user search to colleagues only
- No natural boundary for cross-project approvals or templates

ADR-0004 explicitly deferred the org layer as "Alternative 2". Adding it now,
before the frontend is complete, is significantly cheaper than retrofitting later.

## Decision

Introduce an `Organisation` entity as the top-level tenant boundary above Projects.

### Data model

```
organisations
├── id (UUID)
├── name
├── slug (unique, 2–3 uppercase letters — used in URLs and as prefix namespace)
├── created_by (user_id FK)
├── created_at
└── deleted_at (soft delete)

organisation_members
├── organisation_id FK
├── user_id FK
├── org_role (OWNER | ADMIN | MEMBER)
└── joined_at
PRIMARY KEY (organisation_id, user_id)
```

- `users` gains `organisation_id` FK (nullable during migration, non-null after backfill)
- `projects` gains `organisation_id` FK
- `organisation_members` is a join table — the DB supports multi-org from day one

### Org-level roles

| Role | Permissions |
|------|-------------|
| OWNER | Billing, delete org, all ADMIN permissions |
| ADMIN | Invite members, remove members, create projects |
| MEMBER | Can be added to projects within the org |

### Org membership vs project membership

Org membership defines the **candidate pool** — who can be invited to projects.
Project membership remains a separate explicit layer. Being in an org does not
grant access to any project automatically.

### Onboarding flow

1. User registers via Keycloak (no change to auth).
2. After first login, if the user belongs to no org, they are prompted to create one.
3. Without belonging to an org, no project or job can be created or accessed.
4. The user who creates the org becomes its OWNER automatically.

### Invite flow

- OWNER or ADMIN can invite by email.
- Invited user **must already have an OpsClear account** (no pending registration).
- One active invite per email at a time. Resending cancels the previous token and issues a new one.
- Invite token expires after **7 days**.
- OWNER or ADMIN can revoke a pending invite at any time.
- Email delivery via **Resend** (interface to be defined; implementation can follow in a dedicated phase).

### Member removal

- Removing a member blocks their access immediately.
- Their project memberships are retained in the DB (not cascade-deleted).
- Their assigned jobs remain assigned — OWNER/ADMIN is responsible for reassigning manually.

### Single org (current scope)

Frontend and API enforce one active org per user. Multi-org (contractor scenario)
is explicitly deferred — the data model does not need to change when it is introduced,
only the org-switcher UI and context-resolution logic.

## Alternatives Considered

### Alternative 1: Keep Project as top-level entity

Continue with the current model and add billing/scoping hacks at the project level.

**Pros:**
- No migration required
- Simpler model

**Cons:**
- No natural billing boundary
- User search cannot be scoped to "colleagues"
- Cross-project features (approvals queue, templates) have no clean boundary
- Harder to retrofit later once production data accumulates

**Why rejected:** Kicks the problem down the road at increasing cost.

### Alternative 2: Workspace (Slack-style)

A "Workspace" concept with looser membership — anyone can create a workspace,
multiple workspaces per user from day one.

**Pros:**
- Familiar to users of Slack/Notion

**Cons:**
- Billing and membership scoping become more complex
- Over-engineered for the SME target market (5–50 employees)

**Why rejected:** OpsClear targets single-company teams, not cross-company collaboration.

## Consequences

### Positive

- Clean billing boundary at the org level
- User search scoped to org members when assigning jobs
- Foundation for org-scoped templates, API keys, and cross-project approvals
- URL namespace (`/acme/projects/...`) enabled by org slug

### Negative

- Significant migration: all existing projects and users must be assigned to an org
- Onboarding flow becomes one step longer (create or join org after registration)
- Every API endpoint gains implicit org context

### Neutral

- Keycloak unchanged — user UUID continues to match the `sub` claim
- Project membership layer is unchanged — org membership is additive

## Implementation Notes

- Implement as a dedicated backend phase before the org concept reaches the frontend
- Flyway migrations required for: `organisations`, `organisation_members`, `users.organisation_id`, `projects.organisation_id`
- Existing data backfill: assign all current users and projects to a default org via migration
- Invite token table needed: `organisation_invites` (token, email, org_id, invited_by, expires_at, accepted_at)
- Rollback plan: migrations are additive (new tables, nullable FKs first) — can be reversed before backfill step

## API Changes Checklist

- [ ] Update Postman collection (`api/postman/OpsClear.postman_collection.json`)
- [ ] Add example requests for new endpoints
- [ ] Update environment variables if needed
- [ ] Test the flow manually before marking complete

## References

- ADR-0004: Projects model (deferred org layer as Alternative 2)
- ADR-0025: API key authentication (org is the natural scope for API keys)
- ADR-0027: Human-friendly IDs (org slug is the URL namespace)
