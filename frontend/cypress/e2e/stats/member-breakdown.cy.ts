// ADR-0050 (Job completion velocity stats), JOB-273: the Owner/Admin per-member breakdown on
// the Stats page (Team / By member toggle, members table, drill-down via ?member=<id>, "back to
// team"). Uses cy.loginAs() per docs/dev/process/E2E.md.
//
// The per-member metric definitions, the `members` list contents (former members, zero
// completions) and the 403 on another user's memberId are covered by the backend stats tests.
// This spec covers what only the frontend does: who sees the toggle, the table, the drill-down
// filter on tiles + chart, the URL state across range changes and reloads, and that a Member's
// stale drill-down link is ignored instead of sent (it would 403). Mobile layout is manual only.

import {
  uniqueEmail,
  uniqueSlug,
  userIdFor,
  createOrgWithFullAccess,
  createProjectAs,
  addMember,
  addProjectMember,
  createJobAs,
  updateJobStatusAs,
} from '../../support/orgApi';

const STATS_URL = '**/api/projects/*/stats*';

/** The big number on the tile whose label is exactly `label`. */
function tileValue(label: string) {
  return cy
    .contains('[data-testid="stats-tile"] span', new RegExp(`^${label}$`))
    .parent()
    .find('span')
    .first();
}

function completeJob(email: string, projectId: string, body: Parameters<typeof createJobAs>[2]) {
  return createJobAs(email, projectId, body).then((jobId) =>
    updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS').then(() =>
      updateJobStatusAs(email, projectId, jobId, 'COMPLETED'),
    ),
  );
}

/** A button of the Team / By member toggle (scoped, so "Back to team" etc. never match). */
function viewButton(label: 'Team' | 'By member') {
  return cy.get('[role="group"][aria-label="View"]').contains('button', label);
}

function memberRow(name: string) {
  return cy.contains('[data-testid="stats-member-row"]', name);
}

describe('Stats per-member breakdown', () => {
  it('an owner switches to By member, drills into one member across a range change and a reload, and goes back to the team totals', { tags: '@smoke' }, () => {
    const ownerEmail = uniqueEmail('stats-bd-owner');
    const memberEmail = uniqueEmail('stats-bd-member');
    const idleEmail = uniqueEmail('stats-bd-idle');
    cy.createKeycloakUser(ownerEmail, 'Olga', 'Owner');
    cy.createKeycloakUser(memberEmail, 'Mira', 'Member');
    cy.createKeycloakUser(idleEmail, 'Zed', 'Idle');
    createOrgWithFullAccess(ownerEmail, 'Galaxy Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Galaxy Stats Project').then((projectId) =>
        userIdFor(ownerEmail).then((ownerId) =>
          userIdFor(memberEmail).then((memberId) =>
            userIdFor(idleEmail).then((idleId) => {
              addMember(orgId, ownerEmail, memberId, 'MEMBER');
              addMember(orgId, ownerEmail, idleId, 'MEMBER');
              addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
              addProjectMember(projectId, ownerEmail, idleId, 'MEMBER');

              completeJob(ownerEmail, projectId, { title: 'Mira job', assignedTo: memberId });
              completeJob(ownerEmail, projectId, { title: 'Olga job 1', assignedTo: ownerId });
              completeJob(ownerEmail, projectId, { title: 'Olga job 2', assignedTo: ownerId });
              completeJob(ownerEmail, projectId, { title: 'Unassigned job' });

              cy.intercept('GET', STATS_URL).as('statsFetch');
              cy.loginAs(ownerEmail);
              cy.visit(`/projects/${projectId}/stats`);
              cy.wait('@statsFetch').its('request.url').should('not.include', 'memberId=');

              // Team is the default view: totals include the unassigned job, no table yet.
              viewButton('Team').should('have.attr', 'aria-pressed', 'true');
              tileValue('Completed').should('have.text', '4');
              cy.get('[data-testid="stats-members-table"]').should('not.exist');

              viewButton('By member').click();
              cy.url().should('include', 'view=members');
              cy.get('[data-testid="stats-member-row"]').should('have.length', 3);
              memberRow('Mira Member').find('td').eq(1).should('have.text', '1');
              memberRow('Olga Owner').find('td').eq(1).should('have.text', '2');
              // Zero completions still listed, with "—" for the averages.
              memberRow('Zed Idle').find('td').eq(1).should('have.text', '0');
              memberRow('Zed Idle').find('td').eq(2).should('have.text', '—');

              memberRow('Mira Member').click();
              cy.wait('@statsFetch').its('request.url').should('include', `memberId=${memberId}`);
              cy.url().should('include', `member=${memberId}`);
              cy.get('[data-testid="stats-member-filter"]').should('contain.text', 'Showing: Mira Member');
              tileValue('Completed').should('have.text', '1');
              cy.get('[data-testid="stats-members-table"]').should('not.exist');
              cy.get('[role="group"][aria-label="View"]').should('not.exist');

              // The range selector keeps the member filter.
              cy.contains('button', '4 weeks').click();
              cy.wait('@statsFetch').its('request.url').should((url: string) => {
                expect(url).to.include('weeks=4');
                expect(url).to.include(`memberId=${memberId}`);
              });
              cy.get('[data-testid="stats-chart"]').should('have.attr', 'data-weeks', '4');
              tileValue('Completed').should('have.text', '1');

              // A reload keeps the drill-down (it lives in the URL).
              cy.reload();
              cy.get('[data-testid="stats-member-filter"]').should('contain.text', 'Showing: Mira Member');
              tileValue('Completed').should('have.text', '1');

              cy.contains('button', 'Back to team').click();
              cy.url().should('not.include', 'member=');
              cy.get('[data-testid="stats-member-filter"]').should('not.exist');
              viewButton('Team').should('have.attr', 'aria-pressed', 'true');
              tileValue('Completed').should('have.text', '4');
            }),
          ),
        ),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
    cy.deleteKeycloakUser(idleEmail);
  });

  it('an admin sees the toggle and can drill into their own row', () => {
    const ownerEmail = uniqueEmail('stats-bd-admin-owner');
    const adminEmail = uniqueEmail('stats-bd-admin');
    cy.createKeycloakUser(ownerEmail, 'Otto', 'Owner');
    cy.createKeycloakUser(adminEmail, 'Ada', 'Admin');
    createOrgWithFullAccess(ownerEmail, 'Comet Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Comet Stats Project').then((projectId) =>
        userIdFor(adminEmail).then((adminId) => {
          addMember(orgId, ownerEmail, adminId, 'ADMIN');
          addProjectMember(projectId, ownerEmail, adminId, 'ADMIN');
          completeJob(ownerEmail, projectId, { title: 'Ada job', assignedTo: adminId });
          completeJob(ownerEmail, projectId, { title: 'Unassigned job' });

          cy.loginAs(adminEmail);
          cy.visit(`/projects/${projectId}/stats`);
          cy.contains('h1', 'Team stats').should('be.visible');
          tileValue('Completed').should('have.text', '2');

          viewButton('By member').click();
          memberRow('Ada Admin').click();
          cy.get('[data-testid="stats-member-filter"]').should('contain.text', 'Showing: Ada Admin');
          tileValue('Completed').should('have.text', '1');
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(adminEmail);
  });

  it('a member sees no toggle, and a drill-down link to someone else just shows their own stats', () => {
    const ownerEmail = uniqueEmail('stats-bd-m-owner');
    const memberEmail = uniqueEmail('stats-bd-m-member');
    cy.createKeycloakUser(ownerEmail, 'Oskar', 'Owner');
    cy.createKeycloakUser(memberEmail, 'Mila', 'Member');
    createOrgWithFullAccess(ownerEmail, 'Aurora Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Aurora Stats Project').then((projectId) =>
        userIdFor(ownerEmail).then((ownerId) =>
          userIdFor(memberEmail).then((memberId) => {
            addMember(orgId, ownerEmail, memberId, 'MEMBER');
            addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
            completeJob(ownerEmail, projectId, { title: 'Mila job', assignedTo: memberId });
            completeJob(ownerEmail, projectId, { title: 'Oskar job', assignedTo: ownerId });

            cy.intercept('GET', STATS_URL).as('statsFetch');
            cy.loginAs(memberEmail);
            cy.visit(`/projects/${projectId}/stats?view=members&member=${ownerId}`);
            cy.contains('h1', 'Your stats').should('be.visible');
            cy.wait('@statsFetch').its('request.url').should('not.include', 'memberId=');
            tileValue('Completed').should('have.text', '1');
            cy.get('[role="group"][aria-label="View"]').should('not.exist');
            cy.get('[data-testid="stats-members-table"]').should('not.exist');
            cy.get('[data-testid="stats-member-filter"]').should('not.exist');
          }),
        ),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });
});
