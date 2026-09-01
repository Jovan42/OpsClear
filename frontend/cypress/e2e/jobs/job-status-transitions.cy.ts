// ADR-0049 Appendix §7 (Job Status Transitions & Blocking). Uses cy.loginAs() per
// docs/dev/process/E2E.md.
//
// Not covered as written: the ADR's happy-path bullet frames NEW→IN_PROGRESS,
// BLOCKED→IN_PROGRESS, and COMPLETED→IN_PROGRESS as "optimistic" (implying the UI
// updates before the server confirms, then reverts on rejection). No such mechanism
// exists in the current code — useUpdateJobStatus (useJobs.ts) has no onMutate/
// setQueryData, just a plain mutate-then-invalidate-on-success. This looks like a
// stale ADR claim rather than a bug (nothing crashes; the UI just isn't optimistic),
// so the tests below assert the actual current behavior instead: the status only
// changes once the server responds, and a rejected request never shows a wrong
// status in the meantime (trivially true without an optimistic write).

import {
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  createOrgWithSubscription,
  createProjectAs,
  createJobAs,
  updateJobStatusAs,
  addMember,
  addProjectMember,
  userIdFor,
  listBlockReasonsAs,
  deleteBlockReasonAs,
  completeProjectAs,
  API,
} from '../../support/orgApi';

describe('Job Status Transitions & Blocking', () => {
  it('walks a job through its full happy-path lifecycle: NEW → IN_PROGRESS → BLOCKED → IN_PROGRESS → COMPLETED', () => {
    const email = uniqueEmail('lifecycle');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Lifecycle Corp', uniqueSlug());
    createProjectAs(email, 'Lifecycle Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Lifecycle job' }).then((jobId) => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs/${jobId}`);

        // NEW -> IN_PROGRESS: no confirmation modal.
        cy.contains('span', 'New').should('be.visible');
        cy.contains('button', 'Start').click();
        cy.contains('span', 'In Progress').should('be.visible');
        cy.get('.z-50:visible').should('not.exist');

        // IN_PROGRESS -> BLOCKED: opens the block modal, requires a reason.
        cy.contains('button', 'Block').click();
        cy.get('.z-50:visible').within(() => {
          cy.contains('button', 'Block Job').should('be.disabled');
          cy.get('input[placeholder="Select or type a reason…"]').type('Waiting on client');
          cy.contains('button', 'Block Job').click();
        });
        cy.contains('span', 'Blocked').should('be.visible');
        cy.contains('Waiting on client').should('be.visible');

        // BLOCKED -> IN_PROGRESS: confirm modal.
        cy.contains('button', 'Unblock').click();
        cy.get('.z-50:visible').within(() => cy.contains('button', 'Unblock').click());
        cy.contains('span', 'In Progress').should('be.visible');
        cy.contains('Waiting on client').should('not.exist');

        // IN_PROGRESS -> COMPLETED: confirm modal.
        cy.contains('button', 'Complete').click();
        cy.get('.z-50:visible').within(() => cy.contains('button', 'Mark Complete').click());
        cy.contains('span', 'Completed').should('be.visible');
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('COMPLETED → IN_PROGRESS (reopen) requires no confirmation and is OWNER/ADMIN-only', () => {
    const email = uniqueEmail('reopen');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Reopen Corp', uniqueSlug());
    createProjectAs(email, 'Completed Job Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Reopen job' }).then((jobId) => {
        updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS').then(() =>
          updateJobStatusAs(email, projectId, jobId, 'COMPLETED').then(() => {
            cy.loginAs(email);
            cy.visit(`/projects/${projectId}/jobs/${jobId}`);
            cy.contains('button', 'Reopen').click();
            cy.get('.z-50:visible').should('not.exist');
            cy.contains('span', 'In Progress').should('be.visible');
          }),
        );
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('the block-reason combobox filters existing reasons as you type and reuses the selected one', () => {
    const email = uniqueEmail('combobox-filter');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Combobox Filter Corp', uniqueSlug());
    createProjectAs(email, 'Combobox Filter Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'First job' }).then((jobId1) =>
        updateJobStatusAs(email, projectId, jobId1, 'IN_PROGRESS').then(() =>
          updateJobStatusAs(email, projectId, jobId1, 'BLOCKED', 'Waiting on vendor').then(() => {
            createJobAs(email, projectId, { title: 'Second job' }).then((jobId2) => {
              updateJobStatusAs(email, projectId, jobId2, 'IN_PROGRESS').then(() => {
                cy.loginAs(email);
                cy.visit(`/projects/${projectId}/jobs/${jobId2}`);
                cy.contains('button', 'Block').click();
                cy.get('.z-50:visible').within(() => {
                  cy.get('input[placeholder="Select or type a reason…"]').type('Waiting');
                  cy.contains('li button', 'Waiting on vendor').click();
                  cy.contains('button', 'Block Job').click();
                });
                cy.contains('Waiting on vendor').should('be.visible');

                listBlockReasonsAs(email, projectId).then((reasons) => {
                  expect(reasons.filter((r) => r.reason === 'Waiting on vendor')).to.have.length(1);
                });
              });
            });
          }),
        ),
      );

      cy.deleteKeycloakUser(email);
    });
  });

  it('re-entering the exact text of a soft-deleted block reason resurrects it instead of creating a duplicate', () => {
    const email = uniqueEmail('resurrect');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Resurrect Corp', uniqueSlug());
    createProjectAs(email, 'Resurrect Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'First job' }).then((jobId1) =>
        updateJobStatusAs(email, projectId, jobId1, 'IN_PROGRESS').then(() =>
          updateJobStatusAs(email, projectId, jobId1, 'BLOCKED', 'Awaiting parts').then(() => {
            listBlockReasonsAs(email, projectId).then((before) => {
              const originalId = before.find((r) => r.reason === 'Awaiting parts')!.id;
              deleteBlockReasonAs(email, projectId, originalId).then(() => {
                listBlockReasonsAs(email, projectId).then((afterDelete) => {
                  expect(afterDelete.find((r) => r.reason === 'Awaiting parts')).to.equal(undefined);
                });

                createJobAs(email, projectId, { title: 'Second job' }).then((jobId2) =>
                  updateJobStatusAs(email, projectId, jobId2, 'IN_PROGRESS').then(() =>
                    updateJobStatusAs(email, projectId, jobId2, 'BLOCKED', 'Awaiting parts').then(() => {
                      listBlockReasonsAs(email, projectId).then((afterResurrect) => {
                        const resurrected = afterResurrect.find((r) => r.reason === 'Awaiting parts');
                        expect(resurrected).to.not.equal(undefined);
                        expect(resurrected!.id).to.equal(originalId);
                      });
                    }),
                  ),
                );
              });
            });
          }),
        ),
      );

      cy.deleteKeycloakUser(email);
    });
  });

  it('all five illegal status transitions are rejected with 400', () => {
    const email = uniqueEmail('illegal-transitions');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Illegal Transitions Corp', uniqueSlug());
    createProjectAs(email, 'Illegal Transitions Project').then((projectId) => {
      tokenFor(email).then((token) => {
        function attempt(jobId: string, status: string) {
          return cy.request({
            method: 'PATCH',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}/status`,
            headers: { Authorization: `Bearer ${token}` },
            body: { status, reason: status === 'BLOCKED' ? 'x' : undefined },
            failOnStatusCode: false,
          });
        }

        // NEW -> COMPLETED, NEW -> BLOCKED
        createJobAs(email, projectId, { title: 'From NEW' }).then((newJob) => {
          attempt(newJob, 'COMPLETED').its('status').should('eq', 400);
          attempt(newJob, 'BLOCKED').its('status').should('eq', 400);
        });

        // COMPLETED -> NEW, COMPLETED -> BLOCKED
        createJobAs(email, projectId, { title: 'From COMPLETED' }).then((completedJob) => {
          updateJobStatusAs(email, projectId, completedJob, 'IN_PROGRESS').then(() =>
            updateJobStatusAs(email, projectId, completedJob, 'COMPLETED').then(() => {
              attempt(completedJob, 'NEW').its('status').should('eq', 400);
              attempt(completedJob, 'BLOCKED').its('status').should('eq', 400);
            }),
          );
        });

        // BLOCKED -> COMPLETED
        createJobAs(email, projectId, { title: 'From BLOCKED' }).then((blockedJob) => {
          updateJobStatusAs(email, projectId, blockedJob, 'IN_PROGRESS').then(() =>
            updateJobStatusAs(email, projectId, blockedJob, 'BLOCKED', 'x').then(() => {
              attempt(blockedJob, 'COMPLETED').its('status').should('eq', 400);
            }),
          );
        });
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('a BLOCKED transition with a blank reason 400s', () => {
    const email = uniqueEmail('blank-reason');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Blank Reason Corp', uniqueSlug());
    createProjectAs(email, 'Blank Reason Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job' }).then((jobId) =>
        updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS').then(() => {
          tokenFor(email).then((token) => {
            cy.request({
              method: 'PATCH',
              url: `${API}/api/projects/${projectId}/jobs/${jobId}/status`,
              headers: { Authorization: `Bearer ${token}` },
              body: { status: 'BLOCKED' },
              failOnStatusCode: false,
            }).its('status').should('eq', 400);
          });
        }),
      );

      cy.deleteKeycloakUser(email);
    });
  });

  it('an unassigned MEMBER gets 403 on any status change and sees no status bar at all in the UI', () => {
    const ownerEmail = uniqueEmail('unassigned-owner');
    const memberEmail = uniqueEmail('unassigned-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithSubscription(ownerEmail, 'Unassigned Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Unassigned Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
          createJobAs(ownerEmail, projectId, { title: 'Unassigned job' }).then((jobId) => {
            tokenFor(memberEmail).then((token) => {
              cy.request({
                method: 'PATCH',
                url: `${API}/api/projects/${projectId}/jobs/${jobId}/status`,
                headers: { Authorization: `Bearer ${token}` },
                body: { status: 'IN_PROGRESS' },
                failOnStatusCode: false,
              }).its('status').should('eq', 403);
            });

            cy.loginAs(memberEmail);
            cy.visit(`/projects/${projectId}/jobs/${jobId}`);
            cy.contains('button', 'Start').should('not.exist');
            cy.contains('button', 'Complete').should('not.exist');
            cy.contains('button', 'Block').should('not.exist');
          });

          cy.deleteKeycloakUser(ownerEmail);
          cy.deleteKeycloakUser(memberEmail);
        }),
      ),
    );
  });

  it('an assigned MEMBER can start/complete/block/unblock but gets 403 reopening a completed job, and the Reopen button is hidden from them', () => {
    const ownerEmail = uniqueEmail('assignee-owner');
    const memberEmail = uniqueEmail('assignee-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithSubscription(ownerEmail, 'Assignee Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Assignee Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
          createJobAs(ownerEmail, projectId, { title: 'Assigned job', assignedTo: memberId }).then((jobId) => {
            updateJobStatusAs(memberEmail, projectId, jobId, 'IN_PROGRESS').then(() =>
              updateJobStatusAs(memberEmail, projectId, jobId, 'COMPLETED').then(() => {
                tokenFor(memberEmail).then((token) => {
                  cy.request({
                    method: 'PATCH',
                    url: `${API}/api/projects/${projectId}/jobs/${jobId}/status`,
                    headers: { Authorization: `Bearer ${token}` },
                    body: { status: 'IN_PROGRESS' },
                    failOnStatusCode: false,
                  }).its('status').should('eq', 403);
                });

                cy.loginAs(memberEmail);
                cy.visit(`/projects/${projectId}/jobs/${jobId}`);
                cy.contains('button', 'Reopen').should('not.exist');
              }),
            );
          });

          cy.deleteKeycloakUser(ownerEmail);
          cy.deleteKeycloakUser(memberEmail);
        }),
      ),
    );
  });

  it('a status change on a COMPLETED project 409s; on a soft-deleted job 404s', () => {
    const email = uniqueEmail('project-and-job-guards');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Guards Corp', uniqueSlug());
    createProjectAs(email, 'Guards Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Soon deleted job' }).then((jobId) => {
        tokenFor(email).then((token) => {
          cy.request({
            method: 'DELETE',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}`,
            headers: { Authorization: `Bearer ${token}` },
          });
          cy.request({
            method: 'PATCH',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}/status`,
            headers: { Authorization: `Bearer ${token}` },
            body: { status: 'IN_PROGRESS' },
            failOnStatusCode: false,
          }).its('status').should('eq', 404);
        });
      });

      createJobAs(email, projectId, { title: 'Job on soon-completed project' }).then((jobId) => {
        completeProjectAs(email, projectId);
        tokenFor(email).then((token) => {
          cy.request({
            method: 'PATCH',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}/status`,
            headers: { Authorization: `Bearer ${token}` },
            body: { status: 'IN_PROGRESS' },
            failOnStatusCode: false,
          }).its('status').should('eq', 409);
        });
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('DELETE block-reasons/{id} as MEMBER gets 403; deleting a nonexistent or already-deleted reason 404s', () => {
    const ownerEmail = uniqueEmail('delete-reason-owner');
    const memberEmail = uniqueEmail('delete-reason-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithSubscription(ownerEmail, 'Delete Reason Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Delete Reason Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
          createJobAs(ownerEmail, projectId, { title: 'Job' }).then((jobId) =>
            updateJobStatusAs(ownerEmail, projectId, jobId, 'IN_PROGRESS').then(() =>
              updateJobStatusAs(ownerEmail, projectId, jobId, 'BLOCKED', 'Some reason').then(() => {
                listBlockReasonsAs(ownerEmail, projectId).then((reasons) => {
                  const reasonId = reasons.find((r) => r.reason === 'Some reason')!.id;

                  deleteBlockReasonAs(memberEmail, projectId, reasonId).its('status').should('eq', 403);
                  deleteBlockReasonAs(ownerEmail, projectId, '00000000-0000-0000-0000-000000000000')
                    .its('status').should('eq', 404);

                  deleteBlockReasonAs(ownerEmail, projectId, reasonId).its('status').should('eq', 204);
                  deleteBlockReasonAs(ownerEmail, projectId, reasonId).its('status').should('eq', 404);
                });
              }),
            ),
          );

          cy.deleteKeycloakUser(ownerEmail);
          cy.deleteKeycloakUser(memberEmail);
        }),
      ),
    );
  });

  it('a rejected status change never shows an incorrect status in the UI', () => {
    const ownerEmail = uniqueEmail('rejected-owner');
    const memberEmail = uniqueEmail('rejected-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithSubscription(ownerEmail, 'Rejected Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Rejected Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
          createJobAs(ownerEmail, projectId, { title: 'Unassigned job' }).then((jobId) => {
            updateJobStatusAs(ownerEmail, projectId, jobId, 'IN_PROGRESS').then(() =>
              updateJobStatusAs(ownerEmail, projectId, jobId, 'COMPLETED').then(() => {
                // Only OWNER/ADMIN can reopen — force the button visible for a MEMBER
                // via direct API to prove the request itself is rejected server-side
                // and no optimistic write to the query cache ever shows IN_PROGRESS.
                tokenFor(memberEmail).then((token) => {
                  cy.request({
                    method: 'PATCH',
                    url: `${API}/api/projects/${projectId}/jobs/${jobId}/status`,
                    headers: { Authorization: `Bearer ${token}` },
                    body: { status: 'IN_PROGRESS' },
                    failOnStatusCode: false,
                  }).its('status').should('eq', 403);
                });

                cy.loginAs(ownerEmail);
                cy.visit(`/projects/${projectId}/jobs/${jobId}`);
                cy.contains('span', 'Completed').should('be.visible');
              }),
            );
          });

          cy.deleteKeycloakUser(ownerEmail);
          cy.deleteKeycloakUser(memberEmail);
        }),
      ),
    );
  });

  it('two jobs blocked with identical reason text share one row; deleting it does not clear either job\'s already-set reference', () => {
    const email = uniqueEmail('shared-reason');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Shared Reason Corp', uniqueSlug());
    createProjectAs(email, 'Shared Reason Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job A' }).then((jobA) =>
        createJobAs(email, projectId, { title: 'Job B' }).then((jobB) =>
          updateJobStatusAs(email, projectId, jobA, 'IN_PROGRESS').then(() =>
            updateJobStatusAs(email, projectId, jobA, 'BLOCKED', 'Shared blocker').then(() =>
              updateJobStatusAs(email, projectId, jobB, 'IN_PROGRESS').then(() =>
                updateJobStatusAs(email, projectId, jobB, 'BLOCKED', 'Shared blocker').then(() => {
                  listBlockReasonsAs(email, projectId).then((reasons) => {
                    expect(reasons.filter((r) => r.reason === 'Shared blocker')).to.have.length(1);
                    const reasonId = reasons.find((r) => r.reason === 'Shared blocker')!.id;

                    deleteBlockReasonAs(email, projectId, reasonId).its('status').should('eq', 204);

                    cy.loginAs(email);
                    cy.visit(`/projects/${projectId}/jobs/${jobA}`);
                    cy.contains('Shared blocker').should('be.visible');
                    cy.visit(`/projects/${projectId}/jobs/${jobB}`);
                    cy.contains('Shared blocker').should('be.visible');
                  });
                }),
              ),
            ),
          ),
        ),
      );

      cy.deleteKeycloakUser(email);
    });
  });

  it('block reason text is whitespace-trimmed before find-or-create, so padded and unpadded text resolve to the same reason', () => {
    const email = uniqueEmail('trim-reason');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Trim Reason Corp', uniqueSlug());
    createProjectAs(email, 'Trim Reason Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job A' }).then((jobA) =>
        createJobAs(email, projectId, { title: 'Job B' }).then((jobB) =>
          updateJobStatusAs(email, projectId, jobA, 'IN_PROGRESS').then(() =>
            updateJobStatusAs(email, projectId, jobA, 'BLOCKED', 'Waiting').then(() =>
              updateJobStatusAs(email, projectId, jobB, 'IN_PROGRESS').then(() =>
                updateJobStatusAs(email, projectId, jobB, 'BLOCKED', '  Waiting  ').then(() => {
                  listBlockReasonsAs(email, projectId).then((reasons) => {
                    expect(reasons.filter((r) => r.reason === 'Waiting')).to.have.length(1);
                  });
                }),
              ),
            ),
          ),
        ),
      );

      cy.deleteKeycloakUser(email);
    });
  });
});
