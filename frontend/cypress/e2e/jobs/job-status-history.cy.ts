// ADR-0049 Appendix §10 (Job Status History). Uses cy.loginAs() per
// docs/dev/process/E2E.md.
//
// The backend endpoint itself (creation writes the first entry, every transition
// appends, oldest-first ordering, cross-project 404, member-not-assigned 403, no
// edit/delete route) is already thoroughly covered at the integration level in
// JobHistoryIntegrationTest.java — this spec focuses on what only the real frontend
// exercises: the StatusHistory accordion's rendering (transition labels, inline
// block reasons, per-entry duration, auto-expand), and the JOB_STATUS_HISTORY
// add-on gate hiding the whole section from the UI (JobDetailPage.tsx only renders
// <StatusHistory> inside `hasAddon('JOB_STATUS_HISTORY') && (...)`).

import {
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  createOrgWithFullAccess,
  createOrgWithSubscription,
  createProjectAs,
  createJobAs,
  updateJobStatusAs,
  getJobHistoryAs,
  API,
} from '../../support/orgApi';

describe('Job Status History', () => {
  it('a freshly created job already has one "Created as New" entry, and the section starts expanded with no click needed', () => {
    const email = uniqueEmail('created-entry');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Created Entry Corp', uniqueSlug());
    createProjectAs(email, 'Created Entry Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Fresh job' }).then((jobId) => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs/${jobId}`);

        // Every job gets its creation entry written immediately server-side, so the
        // section is never actually empty in practice — defaultExpanded={history.length
        // > 0} is therefore always true by the time this page can render it. No click.
        cy.contains('button', 'Status history').should('be.visible');
        cy.contains('Created as').should('be.visible');
        cy.contains('span', 'New').should('be.visible');

        getJobHistoryAs(email, projectId, jobId).then((res) => {
          expect(res.body).to.have.length(1);
          expect(res.body[0].changedFrom).to.equal(null);
          expect(res.body[0].changedTo).to.equal('NEW');
        });
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('walks a job through its full lifecycle and renders every transition oldest-first, with the block reason shown inline and a duration on every entry but the last', () => {
    const email = uniqueEmail('lifecycle-history');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Lifecycle History Corp', uniqueSlug());
    createProjectAs(email, 'Lifecycle History Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Lifecycle job' }).then((jobId) => {
        updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS').then(() =>
          updateJobStatusAs(email, projectId, jobId, 'BLOCKED', 'Waiting on parts').then(() =>
            updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS').then(() =>
              updateJobStatusAs(email, projectId, jobId, 'COMPLETED').then(() => {
                cy.loginAs(email);
                cy.visit(`/projects/${projectId}/jobs/${jobId}`);

                // 5 total: created + 4 transitions. Badge shows the count next to the heading.
                cy.contains('button', 'Status history').within(() => {
                  cy.contains('5').should('be.visible');
                });

                cy.get('ol > li').should('have.length', 5);
                cy.contains('Reason: Waiting on parts').should('be.visible');

                // Every entry except the last shows a duration line ("N minutes in X").
                cy.get('ol > li').each(($li, index, $all) => {
                  if (index < $all.length - 1) {
                    cy.wrap($li).contains(/ in /).should('be.visible');
                  } else {
                    cy.wrap($li).contains(/ in /).should('not.exist');
                  }
                });
              }),
            ),
          ),
        );
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('reopening a completed job and completing it again produces two distinct IN_PROGRESS → COMPLETED entries, not a merged/deduped one', () => {
    const email = uniqueEmail('reopen-recomplete');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Reopen Recomplete Corp', uniqueSlug());
    createProjectAs(email, 'Reopen Recomplete Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job' }).then((jobId) => {
        updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS').then(() =>
          updateJobStatusAs(email, projectId, jobId, 'COMPLETED').then(() =>
            updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS').then(() =>
              updateJobStatusAs(email, projectId, jobId, 'COMPLETED').then(() => {
                getJobHistoryAs(email, projectId, jobId).then((res) => {
                  const completions = res.body.filter(
                    (e: { changedFrom: string | null; changedTo: string }) =>
                      e.changedFrom === 'IN_PROGRESS' && e.changedTo === 'COMPLETED',
                  );
                  expect(completions).to.have.length(2);
                  expect(completions[0].id).to.not.equal(completions[1].id);
                });
              }),
            ),
          ),
        );
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('without the JOB_STATUS_HISTORY add-on, the whole section is absent from the UI and the endpoint 403s directly', () => {
    const email = uniqueEmail('no-history-addon');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'No History Addon Corp', uniqueSlug());
    createProjectAs(email, 'No History Addon Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job' }).then((jobId) => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs/${jobId}`);
        cy.contains('button', 'Status history').should('not.exist');

        getJobHistoryAs(email, projectId, jobId).its('status').should('eq', 403);
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('history for a job in another project 404s; there is no edit or delete route for a history entry', () => {
    const email = uniqueEmail('history-guards');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'History Guards Corp', uniqueSlug());
    createProjectAs(email, 'History Guards Project A').then((projectIdA) => {
      createProjectAs(email, 'History Guards Project B').then((projectIdB) => {
        createJobAs(email, projectIdA, { title: 'Job in A' }).then((jobId) => {
          getJobHistoryAs(email, projectIdB, jobId).its('status').should('eq', 404);

          tokenFor(email).then((token) => {
            cy.request({
              method: 'DELETE',
              url: `${API}/api/projects/${projectIdA}/jobs/${jobId}/history/00000000-0000-0000-0000-000000000000`,
              headers: { Authorization: `Bearer ${token}` },
              failOnStatusCode: false,
            }).its('status').should('eq', 404);

            cy.request({
              method: 'PATCH',
              url: `${API}/api/projects/${projectIdA}/jobs/${jobId}/history/00000000-0000-0000-0000-000000000000`,
              headers: { Authorization: `Bearer ${token}` },
              body: {},
              failOnStatusCode: false,
            }).its('status').should('eq', 404);
          });
        });
      });

      cy.deleteKeycloakUser(email);
    });
  });
});
