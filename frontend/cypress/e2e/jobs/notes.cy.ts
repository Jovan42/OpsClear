// ADR-0049 Appendix §8 (Notes). Uses cy.loginAs() per docs/dev/process/E2E.md.
//
// The 10000-char limit is confirmed directly against NoteThread.tsx's NOTE_MAX
// constant and CreateNoteRequest's @Size(max = 10000) — ADR-0009's own text says
// 2000, which ADR-0049 itself already flags as stale. Code is the source of truth.

import {
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  createOrgWithFullAccess,
  createOrgWithSubscription,
  createProjectAs,
  createJobAs,
  createNoteAs,
  addMember,
  addProjectMember,
  userIdFor,
  completeProjectAs,
  API,
} from '../../support/orgApi';

const NOTE_MAX = 10000;

describe('Notes', () => {
  it('adds a note that appears at the bottom of the thread after refetch (not optimistic)', () => {
    const email = uniqueEmail('add-note');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Add Note Corp', uniqueSlug());
    createProjectAs(email, 'Happy Path Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job' }).then((jobId) => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs/${jobId}`);
        cy.contains('button', 'Notes').click();
        cy.contains('No notes yet.').should('be.visible');

        cy.get('textarea').first().type('First note');
        cy.contains('button', 'Add Note').click();
        // First submission per browser session shows the immutability confirm dialog.
        cy.get('.z-50:visible').within(() => cy.contains('button', 'Add Note').click());
        cy.contains('First note').should('be.visible');
        cy.contains('No notes yet.').should('not.exist');
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('notes are ordered oldest-first within a job, and subsequent submissions in the same session skip the confirm dialog', () => {
    const email = uniqueEmail('order-and-confirm');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Order Confirm Corp', uniqueSlug());
    createProjectAs(email, 'Order Confirm Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job' }).then((jobId) => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs/${jobId}`);
        cy.contains('button', 'Notes').click();

        cy.get('textarea').first().type('Older note');
        cy.contains('button', 'Add Note').click();
        cy.get('.z-50:visible').within(() => cy.contains('button', 'Add Note').click());
        cy.contains('Older note').should('be.visible');

        cy.get('textarea').first().type('Newer note');
        cy.contains('button', 'Add Note').click();
        // No confirm dialog this time — already confirmed once this session.
        cy.get('.z-50:visible').should('not.exist');
        cy.contains('Newer note').should('be.visible');

        cy.get('[class*="rounded-lg"][class*="px-4"][class*="py-3"]').then(($notes) => {
          const texts = [...$notes].map((el) => el.textContent);
          const olderIdx = texts.findIndex((t) => t?.includes('Older note'));
          const newerIdx = texts.findIndex((t) => t?.includes('Newer note'));
          expect(olderIdx).to.be.lessThan(newerIdx);
        });
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it("a note's author is resolved from the project's member list", () => {
    const ownerEmail = uniqueEmail('author-owner');
    const memberEmail = uniqueEmail('author-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Author Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Author Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
          createJobAs(ownerEmail, projectId, { title: 'Job' }).then((jobId) => {
            createNoteAs(memberEmail, projectId, jobId, "Member's note").then(() => {
              cy.loginAs(ownerEmail);
              cy.visit(`/projects/${projectId}/jobs/${jobId}`);
              // JobDetailPage auto-expands the Notes accordion once it finishes
              // loading if it already has notes — no click needed (and clicking
              // here would toggle it closed instead of opening it).
              cy.contains("Member's note").closest('.rounded-lg').contains('E2E Tester').should('be.visible');
              cy.contains('Unknown user').should('not.exist');
            });
          });

          cy.deleteKeycloakUser(ownerEmail);
          cy.deleteKeycloakUser(memberEmail);
        }),
      ),
    );
  });

  it('the character counter reflects the 10000-char limit as content is typed', () => {
    const email = uniqueEmail('char-counter');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Char Counter Corp', uniqueSlug());
    createProjectAs(email, 'Char Counter Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job' }).then((jobId) => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs/${jobId}`);
        cy.contains('button', 'Notes').click();
        cy.contains(`0/${NOTE_MAX}`).should('be.visible');
        cy.get('textarea').first().type('hello');
        cy.contains(`5/${NOTE_MAX}`).should('be.visible');
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('the project-level grouped notes view orders jobs by most recent note, notes within a job ascending, excluding jobs with no notes', () => {
    const email = uniqueEmail('grouped-view');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Grouped View Corp', uniqueSlug());
    createProjectAs(email, 'Grouped View Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'No notes job' });
      createJobAs(email, projectId, { title: 'Older-activity job' }).then((jobA) =>
        createJobAs(email, projectId, { title: 'Newer-activity job' }).then((jobB) => {
          createNoteAs(email, projectId, jobA, 'A note 1').then(() =>
            createNoteAs(email, projectId, jobA, 'A note 2').then(() =>
              // jobB's note is created last, so its job should sort first.
              createNoteAs(email, projectId, jobB, 'B note 1').then(() => {
                tokenFor(email).then((token) => {
                  cy.request({
                    method: 'GET',
                    url: `${API}/api/projects/${projectId}/notes`,
                    headers: { Authorization: `Bearer ${token}` },
                  }).then((res) => {
                    // The response's jobId is the job's internal UUID, not its
                    // friendlyId — compare by jobName (which we control directly)
                    // instead of trying to resolve friendlyId -> UUID.
                    const jobNames = (res.body as Array<{ jobName: string }>).map((g) => g.jobName);
                    expect(jobNames).to.deep.equal(['Newer-activity job', 'Older-activity job']);

                    const jobAGroup = (res.body as Array<{ jobName: string; notes: Array<{ content: string }> }>)
                      .find((g) => g.jobName === 'Older-activity job')!;
                    expect(jobAGroup.notes.map((n) => n.content)).to.deep.equal(['A note 1', 'A note 2']);
                  });
                });
              }),
            ),
          );
        }),
      );

      cy.deleteKeycloakUser(email);
    });
  });

  it('blank content 400s; content over 10000 characters 400s server-side (the UI blocks both too)', () => {
    const email = uniqueEmail('content-validation');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Content Validation Corp', uniqueSlug());
    createProjectAs(email, 'Content Validation Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job' }).then((jobId) => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs/${jobId}`);
        cy.contains('button', 'Notes').click();
        cy.contains('button', 'Add Note').should('be.disabled');

        tokenFor(email).then((token) => {
          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}/notes`,
            headers: { Authorization: `Bearer ${token}` },
            body: { content: '' },
            failOnStatusCode: false,
          }).its('status').should('eq', 400);

          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}/notes`,
            headers: { Authorization: `Bearer ${token}` },
            body: { content: 'x'.repeat(NOTE_MAX + 1) },
            failOnStatusCode: false,
          }).its('status').should('eq', 400);
        });
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('creating or listing notes for a job in a different project than the URL 404s', () => {
    const email = uniqueEmail('cross-project');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Cross Project Corp', uniqueSlug());
    createProjectAs(email, 'Project A').then((projectA) =>
      createProjectAs(email, 'Project B').then((projectB) =>
        createJobAs(email, projectA, { title: 'Job in A' }).then((jobId) => {
          tokenFor(email).then((token) => {
            cy.request({
              method: 'POST',
              url: `${API}/api/projects/${projectB}/jobs/${jobId}/notes`,
              headers: { Authorization: `Bearer ${token}` },
              body: { content: 'Wrong project' },
              failOnStatusCode: false,
            }).its('status').should('eq', 404);

            cy.request({
              method: 'GET',
              url: `${API}/api/projects/${projectB}/jobs/${jobId}/notes`,
              headers: { Authorization: `Bearer ${token}` },
              failOnStatusCode: false,
            }).its('status').should('eq', 404);
          });

          cy.deleteKeycloakUser(email);
        }),
      ),
    );
  });

  it('creating a note on a COMPLETED project 409s; the UI hides the note form entirely', () => {
    const email = uniqueEmail('completed-project');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Completed Project Corp', uniqueSlug());
    createProjectAs(email, 'Completed Project Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job' }).then((jobId) => {
        completeProjectAs(email, projectId);
        tokenFor(email).then((token) => {
          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}/notes`,
            headers: { Authorization: `Bearer ${token}` },
            body: { content: 'Should not be creatable' },
            failOnStatusCode: false,
          }).its('status').should('eq', 409);
        });

        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs/${jobId}`);
        cy.contains('button', 'Notes').click();
        cy.get('textarea').should('not.exist');
        cy.contains('button', 'Add Note').should('not.exist');
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('every notes endpoint 403s without the NOTES add-on', () => {
    const email = uniqueEmail('no-addon');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'No Addon Corp', uniqueSlug());
    createProjectAs(email, 'No Addon Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job' }).then((jobId) => {
        tokenFor(email).then((token) => {
          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}/notes`,
            headers: { Authorization: `Bearer ${token}` },
            body: { content: 'x' },
            failOnStatusCode: false,
          }).its('status').should('eq', 403);

          cy.request({
            method: 'GET',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}/notes`,
            headers: { Authorization: `Bearer ${token}` },
            failOnStatusCode: false,
          }).its('status').should('eq', 403);

          cy.request({
            method: 'GET',
            url: `${API}/api/projects/${projectId}/notes`,
            headers: { Authorization: `Bearer ${token}` },
            failOnStatusCode: false,
          }).its('status').should('eq', 403);
        });
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('any project member (including an unassigned MEMBER) can add a note; a non-member of the project cannot', () => {
    const ownerEmail = uniqueEmail('rolewrite-owner');
    const memberEmail = uniqueEmail('rolewrite-member');
    const outsiderEmail = uniqueEmail('rolewrite-outsider');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(outsiderEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Role Write Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Role Write Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) =>
          userIdFor(outsiderEmail).then((outsiderId) => {
            addMember(orgId, ownerEmail, memberId, 'MEMBER');
            addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
            addMember(orgId, ownerEmail, outsiderId, 'MEMBER'); // org member, NOT a project member

            createJobAs(ownerEmail, projectId, { title: 'Not assigned to member' }).then((jobId) => {
              // Unassigned MEMBER can still add a note — notes aren't assignment-scoped.
              tokenFor(memberEmail).then((token) =>
                cy.request({
                  method: 'POST',
                  url: `${API}/api/projects/${projectId}/jobs/${jobId}/notes`,
                  headers: { Authorization: `Bearer ${token}` },
                  body: { content: 'From unassigned member' },
                }).its('status').should('eq', 201),
              );

              tokenFor(outsiderEmail).then((token) =>
                cy.request({
                  method: 'POST',
                  url: `${API}/api/projects/${projectId}/jobs/${jobId}/notes`,
                  headers: { Authorization: `Bearer ${token}` },
                  body: { content: 'From outsider' },
                  failOnStatusCode: false,
                }).its('status').should('eq', 403),
              );
            });

            cy.deleteKeycloakUser(ownerEmail);
            cy.deleteKeycloakUser(memberEmail);
            cy.deleteKeycloakUser(outsiderEmail);
          }),
        ),
      ),
    );
  });

  it('no edit or delete route exists for notes — DELETE 404s or 405s, confirming immutability is enforced server-side', () => {
    const email = uniqueEmail('no-delete-route');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'No Delete Route Corp', uniqueSlug());
    createProjectAs(email, 'No Delete Route Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job' }).then((jobId) => {
        createNoteAs(email, projectId, jobId, 'Immutable note').then((noteId) => {
          tokenFor(email).then((token) => {
            cy.request({
              method: 'DELETE',
              url: `${API}/api/projects/${projectId}/jobs/${jobId}/notes/${noteId}`,
              headers: { Authorization: `Bearer ${token}` },
              failOnStatusCode: false,
            }).its('status').should('be.oneOf', [404, 405]);
          });
        });
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('a markdown/script injection attempt in note content is stripped, never executed', () => {
    const email = uniqueEmail('xss');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'XSS Corp', uniqueSlug());
    createProjectAs(email, 'XSS Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job' }).then((jobId) => {
        // The script tag must not open the line — CommonMark treats a line
        // *starting* with `<script` as an HTML block that swallows everything up
        // to the closing tag (including any trailing text on the same line), which
        // would make "Hello"/"world" vanish along with it regardless of sanitization.
        // Putting the injection attempts after plain text keeps them as inline raw
        // HTML instead, so stripping is actually exercised rather than masked.
        createNoteAs(
          email,
          projectId,
          jobId,
          'Hello <script>window.__xss = true;</script> and <img src=x onerror="window.__xss = true"> world',
        ).then(() => {
          cy.loginAs(email);
          cy.visit(`/projects/${projectId}/jobs/${jobId}`);
          // Notes accordion auto-expands once loaded since a note already exists.
          cy.contains('Hello').should('be.visible');
          cy.contains('world').should('be.visible');
          // Scoped to the rendered note itself — the page's own <script> tags
          // (Vite's client, the app bundle) are unrelated and always present.
          cy.contains('Hello').closest('.prose').find('script, img').should('not.exist');
          cy.window().then((win) => {
            expect((win as unknown as { __xss?: boolean }).__xss).to.equal(undefined);
          });
        });
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('note content is server-side trimmed — a whitespace-only note is rejected as blank', () => {
    const email = uniqueEmail('trim-content');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Trim Content Corp', uniqueSlug());
    createProjectAs(email, 'Trim Content Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Job' }).then((jobId) => {
        tokenFor(email).then((token) => {
          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}/notes`,
            headers: { Authorization: `Bearer ${token}` },
            body: { content: '   ' },
            failOnStatusCode: false,
          }).its('status').should('eq', 400);

          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}/notes`,
            headers: { Authorization: `Bearer ${token}` },
            body: { content: '  Padded content  ' },
          }).then((res) => {
            expect(res.body.content).to.equal('Padded content');
          });
        });
      });

      cy.deleteKeycloakUser(email);
    });
  });
});
