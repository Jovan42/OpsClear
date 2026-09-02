// ADR-0049 Appendix §14 (Job & Project Links). Uses cy.loginAs() per
// docs/dev/process/E2E.md.
//
// "Deleting the owning job/project cascades link deletion with no orphan rows" is
// exercised via a direct SQL DELETE (cy.task('queryDb', ...)), not through the app's
// own API — job/project deletion is always soft-delete (deleted_at) in every reachable
// HTTP endpoint, so the job_links/project_links ON DELETE CASCADE FK constraint is
// never actually triggered by normal app usage. It's still a real DB-level guarantee
// worth locking in (e.g. against a future hard-delete/GDPR-purge path), so this test
// exercises the constraint directly rather than skipping the bullet as unreachable.
//
// LinksSection auto-expands on JobDetailPage when the job already has links
// (JobDetailPage.tsx: `setLinksExpanded(job.links.length > 0)`) — the same
// auto-expand-then-click-closes accordion pattern seen in Notes/Relationships/Status
// History. Tests that pre-seed links via the API before visiting never click the
// "Links" toggle for that reason.

import {
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  userIdFor,
  createOrgWithFullAccess,
  createOrgWithSubscription,
  createProjectAs,
  createJobAs,
  addMember,
  addProjectMember,
  createJobLinkAs,
  createProjectLinkAs,
  getJobAs,
  API,
} from '../../support/orgApi';

describe('Job & Project Links', () => {
  it('any project member adds a job-level and a project-level link; a blank label on a recognized-service URL auto-fills the service name', () => {
    const email = uniqueEmail('add-link');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Falcon Corp', uniqueSlug());
    createProjectAs(email, 'Falcon Link Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Falcon job' }).then((jobId) => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs/${jobId}`);

        cy.get('main').contains('button', 'Links').click();
        cy.contains('button', '+ Add link').click();
        cy.get('input[placeholder="https://…"]').type('https://github.com/opsclear/opsclear/pull/1');
        cy.get('input[placeholder="Label (optional)"]').should('have.value', 'GitHub');
        cy.contains('button', 'Save').click();
        cy.contains('GitHub').should('be.visible');

        cy.visit(`/projects/${projectId}/jobs`);
        cy.get('nav').contains('button', 'Links').click();
        cy.contains('button', '+ Add link').click();
        cy.get('input[placeholder="https://…"]').type('https://gitlab.com/opsclear/wiki');
        cy.get('input[placeholder="Label (optional)"]').should('have.value', 'GitLab');
        cy.contains('button', 'Save').click();
        cy.contains('GitLab').should('be.visible');
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('an unrecognized host falls back to a favicon, then a generic icon if the favicon fails to load', () => {
    const email = uniqueEmail('favicon-fallback');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Nimbus Corp', uniqueSlug());
    createProjectAs(email, 'Nimbus Link Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Nimbus job' }).then((jobId) => {
        createJobLinkAs(email, projectId, jobId, 'https://example.com/some-doc', 'Some doc');

        cy.loginAs(email);
        cy.intercept('GET', '**/s2/favicons**', { statusCode: 404, body: '' }).as('faviconFail');
        cy.visit(`/projects/${projectId}/jobs/${jobId}`);
        cy.contains('Some doc').parents('.rounded-lg').first().find('svg').should('exist');
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('OWNER/ADMIN can edit and delete a link at both scopes; the copy-URL button works; a MEMBER sees no edit/delete controls', () => {
    const ownerEmail = uniqueEmail('edit-delete-owner');
    const memberEmail = uniqueEmail('edit-delete-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Atlas Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Atlas Link Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) =>
          // Assigned to the member — an unassigned MEMBER 403s viewing a job detail
          // page at all (requireCanViewJob), which would block reaching the Links
          // section entirely and defeat the point of this test.
          createJobAs(ownerEmail, projectId, { title: 'Atlas job', assignedTo: memberId }).then((jobId) => {
            addMember(orgId, ownerEmail, memberId, 'MEMBER');
            addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

            createJobLinkAs(ownerEmail, projectId, jobId, 'https://old-url.example.com', 'Old label');
            createProjectLinkAs(ownerEmail, projectId, 'https://old-project-url.example.com', 'Old project label');

            cy.loginAs(ownerEmail);
            cy.visit(`/projects/${projectId}/jobs/${jobId}`);
            cy.get('button[title="Edit link"]').click();
            cy.get('input[placeholder="https://…"]').clear();
            cy.get('input[placeholder="https://…"]').type('https://new-url.example.com');
            cy.contains('button', 'Save').click();
            cy.contains('https://new-url.example.com').should('not.exist');

            cy.get('button[title="Copy URL"]').click();
            cy.get('button[title="Copied!"]').should('be.visible');

            cy.get('button[title="Delete link"]').click();
            cy.get('.z-50:visible').within(() => cy.contains('button', 'Delete').click());
            cy.contains('Old label').should('not.exist');

            cy.loginAs(memberEmail);
            cy.visit(`/projects/${projectId}/jobs/${jobId}`);
            cy.get('main').contains('button', 'Links').click();
            cy.get('button[title="Edit link"]').should('not.exist');
            cy.get('button[title="Delete link"]').should('not.exist');
          }),
        ),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('mailto: and ftp: schemes are accepted (loose scheme policy, no allow-list)', () => {
    const email = uniqueEmail('loose-scheme');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Cedar Corp', uniqueSlug());
    createProjectAs(email, 'Cedar Link Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Cedar job' }).then((jobId) => {
        createJobLinkAs(email, projectId, jobId, 'mailto:team@example.com').then((res) => {
          expect(res.status).to.equal(201);
        });
        createJobLinkAs(email, projectId, jobId, 'ftp://files.example.com/report.pdf').then((res) => {
          expect(res.status).to.equal(201);
        });
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('a blank URL 400s; javascript:/data:/vbscript: schemes 400 (including case variants and whitespace-smuggling); a schemeless or malformed URL 400s; an over-100-char label 400s', () => {
    const email = uniqueEmail('url-validation');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Vega Corp', uniqueSlug());
    createProjectAs(email, 'Vega Link Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Vega job' }).then((jobId) => {
        createJobLinkAs(email, projectId, jobId, '   ').then((res) => {
          expect(res.status).to.equal(400);
        });

        const dangerousSchemes = [
          'javascript:alert(1)',
          'JAVASCRIPT:alert(1)',
          'JaVaScRiPt:alert(1)',
          ' javascript:alert(1)',
          'javascript:alert(1) ',
          '\tjavascript:alert(1)',
          'java\tscript:alert(1)',
          'data:text/html,<script>alert(1)</script>',
          'vbscript:msgbox(1)',
        ];
        dangerousSchemes.forEach((url) => {
          createJobLinkAs(email, projectId, jobId, url).then((res) => {
            expect(res.status, `url=${JSON.stringify(url)}`).to.equal(400);
          });
        });

        createJobLinkAs(email, projectId, jobId, 'example.com').then((res) => {
          expect(res.status).to.equal(400);
        });
        createJobLinkAs(email, projectId, jobId, 'http://[not-a-valid-host').then((res) => {
          expect(res.status).to.equal(400);
        });

        createJobLinkAs(email, projectId, jobId, 'https://example.com', 'x'.repeat(101)).then((res) => {
          expect(res.status).to.equal(400);
        });
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('a MEMBER gets 403 editing or deleting a link at both scopes, but can add — this asymmetry is intentional (ADR-0035)', () => {
    const ownerEmail = uniqueEmail('member-write-owner');
    const memberEmail = uniqueEmail('member-write-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Juniper Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Juniper Link Project').then((projectId) =>
        createJobAs(ownerEmail, projectId, { title: 'Juniper job' }).then((jobId) =>
          userIdFor(memberEmail).then((memberId) => {
            addMember(orgId, ownerEmail, memberId, 'MEMBER');
            addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

            createJobLinkAs(memberEmail, projectId, jobId, 'https://member-added.example.com').then((addRes) => {
              expect(addRes.status).to.equal(201);
              const linkId = addRes.body.id;

              tokenFor(memberEmail).then((token) => {
                cy.request({
                  method: 'PUT',
                  url: `${API}/api/projects/${projectId}/jobs/${jobId}/links/${linkId}`,
                  headers: { Authorization: `Bearer ${token}` },
                  body: { url: 'https://hijacked.example.com' },
                  failOnStatusCode: false,
                }).its('status').should('eq', 403);

                cy.request({
                  method: 'DELETE',
                  url: `${API}/api/projects/${projectId}/jobs/${jobId}/links/${linkId}`,
                  headers: { Authorization: `Bearer ${token}` },
                  failOnStatusCode: false,
                }).its('status').should('eq', 403);
              });
            });

            createProjectLinkAs(memberEmail, projectId, 'https://member-added-project.example.com').then((addRes) => {
              expect(addRes.status).to.equal(201);
              const linkId = addRes.body.id;

              tokenFor(memberEmail).then((token) => {
                cy.request({
                  method: 'PUT',
                  url: `${API}/api/projects/${projectId}/links/${linkId}`,
                  headers: { Authorization: `Bearer ${token}` },
                  body: { url: 'https://hijacked.example.com' },
                  failOnStatusCode: false,
                }).its('status').should('eq', 403);

                cy.request({
                  method: 'DELETE',
                  url: `${API}/api/projects/${projectId}/links/${linkId}`,
                  headers: { Authorization: `Bearer ${token}` },
                  failOnStatusCode: false,
                }).its('status').should('eq', 403);
              });
            });
          }),
        ),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('a linkId from a different job/project 404s; a non-member gets 403 on every endpoint', () => {
    const ownerEmail = uniqueEmail('cross-scope-owner');
    const outsiderEmail = uniqueEmail('cross-scope-outsider');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(outsiderEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Orbit Corp', uniqueSlug()).then(() =>
      createProjectAs(ownerEmail, 'Orbit Link Project A').then((projectIdA) =>
        createProjectAs(ownerEmail, 'Orbit Link Project B').then((projectIdB) =>
          createJobAs(ownerEmail, projectIdA, { title: 'Job in A' }).then((jobIdA) =>
            createJobAs(ownerEmail, projectIdA, { title: 'Other job in A' }).then((otherJobIdA) =>
              createJobLinkAs(ownerEmail, projectIdA, jobIdA, 'https://scoped-job-link.example.com').then((jobLinkRes) =>
                createProjectLinkAs(ownerEmail, projectIdA, 'https://scoped-project-link.example.com').then((projectLinkRes) => {
                  const jobLinkId = jobLinkRes.body.id;
                  const projectLinkId = projectLinkRes.body.id;

                  tokenFor(ownerEmail).then((token) => {
                    // Cross-job: same project, wrong job.
                    cy.request({
                      method: 'PUT',
                      url: `${API}/api/projects/${projectIdA}/jobs/${otherJobIdA}/links/${jobLinkId}`,
                      headers: { Authorization: `Bearer ${token}` },
                      body: { url: 'https://x.example.com' },
                      failOnStatusCode: false,
                    }).its('status').should('eq', 404);

                    // Cross-project: wrong project entirely.
                    cy.request({
                      method: 'DELETE',
                      url: `${API}/api/projects/${projectIdB}/links/${projectLinkId}`,
                      headers: { Authorization: `Bearer ${token}` },
                      failOnStatusCode: false,
                    }).its('status').should('eq', 404);
                  });

                  userIdFor(outsiderEmail).then(() => {
                    tokenFor(outsiderEmail).then((token) => {
                      cy.request({
                        method: 'POST',
                        url: `${API}/api/projects/${projectIdA}/jobs/${jobIdA}/links`,
                        headers: { Authorization: `Bearer ${token}` },
                        body: { url: 'https://x.example.com' },
                        failOnStatusCode: false,
                      }).its('status').should('eq', 403);

                      cy.request({
                        method: 'POST',
                        url: `${API}/api/projects/${projectIdA}/links`,
                        headers: { Authorization: `Bearer ${token}` },
                        body: { url: 'https://x.example.com' },
                        failOnStatusCode: false,
                      }).its('status').should('eq', 403);
                    });
                  });
                }),
              ),
            ),
          ),
        ),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(outsiderEmail);
  });

  it('without the JOB_LINKS add-on: the API 403s, the job-detail Links section is fully hidden, and the project nav shows a locked teaser instead of the dropdown', () => {
    const email = uniqueEmail('no-links-addon');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Sable Corp', uniqueSlug());
    createProjectAs(email, 'Sable Link Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Sable job' }).then((jobId) => {
        tokenFor(email).then((token) => {
          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}/links`,
            headers: { Authorization: `Bearer ${token}` },
            body: { url: 'https://x.example.com' },
            failOnStatusCode: false,
          }).its('status').should('eq', 403);
        });

        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs/${jobId}`);
        cy.contains('button', 'Links').should('not.exist');

        cy.visit(`/projects/${projectId}/jobs`);
        cy.contains('a', 'Links').should('be.visible');
        cy.contains('a', 'Links').find('svg').should('exist');
        cy.contains('a', 'Links').click();
        cy.url().should('include', '/org/settings');
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('subdomain-based Jira detection and www. normalization both resolve to the correct known icon', () => {
    const email = uniqueEmail('subdomain-detection');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Comet Corp', uniqueSlug());
    createProjectAs(email, 'Comet Link Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Comet job' }).then((jobId) => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs/${jobId}`);
        cy.get('main').contains('button', 'Links').click();

        cy.contains('button', '+ Add link').click();
        cy.get('input[placeholder="https://…"]').type('https://mycompany.atlassian.net/browse/OPS-1');
        cy.get('input[placeholder="Label (optional)"]').should('have.value', 'Jira');
        cy.contains('button', 'Save').click();

        cy.contains('button', '+ Add link').click();
        cy.get('input[placeholder="https://…"]').type('https://www.github.com/opsclear/opsclear');
        cy.get('input[placeholder="Label (optional)"]').should('have.value', 'GitHub');
        cy.contains('button', 'Save').click();
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('clearing an auto-filled label before submit saves label: null, and the link falls back to the hostname for display', () => {
    const email = uniqueEmail('clear-label');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Willow Corp', uniqueSlug());
    createProjectAs(email, 'Willow Link Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Willow job' }).then((jobId) => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs/${jobId}`);
        cy.get('main').contains('button', 'Links').click();
        cy.contains('button', '+ Add link').click();
        cy.get('input[placeholder="https://…"]').type('https://github.com/opsclear/opsclear');
        cy.get('input[placeholder="Label (optional)"]').should('have.value', 'GitHub');
        cy.get('input[placeholder="Label (optional)"]').clear();
        cy.contains('button', 'Save').click();

        cy.contains('github.com').should('be.visible');

        getJobAs(email, projectId, jobId).then((job: { links: Array<{ label: string | null }> }) => {
          expect(job.links[0].label).to.equal(null);
        });
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it("editing a link's URL from a known service to an unrecognized one updates the icon on next render", () => {
    const email = uniqueEmail('icon-update');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Sparrow Corp', uniqueSlug());
    createProjectAs(email, 'Sparrow Link Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Sparrow job' }).then((jobId) => {
        createJobLinkAs(email, projectId, jobId, 'https://github.com/opsclear/opsclear', 'GitHub');

        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs/${jobId}`);
        cy.get('svg[fill^="#"]').should('exist');

        cy.get('button[title="Edit link"]').click();
        cy.get('input[placeholder="https://…"]').clear();
        cy.get('input[placeholder="https://…"]').type('https://totally-unknown-host.example.com');
        cy.contains('button', 'Save').click();

        cy.get('svg[fill^="#"]').should('not.exist');
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('deleting the owning job/project cascades link deletion at the DB level, with no orphan rows', () => {
    // A hard DELETE on jobs/projects also has to satisfy every OTHER non-cascading FK
    // a bare row picks up automatically (jobs -> job_status_history from creation;
    // projects -> its owner's project_members row, and any job still in it) — cleared
    // here purely as setup noise, not part of what's under test (job_links/
    // project_links' own ON DELETE CASCADE, the only FK this test actually asserts on).
    const email = uniqueEmail('cascade-delete');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Ember Corp', uniqueSlug());
    createProjectAs(email, 'Ember Link Project').then((projectId) =>
      createJobAs(email, projectId, { title: 'Doomed job' }).then((jobId) => {
        createJobLinkAs(email, projectId, jobId, 'https://job-link.example.com');
        createProjectLinkAs(email, projectId, 'https://project-link.example.com');

        getJobAs(email, projectId, jobId).then((job: { id: string; projectId: string }) => {
          const jobUuid = job.id;
          const projectUuid = job.projectId;

          cy.task('queryDb', { sql: 'SELECT id FROM job_links WHERE job_id = $1', params: [jobUuid] }).then((rows) => {
            expect((rows as unknown[]).length).to.equal(1);
          });
          cy.task('queryDb', { sql: 'SELECT id FROM project_links WHERE project_id = $1', params: [projectUuid] }).then((rows) => {
            expect((rows as unknown[]).length).to.equal(1);
          });

          cy.task('queryDb', { sql: 'DELETE FROM job_status_history WHERE job_id = $1', params: [jobUuid] }).then(() =>
            cy.task('queryDb', { sql: 'DELETE FROM jobs WHERE id = $1', params: [jobUuid] }).then(() => {
              cy.task('queryDb', { sql: 'SELECT id FROM job_links WHERE job_id = $1', params: [jobUuid] }).then((rows) => {
                expect((rows as unknown[]).length).to.equal(0);
              });

              cy.task('queryDb', { sql: 'DELETE FROM project_members WHERE project_id = $1', params: [projectUuid] }).then(() =>
                cy.task('queryDb', { sql: 'DELETE FROM projects WHERE id = $1', params: [projectUuid] }).then(() => {
                  cy.task('queryDb', { sql: 'SELECT id FROM project_links WHERE project_id = $1', params: [projectUuid] }).then((rows) => {
                    expect((rows as unknown[]).length).to.equal(0);
                  });
                }),
              );
            }),
          );
        });
      }),
    );
  });

  it('cross-cutting role matrix: OWNER, ADMIN, MEMBER (assigned or not) can all add a link; only OWNER/ADMIN can edit/delete; a non-member cannot add', () => {
    const ownerEmail = uniqueEmail('matrix-owner');
    const adminEmail = uniqueEmail('matrix-admin');
    const assignedMemberEmail = uniqueEmail('matrix-assigned');
    const unassignedMemberEmail = uniqueEmail('matrix-unassigned');
    [ownerEmail, adminEmail, assignedMemberEmail, unassignedMemberEmail].forEach((e) =>
      cy.createKeycloakUser(e, 'E2E', 'Tester'),
    );

    createOrgWithFullAccess(ownerEmail, 'Talon Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Talon Link Project').then((projectId) =>
        userIdFor(adminEmail).then((adminId) =>
          userIdFor(assignedMemberEmail).then((assignedId) =>
            userIdFor(unassignedMemberEmail).then((unassignedId) => {
              addMember(orgId, ownerEmail, adminId, 'MEMBER');
              addMember(orgId, ownerEmail, assignedId, 'MEMBER');
              addMember(orgId, ownerEmail, unassignedId, 'MEMBER');
              addProjectMember(projectId, ownerEmail, adminId, 'ADMIN');
              addProjectMember(projectId, ownerEmail, assignedId, 'MEMBER');
              addProjectMember(projectId, ownerEmail, unassignedId, 'MEMBER');

              createJobAs(ownerEmail, projectId, { title: 'Assigned job', assignedTo: assignedId }).then((jobId) => {
                [ownerEmail, adminEmail, assignedMemberEmail, unassignedMemberEmail].forEach((email) => {
                  createJobLinkAs(email, projectId, jobId, `https://added-by-${email}.example.com`).then((res) => {
                    expect(res.status, email).to.equal(201);
                  });
                });

                createJobLinkAs(ownerEmail, projectId, jobId, 'https://edit-target.example.com').then((addRes) => {
                  const linkId = addRes.body.id;

                  [assignedMemberEmail, unassignedMemberEmail].forEach((email) => {
                    tokenFor(email).then((token) => {
                      cy.request({
                        method: 'PUT',
                        url: `${API}/api/projects/${projectId}/jobs/${jobId}/links/${linkId}`,
                        headers: { Authorization: `Bearer ${token}` },
                        body: { url: 'https://x.example.com' },
                        failOnStatusCode: false,
                      }).its('status').should('eq', 403);
                    });
                  });

                  tokenFor(adminEmail).then((token) => {
                    cy.request({
                      method: 'PUT',
                      url: `${API}/api/projects/${projectId}/jobs/${jobId}/links/${linkId}`,
                      headers: { Authorization: `Bearer ${token}` },
                      body: { url: 'https://edited-by-admin.example.com' },
                    }).its('status').should('eq', 200);
                  });
                });
              });
            }),
          ),
        ),
      ),
    );

    [ownerEmail, adminEmail, assignedMemberEmail, unassignedMemberEmail].forEach((e) => cy.deleteKeycloakUser(e));
  });
});
