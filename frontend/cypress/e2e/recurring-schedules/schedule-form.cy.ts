// JOB-347 (E2E coverage push). Drives the schedule modal's assignee rotation picker, the
// pause-until / expires-at inputs, the edit flow, every PauseDialog option, and the failure
// states of the schedules page. `recurring-schedules.cy.ts` covers presets, cron preview and
// missed runs, so none of that is repeated here.

import {
  uniqueEmail,
  uniqueSlug,
  userIdFor,
  createOrgWithFullAccess,
  createProjectAs,
  addMember,
  addProjectMember,
  createTemplateAs,
  createScheduleAs,
  getScheduleAs,
} from '../../support/orgApi';

describe('Schedule form modal and pause dialog', () => {
  it('builds an assignee rotation in the modal (add, reorder, remove), sets pause-until and expiry, then edits it', { tags: '@smoke' }, () => {
    const email = uniqueEmail('sched-form');
    cy.createKeycloakUser(email, 'Olga', 'Owner');
    createOrgWithFullAccess(email, 'Sched Form Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(email, 'Sched Form Project').then((projectId) => {
        const memberEmail = uniqueEmail('sched-form-member');
        cy.createKeycloakUser(memberEmail, 'Maja', 'Member');
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, email, memberId, 'MEMBER');
          addProjectMember(projectId, email, memberId, 'MEMBER');
        });
        createTemplateAs(email, projectId, { name: 'Checklist', title: 'Checklist' });

        cy.loginAs(email);
        cy.viewport(1000, 1400);
        cy.visit(`/projects/${projectId}/schedules`);
        cy.contains('No schedules yet').should('be.visible');
        cy.contains('button', 'Create first schedule').click();

        cy.get('.fixed.inset-0.z-50:visible').within(() => {
          cy.get('input[placeholder="e.g. Weekly deploy checklist"]').type('Rotating checklist');
          cy.get('select').first().select('Checklist');

          const search = 'input[placeholder="Add member to rotation…"]';
          cy.get(search).type('maja');
          cy.contains('button', 'Maja').click();
          cy.get(search).type('olga');
          cy.contains('button', 'Olga').click();
          cy.contains('#1').parent().should('contain', 'Maja');
          cy.contains('#2').parent().should('contain', 'Olga');

          // Reorder: Olga moves up and takes slot #1; the first row's move-up is disabled
          cy.get('button[title="Move up"]').first().should('be.disabled');
          cy.get('button[title="Move up"]').eq(1).click();
          cy.contains('#1').parent().should('contain', 'Olga');

          // Remove the second row (Maja), leaving only Olga
          cy.get('button[title="Remove"]').eq(1).click();
          cy.contains('Maja').should('not.exist');

          cy.contains('label', 'Pause until').parent().find('input').type('2099-01-01T09:00');
          cy.contains('label', 'Expires at').parent().find('input').type('2099-06-01T09:00');
          cy.contains('button', 'Create schedule').click();
        });

        cy.contains('p', 'Rotating checklist').should('be.visible');
        cy.contains('Paused').should('be.visible');

        cy.contains('button', 'Edit').click();
        cy.get('.fixed.inset-0.z-50:visible').within(() => {
          cy.contains('Edit Schedule').should('be.visible');
          cy.get('input[placeholder="e.g. Weekly deploy checklist"]').should('have.value', 'Rotating checklist');
          cy.get('input[placeholder="e.g. Weekly deploy checklist"]').clear();
          cy.get('input[placeholder="e.g. Weekly deploy checklist"]').type('Renamed checklist');
          cy.contains('label', 'Pause until').parent().find('input').clear();
          cy.contains('button', 'Save changes').click();
        });
        cy.contains('p', 'Renamed checklist').should('be.visible');
        cy.contains('Active').should('be.visible');
      }),
    );
    cy.deleteKeycloakUser(email);
  });

  it('pauses through the 1 day, 1 week, 1 month and custom date options, and a custom date needs a value before it can be confirmed', () => {
    const email = uniqueEmail('sched-pause');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Sched Timing Corp', uniqueSlug());
    createProjectAs(email, 'Sched Timing Project').then((projectId) =>
      userIdFor(email).then((ownerId) =>
        createTemplateAs(email, projectId, { name: 'T', title: 'T' }).then((templateId) =>
          createScheduleAs(email, projectId, {
            name: 'Pausable',
            templateId,
            cronExpression: '0 0 9 * * *',
            timezone: 'UTC',
            assigneeIds: [ownerId],
          }).then((scheduleId) => {
            cy.loginAs(email);
            cy.visit(`/projects/${projectId}/schedules`);

            const pauseWith = (option: string) => {
              cy.contains('button', 'Pause').click();
              cy.get('.fixed.inset-0.z-50:visible').within(() => {
                cy.contains('label', option).click();
                cy.contains('button', 'Pause').click();
              });
              cy.contains('button', 'Resume').should('be.visible');
            };
            const daysFromNow = (iso: string) => (new Date(iso).getTime() - Date.now()) / 86_400_000;

            pauseWith('1 day');
            getScheduleAs(email, projectId, scheduleId).then((s) => {
              expect(s.status).to.equal('PAUSED');
              expect(daysFromNow(s.pausedUntil as string)).to.be.within(0.9, 1.1);
            });
            cy.contains('button', 'Resume').click();
            cy.contains('button', 'Pause').should('be.visible');

            pauseWith('1 week');
            getScheduleAs(email, projectId, scheduleId).then((s) => {
              expect(daysFromNow(s.pausedUntil as string)).to.be.within(6.9, 7.1);
            });
            cy.contains('button', 'Resume').click();
            cy.contains('button', 'Pause').should('be.visible');

            pauseWith('1 month');
            getScheduleAs(email, projectId, scheduleId).then((s) => {
              expect(daysFromNow(s.pausedUntil as string)).to.be.within(27, 32);
            });
            cy.contains('button', 'Resume').click();
            cy.contains('button', 'Pause').should('be.visible');

            // Custom: Pause stays disabled until a date is picked; Cancel closes without pausing
            cy.contains('button', 'Pause').click();
            cy.get('.fixed.inset-0.z-50:visible').within(() => {
              cy.contains('label', 'Custom date').click();
              cy.contains('button', 'Pause').should('be.disabled');
              cy.contains('button', 'Cancel').click();
            });
            cy.contains('button', 'Pause').should('be.visible');

            cy.contains('button', 'Pause').click();
            cy.get('.fixed.inset-0.z-50:visible').within(() => {
              cy.contains('label', 'Custom date').click();
              cy.contains('label', 'Resume on').parent().find('input').type('2099-03-01T09:00');
              cy.contains('button', 'Pause').should('not.be.disabled').click();
            });
            cy.contains('button', 'Resume').should('be.visible');
            getScheduleAs(email, projectId, scheduleId).then((s) => {
              expect(s.pausedUntil as string).to.match(/^2099-03-01/);
            });
          }),
        ),
      ),
    );
    cy.deleteKeycloakUser(email);
  });

  it('shows the server error inside the modal when creating fails, and an error state when the list fails to load', () => {
    const email = uniqueEmail('sched-err');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Sched Err Corp', uniqueSlug());
    createProjectAs(email, 'Sched Err Project').then((projectId) => {
      createTemplateAs(email, projectId, { name: 'Checklist', title: 'Checklist' });
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/schedules`);

      cy.intercept('POST', '**/api/projects/*/schedules', { statusCode: 500, body: { message: 'boom' } }).as('create');
      cy.contains('button', '+ New Schedule').click();
      cy.get('.fixed.inset-0.z-50:visible').within(() => {
        cy.get('input[placeholder="e.g. Weekly deploy checklist"]').type('Will fail');
        cy.get('select').first().select('Checklist');
        cy.contains('button', 'Create schedule').click();
        cy.wait('@create');
        cy.contains('p.text-red-600', 'status code 500').scrollIntoView();
        cy.contains('p.text-red-600', 'status code 500').should('be.visible');
      });

      cy.intercept('GET', '**/api/projects/*/schedules', { statusCode: 500, body: { message: 'boom' } });
      cy.visit(`/projects/${projectId}/schedules`);
      cy.contains('Failed to load schedules.').should('be.visible');
    });
    cy.deleteKeycloakUser(email);
  });
});
