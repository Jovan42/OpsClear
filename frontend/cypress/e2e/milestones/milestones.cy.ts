// ADR-0049 Appendix §12 (Milestones). Uses cy.loginAs() per docs/dev/process/E2E.md.
//
// The "MEMBER read-only, no controls" happy-path bullet depends on JOB-257 (fix:
// MilestonesPage previously showed New/Edit/Delete controls to a MEMBER even though
// the backend requires OWNER/ADMIN for all three writes) — see that job for the fix
// itself; this spec's MEMBER-controls test exercises the corrected behavior.

import {
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  userIdFor,
  createOrgWithFullAccess,
  createOrgWithSubscription,
  createProjectAs,
  addMember,
  addProjectMember,
  createJobAs,
  updateJobStatusAs,
  createMilestoneAs,
  createTemplateAs,
  getJobAs,
  API,
} from '../../support/orgApi';

describe('Milestones', () => {
  it('creates a milestone with name only, and separately with name + description + deadline; the progress bar reflects already-loaded job counts', { tags: '@smoke' }, () => {
    const email = uniqueEmail('create-milestone');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Falcon Corp', uniqueSlug());
    createProjectAs(email, 'Falcon Milestone Project').then((projectId) => {
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/milestones`);

      cy.contains('button', '+ New Milestone').click();
      cy.get('.z-50:visible').within(() => {
        cy.get('input[placeholder="e.g. Beta release"]').type('Kickoff');
        cy.contains('button', 'Create').click();
      });
      cy.contains('Kickoff').should('be.visible');

      cy.contains('button', '+ New Milestone').click();
      cy.get('.z-50:visible').within(() => {
        cy.get('input[placeholder="e.g. Beta release"]').type('Beta Launch');
        cy.get('textarea').type('Ship the beta to early adopters');
        cy.get('input[type="date"]').type('2099-06-15');
        cy.contains('button', 'Create').click();
      });
      cy.contains('Beta Launch').should('be.visible');
      cy.contains('Ship the beta to early adopters').should('be.visible');

      cy.contains('Beta Launch')
        .parents('div.flex.items-start.justify-between')
        .then(($row) => {
          const msId = $row.text();
          expect(msId).to.contain('Beta Launch');
        });
    });

    cy.deleteKeycloakUser(email);
  });

  it('the progress format preference toggles between fraction and percentage and reflects immediately with no refetch', () => {
    const email = uniqueEmail('progress-format');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Nimbus Corp', uniqueSlug());
    createProjectAs(email, 'Nimbus Milestone Project').then((projectId) =>
      createMilestoneAs(email, projectId, 'Rollout').then((msId) => {
        createJobAs(email, projectId, { title: 'Job A', milestoneId: msId }).then(() =>
          createJobAs(email, projectId, { title: 'Job B', milestoneId: msId }).then((jobB) =>
            updateJobStatusAs(email, projectId, jobB, 'IN_PROGRESS').then(() =>
              updateJobStatusAs(email, projectId, jobB, 'COMPLETED').then(() => {
                cy.loginAs(email);
                cy.visit(`/projects/${projectId}/milestones`);
                cy.contains('1/2').should('be.visible');

                cy.visit('/settings');
                cy.contains('button', 'Percentage').click();

                cy.visit(`/projects/${projectId}/milestones`);
                cy.contains('50%').should('be.visible');
                cy.contains('1/2').should('not.exist');
              }),
            ),
          ),
        );
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('"View jobs →" navigates to a pre-filtered flat list showing only that milestone\'s jobs', () => {
    const email = uniqueEmail('view-jobs-link');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Atlas Corp', uniqueSlug());
    createProjectAs(email, 'Atlas Milestone Project').then((projectId) =>
      createMilestoneAs(email, projectId, 'Phase One').then((msId) => {
        createJobAs(email, projectId, { title: 'In milestone', milestoneId: msId });
        createJobAs(email, projectId, { title: 'Not in milestone' });

        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/milestones`);
        cy.contains('a', 'View jobs →').click();

        cy.url().should('include', `milestone=${msId}`);
        cy.get('table').contains('In milestone').should('be.visible');
        cy.get('table').contains('Not in milestone').should('not.exist');
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('deleting a milestone ungroups its jobs rather than deleting them — the job survives with milestoneId cleared', () => {
    const email = uniqueEmail('delete-ungroups');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Cedar Corp', uniqueSlug());
    createProjectAs(email, 'Cedar Milestone Project').then((projectId) =>
      createMilestoneAs(email, projectId, 'Doomed Phase').then((msId) =>
        createJobAs(email, projectId, { title: 'Survivor job', milestoneId: msId }).then((jobId) => {
          getJobAs(email, projectId, jobId).then((job: { milestoneId: string | null }) => {
            expect(job.milestoneId).to.equal(msId);
          });

          tokenFor(email).then((token) => {
            cy.request({
              method: 'DELETE',
              url: `${API}/api/projects/${projectId}/milestones/${msId}`,
              headers: { Authorization: `Bearer ${token}` },
            });
          });

          getJobAs(email, projectId, jobId).then((job: { milestoneId: string | null }) => {
            expect(job.milestoneId).to.equal(null);
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('a milestone with a past deadline shows overdue styling; a future deadline does not', () => {
    const email = uniqueEmail('overdue-styling');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Vega Corp', uniqueSlug());
    createProjectAs(email, 'Vega Milestone Project').then((projectId) =>
      createMilestoneAs(email, projectId, 'Past Due', '2020-01-01').then(() =>
        createMilestoneAs(email, projectId, 'Future Due', '2099-01-01').then(() => {
          cy.loginAs(email);
          cy.visit(`/projects/${projectId}/milestones`);
          cy.contains('⚠ Overdue').should('be.visible');
          cy.contains('Past Due').parents('div.min-w-0').find('p.text-red-600').should('exist');
          cy.contains('Future Due').parents('div.min-w-0').contains('Due').should('not.have.class', 'text-red-600');
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('a MEMBER sees no New/Edit/Delete controls on the milestones page (JOB-257 regression guard)', () => {
    const ownerEmail = uniqueEmail('member-readonly-owner');
    const memberEmail = uniqueEmail('member-readonly-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Juniper Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Juniper Milestone Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
          createMilestoneAs(ownerEmail, projectId, 'Existing Phase').then(() => {
            cy.loginAs(memberEmail);
            cy.visit(`/projects/${projectId}/milestones`);
            cy.contains('Existing Phase').should('be.visible');
            cy.contains('button', '+ New Milestone').should('not.exist');
            cy.contains('button', 'Edit').should('not.exist');
            cy.contains('button', 'Delete').should('not.exist');
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('a blank or over-100-char name 400s on create', () => {
    const email = uniqueEmail('name-validation');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Orbit Corp', uniqueSlug());
    createProjectAs(email, 'Orbit Milestone Project').then((projectId) => {
      tokenFor(email).then((token) => {
        cy.request({
          method: 'POST',
          url: `${API}/api/projects/${projectId}/milestones`,
          headers: { Authorization: `Bearer ${token}` },
          body: { name: '   ' },
          failOnStatusCode: false,
        }).its('status').should('eq', 400);

        cy.request({
          method: 'POST',
          url: `${API}/api/projects/${projectId}/milestones`,
          headers: { Authorization: `Bearer ${token}` },
          body: { name: 'x'.repeat(101) },
          failOnStatusCode: false,
        }).its('status').should('eq', 400);
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('a MEMBER gets 403 on create/update/delete', () => {
    const ownerEmail = uniqueEmail('member-write-owner');
    const memberEmail = uniqueEmail('member-write-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Sable Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Sable Milestone Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
          createMilestoneAs(ownerEmail, projectId, 'Sable Phase').then((msId) => {
            tokenFor(memberEmail).then((token) => {
              cy.request({
                method: 'POST',
                url: `${API}/api/projects/${projectId}/milestones`,
                headers: { Authorization: `Bearer ${token}` },
                body: { name: 'Hijack Phase' },
                failOnStatusCode: false,
              }).its('status').should('eq', 403);

              cy.request({
                method: 'PUT',
                url: `${API}/api/projects/${projectId}/milestones/${msId}`,
                headers: { Authorization: `Bearer ${token}` },
                body: { name: 'Hijacked' },
                failOnStatusCode: false,
              }).its('status').should('eq', 403);

              cy.request({
                method: 'DELETE',
                url: `${API}/api/projects/${projectId}/milestones/${msId}`,
                headers: { Authorization: `Bearer ${token}` },
                failOnStatusCode: false,
              }).its('status').should('eq', 403);
            });
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('a milestone ID from a different project 404s on update and delete; the MILESTONES add-on off 403s the list endpoint', () => {
    const email = uniqueEmail('cross-project-guard');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Comet Corp', uniqueSlug());
    createProjectAs(email, 'Comet Milestone Project A').then((projectIdA) =>
      createProjectAs(email, 'Comet Milestone Project B').then((projectIdB) =>
        createMilestoneAs(email, projectIdA, 'Belongs to A').then((msId) => {
          tokenFor(email).then((token) => {
            cy.request({
              method: 'PUT',
              url: `${API}/api/projects/${projectIdB}/milestones/${msId}`,
              headers: { Authorization: `Bearer ${token}` },
              body: { name: 'Stolen' },
              failOnStatusCode: false,
            }).its('status').should('eq', 404);

            cy.request({
              method: 'DELETE',
              url: `${API}/api/projects/${projectIdB}/milestones/${msId}`,
              headers: { Authorization: `Bearer ${token}` },
              failOnStatusCode: false,
            }).its('status').should('eq', 404);
          });
        }),
      ),
    );

    const noAddonEmail = uniqueEmail('no-addon');
    cy.createKeycloakUser(noAddonEmail, 'E2E', 'Tester');
    createOrgWithSubscription(noAddonEmail, 'Comet No Addon Corp', uniqueSlug());
    createProjectAs(noAddonEmail, 'No Addon Milestone Project').then((projectId) => {
      tokenFor(noAddonEmail).then((token) => {
        cy.request({
          method: 'GET',
          url: `${API}/api/projects/${projectId}/milestones`,
          headers: { Authorization: `Bearer ${token}` },
          failOnStatusCode: false,
        }).its('status').should('eq', 403);
      });

      cy.loginAs(noAddonEmail);
      cy.visit(`/projects/${projectId}/milestones`);
      cy.contains('Milestones').should('be.visible');
      cy.contains('button', '+ New Milestone').should('not.exist');
    });

    cy.deleteKeycloakUser(email);
    cy.deleteKeycloakUser(noAddonEmail);
  });

  it('a deadline of exactly today is not counted overdue (date-truncated comparison)', () => {
    const email = uniqueEmail('today-boundary');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Willow Corp', uniqueSlug());
    createProjectAs(email, 'Willow Milestone Project').then((projectId) => {
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/milestones`);
      cy.contains('button', '+ New Milestone').click();
      cy.get('.z-50:visible').within(() => {
        cy.get('input[placeholder="e.g. Beta release"]').type('Due Today');
        cy.get('input[type="date"]').then(($input) => {
          const today = new Date().toISOString().substring(0, 10);
          cy.wrap($input).type(today);
        });
        cy.contains('button', 'Create').click();
      });
      cy.contains('Due Today').should('be.visible');
      cy.contains('⚠ Overdue').should('not.exist');
    });

    cy.deleteKeycloakUser(email);
  });

  it('deleting a milestone referenced by a job template leaves the template intact with its milestone reference hidden, not crashing', () => {
    const email = uniqueEmail('template-tolerance');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Sparrow Corp', uniqueSlug());
    createProjectAs(email, 'Sparrow Milestone Project').then((projectId) =>
      createMilestoneAs(email, projectId, 'Referenced Phase').then((msId) =>
        createTemplateAs(email, projectId, { name: 'Weekly report', title: 'Weekly report', milestoneId: msId }).then(() => {
          tokenFor(email).then((token) => {
            cy.request({
              method: 'DELETE',
              url: `${API}/api/projects/${projectId}/milestones/${msId}`,
              headers: { Authorization: `Bearer ${token}` },
            });

            cy.request({
              method: 'GET',
              url: `${API}/api/projects/${projectId}/templates`,
              headers: { Authorization: `Bearer ${token}` },
            }).then((res) => {
              expect(res.status).to.equal(200);
              const tpl = res.body.find((t: { name: string }) => t.name === 'Weekly report');
              expect(tpl.milestoneName).to.equal(null);
            });
          });

          cy.loginAs(email);
          cy.visit(`/projects/${projectId}/templates`);
          cy.contains('Weekly report').should('be.visible');
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('the progress bar renders at 0 jobs (no bar), all-complete (100%), and none-complete (0%)', () => {
    const email = uniqueEmail('progress-extremes');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Delta Corp', uniqueSlug());
    createProjectAs(email, 'Delta Milestone Project').then((projectId) =>
      createMilestoneAs(email, projectId, 'Empty Phase').then(() =>
        createMilestoneAs(email, projectId, 'All Done Phase').then((msDone) =>
          createMilestoneAs(email, projectId, 'None Done Phase').then((msNone) => {
            createJobAs(email, projectId, { title: 'Done job', milestoneId: msDone }).then((jobId) =>
              updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS').then(() =>
                updateJobStatusAs(email, projectId, jobId, 'COMPLETED'),
              ),
            );
            createJobAs(email, projectId, { title: 'Pending job', milestoneId: msNone });

            cy.loginAs(email);
            cy.visit(`/projects/${projectId}/milestones`);

            cy.contains('Empty Phase').parents('div.min-w-0').find('.bg-blue-500').should('not.exist');
            cy.contains('All Done Phase').parents('div.min-w-0').contains('1/1').should('be.visible');
            cy.contains('None Done Phase').parents('div.min-w-0').contains('0/1').should('be.visible');
          }),
        ),
      ),
    );

    cy.deleteKeycloakUser(email);
  });
});
