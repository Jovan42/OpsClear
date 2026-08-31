// ADR-0049 Appendix §5 (Job List & Filtering). Uses cy.loginAs() per
// docs/dev/process/E2E.md.

import {
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  createOrgWithFullAccess,
  createOrgWithSubscription,
  createProjectAs,
  createJobAs,
  updateJobStatusAs,
  addMember,
  addProjectMember,
  userIdFor,
  createMilestoneAs,
  createJobTypeAs,
  completeProjectAs,
  API,
} from '../../support/orgApi';

describe('Job List & Filtering', () => {
  it('status tabs show correct counts and filter the list', () => {
    const email = uniqueEmail('tabs');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Tabs Corp', uniqueSlug());
    createProjectAs(email, 'Tabs Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'New job' });
      createJobAs(email, projectId, { title: 'In progress job' }).then((jobId) =>
        updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS'),
      );
      createJobAs(email, projectId, { title: 'Blocked job' }).then((jobId) =>
        updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS').then(() =>
          updateJobStatusAs(email, projectId, jobId, 'BLOCKED', 'waiting on client'),
        ),
      );
      createJobAs(email, projectId, { title: 'Completed job' }).then((jobId) =>
        updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS').then(() =>
          updateJobStatusAs(email, projectId, jobId, 'COMPLETED'),
        ),
      );

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);

      // Scoped to the status-tab bar (identified via the unambiguous "All" tab's
      // parent) since a plain `cy.contains('button', 'New')` also matches the
      // unrelated "+ New Job" button elsewhere on the page.
      cy.contains('button', 'All').parent().as('tabBar');
      cy.get('@tabBar').contains('button', 'All').find('span').last().should('contain.text', '4');
      cy.get('@tabBar').contains('button', 'New').find('span').last().should('contain.text', '1');
      cy.contains('button', 'In Progress').find('span').last().should('contain.text', '1');
      cy.contains('button', 'Blocked').find('span').last().should('contain.text', '1');
      cy.contains('button', 'Completed').find('span').last().should('contain.text', '1');

      cy.contains('button', 'Blocked').click();
      cy.get('table').contains('Blocked job').should('be.visible');
      cy.contains('New job').should('not.exist');
      cy.contains('In progress job').should('not.exist');
      cy.contains('Completed job').should('not.exist');

      cy.deleteKeycloakUser(email);
    });
  });

  it('search matches job title and client', () => {
    const email = uniqueEmail('search');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Search Corp', uniqueSlug());
    createProjectAs(email, 'Search Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Fix the login bug' });
      createJobAs(email, projectId, { title: 'Unrelated task', client: 'Acme Rockets' });

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);

      cy.get('input[type="search"]').type('login');
      cy.get('table').contains('Fix the login bug').should('be.visible');
      cy.contains('Unrelated task').should('not.exist');

      cy.get('input[type="search"]').clear();
      cy.get('input[type="search"]').type('Acme');
      cy.get('table').contains('Unrelated task').should('be.visible');
      cy.contains('Fix the login bug').should('not.exist');

      cy.deleteKeycloakUser(email);
    });
  });

  it('priority filter narrows the list to matching jobs', () => {
    const email = uniqueEmail('priority');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Priority Corp', uniqueSlug());
    createProjectAs(email, 'Priority Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Critical job', priority: 'CRITICAL' });
      createJobAs(email, projectId, { title: 'Low job', priority: 'LOW' });

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);
      cy.get('table').contains('Critical job').should('be.visible');
      cy.get('table').contains('Low job').should('be.visible');

      cy.get('select').eq(0).select('CRITICAL');
      cy.get('table').contains('Critical job').should('be.visible');
      cy.contains('Low job').should('not.exist');

      cy.deleteKeycloakUser(email);
    });
  });

  it('toggling to flat view reveals the milestone filter, which narrows the list', () => {
    const email = uniqueEmail('milestone-filter');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Milestone Filter Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Milestone Filter Project').then((projectId) => {
        createMilestoneAs(email, projectId, 'Phase 1').then((msId) => {
          createJobAs(email, projectId, { title: 'Phase 1 job', milestoneId: msId });
          createJobAs(email, projectId, { title: 'Unassigned job' });

          cy.loginAs(email);
          cy.visit(`/projects/${projectId}/jobs`);
          // Default view mode preference is GROUPED — the milestone <select> only
          // renders once the view is flat, so it isn't visible yet.
          cy.contains('Phase 1').should('be.visible');
          cy.get('select').should('have.length', 1);

          cy.contains('button', 'Grouped').click();
          cy.get('select').should('have.length', 2);
          cy.get('select').eq(1).select('Phase 1');
          cy.get('table').contains('Phase 1 job').should('be.visible');
          cy.contains('Unassigned job').should('not.exist');
          // Forced into flat view: the view-mode toggle button is gone entirely while
          // a milestone filter is active (component only renders it when !milestoneFilterActive).
          cy.contains('button', 'Flat').should('not.exist');

          cy.deleteKeycloakUser(email);
        });
      }),
    );
  });

  it('opening the job list with a milestone filter already in the URL forces flat view, even though grouped is the preference default', () => {
    const email = uniqueEmail('milestone-url');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Milestone URL Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Milestone URL Project').then((projectId) => {
        createMilestoneAs(email, projectId, 'Phase 1').then((msId) => {
          createJobAs(email, projectId, { title: 'Phase 1 job', milestoneId: msId });
          createJobAs(email, projectId, { title: 'Unassigned job' });

          cy.loginAs(email);
          cy.visit(`/projects/${projectId}/jobs?milestone=${msId}`);
          cy.get('table').contains('Phase 1 job').should('be.visible');
          cy.contains('Unassigned job').should('not.exist');
          cy.contains('button', 'Grouped').should('not.exist');
          cy.contains('button', 'Flat').should('not.exist');
          cy.get('table').should('exist');

          cy.deleteKeycloakUser(email);
        });
      }),
    );
  });

  it('job type filter narrows the list (JOB_TYPES add-on)', () => {
    const email = uniqueEmail('type-filter');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Type Filter Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Type Filter Project').then((projectId) => {
        createJobTypeAs(email, projectId, 'Bug', 'RED').then((typeId) => {
          createJobAs(email, projectId, { title: 'Bug job', typeId });
          createJobAs(email, projectId, { title: 'Untyped job' });

          cy.loginAs(email);
          cy.visit(`/projects/${projectId}/jobs`);
          cy.get('table').contains('Bug job').should('be.visible');
          cy.get('table').contains('Untyped job').should('be.visible');

          cy.get('select').eq(1).select('Bug');
          cy.get('table').contains('Bug job').should('be.visible');
          cy.contains('Untyped job').should('not.exist');

          cy.deleteKeycloakUser(email);
        });
      }),
    );
  });

  it('clicking a sortable column header toggles ascending/descending order', () => {
    const email = uniqueEmail('sort');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Sort Corp', uniqueSlug());
    createProjectAs(email, 'Sort Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Bravo job' });
      createJobAs(email, projectId, { title: 'Alpha job' });

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);

      cy.contains('th', 'Title').click();
      cy.get('tbody tr').first().should('contain.text', 'Alpha job');
      cy.contains('th', 'Title').click();
      cy.get('tbody tr').first().should('contain.text', 'Bravo job');

      cy.deleteKeycloakUser(email);
    });
  });

  it('grouped view shows a per-milestone progress bar; toggling switches to flat view', () => {
    const email = uniqueEmail('grouped');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Grouped Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Milestone View Project').then((projectId) => {
        createMilestoneAs(email, projectId, 'Launch').then((msId) => {
          createJobAs(email, projectId, { title: 'Launch job 1', milestoneId: msId }).then((jobId) =>
            updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS').then(() =>
              updateJobStatusAs(email, projectId, jobId, 'COMPLETED'),
            ),
          );
          createJobAs(email, projectId, { title: 'Launch job 2', milestoneId: msId });

          cy.loginAs(email);
          cy.visit(`/projects/${projectId}/jobs`);
          cy.contains('Launch').parent().parent().contains('1/2').should('exist');

          cy.contains('button', 'Grouped').click();
          cy.contains('button', 'Flat').should('be.visible');
          cy.get('table').should('exist');
          cy.get('table thead th').first().should('contain.text', 'Title');

          cy.deleteKeycloakUser(email);
        });
      }),
    );
  });

  it('clicking a job row navigates to its detail page', () => {
    const email = uniqueEmail('navigate');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Navigate Corp', uniqueSlug());
    createProjectAs(email, 'Navigate Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Navigate to me' }).then((jobId) => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs`);
        cy.get('table').contains('Navigate to me').click();
        cy.url().should('include', `/projects/${projectId}/jobs/${jobId}`);
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('the "+ New Job" button is disabled once the project is COMPLETED', () => {
    const email = uniqueEmail('completed-project');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Completed Project Corp', uniqueSlug());
    createProjectAs(email, 'Soon Completed Project').then((projectId) => {
      completeProjectAs(email, projectId);

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);
      cy.contains('button', '+ New Job').should('be.disabled');

      cy.deleteKeycloakUser(email);
    });
  });

  it('a MEMBER sees only jobs assigned to them; OWNER and ADMIN see every job', () => {
    const ownerEmail = uniqueEmail('rolevis-owner');
    const adminEmail = uniqueEmail('rolevis-admin');
    const memberEmail = uniqueEmail('rolevis-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(adminEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithSubscription(ownerEmail, 'Role Vis Corp', uniqueSlug()).then((orgId) => {
      createProjectAs(ownerEmail, 'Role Vis Project').then((projectId) => {
        userIdFor(adminEmail).then((adminId) => {
          userIdFor(memberEmail).then((memberId) => {
            addMember(orgId, ownerEmail, adminId, 'ADMIN');
            addMember(orgId, ownerEmail, memberId, 'MEMBER');
            addProjectMember(projectId, ownerEmail, adminId, 'ADMIN');
            addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

            createJobAs(ownerEmail, projectId, { title: 'Assigned to member', assignedTo: memberId });
            createJobAs(ownerEmail, projectId, { title: 'Not assigned to member' });

            cy.loginAs(memberEmail);
            cy.visit(`/projects/${projectId}/jobs`);
            cy.get('table').contains('Assigned to member').should('be.visible');
            cy.contains('Not assigned to member').should('not.exist');

            cy.loginAs(adminEmail);
            cy.visit(`/projects/${projectId}/jobs`);
            cy.get('table').contains('Assigned to member').should('be.visible');
            cy.get('table').contains('Not assigned to member').should('be.visible');

            cy.loginAs(ownerEmail);
            cy.visit(`/projects/${projectId}/jobs`);
            cy.get('table').contains('Assigned to member').should('be.visible');
            cy.get('table').contains('Not assigned to member').should('be.visible');

            cy.deleteKeycloakUser(ownerEmail);
            cy.deleteKeycloakUser(adminEmail);
            cy.deleteKeycloakUser(memberEmail);
          });
        });
      });
    });
  });

  it('an org member who is not a project member gets 403 on the job list', () => {
    const ownerEmail = uniqueEmail('err-owner');
    const outsiderEmail = uniqueEmail('err-outsider');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(outsiderEmail, 'E2E', 'Tester');
    createOrgWithSubscription(ownerEmail, 'Err Owner Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Err Project').then((projectId) =>
        userIdFor(outsiderEmail).then((outsiderId) => {
          addMember(orgId, ownerEmail, outsiderId, 'MEMBER');

          tokenFor(outsiderEmail).then((token) => {
            cy.request({
              method: 'GET',
              url: `${API}/api/projects/${projectId}/jobs`,
              headers: { Authorization: `Bearer ${token}` },
              failOnStatusCode: false,
            }).its('status').should('eq', 403);
          });

          cy.deleteKeycloakUser(ownerEmail);
          cy.deleteKeycloakUser(outsiderEmail);
        }),
      ),
    );
  });

  it('a nonexistent project 404s; a soft-deleted project 404s', () => {
    const email = uniqueEmail('err-404');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, '404 Corp', uniqueSlug());
    createProjectAs(email, 'Soon Deleted Project').then((projectId) => {
      tokenFor(email).then((token) => {
        cy.request({
          method: 'GET',
          url: `${API}/api/projects/PRJ-999999/jobs`,
          headers: { Authorization: `Bearer ${token}` },
          failOnStatusCode: false,
        }).its('status').should('eq', 404);

        cy.request({
          method: 'DELETE',
          url: `${API}/api/projects/${projectId}`,
          headers: { Authorization: `Bearer ${token}` },
        });

        cy.request({
          method: 'GET',
          url: `${API}/api/projects/${projectId}/jobs`,
          headers: { Authorization: `Bearer ${token}` },
          failOnStatusCode: false,
        }).its('status').should('eq', 404);
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('a user with no org membership at all gets 403 NOT_IN_ORG', () => {
    const ownerEmail = uniqueEmail('err-orphan-owner');
    const orphanEmail = uniqueEmail('err-orphan');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(orphanEmail, 'E2E', 'Tester');
    createOrgWithSubscription(ownerEmail, 'Orphan Owner Corp', uniqueSlug());
    createProjectAs(ownerEmail, 'Orphan Project').then((projectId) => {
      tokenFor(orphanEmail).then((token) => {
        cy.request({
          method: 'GET',
          url: `${API}/api/projects/${projectId}/jobs`,
          headers: { Authorization: `Bearer ${token}` },
          failOnStatusCode: false,
        }).its('status').should('eq', 403);
      });

      cy.deleteKeycloakUser(ownerEmail);
      cy.deleteKeycloakUser(orphanEmail);
    });
  });

  it('a failed fetch shows PageError with a retry option', () => {
    const email = uniqueEmail('page-error');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Page Error Corp', uniqueSlug());
    createProjectAs(email, 'Page Error Project').then((projectId) => {
      cy.loginAs(email);
      cy.intercept('GET', `**/api/projects/${projectId}/jobs*`, { forceNetworkError: true }).as('failedJobs');
      cy.visit(`/projects/${projectId}/jobs`);
      cy.wait('@failedJobs');
      cy.contains('Failed to load jobs.').should('be.visible');
      cy.contains('button', 'Try again').should('be.visible');

      cy.deleteKeycloakUser(email);
    });
  });

  it('an empty project shows the empty state with a create-first-job CTA; filtering to zero via a status tab shows different copy with no CTA', () => {
    const email = uniqueEmail('empty');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Empty Corp', uniqueSlug());
    createProjectAs(email, 'Empty Project').then((projectId) => {
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);
      cy.contains('No jobs yet.').should('be.visible');
      cy.contains('button', 'Create first job').should('be.visible');

      createJobAs(email, projectId, { title: 'Only job' });
      cy.reload();
      cy.contains('button', 'Blocked').click();
      cy.contains('No jobs match this filter.').should('be.visible');
      cy.contains('button', 'Create first job').should('not.exist');

      cy.deleteKeycloakUser(email);
    });
  });

  it('filtering to zero via the priority filter shows "no jobs match" copy with no create CTA', () => {
    const email = uniqueEmail('empty-priority');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Empty Priority Corp', uniqueSlug());
    createProjectAs(email, 'Empty Priority Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Only critical job', priority: 'CRITICAL' });

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);
      cy.get('select').eq(0).select('LOW');
      cy.contains('No jobs match this filter.').should('be.visible');
      cy.contains('button', 'Create first job').should('not.exist');

      cy.deleteKeycloakUser(email);
    });
  });

  it('the hideCompletedFromAll preference removes COMPLETED jobs from the All tab but keeps them in their own tab', () => {
    const email = uniqueEmail('hide-completed');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Job Visibility Corp', uniqueSlug());
    createProjectAs(email, 'Job Visibility Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Active job' });
      createJobAs(email, projectId, { title: 'Done job' }).then((jobId) => {
        updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS');
        updateJobStatusAs(email, projectId, jobId, 'COMPLETED');
      });

      cy.loginAs(email);
      cy.visit('/settings');
      cy.contains('Completed jobs in All tab').parent().parent().within(() => cy.contains('button', 'Hide').click());

      cy.visit(`/projects/${projectId}/jobs`);
      cy.contains('button', 'All').click();
      cy.get('table').contains('Active job').should('be.visible');
      cy.contains('Done job').should('not.exist');

      cy.contains('button', 'Completed').click();
      cy.get('table').contains('Done job').should('be.visible');

      cy.deleteKeycloakUser(email);
    });
  });

  it('ascending deadline sort places jobs with no deadline last', () => {
    const email = uniqueEmail('deadline-sort-asc');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Deadline Sort Asc Corp', uniqueSlug());
    createProjectAs(email, 'Deadline Sort Asc Project').then((projectId) => {
      const soon = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString();
      createJobAs(email, projectId, { title: 'No deadline job' });
      createJobAs(email, projectId, { title: 'Has deadline job', deadline: soon });

      cy.loginAs(email);
      // Default sort-order preference is DEADLINE_ASC, so the page loads already
      // sorted ascending by deadline without needing a header click.
      cy.visit(`/projects/${projectId}/jobs`);
      cy.get('tbody tr').last().should('contain.text', 'No deadline job');

      cy.deleteKeycloakUser(email);
    });
  });

  it('descending deadline sort places jobs with no deadline last', () => {
    const email = uniqueEmail('deadline-sort-desc');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Deadline Sort Desc Corp', uniqueSlug());
    createProjectAs(email, 'Deadline Sort Desc Project').then((projectId) => {
      const soon = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString();
      createJobAs(email, projectId, { title: 'No deadline job' });
      createJobAs(email, projectId, { title: 'Has deadline job', deadline: soon });

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);
      // Default sort is DEADLINE_ASC, so one click toggles to descending.
      cy.contains('th', 'Deadline').click();
      cy.get('tbody tr').last().should('contain.text', 'No deadline job');

      cy.deleteKeycloakUser(email);
    });
  });

  it('an empty milestone group still renders as a collapsed/expanded shell', () => {
    const email = uniqueEmail('empty-group');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Empty Group Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Empty Group Project').then((projectId) => {
        createMilestoneAs(email, projectId, 'Empty Phase');

        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs`);
        // Default milestoneAccordionState preference is EXPANDED — the empty group's
        // shell (header + "0" count badge) renders with its empty-in-group message
        // visible without any interaction.
        cy.contains('Empty Phase').should('be.visible');
        cy.contains('No jobs in this group.').should('be.visible');

        // Collapsing the shell hides the message but keeps the header itself.
        cy.contains('Empty Phase').click();
        cy.contains('No jobs in this group.').should('not.exist');
        cy.contains('Empty Phase').should('be.visible');

        cy.deleteKeycloakUser(email);
      }),
    );
  });
});
