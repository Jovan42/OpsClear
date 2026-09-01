// ADR-0049 Appendix §11 (Projects — CRUD, Lifecycle, Members). Uses cy.loginAs()
// per docs/dev/process/E2E.md.
//
// Project names in fixtures deliberately avoid words that collide with fixed UI
// chrome text (tab labels "Active"/"Completed"/"All", buttons "+ New Project",
// "Complete project", "Delete project", "Reactivate", "Save changes", "Remove",
// "Add", the "Owner"/"Completed" status badges, and the header's project-switcher
// button, which renders the current project's own name) — see the recurring
// project-name/button-text substring collisions noted across prior backfill jobs.

import {
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  userIdFor,
  createOrgAs,
  createOrgWithSubscription,
  createOrgWithFullAccess,
  createProjectAs,
  addMember,
  addProjectMember,
  listProjectMembersAs,
  createJobAs,
  updateJobStatusAs,
  createTemplateAs,
  createScheduleAs,
  getScheduleAs,
  API,
} from '../../support/orgApi';

describe('Projects (CRUD, Lifecycle, Members)', () => {
  it('creates a project with name only, and separately with name + description + block reasons; the creator is auto-OWNER', () => {
    const email = uniqueEmail('create-project');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Falcon Corp', uniqueSlug());
    cy.loginAs(email);
    cy.visit('/projects');

    cy.contains('button', '+ New Project').click();
    cy.get('.z-50:visible').within(() => {
      cy.get('input[placeholder="e.g. Website Redesign"]').type('Falcon Launch');
      cy.contains('button', 'Create project').click();
    });
    cy.contains('h2', 'Falcon Launch').should('be.visible');
    cy.contains('h2', 'Falcon Launch').parents('div.relative').within(() => {
      cy.contains('Owner').should('be.visible');
    });

    cy.contains('button', '+ New Project').click();
    cy.get('.z-50:visible').within(() => {
      cy.get('input[placeholder="e.g. Website Redesign"]').type('Harbor Rollout');
      cy.get('textarea').first().type('A full rollout of the harbor system');
      cy.contains('button', 'Block reasons').click();
      cy.get('input[placeholder="e.g. Waiting on client"]').type('Waiting on vendor');
      cy.contains('button', 'Add').click();
      cy.get('input[placeholder="e.g. Waiting on client"]').type('Awaiting approval');
      cy.contains('button', 'Add').click();
      cy.contains('button', 'Create project').click();
    });
    cy.contains('h2', 'Harbor Rollout').should('be.visible');
    cy.contains('h2', 'Harbor Rollout').click();
    cy.contains('a, button', 'Settings').click({ force: true });
    cy.url().should('include', '/settings');
    cy.contains('Waiting on vendor').should('be.visible');
    cy.contains('Awaiting approval').should('be.visible');
  });

  it('the ACTIVE/COMPLETED/ALL tabs filter the list and their badge counts stay accurate', () => {
    const email = uniqueEmail('tab-filter');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Nimbus Corp', uniqueSlug());
    createProjectAs(email, 'Nimbus Active').then(() => {
      createProjectAs(email, 'Nimbus Done').then((doneId) => {
        tokenFor(email).then((token) => {
          cy.request({
            method: 'PATCH',
            url: `${API}/api/projects/${doneId}/status`,
            headers: { Authorization: `Bearer ${token}` },
            body: { status: 'COMPLETED' },
          });
        });

        cy.loginAs(email);
        cy.visit('/projects');

        cy.contains('h2', 'Nimbus Active').should('be.visible');
        cy.contains('h2', 'Nimbus Done').should('not.exist');

        cy.contains('button', 'Completed').click();
        cy.contains('h2', 'Nimbus Done').should('be.visible');
        cy.contains('h2', 'Nimbus Active').should('not.exist');

        cy.contains('button', 'All').click();
        cy.contains('h2', 'Nimbus Active').should('be.visible');
        cy.contains('h2', 'Nimbus Done').should('be.visible');
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('OWNER and ADMIN can edit project details inline; a MEMBER sees the fields disabled and no Danger Zone', () => {
    const ownerEmail = uniqueEmail('edit-owner');
    const adminEmail = uniqueEmail('edit-admin');
    const memberEmail = uniqueEmail('edit-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(adminEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithSubscription(ownerEmail, 'Atlas Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Atlas Migration').then((projectId) =>
        userIdFor(adminEmail).then((adminId) =>
          userIdFor(memberEmail).then((memberId) => {
            addMember(orgId, ownerEmail, adminId, 'MEMBER');
            addMember(orgId, ownerEmail, memberId, 'MEMBER');
            addProjectMember(projectId, ownerEmail, adminId, 'ADMIN');
            addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

            cy.loginAs(ownerEmail);
            cy.visit(`/projects/${projectId}/settings`);
            cy.get('#proj-name').clear();
            cy.get('#proj-name').type('Atlas Migration Renamed');
            cy.contains('button', 'Save changes').click();
            cy.get('#proj-name').should('have.value', 'Atlas Migration Renamed');

            cy.loginAs(adminEmail);
            cy.visit(`/projects/${projectId}/settings`);
            cy.get('#proj-name').should('not.be.disabled');
            cy.contains('Danger zone').should('not.exist');

            cy.loginAs(memberEmail);
            cy.visit(`/projects/${projectId}/settings`);
            cy.get('#proj-name').should('be.disabled');
            cy.contains('Danger zone').should('not.exist');
            cy.contains('button', 'Save changes').should('not.exist');
          }),
        ),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(adminEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('an OWNER marks a project COMPLETED then reactivates it, and the cycle repeats cleanly with no open-jobs check on reactivation', () => {
    const email = uniqueEmail('lifecycle-cycle');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Cedar Corp', uniqueSlug());
    createProjectAs(email, 'Cedar Expansion').then((projectId) => {
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/settings`);

      for (let i = 0; i < 2; i++) {
        cy.contains('button', 'Complete project').click();
        cy.contains('This project is completed').should('be.visible');
        cy.contains('button', 'Reactivate').click();
        cy.contains('This project is completed').should('not.exist');
        cy.contains('button', 'Complete project').should('be.visible');
      }
    });

    cy.deleteKeycloakUser(email);
  });

  it('deletes a project via the exact-name confirmation, which is case- and whitespace-sensitive', () => {
    const email = uniqueEmail('delete-project');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Vega Corp', uniqueSlug());
    createProjectAs(email, 'Vega Onboarding').then((projectId) => {
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/settings`);
      cy.contains('button', 'Delete project').click();

      cy.get('.z-50:visible').within(() => {
        cy.get('input[placeholder="Vega Onboarding"]').type('vega onboarding');
        cy.contains('button', 'Delete project').should('be.disabled');
        cy.get('input[placeholder="Vega Onboarding"]').clear();
        cy.get('input[placeholder="Vega Onboarding"]').type('Vega Onboarding ');
        cy.contains('button', 'Delete project').should('be.disabled');
        cy.get('input[placeholder="Vega Onboarding"]').clear();
        cy.get('input[placeholder="Vega Onboarding"]').type('Vega Onboarding');
        cy.contains('button', 'Delete project').should('not.be.disabled');
        cy.contains('button', 'Delete project').click();
      });

      cy.url().should('eq', `${Cypress.config().baseUrl}/projects`);
      cy.contains('h2', 'Vega Onboarding').should('not.exist');
    });

    cy.deleteKeycloakUser(email);
  });

  it('an OWNER searches by email to add a member via the UI, changes their role, then removes them', () => {
    const ownerEmail = uniqueEmail('members-owner');
    const memberEmail = uniqueEmail('members-target');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Target');
    createOrgWithSubscription(ownerEmail, 'Juniper Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Juniper Revamp').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');

          cy.loginAs(ownerEmail);
          cy.visit(`/projects/${projectId}/settings`);

          cy.get('input[placeholder="Search by email…"]').type(memberEmail);
          cy.contains('li button', memberEmail).click();
          cy.contains('button', 'Add').click();
          cy.contains('td', memberEmail).should('be.visible');

          listProjectMembersAs(ownerEmail, projectId).then((members) => {
            const row = members.find((m) => m.userId === memberId)!;
            expect(row.role).to.equal('MEMBER');

            cy.contains('td', memberEmail)
              .parent('tr')
              .find('select')
              .select('Admin');
            listProjectMembersAs(ownerEmail, projectId).then((after) => {
              expect(after.find((m) => m.userId === memberId)!.role).to.equal('ADMIN');
            });

            cy.contains('td', memberEmail).parent('tr').contains('button', 'Remove').click();
            cy.contains('td', memberEmail).should('not.exist');
            listProjectMembersAs(ownerEmail, projectId).then((afterRemove) => {
              expect(afterRemove.find((m) => m.userId === memberId)).to.equal(undefined);
            });
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('a blank/whitespace name 400s; a name over the 255-char backend limit 400s even though it is longer than the stricter 80-char frontend cap', () => {
    const email = uniqueEmail('name-validation');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Orbit Corp', uniqueSlug());
    tokenFor(email).then((token) => {
      cy.request({
        method: 'POST',
        url: `${API}/api/projects`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name: '   ' },
        failOnStatusCode: false,
      }).its('status').should('eq', 400);

      cy.request({
        method: 'POST',
        url: `${API}/api/projects`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name: 'x'.repeat(256) },
        failOnStatusCode: false,
      }).its('status').should('eq', 400);

      cy.request({
        method: 'POST',
        url: `${API}/api/projects`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name: 'x'.repeat(255) },
        failOnStatusCode: false,
      }).its('status').should('eq', 201);
    });

    cy.loginAs(email);
    cy.visit('/projects');
    cy.contains('button', '+ New Project').click();
    cy.get('.z-50:visible').within(() => {
      cy.get('input[placeholder="e.g. Website Redesign"]').type('y'.repeat(81));
      cy.contains('button', 'Create project').click();
      cy.contains('Max 80 characters').should('be.visible');
    });

    cy.deleteKeycloakUser(email);
  });

  it('a duplicate name under the same owner 409s, but the same name under a different owner in the same org succeeds', () => {
    const ownerAEmail = uniqueEmail('dup-owner-a');
    const ownerBEmail = uniqueEmail('dup-owner-b');
    cy.createKeycloakUser(ownerAEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(ownerBEmail, 'E2E', 'Tester');
    createOrgAs(ownerAEmail, 'Sable Corp', uniqueSlug()).then((orgId) =>
      userIdFor(ownerBEmail).then((ownerBId) => {
        addMember(orgId, ownerAEmail, ownerBId, 'MEMBER');

        createProjectAs(ownerAEmail, 'Sable Program').then(() => {
          tokenFor(ownerAEmail).then((token) => {
            cy.request({
              method: 'POST',
              url: `${API}/api/projects`,
              headers: { Authorization: `Bearer ${token}` },
              body: { name: 'Sable Program' },
              failOnStatusCode: false,
            }).its('status').should('eq', 409);
          });

          createProjectAs(ownerBEmail, 'Sable Program').then((secondId) => {
            expect(secondId).to.be.a('string');
          });
        });
      }),
    );

    cy.deleteKeycloakUser(ownerAEmail);
    cy.deleteKeycloakUser(ownerBEmail);
  });

  it('more than 20 block reasons, or a blank one in the list, 400s on create', () => {
    const email = uniqueEmail('block-reasons-limit');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgAs(email, 'Comet Corp', uniqueSlug());
    tokenFor(email).then((token) => {
      cy.request({
        method: 'POST',
        url: `${API}/api/projects`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name: 'Comet Drive Too Many', blockReasons: Array.from({ length: 21 }, (_, i) => `Reason ${i}`) },
        failOnStatusCode: false,
      }).its('status').should('eq', 400);

      cy.request({
        method: 'POST',
        url: `${API}/api/projects`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name: 'Comet Drive Blank Reason', blockReasons: ['Valid reason', '   '] },
        failOnStatusCode: false,
      }).its('status').should('eq', 400);
    });

    cy.deleteKeycloakUser(email);
  });

  it('a MEMBER gets 403 updating project details; an ADMIN (not OWNER) gets a stricter 403 changing status or deleting', () => {
    const ownerEmail = uniqueEmail('role-gate-owner');
    const adminEmail = uniqueEmail('role-gate-admin');
    const memberEmail = uniqueEmail('role-gate-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(adminEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgAs(ownerEmail, 'Willow Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Willow Program').then((projectId) =>
        userIdFor(adminEmail).then((adminId) =>
          userIdFor(memberEmail).then((memberId) => {
            addMember(orgId, ownerEmail, adminId, 'MEMBER');
            addMember(orgId, ownerEmail, memberId, 'MEMBER');
            addProjectMember(projectId, ownerEmail, adminId, 'ADMIN');
            addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

            tokenFor(memberEmail).then((token) => {
              cy.request({
                method: 'PUT',
                url: `${API}/api/projects/${projectId}`,
                headers: { Authorization: `Bearer ${token}` },
                body: { name: 'Willow Program Hijacked' },
                failOnStatusCode: false,
              }).its('status').should('eq', 403);
            });

            tokenFor(adminEmail).then((token) => {
              cy.request({
                method: 'PATCH',
                url: `${API}/api/projects/${projectId}/status`,
                headers: { Authorization: `Bearer ${token}` },
                body: { status: 'COMPLETED' },
                failOnStatusCode: false,
              }).its('status').should('eq', 403);

              cy.request({
                method: 'DELETE',
                url: `${API}/api/projects/${projectId}`,
                headers: { Authorization: `Bearer ${token}` },
                failOnStatusCode: false,
              }).its('status').should('eq', 403);
            });
          }),
        ),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(adminEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('marking a project COMPLETED while it still has open jobs 409s with the open-job count in the message', () => {
    const email = uniqueEmail('open-jobs-guard');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgAs(email, 'Delta Corp', uniqueSlug());
    createProjectAs(email, 'Delta Program').then((projectId) => {
      // "Open" per countOpenJobsByProjectId means IN_PROGRESS/BLOCKED — a freshly
      // created NEW job does not block completion, so it must be transitioned first.
      createJobAs(email, projectId, { title: 'Still open' }).then((jobId) =>
        updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS').then(() => {
          tokenFor(email).then((token) => {
            cy.request({
              method: 'PATCH',
              url: `${API}/api/projects/${projectId}/status`,
              headers: { Authorization: `Bearer ${token}` },
              body: { status: 'COMPLETED' },
              failOnStatusCode: false,
            }).then((res) => {
              expect(res.status).to.equal(409);
              expect(res.body.message).to.contain('1');
            });
          });
        }),
      );
    });

    cy.deleteKeycloakUser(email);
  });

  it('member-management edge cases: adding an already-member 409s, assigning/changing to OWNER 403s, removing the OWNER 403s, and a memberId from a different project 404s', () => {
    const ownerEmail = uniqueEmail('member-edge-owner');
    const targetEmail = uniqueEmail('member-edge-target');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(targetEmail, 'E2E', 'Tester');
    createOrgAs(ownerEmail, 'Sparrow Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Sparrow Program A').then((projectIdA) =>
        createProjectAs(ownerEmail, 'Sparrow Program B').then((projectIdB) =>
          userIdFor(targetEmail).then((targetId) => {
            addMember(orgId, ownerEmail, targetId, 'MEMBER');
            addProjectMember(projectIdA, ownerEmail, targetId, 'MEMBER');

            tokenFor(ownerEmail).then((token) => {
              // Already a member.
              cy.request({
                method: 'POST',
                url: `${API}/api/projects/${projectIdA}/members`,
                headers: { Authorization: `Bearer ${token}` },
                body: { userId: targetId, role: 'MEMBER' },
                failOnStatusCode: false,
              }).its('status').should('eq', 409);

              // Assigning OWNER role on add.
              cy.request({
                method: 'POST',
                url: `${API}/api/projects/${projectIdB}/members`,
                headers: { Authorization: `Bearer ${token}` },
                body: { userId: targetId, role: 'OWNER' },
                failOnStatusCode: false,
              }).its('status').should('eq', 403);

              listProjectMembersAs(ownerEmail, projectIdA).then((membersA) => {
                const targetMembership = membersA.find((m) => m.userId === targetId)!;
                const ownerMembership = membersA.find((m) => m.userId !== targetId)!;

                // Changing to OWNER role on update.
                cy.request({
                  method: 'PUT',
                  url: `${API}/api/projects/${projectIdA}/members/${targetMembership.id}`,
                  headers: { Authorization: `Bearer ${token}` },
                  body: { role: 'OWNER' },
                  failOnStatusCode: false,
                }).its('status').should('eq', 403);

                // Removing the OWNER.
                cy.request({
                  method: 'DELETE',
                  url: `${API}/api/projects/${projectIdA}/members/${ownerMembership.id}`,
                  headers: { Authorization: `Bearer ${token}` },
                  failOnStatusCode: false,
                }).its('status').should('eq', 403);

                // memberId belonging to a different project.
                cy.request({
                  method: 'PUT',
                  url: `${API}/api/projects/${projectIdB}/members/${targetMembership.id}`,
                  headers: { Authorization: `Bearer ${token}` },
                  body: { role: 'ADMIN' },
                  failOnStatusCode: false,
                }).its('status').should('eq', 404);

                cy.request({
                  method: 'DELETE',
                  url: `${API}/api/projects/${projectIdB}/members/${targetMembership.id}`,
                  headers: { Authorization: `Bearer ${token}` },
                  failOnStatusCode: false,
                }).its('status').should('eq', 404);
              });
            });
          }),
        ),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(targetEmail);
  });

  it('a non-member of the project (but a member of the org) fetching it by ID gets 403, not 404 — existence is not hidden at that layer', () => {
    const ownerEmail = uniqueEmail('non-member-owner');
    const outsiderEmail = uniqueEmail('non-member-outsider');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(outsiderEmail, 'E2E', 'Tester');
    createOrgAs(ownerEmail, 'Ember Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Ember Program').then((projectId) =>
        userIdFor(outsiderEmail).then((outsiderId) => {
          addMember(orgId, ownerEmail, outsiderId, 'MEMBER');

          tokenFor(outsiderEmail).then((token) => {
            cy.request({
              method: 'GET',
              url: `${API}/api/projects/${projectId}`,
              headers: { Authorization: `Bearer ${token}` },
              failOnStatusCode: false,
            }).its('status').should('eq', 403);
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(outsiderEmail);
  });

  it('removing the sole assignee on a recurring schedule cascades: the rotation empties and the schedule auto-pauses as PAUSED_NO_ASSIGNEES', () => {
    const ownerEmail = uniqueEmail('cascade-owner');
    const soleAssigneeEmail = uniqueEmail('cascade-sole-assignee');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(soleAssigneeEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Talon Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Talon Rotation').then((projectId) =>
        userIdFor(soleAssigneeEmail).then((soleAssigneeId) => {
          addMember(orgId, ownerEmail, soleAssigneeId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, soleAssigneeId, 'MEMBER');

          createTemplateAs(ownerEmail, projectId, { name: 'Weekly check-in', title: 'Weekly check-in' }).then((templateId) =>
            createScheduleAs(ownerEmail, projectId, {
              name: 'Weekly rotation',
              templateId,
              cronExpression: '0 0 9 * * *',
              timezone: 'UTC',
              assigneeIds: [soleAssigneeId],
            }).then((scheduleId) => {
              listProjectMembersAs(ownerEmail, projectId).then((members) => {
                const membership = members.find((m) => m.userId === soleAssigneeId)!;
                tokenFor(ownerEmail).then((token) => {
                  cy.request({
                    method: 'DELETE',
                    url: `${API}/api/projects/${projectId}/members/${membership.id}`,
                    headers: { Authorization: `Bearer ${token}` },
                  });
                });
              });

              getScheduleAs(ownerEmail, projectId, scheduleId).then((schedule) => {
                expect(schedule.assignees).to.have.length(0);
                expect(schedule.status).to.equal('PAUSED_NO_ASSIGNEES');
              });
            }),
          );
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(soleAssigneeEmail);
  });
});
