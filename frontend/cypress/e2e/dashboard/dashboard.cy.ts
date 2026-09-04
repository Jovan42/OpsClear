// ADR-0049 Appendix §18 (Dashboard). Uses cy.loginAs() per docs/dev/process/E2E.md.
//
// DashboardService/DashboardController already have exhaustive backend coverage
// (DashboardServiceTest: 16 unit tests covering summary counts, blocked/overdue
// sorting, the null/future/past-deadline boundary cases, MEMBER-vs-OWNER pending
// approvals, type-breakdown grouping+sorting; DashboardIntegrationTest: 19 tests
// covering 403/401, MEMBER job-scoping, and the same data-correctness matrix over
// HTTP). This spec deliberately does NOT re-prove that data logic — it focuses on
// what only the real frontend exercises: rendering, click-navigation, empty-state
// selection, addon-gating UI behavior (upgrade card + no underlying fetch), and
// preference-driven section visibility.
//
// One correction to this job's own ADR-0049 bullet: "No manual refresh exists yet
// (ADR-0048 unimplemented)" is stale — RefreshButton.tsx's own docstring says
// "ADR-0048: explicit, user-initiated refresh" and DashboardPage.tsx renders it.
// ADR-0048 shipped since ADR-0049 was written. Tested as real, working behavior
// below, not skipped.
//
// The exact-millisecond "deadline == now" boundary is already precisely covered by
// DashboardServiceTest with a controlled clock; this spec only checks the practical
// near-boundary direction (a few minutes future vs. past), since real wall-clock E2E
// timing can't deterministically hit an exact instant.

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
  createJobTypeAs,
  completeProjectAs,
  requestApprovalAs,
  API,
} from '../../support/orgApi';

describe('Dashboard', () => {
  it('/projects/:id redirects to the dashboard by default, and the happy-path summary/donut/type-breakdown render with working click-navigation', { tags: '@smoke' }, () => {
    const email = uniqueEmail('happy-path');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Falcon Corp', uniqueSlug());
    createProjectAs(email, 'Falcon Dashboard Project').then((projectId) =>
      createJobTypeAs(email, projectId, 'Bug', 'RED').then((typeId) => {
        createJobAs(email, projectId, { title: 'New job', typeId });
        createJobAs(email, projectId, { title: 'Another new job', typeId }).then((jobId) =>
          updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS'),
        );

        cy.loginAs(email);
        cy.visit(`/projects/${projectId}`);
        cy.url().should('include', `/projects/${projectId}/dashboard`);

        cy.contains('h2', 'Summary').should('be.visible');
        cy.contains('h2', 'Status distribution').should('be.visible');
        cy.contains('h2', 'By type').should('be.visible');
        cy.contains('span', 'Bug').parents('.flex.items-center.gap-3').contains('2').should('be.visible');

        cy.contains('button', 'New').click();
        cy.url().should('include', '/jobs?status=NEW');

        cy.go('back');
        cy.contains('button', 'Overdue').click();
        cy.url().should('not.include', '/jobs');
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('the type breakdown widget is hidden when no job has a type, and reappears sorted by count descending once jobs do', () => {
    const email = uniqueEmail('type-breakdown');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Nimbus Corp', uniqueSlug());
    createProjectAs(email, 'Nimbus Dashboard Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Untyped job' });

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/dashboard`);
      cy.contains('h2', 'By type').should('not.exist');

      createJobTypeAs(email, projectId, 'Bug', 'RED').then((bugId) =>
        createJobTypeAs(email, projectId, 'Feature', 'BLUE').then((featureId) => {
          createJobAs(email, projectId, { title: 'Bug 1', typeId: bugId });
          createJobAs(email, projectId, { title: 'Bug 2', typeId: bugId });
          createJobAs(email, projectId, { title: 'Feature 1', typeId: featureId });

          cy.visit(`/projects/${projectId}/dashboard`);
          cy.contains('h2', 'By type')
            .parents('div.bg-white')
            .find('.flex.items-center.gap-3')
            .first()
            .contains('Bug')
            .should('be.visible');
        }),
      );
    });

    cy.deleteKeycloakUser(email);
  });

  it('the blocked section is sorted oldest-first with the block reason shown, and a BLOCKED-and-overdue job appears in both the blocked and overdue sections', () => {
    const email = uniqueEmail('blocked-overdue');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Atlas Corp', uniqueSlug());
    createProjectAs(email, 'Atlas Dashboard Project').then((projectId) => {
      const past = new Date(Date.now() - 5 * 60_000).toISOString();

      // Sequential, not parallel — Cypress's command queue processes job A's full
      // create->in_progress->blocked chain before job B's starts, even though
      // they're separate statements, so blockedAt naturally differs with no
      // arbitrary wait needed.
      createJobAs(email, projectId, { title: 'First blocked', deadline: past }).then((jobA) =>
        updateJobStatusAs(email, projectId, jobA, 'IN_PROGRESS').then(() =>
          updateJobStatusAs(email, projectId, jobA, 'BLOCKED', 'Waiting on vendor'),
        ),
      );
      createJobAs(email, projectId, { title: 'Second blocked' }).then((jobB) =>
        updateJobStatusAs(email, projectId, jobB, 'IN_PROGRESS').then(() =>
          updateJobStatusAs(email, projectId, jobB, 'BLOCKED', 'Waiting on approval'),
        ),
      );

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/dashboard`);

      cy.contains('h2', 'Blocked').parent().parent().within(() => {
        cy.get('.space-y-2 > div').first().contains('First blocked');
        cy.get('.space-y-2 > div').eq(1).contains('Second blocked');
      });
      cy.contains('"Waiting on vendor"').should('be.visible');

      // "First blocked" also carries a past deadline, so it's overdue-and-blocked —
      // it must appear in the Overdue section too, not just Blocked.
      cy.contains('h2', 'Overdue').parent().parent().within(() => {
        cy.contains('First blocked').should('be.visible');
        cy.contains('Second blocked').should('not.exist');
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('the overdue section is sorted soonest-first and excludes COMPLETED jobs even with a past deadline', () => {
    const email = uniqueEmail('overdue-sort');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Cedar Corp', uniqueSlug());
    createProjectAs(email, 'Cedar Dashboard Project').then((projectId) => {
      const soon = new Date(Date.now() - 1 * 60_000).toISOString();
      const longAgo = new Date(Date.now() - 10 * 60_000).toISOString();

      createJobAs(email, projectId, { title: 'Due soon', deadline: soon });
      createJobAs(email, projectId, { title: 'Due long ago', deadline: longAgo });
      createJobAs(email, projectId, { title: 'Completed but overdue', deadline: longAgo }).then((jobId) =>
        updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS').then(() =>
          updateJobStatusAs(email, projectId, jobId, 'COMPLETED'),
        ),
      );

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/dashboard`);

      cy.contains('h2', 'Overdue').parent().parent().within(() => {
        cy.get('.space-y-2 > div').first().contains('Due long ago');
        cy.get('.space-y-2 > div').eq(1).contains('Due soon');
        cy.contains('Completed but overdue').should('not.exist');
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('the pending approvals section shows up to 5 with a "view all" link past that, is OWNER/ADMIN-only, and is entirely absent (not empty) for a MEMBER', () => {
    const ownerEmail = uniqueEmail('approvals-owner');
    const memberEmail = uniqueEmail('approvals-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Vega Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Vega Dashboard Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

          const jobIds: string[] = [];
          for (let i = 0; i < 6; i++) {
            createJobAs(ownerEmail, projectId, { title: `Approval job ${i}` }).then((jobId) => {
              jobIds.push(jobId);
              requestApprovalAs(ownerEmail, projectId, jobId, `Please approve ${i}`);
            });
          }

          cy.loginAs(ownerEmail);
          cy.visit(`/projects/${projectId}/dashboard`);
          cy.contains('h2', 'Pending Approvals').should('be.visible');
          cy.contains('h2', 'Pending Approvals').parent().parent().within(() => {
            cy.get('.space-y-2 > div').should('have.length', 5);
          });
          cy.contains('→ View all').click();
          cy.url().should('include', '/approvals');

          cy.loginAs(memberEmail);
          cy.visit(`/projects/${projectId}/dashboard`);
          cy.contains('h2', 'Pending Approvals').should('not.exist');
          cy.contains('Pending approvals').should('not.exist');
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('a COMPLETED project shows the completed banner, and a markdown project description renders formatted', () => {
    const email = uniqueEmail('completed-banner');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Juniper Corp', uniqueSlug());
    tokenFor(email).then((token) =>
      cy
        .request({
          method: 'POST',
          url: `${API}/api/projects`,
          headers: { Authorization: `Bearer ${token}` },
          body: { name: 'Juniper Dashboard Project', description: 'Ships **fast**' },
        })
        .then(({ body }: { body: { friendlyId: string } }) => {
          const projectId = body.friendlyId;
          cy.loginAs(email);
          cy.visit(`/projects/${projectId}/dashboard`);
          cy.get('strong').contains('fast').should('be.visible');
          cy.contains('This project is completed').should('not.exist');

          completeProjectAs(email, projectId);
          cy.visit(`/projects/${projectId}/dashboard`);
          cy.contains('This project is completed').should('be.visible');
        }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('zero jobs shows a distinct "no jobs yet" empty state; jobs with all sections empty shows the "all clear" empty state', () => {
    const email = uniqueEmail('empty-states');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Sable Corp', uniqueSlug());
    createProjectAs(email, 'Sable Dashboard Project').then((projectId) => {
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/dashboard`);
      cy.contains('No jobs yet.').should('be.visible');
      cy.contains('All clear').should('not.exist');

      createJobAs(email, projectId, { title: 'Just a new job' });
      cy.visit(`/projects/${projectId}/dashboard`);
      cy.contains('No jobs yet.').should('not.exist');
      cy.contains('All clear').should('be.visible');
    });

    cy.deleteKeycloakUser(email);
  });

  it('hiding a section via preference still hides it even with data, and the "all clear" state still shows correctly since a hidden-but-non-empty section no longer blocks it', () => {
    const email = uniqueEmail('section-toggle');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Orbit Corp', uniqueSlug());
    createProjectAs(email, 'Orbit Dashboard Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Blocked job' }).then((jobId) =>
        updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS').then(() =>
          updateJobStatusAs(email, projectId, jobId, 'BLOCKED', 'Some reason').then(() => {
            cy.loginAs(email);
            cy.visit(`/projects/${projectId}/dashboard`);
            cy.contains('h2', 'Blocked').should('be.visible');
            cy.contains('All clear').should('not.exist');

            cy.visit('/settings');
            cy.contains('Blocked jobs section').parent().parent().within(() => {
              cy.contains('button', 'Hide').click();
            });

            cy.visit(`/projects/${projectId}/dashboard`);
            cy.contains('h2', 'Blocked').should('not.exist');
            cy.contains('All clear').should('be.visible');
          }),
        ),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('the manual refresh button refetches on click', () => {
    const email = uniqueEmail('refresh-button');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Willow Corp', uniqueSlug());
    createProjectAs(email, 'Willow Dashboard Project').then((projectId) => {
      cy.intercept('GET', '**/api/projects/*/dashboard').as('dashboardFetch');
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/dashboard`);
      cy.wait('@dashboardFetch');
      cy.get('button[aria-label="Refresh"]').click();
      cy.wait('@dashboardFetch');
    });

    cy.deleteKeycloakUser(email);
  });

  it('the DASHBOARD add-on off shows an upgrade card and never fetches', () => {
    const email = uniqueEmail('addon-and-error');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Comet Corp', uniqueSlug());
    createProjectAs(email, 'Comet Dashboard Project').then((projectId) => {
      cy.intercept('GET', '**/api/projects/*/dashboard').as('dashboardFetch');
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/dashboard`);
      cy.contains('Dashboard').should('be.visible');
      cy.contains('button, a', 'Upgrade').should('exist');
      cy.get('@dashboardFetch.all').should('have.length', 0);
    });

    cy.deleteKeycloakUser(email);
  });

  it('a failed dashboard load shows PageError with a working retry', () => {
    const email = uniqueEmail('load-error');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Delta Corp', uniqueSlug());
    createProjectAs(email, 'Delta Dashboard Project').then((projectId) => {
      // The QueryClient has retry: 1 (main.tsx) — a single failing response gets
      // silently retried and succeeds, never surfacing an error. Every request must
      // fail (including that automatic retry) until the flag is flipped, well after
      // the error UI has already rendered, so the later "Try again" click is what
      // actually recovers it.
      let failing = true;
      cy.intercept('GET', '**/api/projects/*/dashboard', (req) => {
        if (failing) {
          req.reply({ statusCode: 500, body: { error: 'boom' } });
        } else {
          req.continue();
        }
      }).as('dashboardFetch');

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/dashboard`);
      cy.contains('Failed to load dashboard.').should('be.visible');
      cy.then(() => { failing = false; });
      cy.contains('button', 'Try again').click();
      cy.contains('Failed to load dashboard.').should('not.exist');
      cy.contains('Summary').should('be.visible');
    });

    cy.deleteKeycloakUser(email);
  });

  it('a non-member gets 403 fetching the dashboard directly; a nonexistent project 404s', () => {
    const ownerEmail = uniqueEmail('non-member-owner');
    const outsiderEmail = uniqueEmail('non-member-outsider');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(outsiderEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Ember Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Ember Dashboard Project').then((projectId) =>
        userIdFor(outsiderEmail).then((outsiderId) => {
          addMember(orgId, ownerEmail, outsiderId, 'MEMBER');

          tokenFor(outsiderEmail).then((token) => {
            cy.request({
              method: 'GET',
              url: `${API}/api/projects/${projectId}/dashboard`,
              headers: { Authorization: `Bearer ${token}` },
              failOnStatusCode: false,
            }).its('status').should('eq', 403);
          });

          tokenFor(ownerEmail).then((token) => {
            cy.request({
              method: 'GET',
              url: `${API}/api/projects/00000000-0000-0000-0000-000000000000/dashboard`,
              headers: { Authorization: `Bearer ${token}` },
              failOnStatusCode: false,
            }).its('status').should('eq', 404);
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(outsiderEmail);
  });

  it('a job\'s deadline a few minutes in the future is not overdue; a few minutes in the past is', () => {
    const email = uniqueEmail('near-boundary');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Sparrow Corp', uniqueSlug());
    createProjectAs(email, 'Sparrow Dashboard Project').then((projectId) => {
      const future = new Date(Date.now() + 5 * 60_000).toISOString();
      const past = new Date(Date.now() - 5 * 60_000).toISOString();
      createJobAs(email, projectId, { title: 'Future deadline', deadline: future });
      createJobAs(email, projectId, { title: 'Past deadline', deadline: past });

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/dashboard`);
      cy.contains('h2', 'Overdue').parent().parent().within(() => {
        cy.contains('Past deadline').should('be.visible');
        cy.contains('Future deadline').should('not.exist');
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('a MEMBER\'s dashboard is scoped to their own assigned jobs only', () => {
    const ownerEmail = uniqueEmail('member-scope-owner');
    const memberEmail = uniqueEmail('member-scope-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Talon Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Talon Dashboard Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

          createJobAs(ownerEmail, projectId, { title: 'Assigned to member', assignedTo: memberId });
          createJobAs(ownerEmail, projectId, { title: 'Not assigned to member' });

          cy.loginAs(memberEmail);
          cy.visit(`/projects/${projectId}/dashboard`);
          cy.contains('span.text-2xl', '1').should('be.visible');
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });
});
