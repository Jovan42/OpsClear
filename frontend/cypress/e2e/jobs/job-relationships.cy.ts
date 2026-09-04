// ADR-0049 Appendix §9 (Job Relationships). Uses cy.loginAs() per
// docs/dev/process/E2E.md.
//
// Two behaviors here are ALREADY-FLAGGED, accepted-pending-a-product-decision gaps
// (see the ADR's own "Findings during this audit" section), not new bugs to file:
// - JobRelationshipService.create()/delete() only call requireMember, never
//   requireOwnerOrAdmin — a plain MEMBER can add/remove relationships via a direct
//   API call even though the UI's × button and "+ Add" trigger are both gated to
//   canManage (OWNER/ADMIN only).
// - Neither create() nor delete() calls requireProjectNotCompleted, unlike jobs,
//   notes, and status changes — relationships can be mutated on a COMPLETED project.
// The tests below lock in and document the actual current behavior for each,
// rather than assuming either one is correct or incorrect.

import {
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  createOrgWithFullAccess,
  createOrgWithSubscription,
  createProjectAs,
  createJobAs,
  createRelationshipAs,
  deleteRelationshipAs,
  getJobAs,
  addMember,
  addProjectMember,
  userIdFor,
  completeProjectAs,
  API,
} from '../../support/orgApi';

// The relationships API's targetJobId body field only accepts a raw UUID (unlike
// path segments elsewhere, it does not resolve friendlyIds) — createJobAs only ever
// returns a friendlyId, so tests posting raw request bodies directly (bypassing the
// createRelationshipAs helper, which already does this resolution) need to resolve
// it themselves first.
function rawJobId(email: string, projectId: string, jobFriendlyId: string) {
  return getJobAs(email, projectId, jobFriendlyId).then((job: { id: string }) => job.id);
}

describe('Job Relationships', () => {
  it('adds a BLOCKED_BY relationship via the search modal; it shows on both jobs with a flipped label on the incoming side', { tags: '@smoke' }, () => {
    const email = uniqueEmail('add-relationship');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Add Relationship Corp', uniqueSlug());
    createProjectAs(email, 'Add Relationship Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job Alpha' }).then((jobA) =>
        createJobAs(email, projectId, { title: 'Job Beta' }).then((jobB) => {
          cy.loginAs(email);
          cy.visit(`/projects/${projectId}/jobs/${jobA}`);
          cy.contains('div[role="button"]', 'Relationships').click();
          cy.contains('button', '+ Add').click();
          cy.get('.z-50:visible').within(() => {
            cy.contains('label', 'Blocked by').click();
            cy.get('input[placeholder="Search jobs…"]').type('Beta');
            cy.contains('button', 'Job Beta').click();
            cy.contains('button', 'Add').click();
          });
          cy.contains('Blocked by').should('be.visible');
          cy.contains('Job Beta').should('be.visible');

          cy.visit(`/projects/${projectId}/jobs/${jobB}`);
          // Section auto-expands since jobB already has a relationship — no click.
          // Incoming side flips the label: "Blocked by" becomes "Blocks".
          cy.contains('Blocks').should('be.visible');
          cy.contains('Job Alpha').should('be.visible');
        }),
      );

      cy.deleteKeycloakUser(email);
    });
  });

  it('removes a relationship via the × button', () => {
    const email = uniqueEmail('remove-relationship');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Remove Relationship Corp', uniqueSlug());
    createProjectAs(email, 'Remove Relationship Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job Alpha' }).then((jobA) =>
        createJobAs(email, projectId, { title: 'Job Beta' }).then((jobB) =>
          createRelationshipAs(email, projectId, jobA, jobB, 'RELATED_TO').then(() => {
            cy.loginAs(email);
            cy.visit(`/projects/${projectId}/jobs/${jobA}`);
            // Section auto-expands since the job already has a relationship.
            cy.contains('Job Beta').should('be.visible');
            // The button's rendered text is just "×" — "Remove relationship" is only
            // its title attribute (a tooltip), not visible text cy.contains() can match.
            cy.get('button[title="Remove relationship"]').click();
            cy.contains('Job Beta').should('not.exist');
            cy.contains('No relationships yet.').should('be.visible');
          }),
        ),
      );

      cy.deleteKeycloakUser(email);
    });
  });

  it('the add-relationship search excludes the current job from results', () => {
    const email = uniqueEmail('search-excludes-self');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Search Excludes Self Corp', uniqueSlug());
    createProjectAs(email, 'Search Excludes Self Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Self Job' }).then((jobId) => {
        createJobAs(email, projectId, { title: 'Other Job' }).then(() => {
          cy.loginAs(email);
          cy.visit(`/projects/${projectId}/jobs/${jobId}`);
          cy.contains('div[role="button"]', 'Relationships').click();
          cy.contains('button', '+ Add').click();
          cy.get('.z-50:visible').within(() => {
            cy.contains('button', 'Other Job').should('be.visible');
            cy.contains('button', 'Self Job').should('not.exist');
          });
        });
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('missing targetJobId or type 400s; a self-reference 400s', () => {
    const email = uniqueEmail('create-validation');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Create Validation Corp', uniqueSlug());
    createProjectAs(email, 'Create Validation Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job' }).then((jobId) => {
        tokenFor(email).then((token) => {
          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}/relationships`,
            headers: { Authorization: `Bearer ${token}` },
            body: { type: 'RELATED_TO' },
            failOnStatusCode: false,
          }).its('status').should('eq', 400);

          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}/relationships`,
            headers: { Authorization: `Bearer ${token}` },
            body: { targetJobId: '00000000-0000-0000-0000-000000000000' },
            failOnStatusCode: false,
          }).its('status').should('eq', 400);

          rawJobId(email, projectId, jobId).then((rawId) =>
            cy.request({
              method: 'POST',
              url: `${API}/api/projects/${projectId}/jobs/${jobId}/relationships`,
              headers: { Authorization: `Bearer ${token}` },
              body: { targetJobId: rawId, type: 'RELATED_TO' },
              failOnStatusCode: false,
            }).its('status').should('eq', 400),
          );
        });
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('a duplicate (source, target, type) triple 409s, but the reverse triple of the same type is allowed to coexist', () => {
    const email = uniqueEmail('duplicate-and-reverse');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Duplicate Reverse Corp', uniqueSlug());
    createProjectAs(email, 'Duplicate Reverse Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job Alpha' }).then((jobA) =>
        createJobAs(email, projectId, { title: 'Job Beta' }).then((jobB) => {
          createRelationshipAs(email, projectId, jobA, jobB, 'RELATED_TO').then(() => {
            tokenFor(email).then((token) => {
              rawJobId(email, projectId, jobB).then((rawTargetId) =>
                cy.request({
                  method: 'POST',
                  url: `${API}/api/projects/${projectId}/jobs/${jobA}/relationships`,
                  headers: { Authorization: `Bearer ${token}` },
                  body: { targetJobId: rawTargetId, type: 'RELATED_TO' },
                  failOnStatusCode: false,
                }).its('status').should('eq', 409),
              );
            });

            // Not flagged as a bug by ADR-0049 — the reverse (B -> A) of the same
            // type is a structurally distinct row and is not blocked, so both
            // directions can coexist. Documenting actual current behavior.
            createRelationshipAs(email, projectId, jobB, jobA, 'RELATED_TO').then((reverseId) => {
              expect(reverseId).to.be.a('string');
            });
          });
        }),
      );

      cy.deleteKeycloakUser(email);
    });
  });

  it('a cross-project or nonexistent targetJobId 404s', () => {
    const email = uniqueEmail('target-validation');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Target Validation Corp', uniqueSlug());
    createProjectAs(email, 'Project A').then((projectA) =>
      createProjectAs(email, 'Project B').then((projectB) =>
        createJobAs(email, projectA, { title: 'Job in A' }).then((jobInA) =>
          createJobAs(email, projectB, { title: 'Job in B' }).then((jobInB) => {
            tokenFor(email).then((token) => {
              rawJobId(email, projectB, jobInB).then((rawTargetId) =>
                cy.request({
                  method: 'POST',
                  url: `${API}/api/projects/${projectA}/jobs/${jobInA}/relationships`,
                  headers: { Authorization: `Bearer ${token}` },
                  body: { targetJobId: rawTargetId, type: 'RELATED_TO' },
                  failOnStatusCode: false,
                }).its('status').should('eq', 404),
              );

              cy.request({
                method: 'POST',
                url: `${API}/api/projects/${projectA}/jobs/${jobInA}/relationships`,
                headers: { Authorization: `Bearer ${token}` },
                body: { targetJobId: '00000000-0000-0000-0000-000000000000', type: 'RELATED_TO' },
                failOnStatusCode: false,
              }).its('status').should('eq', 404);
            });

            cy.deleteKeycloakUser(email);
          }),
        ),
      ),
    );
  });

  it('deleting a relationship not belonging to the given job (neither source nor target) 404s', () => {
    const email = uniqueEmail('delete-not-belonging');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Delete Not Belonging Corp', uniqueSlug());
    createProjectAs(email, 'Delete Not Belonging Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job Alpha' }).then((jobA) =>
        createJobAs(email, projectId, { title: 'Job Beta' }).then((jobB) =>
          createJobAs(email, projectId, { title: 'Unrelated job' }).then((jobC) =>
            createRelationshipAs(email, projectId, jobA, jobB, 'RELATED_TO').then((relId) => {
              deleteRelationshipAs(email, projectId, jobC, relId).its('status').should('eq', 404);
            }),
          ),
        ),
      );

      cy.deleteKeycloakUser(email);
    });
  });

  it('every relationships endpoint 403s without the JOB_RELATIONSHIPS add-on', () => {
    const email = uniqueEmail('no-addon');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'No Addon Corp', uniqueSlug());
    createProjectAs(email, 'No Addon Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job Alpha' }).then((jobA) => {
        tokenFor(email).then((token) => {
          // The add-on gate runs before service logic even resolves the target
          // job, so targetJobId's exact value doesn't matter here — no UUID
          // resolution needed for this one.
          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/jobs/${jobA}/relationships`,
            headers: { Authorization: `Bearer ${token}` },
            body: { targetJobId: '00000000-0000-0000-0000-000000000000', type: 'RELATED_TO' },
            failOnStatusCode: false,
          }).its('status').should('eq', 403);

          cy.request({
            method: 'DELETE',
            url: `${API}/api/projects/${projectId}/jobs/${jobA}/relationships/00000000-0000-0000-0000-000000000000`,
            headers: { Authorization: `Bearer ${token}` },
            failOnStatusCode: false,
          }).its('status').should('eq', 403);
        });
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('a plain MEMBER can create and delete relationships via a direct API call, even though the UI hides both controls from them (flagged permission gap, current behavior)', () => {
    const ownerEmail = uniqueEmail('permgap-owner');
    const memberEmail = uniqueEmail('permgap-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Perm Gap Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Perm Gap Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
          // Assigned to the member — a MEMBER can only view jobs assigned to them
          // (requireCanViewJob), unrelated to relationships; this just lets them
          // load the page at all so the UI-hides-the-button half of this test works.
          createJobAs(ownerEmail, projectId, { title: 'Job Alpha', assignedTo: memberId }).then((jobA) =>
            createJobAs(ownerEmail, projectId, { title: 'Job Beta' }).then((jobB) => {
              tokenFor(memberEmail).then((token) =>
                rawJobId(ownerEmail, projectId, jobB).then((rawTargetId) =>
                  cy.request({
                    method: 'POST',
                    url: `${API}/api/projects/${projectId}/jobs/${jobA}/relationships`,
                    headers: { Authorization: `Bearer ${token}` },
                    body: { targetJobId: rawTargetId, type: 'RELATED_TO' },
                  }).then((res) => {
                    expect(res.status).to.equal(201);
                    const relId = res.body.id as string;

                    deleteRelationshipAs(memberEmail, projectId, jobA, relId).its('status').should('eq', 204);
                  }),
                ),
              );

              // The UI itself still hides both controls from a MEMBER — the gap is
              // API-only, not a UI regression.
              cy.loginAs(memberEmail);
              cy.visit(`/projects/${projectId}/jobs/${jobA}`);
              cy.contains('div[role="button"]', 'Relationships').click();
              cy.contains('button', '+ Add').should('not.exist');
            }),
          );

          cy.deleteKeycloakUser(ownerEmail);
          cy.deleteKeycloakUser(memberEmail);
        }),
      ),
    );
  });

  it('a soft-deleted job still leaves its relationship row on the other job, shown with "(deleted)" in place of its title', () => {
    const email = uniqueEmail('cascade');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Cascade Corp', uniqueSlug());
    createProjectAs(email, 'Cascade Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Survivor job' }).then((jobA) =>
        createJobAs(email, projectId, { title: 'Doomed job' }).then((jobB) =>
          createRelationshipAs(email, projectId, jobA, jobB, 'RELATED_TO').then(() => {
            tokenFor(email).then((token) =>
              cy.request({
                method: 'DELETE',
                url: `${API}/api/projects/${projectId}/jobs/${jobB}`,
                headers: { Authorization: `Bearer ${token}` },
              }),
            );

            getJobAs(email, projectId, jobA).then((job: { relationships: Array<{ job: { title: string | null } }> }) => {
              expect(job.relationships).to.have.length(1);
              expect(job.relationships[0].job.title).to.equal(null);
            });

            cy.loginAs(email);
            cy.visit(`/projects/${projectId}/jobs/${jobA}`);
            // Section auto-expands since jobA already has a relationship — no click.
            cy.contains('(deleted)').should('be.visible');
          }),
        ),
      );

      cy.deleteKeycloakUser(email);
    });
  });

  it('relationships can still be added and removed on a COMPLETED project (flagged gap, current behavior — unlike jobs/notes/status)', () => {
    const email = uniqueEmail('completed-project-gap');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Completed Project Gap Corp', uniqueSlug());
    createProjectAs(email, 'Completed Project Gap Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job Alpha' }).then((jobA) =>
        createJobAs(email, projectId, { title: 'Job Beta' }).then((jobB) =>
          rawJobId(email, projectId, jobB).then((rawTargetId) => {
            completeProjectAs(email, projectId);
            tokenFor(email).then((token) =>
              cy.request({
                method: 'POST',
                url: `${API}/api/projects/${projectId}/jobs/${jobA}/relationships`,
                headers: { Authorization: `Bearer ${token}` },
                body: { targetJobId: rawTargetId, type: 'RELATED_TO' },
              }).then((res) => {
                expect(res.status).to.equal(201);
                deleteRelationshipAs(email, projectId, jobA, res.body.id as string).its('status').should('eq', 204);
              }),
            );
          }),
        ),
      );

      cy.deleteKeycloakUser(email);
    });
  });
});
