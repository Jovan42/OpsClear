// ADR-0049 Appendix §17 (Approvals Workflow + Queue). Uses cy.loginAs() per
// docs/dev/process/E2E.md.
//
// ADR-0016 §5 claims a 409 on decide closes the modal and shows a toast. The actual
// current code (ApprovalDecisionModal.tsx) does neither — on a 409 the mutation's
// onSuccess (which calls handleClose) never fires, so the modal stays open with an
// inline red banner instead. This spec asserts the real behavior, not the stale ADR
// text (ADR-0049's own bullet already flags this drift).
//
// True concurrent requests aren't practically constructible through Cypress's single
// command queue (each cy.request() blocks until it resolves before the next is even
// sent, so two sequential decides are never actually in flight at the same time).
// The "concurrent decision race" test below instead decides an approval once, then
// immediately decides it again — this exercises the exact same atomic
// `UPDATE ... WHERE status = 'PENDING'` guard a genuine race would hit (the second
// caller always finds status already flipped), just without real wall-clock overlap.
//
// Checked the two obvious surfaces (ApprovalQueuePage.tsx, ApprovalList.tsx, and
// JobStatusBar's request-approval trigger) for the MEMBER-controls-shown-unconditionally
// bug found independently on Milestones (JOB-257), Templates (JOB-260), and Schedules
// (JOB-263) — all three are already correctly gated here (role-based redirect on the
// queue page, isOwnerOrAdmin checks on both decide-button surfaces, and canAct
// combining isOwnerOrAdmin || isAssigned for the request trigger). Not a 4th instance.

import {
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  userIdFor,
  createOrgWithFullAccess,
  createOrgWithSubscription,
  createOrgWithAddonPastDue,
  createProjectAs,
  createJobAs,
  addMember,
  addProjectMember,
  requestApprovalAs,
  decideApprovalAs,
  listApprovalsByJobAs,
  listPendingApprovalsAs,
  API,
} from '../../support/orgApi';

describe('Approvals Workflow + Queue', () => {
  it('a MEMBER requests approval on their own assigned job; an OWNER/ADMIN requests on any job', () => {
    const ownerEmail = uniqueEmail('request-owner');
    const memberEmail = uniqueEmail('request-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Falcon Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Falcon Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

          createJobAs(ownerEmail, projectId, { title: 'Assigned job', assignedTo: memberId }).then((jobA) => {
            requestApprovalAs(memberEmail, projectId, jobA, 'Need to purchase parts — €800')
              .its('status').should('eq', 201);
            listApprovalsByJobAs(ownerEmail, projectId, jobA).then((approvals) => {
              expect(approvals).to.have.length(1);
              expect(approvals[0].status).to.equal('PENDING');
            });
          });

          createJobAs(ownerEmail, projectId, { title: 'Unassigned job' }).then((jobB) => {
            requestApprovalAs(ownerEmail, projectId, jobB, 'Owner-initiated request')
              .its('status').should('eq', 201);
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('an OWNER/ADMIN approves and rejects, with and without a comment — status, approverId, and decidedAt are set', () => {
    const email = uniqueEmail('decide-basic');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Nimbus Corp', uniqueSlug());
    createProjectAs(email, 'Nimbus Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Job A' }).then((jobId) =>
        requestApprovalAs(email, projectId, jobId, 'Approve with comment').then((res) => {
          const approvalId = res.body.id as string;
          decideApprovalAs(email, projectId, jobId, approvalId, 'APPROVED', 'Looks good').then((decideRes) => {
            expect(decideRes.status).to.equal(200);
            expect(decideRes.body.status).to.equal('APPROVED');
            expect(decideRes.body.comment).to.equal('Looks good');
            expect(decideRes.body.approverId).to.be.a('string');
            expect(decideRes.body.decidedAt).to.be.a('string');
          });
        }),
      ).then(() =>
        createJobAs(email, projectId, { title: 'Job B' }).then((jobId) =>
          requestApprovalAs(email, projectId, jobId, 'Reject without comment').then((res) => {
            const approvalId = res.body.id as string;
            decideApprovalAs(email, projectId, jobId, approvalId, 'REJECTED').then((decideRes) => {
              expect(decideRes.status).to.equal(200);
              expect(decideRes.body.status).to.equal('REJECTED');
              expect(decideRes.body.comment).to.equal(null);
            });
          }),
        ),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('a job accumulates multiple simultaneous PENDING approvals, each independently decidable', () => {
    const email = uniqueEmail('multi-pending');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Atlas Corp', uniqueSlug());
    createProjectAs(email, 'Atlas Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Multi-approval job' }).then((jobId) =>
        requestApprovalAs(email, projectId, jobId, 'Purchase transformer').then((resA) =>
          requestApprovalAs(email, projectId, jobId, 'Close road access').then((resB) => {
            const idA = resA.body.id as string;
            const idB = resB.body.id as string;

            decideApprovalAs(email, projectId, jobId, idA, 'APPROVED').its('status').should('eq', 200);

            listApprovalsByJobAs(email, projectId, jobId).then((approvals) => {
              const a = approvals.find((x) => x.id === idA)!;
              const b = approvals.find((x) => x.id === idB)!;
              expect(a.status).to.equal('APPROVED');
              expect(b.status).to.equal('PENDING');
            });

            decideApprovalAs(email, projectId, jobId, idB, 'REJECTED').its('status').should('eq', 200);
          }),
        ),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('the queue page groups by job oldest-first, orders groups by oldest pending item, "→ Job" navigates, and a successful decide removes the card', () => {
    const email = uniqueEmail('queue-layout');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Cedar Corp', uniqueSlug());
    createProjectAs(email, 'Cedar Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Later Job' }).then((laterJob) =>
        createJobAs(email, projectId, { title: 'Earlier Job' }).then((earlierJob) => {
          // "Later Job" gets the chronologically FIRST request overall, so despite
          // its name, its group must float to the top — group order follows the
          // oldest pending item across the whole project, not the job's own name
          // or creation order.
          requestApprovalAs(email, projectId, laterJob, 'Later job first request').then(() =>
            requestApprovalAs(email, projectId, earlierJob, 'Earlier job — requested after both of Later Job\'s').then(() =>
              requestApprovalAs(email, projectId, laterJob, 'Later job second request').then(() => {
                cy.loginAs(email);
                cy.visit(`/projects/${projectId}/approvals`);

                // Anchor on the real content having rendered before reading DOM
                // order — the page's own h2 doesn't exist yet during the loading
                // skeleton, and querying too early would silently match zero.
                cy.contains('h2', 'Later Job').should('be.visible');
                cy.contains('h2', 'Earlier Job').should('be.visible');
                cy.get('h2').then(($headings) => {
                  const texts = [...$headings].map((el) => el.textContent);
                  const laterIdx = texts.indexOf('Later Job');
                  const earlierIdx = texts.indexOf('Earlier Job');
                  expect(laterIdx).to.be.lessThan(earlierIdx);
                });

                cy.contains('h2', 'Later Job')
                  .parent()
                  .contains('button', '→ Job')
                  .click();
                // The queue links via the raw UUID jobId (ApprovalResponse.jobId),
                // not the friendlyId createJobAs returns — the route resolves both,
                // so just confirm it actually navigated to a job detail page.
                cy.url().should('include', '/jobs/').and('not.include', '/approvals');
                cy.contains('Later job first request').should('be.visible');

                cy.visit(`/projects/${projectId}/approvals`);
                cy.contains('Later job first request')
                  .parent()
                  .contains('button', 'Approve')
                  .click();
                cy.get('.z-50:visible').within(() => cy.contains('button', 'Approve').click());
                cy.contains('Later job first request').should('not.exist');
              }),
            ),
          );
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('a blank or over-2000-char description 400s; MEMBER on an unassigned job 403s; a non-member 403s; MEMBER deciding or listing pending 403s', () => {
    const ownerEmail = uniqueEmail('validation-owner');
    const memberEmail = uniqueEmail('validation-member');
    const outsiderEmail = uniqueEmail('validation-outsider');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(outsiderEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Vega Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Vega Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) =>
          userIdFor(outsiderEmail).then((outsiderId) => {
            addMember(orgId, ownerEmail, memberId, 'MEMBER');
            addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
            addMember(orgId, ownerEmail, outsiderId, 'MEMBER');
            // outsiderEmail is an ORG member but never added to this PROJECT.

            createJobAs(ownerEmail, projectId, { title: 'Unassigned job' }).then((jobId) => {
              requestApprovalAs(ownerEmail, projectId, jobId, '   ').its('status').should('eq', 400);
              requestApprovalAs(ownerEmail, projectId, jobId, 'x'.repeat(2001)).its('status').should('eq', 400);
              requestApprovalAs(memberEmail, projectId, jobId, 'Trying my luck').its('status').should('eq', 403);
              requestApprovalAs(outsiderEmail, projectId, jobId, 'Not even a project member').its('status').should('eq', 403);

              requestApprovalAs(ownerEmail, projectId, jobId, 'Real request').then((res) => {
                const approvalId = res.body.id as string;
                decideApprovalAs(memberEmail, projectId, jobId, approvalId, 'APPROVED').its('status').should('eq', 403);
                listPendingApprovalsAs(memberEmail, projectId).its('status').should('eq', 403);
              });
            });
          }),
        ),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
    cy.deleteKeycloakUser(outsiderEmail);
  });

  it('PATCH status:PENDING 400s with CANNOT_SET_PENDING; deciding an approval that doesn\'t belong to the given job or a wrong-project job 404s; deciding on a COMPLETED project 409s', () => {
    const email = uniqueEmail('decide-guards');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Sable Corp', uniqueSlug());
    createProjectAs(email, 'Sable Project A').then((projectIdA) =>
      createProjectAs(email, 'Sable Project B').then((projectIdB) =>
        createJobAs(email, projectIdA, { title: 'Job A1' }).then((jobA1) =>
          createJobAs(email, projectIdA, { title: 'Job A2' }).then((jobA2) =>
            createJobAs(email, projectIdB, { title: 'Job B1' }).then((jobB1) =>
              requestApprovalAs(email, projectIdA, jobA1, 'On job A1').then((resA1) => {
                const approvalId = resA1.body.id as string;

                tokenFor(email).then((token) => {
                  cy.request({
                    method: 'PATCH',
                    url: `${API}/api/projects/${projectIdA}/jobs/${jobA1}/approvals/${approvalId}/status`,
                    headers: { Authorization: `Bearer ${token}` },
                    body: { status: 'PENDING' },
                    failOnStatusCode: false,
                  }).its('status').should('eq', 400);
                });

                // Approval belongs to jobA1, not jobA2.
                decideApprovalAs(email, projectIdA, jobA2, approvalId, 'APPROVED').its('status').should('eq', 404);
                // jobA1 doesn't belong to projectIdB.
                decideApprovalAs(email, projectIdB, jobA1, approvalId, 'APPROVED').its('status').should('eq', 404);
                // jobB1 has no such approval at all, and belongs to a different project than jobA1's approval.
                decideApprovalAs(email, projectIdA, jobB1, approvalId, 'APPROVED').its('status').should('eq', 404);

                tokenFor(email).then((token) => {
                  cy.request({
                    method: 'PATCH',
                    url: `${API}/api/projects/${projectIdA}/status`,
                    headers: { Authorization: `Bearer ${token}` },
                    body: { status: 'COMPLETED' },
                  });
                  cy.request({
                    method: 'PATCH',
                    url: `${API}/api/projects/${projectIdA}/jobs/${jobA1}/approvals/${approvalId}/status`,
                    headers: { Authorization: `Bearer ${token}` },
                    body: { status: 'APPROVED' },
                    failOnStatusCode: false,
                  }).its('status').should('eq', 409);
                });
              }),
            ),
          ),
        ),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('two decisions on the same approval: the first wins (200), the second gets 409 ALREADY_DECIDED', () => {
    const email = uniqueEmail('decision-race');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Comet Corp', uniqueSlug());
    createProjectAs(email, 'Comet Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Race job' }).then((jobId) =>
        requestApprovalAs(email, projectId, jobId, 'Race target').then((res) => {
          const approvalId = res.body.id as string;
          decideApprovalAs(email, projectId, jobId, approvalId, 'APPROVED').its('status').should('eq', 200);
          decideApprovalAs(email, projectId, jobId, approvalId, 'REJECTED').then((second) => {
            expect(second.status).to.equal(409);
          });

          listApprovalsByJobAs(email, projectId, jobId).then((approvals) => {
            expect(approvals[0].status).to.equal('APPROVED');
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('the APPROVALS add-on off 403s every endpoint; a PAST_DUE org blocks writes but reads still succeed', () => {
    const noAddonEmail = uniqueEmail('no-addon');
    cy.createKeycloakUser(noAddonEmail, 'E2E', 'Tester');
    createOrgWithSubscription(noAddonEmail, 'Willow Corp', uniqueSlug());
    createProjectAs(noAddonEmail, 'Willow Project').then((projectId) =>
      createJobAs(noAddonEmail, projectId, { title: 'No addon job' }).then((jobId) => {
        requestApprovalAs(noAddonEmail, projectId, jobId, 'Should be blocked').its('status').should('eq', 403);
        tokenFor(noAddonEmail).then((token) => {
          cy.request({
            method: 'GET',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}/approvals`,
            headers: { Authorization: `Bearer ${token}` },
            failOnStatusCode: false,
          }).its('status').should('eq', 403);
        });
        listPendingApprovalsAs(noAddonEmail, projectId).its('status').should('eq', 403);
      }),
    );
    cy.deleteKeycloakUser(noAddonEmail);

    const pastDueEmail = uniqueEmail('past-due');
    cy.createKeycloakUser(pastDueEmail, 'E2E', 'Tester');
    createOrgWithAddonPastDue(pastDueEmail, 'Orbit Corp', uniqueSlug(), 'APPROVALS').then(() =>
      createProjectAs(pastDueEmail, 'Orbit Project').then((projectId) =>
        createJobAs(pastDueEmail, projectId, { title: 'Past due job' }).then((jobId) => {
          requestApprovalAs(pastDueEmail, projectId, jobId, 'Blocked by past-due').its('status').should('eq', 403);
          listApprovalsByJobAs(pastDueEmail, projectId, jobId).then((approvals) => {
            expect(approvals).to.have.length(0);
          });
        }),
      ),
    );
    cy.deleteKeycloakUser(pastDueEmail);
  });

  it('a MEMBER navigating directly to the queue URL is silently redirected to the job list', () => {
    const ownerEmail = uniqueEmail('redirect-owner');
    const memberEmail = uniqueEmail('redirect-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Sparrow Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Sparrow Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

          cy.loginAs(memberEmail);
          cy.visit(`/projects/${projectId}/approvals`);
          cy.url().should('include', `/projects/${projectId}/jobs`);
          cy.url().should('not.include', '/approvals');
          cy.contains('Access denied').should('not.exist');
          cy.contains('Access Denied').should('not.exist');
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('a 409 on decide in the UI keeps the modal open with an inline banner, not the stale ADR-0016 "closes + toast" behavior', () => {
    const ownerEmail = uniqueEmail('ui-409-owner');
    const adminEmail = uniqueEmail('ui-409-admin');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(adminEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Talon Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Talon Project').then((projectId) =>
        userIdFor(adminEmail).then((adminId) => {
          addMember(orgId, ownerEmail, adminId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, adminId, 'ADMIN');

          createJobAs(ownerEmail, projectId, { title: 'UI 409 job' }).then((jobId) =>
            requestApprovalAs(ownerEmail, projectId, jobId, 'Decide me').then((res) => {
              const approvalId = res.body.id as string;

              cy.loginAs(ownerEmail);
              cy.visit(`/projects/${projectId}/jobs/${jobId}`);
              // Section is already auto-expanded (a PENDING approval exists before
              // the visit) — no toggle click needed.
              cy.contains('button', 'Approve').click();
              cy.get('.z-50:visible').should('be.visible');

              // The modal is open on stale PENDING state; someone else decides the
              // SAME approval out from under it before the modal is submitted.
              decideApprovalAs(adminEmail, projectId, jobId, approvalId, 'APPROVED').its('status').should('eq', 200);

              cy.get('.z-50:visible').within(() => {
                cy.contains('button', 'Approve').click();
                cy.contains('already decided by another user').should('be.visible');
                cy.get('textarea').should('be.visible');
              });
            }),
          );
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(adminEmail);
  });

  it('an approver can decide their own requested approval — no self-approval restriction', () => {
    const email = uniqueEmail('self-approve');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Ember Corp', uniqueSlug());
    createProjectAs(email, 'Ember Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Self-approve job' }).then((jobId) =>
        requestApprovalAs(email, projectId, jobId, 'I requested this myself').then((res) => {
          const approvalId = res.body.id as string;
          decideApprovalAs(email, projectId, jobId, approvalId, 'APPROVED').its('status').should('eq', 200);
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('a job soft-deleted after an approval was requested 404s on decide', () => {
    const email = uniqueEmail('soft-delete-race');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Delta Corp', uniqueSlug());
    createProjectAs(email, 'Delta Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Soon-deleted job' }).then((jobId) =>
        requestApprovalAs(email, projectId, jobId, 'About to lose its job').then((res) => {
          const approvalId = res.body.id as string;
          tokenFor(email).then((token) => {
            cy.request({
              method: 'DELETE',
              url: `${API}/api/projects/${projectId}/jobs/${jobId}`,
              headers: { Authorization: `Bearer ${token}` },
            });
          });
          decideApprovalAs(email, projectId, jobId, approvalId, 'APPROVED').its('status').should('eq', 404);
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('a whitespace-only description 400s before trimming; a valid padded description is persisted trimmed', () => {
    const email = uniqueEmail('trim-description');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Juniper Corp', uniqueSlug());
    createProjectAs(email, 'Juniper Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Trim job' }).then((jobId) => {
        requestApprovalAs(email, projectId, jobId, '   \t  ').its('status').should('eq', 400);
        requestApprovalAs(email, projectId, jobId, '  Needs a real trim  ').then((res) => {
          expect(res.status).to.equal(201);
          expect(res.body.description).to.equal('Needs a real trim');
        });
      }),
    );

    cy.deleteKeycloakUser(email);
  });
});
