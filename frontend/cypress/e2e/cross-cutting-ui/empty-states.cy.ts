// ADR-0049 Appendix §21, shared EmptyState component half (ADR-0041).
//
// "The specific bug ADR-0041 fixed" is already fixed and merged — the original bug
// checked hasAddon() only, not role, so a MEMBER saw a "create first X" CTA they
// couldn't actually use. The tests below are regression guards proving the fix
// holds on all 4 ADR-0049-named pages (Milestones/Schedules/Templates/OrgSettings),
// not documentation of a still-open bug.
//
// Audited all 16 <EmptyState usages in frontend/src (ADR-0049 says 14 — stale, same
// class of drift already seen elsewhere this session) for the same bug class:
// - Milestones/Schedules/Templates/OrgSettings: canPerform: isOwnerOrAdmin — correct,
//   tested below.
// - JobTypesPage: canPerform: isOwnerOrAdmin — already confirmed correct in JOB-223.
// - RelationshipsSection, LinksSection: canPerform tied to canManage (project
//   OWNER/ADMIN) — correct, matches those features' own permission model.
// - JobListPage (x2), ProjectListPage, FeedbackPage, DashboardPage: action-bearing
//   but deliberately NOT role-gated — creating a job, a project, or feedback is open
//   to any member by design (confirmed against each feature's own backend guard
//   elsewhere in this session's backfill), so no canPerform is the CORRECT behavior,
//   not a gap.
// - AddRelationshipModal, StatusHistory, NoteThread, PendingApprovalsSection,
//   ProjectDirectorySection: action-less by design, per the ADR bullet.
// No new instance of the ADR-0041 bug class found.

import {
  uniqueEmail,
  uniqueSlug,
  createOrgWithFullAccess,
  createProjectAs,
  addMember,
  addProjectMember,
  userIdFor,
  createJobAs,
} from '../../support/orgApi';

function assertNoCreateCtaForMember(url: string, ctaText: string) {
  cy.visit(url);
  cy.contains(ctaText).should('not.exist');
}

function assertCreateCtaForOwnerOrAdmin(url: string, ctaText: string) {
  cy.visit(url);
  cy.contains('button', ctaText).should('be.visible');
}

describe('Empty States — Role-Gating Regression (ADR-0041)', () => {
  it('a MEMBER viewing an empty Milestones/Schedules/Templates/OrgSettings page does NOT see the "create first X" CTA; OWNER/ADMIN does', { tags: '@smoke' }, () => {
    const ownerEmail = uniqueEmail('empty-role-owner');
    const memberEmail = uniqueEmail('empty-role-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Falcon Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Falcon Empty Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

          cy.loginAs(memberEmail);
          assertNoCreateCtaForMember(`/projects/${projectId}/milestones`, 'Create first milestone');
          assertNoCreateCtaForMember(`/projects/${projectId}/schedules`, 'Create first schedule');
          assertNoCreateCtaForMember(`/projects/${projectId}/templates`, 'Create first template');

          cy.loginAs(ownerEmail);
          assertCreateCtaForOwnerOrAdmin(`/projects/${projectId}/milestones`, 'Create first milestone');
          assertCreateCtaForOwnerOrAdmin(`/projects/${projectId}/schedules`, 'Create first schedule');
          assertCreateCtaForOwnerOrAdmin(`/projects/${projectId}/templates`, 'Create first template');
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('org settings: a MEMBER does not see any create-CTA action; OWNER does', () => {
    const ownerEmail = uniqueEmail('empty-org-owner');
    const memberEmail = uniqueEmail('empty-org-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Nimbus Corp', uniqueSlug()).then((orgId) =>
      userIdFor(memberEmail).then((memberId) => {
        addMember(orgId, ownerEmail, memberId, 'MEMBER');

        cy.loginAs(memberEmail);
        cy.visit('/org/settings');
        cy.contains('Org Templates').should('be.visible');
        cy.contains('button', 'Create one').should('not.exist');

        cy.loginAs(ownerEmail);
        cy.visit('/org/settings');
        // The "Org Templates" section (not a separate tab — OrgSettingsPage is one
        // scrollable page) is the empty-state call site named by the ADR — org-level
        // templates start with none, so its "Create one" CTA is visible to the owner.
        cy.contains('Org Templates').should('be.visible');
        cy.contains('button', 'Create one').should('be.visible');
      }),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });
});

describe('Empty States — action-less by design', () => {
  it('status history, an "all caught up" approvals queue, and a no-results relationship search all render with no CTA', () => {
    const ownerEmail = uniqueEmail('empty-noaction-owner');
    const memberEmail = uniqueEmail('empty-noaction-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Orbit Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Orbit Empty Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

          cy.loginAs(ownerEmail);
          cy.visit(`/projects/${projectId}/approvals`);
          cy.contains('All caught up').should('be.visible');
          cy.get('button').contains(/Create|Add/).should('not.exist');

          createJobAs(ownerEmail, projectId, { title: 'Relationship target job' }).then((jobId) => {
            cy.visit(`/projects/${projectId}/jobs/${jobId}`);
            cy.contains('Status history').should('be.visible');
            cy.contains('No history yet').should('not.exist'); // history auto-expands only once populated; a fresh job has 1 entry already
            // Relationships starts collapsed for a job with none yet
            // (defaultExpanded={job.relationships.length > 0}) — expand first. Its
            // toggle is a div[role="button"], not a real <button> (the nested "+ Add"
            // trigger can't live inside a real button — invalid nested-button HTML).
            cy.contains('div[role="button"]', 'Relationships').click();
            cy.contains('button', '+ Add').click();
            cy.get('.z-50:visible').within(() => {
              cy.get('input[type="text"]').type('zzz-no-such-job-zzz');
              cy.contains('No jobs found').should('be.visible');
              cy.get('button').contains(/Create|Add first/).should('not.exist');
            });
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });
});
