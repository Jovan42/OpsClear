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

Status is as recorded in each ADR. New ADRs: add a row here in the same PR.

| # | Title | Status | Date |
|---|-------|--------|------|
| 0001 | [Initial Tech Stack](0001-initial-tech-stack.md) | Implemented | 2026-02-03 |
| 0002 | [Authentication with Keycloak](0002-authentication.md) | Proposed | 2026-02-05 |
| 0003 | [CI/CD Pipeline with GitHub Actions](0003-ci-cd-pipeline.md) | Proposed | 2026-02-05 |
| 0004 | [Projects Model and Multi-Tenancy](0004-projects-model.md) | Proposed | 2026-02-05 |
| 0005 | [Roles and Permissions Model](0005-roles-and-permissions.md) | Proposed | 2026-02-06 |
| 0006 | [DTO Mapping Strategy — Manual `from()` over MapStruct](0006-dto-mapping-strategy.md) | Accepted | 2026-02-24 |
| 0007 | [Job Model and Status Flow](0007-job-model-and-status-flow.md) | Accepted | 2026-02-24 |
| 0008 | [Blocking Model](0008-blocking-model.md) | Accepted | 2026-02-27 |
| 0009 | [Notes Model](0009-notes-model.md) | Accepted | 2026-02-27 |
| 0010 | [Approval Model](0010-approval-model.md) | Accepted | 2026-03-01 |
| 0011 | [Frontend Architecture](0011-frontend-architecture.md) | Accepted | 2026-03-02 |
| 0012 | [Auth UI Approach](0012-auth-ui-approach.md) | Accepted | 2026-03-03 |
| 0013 | [Projects Screens](0013-projects-screens.md) | Accepted | 2026-03-03 |
| 0014 | [Jobs Screens](0014-jobs-screens.md) | Accepted | 2026-03-03 |
| 0015 | [Job Detail and Inline Actions](0015-job-detail-and-inline-actions.md) | Accepted | 2026-03-04 |
| 0016 | [Approvals Screens](0016-approvals-screens.md) | Accepted | 2026-03-09 |
| 0017 | [Dashboard](0017-dashboard.md) | Accepted | 2026-03-09 |
| 0018 | [User Settings — Storage Strategy and Scope](0018-user-settings.md) | Accepted | 2026-03-10 |
| 0019 | [Dark Mode — Implementation Strategy](0019-dark-mode.md) | Accepted | 2026-03-10 |
| 0020 | [Job Priority](0020-job-priority.md) | Accepted | 2026-03-11 |
| 0021 | [Milestone Grouping for Jobs](0021-milestone-grouping.md) | Accepted | 2026-03-16 |
| 0022 | [Job Relationships](0022-job-relationships.md) | Accepted | 2026-03-18 |
| 0023 | [User Preferences](0023-user-preferences.md) | Accepted | 2026-03-18 |
| 0024 | [Milestone Management UX](0024-milestone-management-ux.md) | Accepted | 2026-03-20 |
| 0025 | [API Key Authentication](0025-api-key-authentication.md) | Accepted | 2026-03-20 |
| 0026 | [Organisation layer as top-level tenant boundary](0026-organisation-layer.md) | Proposed | 2026-03-26 |
| 0027 | [Human-friendly IDs for projects, jobs, and milestones](0027-human-friendly-ids.md) | Proposed | 2026-03-26 |
| 0028 | [Job status change history](0028-job-status-change-history.md) | Accepted | 2026-04-06 |
| 0029 | [Public landing page](0029-public-landing-page.md) | Proposed | 2026-04-24 |
| 0030 | [Subscription data model and management](0030-subscription-data-model.md) | Proposed | 2026-05-07 |
| 0031 | [Feature gating full stack](0031-feature-gating.md) | Proposed | 2026-05-07 |
| 0032 | [Job Templates](0032-job-templates.md) | Accepted | 2026-05-08 |
| 0033 | [Org-Level Job Templates](0033-org-level-templates.md) | Accepted | 2026-05-10 |
| 0034 | [Recurring Job Scheduling](0034-recurring-jobs.md) | Accepted | 2026-05-11 |
| 0035 | [Job and Project Links](0035-job-and-project-links.md) | Proposed | 2026-07-20 |
| 0036 | [Custom Branded Keycloak Auth Pages](0036-keycloak-custom-auth-pages.md) | Proposed | 2026-07-21 |
| 0037 | [Localization (i18n) Support](0037-localization-i18n-support.md) | Proposed | 2026-07-21 |
| 0038 | [Mobile Nav Hamburger Drawer](0038-mobile-nav-hamburger-drawer.md) | Proposed | 2026-07-22 |
| 0039 | [Markdown Formatting Toolbar](0039-markdown-formatting-toolbar.md) | Proposed | 2026-07-22 |
| 0040 | [Interactive Live-Component Demos on /features](0040-interactive-feature-demos.md) | Proposed | 2026-07-22 |
| 0041 | [Polished Empty States Across the App](0041-polished-empty-states.md) | Proposed | 2026-07-23 |
| 0042 | [Job Types](0042-job-types.md) | Proposed | 2026-07-23 |
| 0043 | [Super Admin Console (Pricing Configuration + Customer Credits)](0043-super-admin-console.md) | Proposed | 2026-07-27 |
| 0044 | [Payment Gateway Integration (Paddle)](0044-paddle-payment-integration.md) | Proposed | 2026-08-10 |
| 0045 | [Org-Wide Project Directory for Owner/Admin](0045-org-wide-project-directory.md) | Proposed | 2026-08-14 |
| 0046 | [Cross-Project Pending Approval Queue](0046-cross-project-approval-queue.md) | Proposed | 2026-08-14 |
| 0047 | [Quick Project Switcher](0047-quick-project-switcher.md) | Proposed | 2026-08-14 |
| 0048 | [Manual Refresh Button](0048-manual-refresh-button.md) | Proposed | 2026-08-14 |
| 0049 | [E2E Test Coverage — Framework, Process, and Full Backfill](0049-e2e-test-coverage-plan.md) | Proposed | 2026-08-20 |
| 0050 | [Job Completion Velocity Stats](0050-job-completion-velocity-stats.md) | Proposed | 2026-11-04 |
| 0051 | [In-App Notification Centre](0051-in-app-notification-centre.md) | Proposed | 2026-11-04 |
| 0052 | [Gated Completion Requiring Approval](0052-gated-completion-approval.md) | Proposed | 2026-11-04 |
| 0053 | [Per-Project Job-Detail Section Visibility](0053-per-project-job-section-visibility.md) | Proposed | 2026-11-05 |
| 0054 | [Claude Code Agents and Skills for the Dev Process](0054-claude-code-agents-and-skills.md) | Proposed | 2026-11-05 |
| 0055 | [GitHub ↔ OpsClear Job Sync](0055-github-job-sync.md) | Proposed | 2026-11-06 |
| 0056 | [CI Process Checks](0056-ci-process-checks.md) | Proposed | 2026-11-08 |
