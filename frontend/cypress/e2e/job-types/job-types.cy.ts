// ADR-0049 Appendix §16 (Job Types). Uses cy.loginAs() per docs/dev/process/E2E.md.
//
// Unlike Milestones (JOB-257), Templates (JOB-260), and Schedules (JOB-263) — all of
// which independently had write controls rendered unconditionally to MEMBER despite
// the backend requiring OWNER/ADMIN — JobTypesPage.tsx already correctly gates every
// control (the top "+ New Type" button, the up/down reorder chevrons, and each row's
// Edit/Delete) behind `canManage`/`isOwnerOrAdmin`. Confirmed by reading the component
// before writing the MEMBER-view test below, so that test is a genuine regression
// guard locking in already-correct behavior, not a speculative check.
//
// The "org template's defaultTypeName still matches a deleted-and-recreated (or just
// renamed) type has no delete guard" edge case is an INTENTIONAL, ADR-0042-documented
// design decision, not a bug: JobTemplateRepository.countTemplatesReferencing() only
// ever queries DEFAULT_TYPE_ID (the project-scoped FK) — DEFAULT_TYPE_NAME (the
// org-scoped, name-only field) has no FK and is never counted, so there is literally
// no code path that could block this delete. Asserted as-is below, not filed as a bug.

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
  createJobTypeAs,
  listJobTypesAs,
  updateJobTypeAs,
  deleteJobTypeAs,
  createTemplateAs,
  createOrgTemplateAs,
  API,
} from '../../support/orgApi';

describe('Job Types', () => {
  it('an OWNER creates job types from the swatch picker and edits name/color', () => {
    const email = uniqueEmail('create-edit');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Falcon Corp', uniqueSlug());
    createProjectAs(email, 'Falcon Types Project').then((projectId) => {
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/types`);

      cy.contains('button', '+ New Type').click();
      cy.get('.z-50:visible').within(() => {
        cy.get('input[placeholder="e.g. Bug, Feature, Maintenance"]').type('Bug');
        cy.get('button[title="Teal"]').click();
        cy.contains('button', 'Create').click();
      });
      cy.contains('Bug').should('be.visible');

      cy.contains('Bug').parents('div.flex.items-center.justify-between').within(() => {
        cy.contains('button', 'Edit').click();
      });
      cy.get('.z-50:visible').within(() => {
        cy.get('input[placeholder="e.g. Bug, Feature, Maintenance"]').clear();
        cy.get('input[placeholder="e.g. Bug, Feature, Maintenance"]').type('Defect');
        cy.get('button[title="Purple"]').click();
        cy.contains('button', 'Save').click();
      });
      cy.contains('Defect').should('be.visible');
      cy.contains('Bug').should('not.exist');
    });

    cy.deleteKeycloakUser(email);
  });

  it('reorders types via the up/down chevrons (adjacent displayOrder swap) and deletes an unreferenced type', () => {
    const email = uniqueEmail('reorder-delete');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Nimbus Corp', uniqueSlug());
    createProjectAs(email, 'Nimbus Types Project').then((projectId) => {
      createJobTypeAs(email, projectId, 'Alpha', 'RED').then(() =>
        createJobTypeAs(email, projectId, 'Beta', 'BLUE').then(() => {
          cy.loginAs(email);
          cy.visit(`/projects/${projectId}/types`);

          cy.get('div.divide-y > div').eq(0).should('contain.text', 'Alpha');
          cy.get('div.divide-y > div').eq(1).should('contain.text', 'Beta');

          cy.get('div.divide-y > div').eq(1).find('button[aria-label="Move up"]').click();

          cy.get('div.divide-y > div').eq(0).should('contain.text', 'Beta');
          cy.get('div.divide-y > div').eq(1).should('contain.text', 'Alpha');

          cy.contains('Beta').parents('div.flex.items-center.justify-between').within(() => {
            cy.contains('button', 'Delete').click();
          });
          cy.get('.z-50:visible').within(() => cy.contains('button', 'Delete').click());
          cy.contains('Beta').should('not.exist');
          cy.contains('Alpha').should('be.visible');
        }),
      );
    });

    cy.deleteKeycloakUser(email);
  });

  it('a MEMBER sees a read-only list — no New/reorder/Edit/Delete controls; list/detail badges and the dashboard type breakdown populate correctly', () => {
    const ownerEmail = uniqueEmail('readonly-owner');
    const memberEmail = uniqueEmail('readonly-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Atlas Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Atlas Types Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

          createJobTypeAs(ownerEmail, projectId, 'Feature', 'GREEN').then((typeId) => {
            // Assigned to the member — a MEMBER's job list/detail views are scoped to
            // their own assigned jobs, so an unassigned fixture job wouldn't appear.
            createJobAs(ownerEmail, projectId, { title: 'Typed job', typeId, assignedTo: memberId }).then((jobFriendlyId) => {
              cy.loginAs(memberEmail);
              cy.visit(`/projects/${projectId}/types`);
              cy.contains('Feature').should('be.visible');
              cy.contains('button', '+ New Type').should('not.exist');
              cy.contains('button', 'Edit').should('not.exist');
              cy.contains('button', 'Delete').should('not.exist');
              cy.get('button[aria-label="Move up"]').should('not.exist');
              cy.get('button[aria-label="Move down"]').should('not.exist');

              cy.visit(`/projects/${projectId}/jobs`);
              cy.get('table').contains('Feature').should('be.visible');

              cy.visit(`/projects/${projectId}/jobs/${jobFriendlyId}`);
              cy.contains('Feature').should('be.visible');

              cy.visit(`/projects/${projectId}/dashboard`);
              cy.contains('By type').should('be.visible');
              cy.contains('Feature').should('be.visible');
              cy.contains('By type').parents('div.bg-white').within(() => {
                cy.contains('1').should('be.visible');
              });
            });
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('a blank or over-100-char name 400s; an invalid color value 400s cleanly instead of leaking a raw DB/SQL error', () => {
    const email = uniqueEmail('name-color-validation');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Orbit Corp', uniqueSlug());
    createProjectAs(email, 'Orbit Types Project').then((projectId) => {
      tokenFor(email).then((token) => {
        cy.request({
          method: 'POST',
          url: `${API}/api/projects/${projectId}/job-types`,
          headers: { Authorization: `Bearer ${token}` },
          body: { name: '   ', color: 'RED' },
          failOnStatusCode: false,
        }).its('status').should('eq', 400);

        cy.request({
          method: 'POST',
          url: `${API}/api/projects/${projectId}/job-types`,
          headers: { Authorization: `Bearer ${token}` },
          body: { name: 'x'.repeat(101), color: 'RED' },
          failOnStatusCode: false,
        }).its('status').should('eq', 400);

        cy.request({
          method: 'POST',
          url: `${API}/api/projects/${projectId}/job-types`,
          headers: { Authorization: `Bearer ${token}` },
          body: { name: 'Bad Color', color: 'MAGENTA' },
          failOnStatusCode: false,
        }).then((res) => {
          expect(res.status).to.equal(400);
          expect(res.body).to.have.keys(['error', 'message', 'timestamp']);
          expect(JSON.stringify(res.body)).to.not.match(/SQLException|PSQLException|org\.postgresql/i);
        });
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
      createProjectAs(ownerEmail, 'Sable Types Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
          createJobTypeAs(ownerEmail, projectId, 'Chore', 'GRAY').then((typeId) => {
            tokenFor(memberEmail).then((token) => {
              cy.request({
                method: 'POST',
                url: `${API}/api/projects/${projectId}/job-types`,
                headers: { Authorization: `Bearer ${token}` },
                body: { name: 'Hijack', color: 'RED' },
                failOnStatusCode: false,
              }).its('status').should('eq', 403);
            });

            updateJobTypeAs(memberEmail, projectId, typeId, { name: 'Hijacked', color: 'RED', displayOrder: 0 })
              .its('status').should('eq', 403);
            deleteJobTypeAs(memberEmail, projectId, typeId).its('status').should('eq', 403);
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('delete is blocked by jobs only, templates only, or both — each with the matching 409 message and count(s)', () => {
    const email = uniqueEmail('delete-guard');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Willow Corp', uniqueSlug());
    createProjectAs(email, 'Willow Types Project').then((projectId) => {
      // Referenced by a job only.
      createJobTypeAs(email, projectId, 'JobOnly', 'RED').then((jobOnlyType) =>
        createJobAs(email, projectId, { title: 'Uses JobOnly', typeId: jobOnlyType }).then(() => {
          deleteJobTypeAs(email, projectId, jobOnlyType).then((res) => {
            expect(res.status).to.equal(409);
            expect(res.body.message).to.equal('Cannot delete type: 1 job(s) still use this type');
          });
        }),
      );

      // Referenced by a template only.
      createJobTypeAs(email, projectId, 'TemplateOnly', 'BLUE').then((templateOnlyType) =>
        createTemplateAs(email, projectId, { name: 'Uses TemplateOnly', defaultTypeId: templateOnlyType }).then(() => {
          deleteJobTypeAs(email, projectId, templateOnlyType).then((res) => {
            expect(res.status).to.equal(409);
            expect(res.body.message).to.equal('Cannot delete type: 1 template(s) still use this type');
          });
        }),
      );

      // Referenced by both a job and a template.
      createJobTypeAs(email, projectId, 'Both', 'GREEN').then((bothType) =>
        createJobAs(email, projectId, { title: 'Uses Both', typeId: bothType }).then(() =>
          createTemplateAs(email, projectId, { name: 'Also uses Both', defaultTypeId: bothType }).then(() => {
            deleteJobTypeAs(email, projectId, bothType).then((res) => {
              expect(res.status).to.equal(409);
              expect(res.body.message).to.equal('Cannot delete type: 1 job(s) and 1 template(s) still use this type');
            });

            // Same delete attempt, driven through the UI, shows the message inline.
            cy.loginAs(email);
            cy.visit(`/projects/${projectId}/types`);
            cy.contains('Both').parents('div.flex.items-center.justify-between').within(() => {
              cy.contains('button', 'Delete').click();
            });
            cy.get('.z-50:visible').within(() => {
              cy.contains('button', 'Delete').click();
              cy.contains('Cannot delete type: 1 job(s) and 1 template(s) still use this type').should('be.visible');
            });
          }),
        ),
      );
    });

    cy.deleteKeycloakUser(email);
  });

  it('without the JOB_TYPES add-on, the list endpoint 403s and the UI shows an upgrade card instead of the list', () => {
    const email = uniqueEmail('no-addon');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Comet Corp', uniqueSlug());
    createProjectAs(email, 'Comet Types Project').then((projectId) => {
      tokenFor(email).then((token) => {
        cy.request({
          method: 'GET',
          url: `${API}/api/projects/${projectId}/job-types`,
          headers: { Authorization: `Bearer ${token}` },
          failOnStatusCode: false,
        }).its('status').should('eq', 403);
      });

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/types`);
      cy.contains('button', '+ New Type').should('not.exist');
    });

    cy.deleteKeycloakUser(email);
  });

  it('a rapid double reorder-click (two independent PUTs, not a batch endpoint) leaves displayOrder consistent, not duplicated or lost', () => {
    const email = uniqueEmail('rapid-reorder');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Sparrow Corp', uniqueSlug());
    createProjectAs(email, 'Sparrow Types Project').then((projectId) => {
      createJobTypeAs(email, projectId, 'First', 'RED').then(() =>
        createJobTypeAs(email, projectId, 'Second', 'BLUE').then(() =>
          createJobTypeAs(email, projectId, 'Third', 'GREEN').then(() => {
            cy.loginAs(email);
            cy.visit(`/projects/${projectId}/types`);

            // Click "move up" on the last row twice in quick succession — each click
            // fires its own pair of PUTs (no batch/reorder endpoint exists), so two
            // rapid clicks race four PUT requests against three rows.
            cy.get('div.divide-y > div').eq(2).find('button[aria-label="Move up"]').click();
            cy.get('div.divide-y > div').eq(1).find('button[aria-label="Move up"]').click();

            listJobTypesAs(email, projectId).then((types) => {
              const orders = types.map((t) => t.displayOrder).slice().sort((a, b) => a - b);
              const unique = new Set(orders);
              expect(unique.size, 'displayOrder values must be unique, not duplicated').to.equal(3);
              expect(types.map((t) => t.name).sort()).to.deep.equal(['First', 'Second', 'Third']);
            });
          }),
        ),
      );
    });

    cy.deleteKeycloakUser(email);
  });

  it('deleting a type still matched by an org template\'s name-only defaultTypeName has no delete guard (intentional, ADR-0042)', () => {
    const email = uniqueEmail('org-template-no-guard');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Delta Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(email, 'Delta Types Project').then((projectId) =>
        createJobTypeAs(email, projectId, 'Bug', 'RED').then((typeId) =>
          createOrgTemplateAs(email, orgId, { name: 'Org Bug Template', defaultTypeName: 'Bug' }).then(() => {
            deleteJobTypeAs(email, projectId, typeId).its('status').should('eq', 204);
          }),
        ),
      ),
    );

    cy.deleteKeycloakUser(email);
  });
});
