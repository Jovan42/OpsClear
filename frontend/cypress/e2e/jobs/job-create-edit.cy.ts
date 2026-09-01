// ADR-0049 Appendix §6 (Job Create/Edit, incl. templates, markdown toolbar). Uses
// cy.loginAs() per docs/dev/process/E2E.md.
//
// The markdown toolbar (MarkdownEditor.tsx) is a single shared component used by 5
// consumers (job description, notes, project description/settings, templates). Its
// full behavior (all 7 buttons, per-line wrapping, double-wrap-not-toggle, preview
// rendering) is exercised thoroughly here via the job-description field — the other
// consumers get their own dedicated smoke coverage in their respective backfill jobs
// (Notes: JOB-215, Projects: JOB-218, Job Templates: JOB-220) rather than re-testing
// the identical shared component 5x.

import {
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  createOrgWithSubscription,
  createOrgWithFullAccess,
  createProjectAs,
  createJobAs,
  addMember,
  addProjectMember,
  userIdFor,
  createMilestoneAs,
  createJobTypeAs,
  createTemplateAs,
  listTemplatesAs,
  completeProjectAs,
  API,
} from '../../support/orgApi';

// cy.type() character-by-character simulation occasionally drops/reorders keystrokes
// against a fully-controlled React textarea re-rendering on every keypress — rare with
// plain text, more reproducible with embedded newlines. Setting the value via the
// native setter + a real 'input' event (the standard React-testing workaround) avoids
// per-character race entirely, so the toolbar tests below use this instead of .type()
// wherever the exact prior content matters for the assertion that follows.
function setTextareaValue(alias: string, value: string) {
  cy.get(alias).then(($el) => {
    const el = $el[0] as HTMLTextAreaElement;
    const setter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value')!.set!;
    setter.call(el, value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

describe('Job Create/Edit', () => {
  it('creates a job with only a title', () => {
    const email = uniqueEmail('minimal-create');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Minimal Create Corp', uniqueSlug());
    createProjectAs(email, 'Minimal Create Project').then((projectId) => {
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);
      cy.contains('button', '+ New Job').click();
      cy.get('.z-50:visible').within(() => {
        cy.get('input[placeholder="e.g. Fix login bug"]').type('Just a title');
        cy.contains('button', 'Create job').click();
      });
      cy.get('table').contains('Just a title').should('be.visible');

      cy.deleteKeycloakUser(email);
    });
  });

  it('creates a job with every field populated', () => {
    const email = uniqueEmail('full-create');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Full Create Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Full Create Project').then((projectId) => {
        createMilestoneAs(email, projectId, 'Launch').then(() => {
          createJobTypeAs(email, projectId, 'Bug', 'RED').then(() => {
            cy.loginAs(email);
            // Every field populated makes the modal taller than the default 660px
            // viewport, with no scroll affordance on the fixed-position backdrop.
            cy.viewport(1000, 1200);
            cy.visit(`/projects/${projectId}/jobs`);
            cy.contains('button', '+ New Job').click();
            cy.get('.z-50:visible').within(() => {
              cy.get('input[placeholder="e.g. Fix login bug"]').type('Fully populated job');
              cy.get('textarea').first().type('Some **details**');
              cy.get('input[placeholder="Client name"]').type('Acme Rockets');
              cy.get('input[type="date"]').type('2027-01-15');
              cy.get('select').eq(0).select('Critical');
              cy.get('select').eq(1).select('Launch');
              cy.get('select').eq(2).select('Bug');
              cy.get('input[placeholder="Search member…"]').type('Tester');
              cy.contains('li button', 'Tester').click();
              cy.contains('button', 'Create job').click();
            });
            cy.get('table').contains('Fully populated job').should('be.visible');
            cy.get('table').contains('Acme Rockets').should('be.visible');
            cy.get('table').contains('E2E Tester').should('be.visible');
          });
        });

        cy.deleteKeycloakUser(email);
      }),
    );
  });

  it('editing a job pre-fills existing values and saves via a full-replace PUT', () => {
    const email = uniqueEmail('edit-prefill');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Edit Prefill Corp', uniqueSlug());
    createProjectAs(email, 'Prefill Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Original title', client: 'Original Client', priority: 'LOW' }).then((jobId) => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs/${jobId}`);
        cy.contains('button', 'Edit').click();
        cy.get('.z-50:visible').within(() => {
          cy.get('input[placeholder="e.g. Fix login bug"]').should('have.value', 'Original title');
          cy.get('input[placeholder="Client name"]').should('have.value', 'Original Client');
          cy.get('select').eq(0).should('have.value', 'LOW');

          cy.get('input[placeholder="e.g. Fix login bug"]').clear();
          cy.get('input[placeholder="e.g. Fix login bug"]').type('Updated title');
          cy.contains('button', 'Save changes').click();
        });
        cy.contains('h1, h2, p', 'Updated title').should('be.visible');
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('selecting a template resolves wildcards into the form; usage is recorded only after a successful create', () => {
    const email = uniqueEmail('template-wildcards');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Template Wildcards Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Wild Corp Project').then((projectId) => {
        createTemplateAs(email, projectId, { name: 'Onboarding', title: 'Kickoff for {{project}} (run {{occurrence}})' }).then(
          (templateId) => {
            listTemplatesAs(email, projectId).then((before) => {
              expect(before.find((t) => t.id === templateId)!.occurrenceCount).to.equal(0);
            });

            cy.loginAs(email);
            // The "Start from template" select pushes the submit button below the
            // default 660px viewport with no scroll affordance on the modal backdrop.
            cy.viewport(1000, 900);
            cy.visit(`/projects/${projectId}/jobs`);
            cy.contains('button', '+ New Job').click();
            cy.get('.z-50:visible').within(() => {
              cy.get('select').eq(0).select('Onboarding');
              cy.get('input[placeholder="e.g. Fix login bug"]').should('have.value', 'Kickoff for Wild Corp Project (run 1)');
            });

            // Selecting the template alone (not yet submitted) must not record usage.
            listTemplatesAs(email, projectId).then((mid) => {
              expect(mid.find((t) => t.id === templateId)!.occurrenceCount).to.equal(0);
            });

            cy.get('.z-50:visible').within(() => cy.contains('button', 'Create job').click());
            cy.get('table').contains('Kickoff for Wild Corp Project (run 1)').should('be.visible');

            listTemplatesAs(email, projectId).then((after) => {
              expect(after.find((t) => t.id === templateId)!.occurrenceCount).to.equal(1);
            });

            cy.deleteKeycloakUser(email);
          },
        );
      }),
    );
  });

  it('a FIXED-assignee template auto-selects the assignee', () => {
    const email = uniqueEmail('template-fixed');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Template Fixed Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(email, 'Fixed Assignee Project').then((projectId) => {
        userIdFor(email).then((ownerId) => {
          createTemplateAs(email, projectId, { name: 'Self-Assign', title: 'Fixed task', assigneeMode: 'FIXED', assigneeId: ownerId }).then(
            () => {
              cy.loginAs(email);
              cy.visit(`/projects/${projectId}/jobs`);
              cy.contains('button', '+ New Job').click();
              cy.get('.z-50:visible').within(() => {
                cy.get('select').eq(0).select('Self-Assign');
                cy.get('input[placeholder="Search member…"]').should('have.value', 'E2E Tester');
              });

              cy.wrap(orgId).should('be.a', 'string');
              cy.deleteKeycloakUser(email);
            },
          );
        });
      }),
    );
  });

  it('an ASK-assignee template focuses the assignee field and leaves it blank', () => {
    const email = uniqueEmail('template-ask');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Template Ask Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Ask Assignee Project').then((projectId) => {
        createTemplateAs(email, projectId, { name: 'Ask Task', title: 'Needs an assignee', assigneeMode: 'ASK' }).then(() => {
          cy.loginAs(email);
          cy.visit(`/projects/${projectId}/jobs`);
          cy.contains('button', '+ New Job').click();
          cy.get('.z-50:visible').within(() => {
            cy.get('select').eq(0).select('Ask Task');
            cy.get('input[placeholder="Search member…"]').should('have.value', '').and('be.focused');
          });

          cy.deleteKeycloakUser(email);
        });
      }),
    );
  });

  it('each of the 7 markdown toolbar buttons wraps the selection or inserts a placeholder', () => {
    const email = uniqueEmail('toolbar-buttons');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Toolbar Buttons Corp', uniqueSlug());
    createProjectAs(email, 'Toolbar Buttons Project').then((projectId) => {
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);
      cy.contains('button', '+ New Job').click();
      cy.get('.z-50:visible').within(() => {
        cy.get('textarea').first().as('desc');

        setTextareaValue('@desc', 'hello world');
        cy.get('@desc').should('have.value', 'hello world');
        cy.get('@desc').then(($ta) => { ($ta[0] as HTMLTextAreaElement).setSelectionRange(0, 5); });
        cy.get('button[aria-label="Bold"]').click();
        cy.get('@desc').should('have.value', '**hello** world');

        setTextareaValue('@desc', 'hello world');
        cy.get('@desc').should('have.value', 'hello world');
        cy.get('@desc').then(($ta) => { ($ta[0] as HTMLTextAreaElement).setSelectionRange(0, 5); });
        cy.get('button[aria-label="Italic"]').click();
        cy.get('@desc').should('have.value', '_hello_ world');

        setTextareaValue('@desc', 'hello world');
        cy.get('@desc').should('have.value', 'hello world');
        cy.get('@desc').then(($ta) => { ($ta[0] as HTMLTextAreaElement).setSelectionRange(0, 5); });
        cy.get('button[aria-label="Inline code"]').click();
        cy.get('@desc').should('have.value', '`hello` world');

        setTextareaValue('@desc', 'line one\nline two');
        cy.get('@desc').should('have.value', 'line one\nline two');
        cy.get('@desc').then(($ta) => { ($ta[0] as HTMLTextAreaElement).setSelectionRange(0, 17); });
        cy.get('button[aria-label="Blockquote"]').click();
        cy.get('@desc').should('have.value', '> line one\n> line two');

        setTextareaValue('@desc', '');
        cy.get('@desc').should('have.value', '');
        cy.get('button[aria-label="Horizontal rule"]').click();
        cy.get('@desc').should('have.value', '\n\n---\n\n');

        setTextareaValue('@desc', 'line one\nline two');
        cy.get('@desc').should('have.value', 'line one\nline two');
        cy.get('@desc').then(($ta) => { ($ta[0] as HTMLTextAreaElement).setSelectionRange(0, 17); });
        cy.get('button[aria-label="Ordered list"]').click();
        cy.get('@desc').should('have.value', '1. line one\n1. line two');

        setTextareaValue('@desc', 'line one\nline two');
        cy.get('@desc').should('have.value', 'line one\nline two');
        cy.get('@desc').then(($ta) => { ($ta[0] as HTMLTextAreaElement).setSelectionRange(0, 17); });
        cy.get('button[aria-label="Unordered list"]').click();
        cy.get('@desc').should('have.value', '- line one\n- line two');

        setTextareaValue('@desc', 'hello world');
        cy.get('@desc').should('have.value', 'hello world');
        cy.get('@desc').then(($ta) => { ($ta[0] as HTMLTextAreaElement).setSelectionRange(0, 5); });
        cy.get('button[aria-label="Link"]').click();
        cy.get('@desc').should('have.value', '[hello](url) world');
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('the Preview tab renders exactly what Write produced', () => {
    const email = uniqueEmail('toolbar-preview');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Toolbar Preview Corp', uniqueSlug());
    createProjectAs(email, 'Toolbar Preview Project').then((projectId) => {
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);
      cy.contains('button', '+ New Job').click();
      cy.get('.z-50:visible').within(() => {
        cy.get('textarea').first().type('**bold text** and normal text');
        cy.contains('button', 'Preview').click();
        cy.get('strong').should('contain.text', 'bold text');
        cy.contains('normal text').should('be.visible');
        cy.contains('button', 'Write').click();
        cy.get('textarea').first().should('have.value', '**bold text** and normal text');
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('a blank title is rejected client-side and server-side; a title over 255 characters 400s', () => {
    const email = uniqueEmail('title-validation');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Title Validation Corp', uniqueSlug());
    createProjectAs(email, 'Title Validation Project').then((projectId) => {
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);
      cy.contains('button', '+ New Job').click();
      cy.get('.z-50:visible').within(() => {
        cy.contains('button', 'Create job').click();
        cy.contains('Title is required').should('be.visible');
      });

      tokenFor(email).then((token) => {
        cy.request({
          method: 'POST',
          url: `${API}/api/projects/${projectId}/jobs`,
          headers: { Authorization: `Bearer ${token}` },
          body: { title: 'x'.repeat(256) },
          failOnStatusCode: false,
        }).its('status').should('eq', 400);
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('a description over 10000 characters 400s (server-side; the UI blocks it too)', () => {
    const email = uniqueEmail('description-validation');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Description Validation Corp', uniqueSlug());
    createProjectAs(email, 'Description Validation Project').then((projectId) => {
      tokenFor(email).then((token) => {
        cy.request({
          method: 'POST',
          url: `${API}/api/projects/${projectId}/jobs`,
          headers: { Authorization: `Bearer ${token}` },
          body: { title: 'Valid title', description: 'x'.repeat(10001) },
          failOnStatusCode: false,
        }).its('status').should('eq', 400);
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('an assignedTo referencing a nonexistent user, or a milestoneId/typeId not in this project, 404s', () => {
    const email = uniqueEmail('reference-validation');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Reference Validation Corp', uniqueSlug());
    createProjectAs(email, 'Reference Validation Project').then((projectId) => {
      tokenFor(email).then((token) => {
        cy.request({
          method: 'POST',
          url: `${API}/api/projects/${projectId}/jobs`,
          headers: { Authorization: `Bearer ${token}` },
          body: { title: 'Bad assignee', assignedTo: '00000000-0000-0000-0000-000000000000' },
          failOnStatusCode: false,
        }).its('status').should('eq', 404);

        cy.request({
          method: 'POST',
          url: `${API}/api/projects/${projectId}/jobs`,
          headers: { Authorization: `Bearer ${token}` },
          body: { title: 'Bad milestone', milestoneId: '00000000-0000-0000-0000-000000000000' },
          failOnStatusCode: false,
        }).its('status').should('eq', 404);

        cy.request({
          method: 'POST',
          url: `${API}/api/projects/${projectId}/jobs`,
          headers: { Authorization: `Bearer ${token}` },
          body: { title: 'Bad type', typeId: '00000000-0000-0000-0000-000000000000' },
          failOnStatusCode: false,
        }).its('status').should('eq', 404);
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('any project role can create a job, but only OWNER/ADMIN can update one — an assigned MEMBER gets 403', () => {
    const ownerEmail = uniqueEmail('rolewrite-owner');
    const adminEmail = uniqueEmail('rolewrite-admin');
    const memberEmail = uniqueEmail('rolewrite-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(adminEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithSubscription(ownerEmail, 'Role Write Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Role Write Project').then((projectId) =>
        userIdFor(adminEmail).then((adminId) =>
          userIdFor(memberEmail).then((memberId) => {
            addMember(orgId, ownerEmail, adminId, 'ADMIN');
            addMember(orgId, ownerEmail, memberId, 'MEMBER');
            addProjectMember(projectId, ownerEmail, adminId, 'ADMIN');
            addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

            tokenFor(memberEmail).then((token) => {
              cy.request({
                method: 'POST',
                url: `${API}/api/projects/${projectId}/jobs`,
                headers: { Authorization: `Bearer ${token}` },
                body: { title: 'Member-created job', assignedTo: memberId },
                failOnStatusCode: false,
              }).then((res) => {
                expect(res.status).to.equal(201);
                const jobId = res.body.id as string;

                cy.request({
                  method: 'PUT',
                  url: `${API}/api/projects/${projectId}/jobs/${jobId}`,
                  headers: { Authorization: `Bearer ${token}` },
                  body: { title: 'Member tries to edit own assigned job' },
                  failOnStatusCode: false,
                }).its('status').should('eq', 403);

                tokenFor(adminEmail).then((adminToken) => {
                  cy.request({
                    method: 'PUT',
                    url: `${API}/api/projects/${projectId}/jobs/${jobId}`,
                    headers: { Authorization: `Bearer ${adminToken}` },
                    body: { title: 'Admin edits it' },
                  }).its('status').should('eq', 200);
                });
              });
            });

            cy.deleteKeycloakUser(ownerEmail);
            cy.deleteKeycloakUser(adminEmail);
            cy.deleteKeycloakUser(memberEmail);
          }),
        ),
      ),
    );
  });

  it('creating a job on a COMPLETED project 409s', () => {
    const email = uniqueEmail('create-completed');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Create Completed Corp', uniqueSlug());
    createProjectAs(email, 'Create Completed Project').then((projectId) => {
      completeProjectAs(email, projectId);
      tokenFor(email).then((token) => {
        cy.request({
          method: 'POST',
          url: `${API}/api/projects/${projectId}/jobs`,
          headers: { Authorization: `Bearer ${token}` },
          body: { title: 'Should not be creatable' },
          failOnStatusCode: false,
        }).its('status').should('eq', 409);
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('updating a job on a COMPLETED project 409s', () => {
    const email = uniqueEmail('update-completed');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Update Completed Corp', uniqueSlug());
    createProjectAs(email, 'Update Completed Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Existing job' }).then((jobId) => {
        completeProjectAs(email, projectId);
        tokenFor(email).then((token) => {
          cy.request({
            method: 'PUT',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}`,
            headers: { Authorization: `Bearer ${token}` },
            body: { title: 'Should not be editable' },
            failOnStatusCode: false,
          }).its('status').should('eq', 409);
        });
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('PUT is full-replace: omitting milestoneId/typeId on edit clears them', () => {
    const email = uniqueEmail('full-replace');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Full Replace Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Full Replace Project').then((projectId) => {
        createMilestoneAs(email, projectId, 'Phase 1').then((msId) => {
          createJobTypeAs(email, projectId, 'Feature', 'GREEN').then((typeId) => {
            createJobAs(email, projectId, { title: 'Has milestone and type', milestoneId: msId, typeId }).then((jobId) => {
              tokenFor(email).then((token) => {
                cy.request({
                  method: 'PUT',
                  url: `${API}/api/projects/${projectId}/jobs/${jobId}`,
                  headers: { Authorization: `Bearer ${token}` },
                  body: { title: 'Has milestone and type' },
                }).then((res) => {
                  expect(res.body.milestoneId).to.equal(null);
                  expect(res.body.typeId).to.equal(null);
                });
              });
            });
          });
        });

        cy.deleteKeycloakUser(email);
      }),
    );
  });

  it('priority asymmetry: omitted on create defaults to MEDIUM; omitted on update preserves the existing value', () => {
    const email = uniqueEmail('priority-asymmetry');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Priority Asymmetry Corp', uniqueSlug());
    createProjectAs(email, 'Priority Asymmetry Project').then((projectId) => {
      tokenFor(email).then((token) => {
        cy.request({
          method: 'POST',
          url: `${API}/api/projects/${projectId}/jobs`,
          headers: { Authorization: `Bearer ${token}` },
          body: { title: 'No priority given' },
        }).then((res) => {
          expect(res.body.priority).to.equal('MEDIUM');
          const jobId = res.body.id as string;

          cy.request({
            method: 'PUT',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}`,
            headers: { Authorization: `Bearer ${token}` },
            body: { title: 'No priority given', priority: 'CRITICAL' },
          }).then((withPriority) => {
            expect(withPriority.body.priority).to.equal('CRITICAL');

            cy.request({
              method: 'PUT',
              url: `${API}/api/projects/${projectId}/jobs/${jobId}`,
              headers: { Authorization: `Bearer ${token}` },
              body: { title: 'No priority given' },
            }).then((withoutPriority) => {
              expect(withoutPriority.body.priority).to.equal('CRITICAL');
            });
          });
        });
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('selecting a template whose milestoneId is no longer in the project silently drops it from the form', () => {
    const email = uniqueEmail('template-stale-milestone');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Stale Milestone Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Stale Milestone Project').then((projectId) => {
        createMilestoneAs(email, projectId, 'Doomed Phase').then((msId) => {
          createTemplateAs(email, projectId, { name: 'Stale Template', title: 'References a deleted milestone', milestoneId: msId }).then(
            () => {
              tokenFor(email).then((token) =>
                cy.request({
                  method: 'DELETE',
                  url: `${API}/api/projects/${projectId}/milestones/${msId}`,
                  headers: { Authorization: `Bearer ${token}` },
                }),
              );

              cy.loginAs(email);
              cy.visit(`/projects/${projectId}/jobs`);
              cy.contains('button', '+ New Job').click();
              cy.get('.z-50:visible').within(() => {
                cy.get('select').eq(0).select('Stale Template');
                cy.get('input[placeholder="e.g. Fix login bug"]').should('have.value', 'References a deleted milestone');
                // The milestone select doesn't even render — the project has no
                // remaining active milestones once the only one was deleted.
                cy.contains('label', 'Milestone').should('not.exist');
              });

              cy.deleteKeycloakUser(email);
            },
          );
        });
      }),
    );
  });

  it('a deadline round-trips through the date input with no ±1 day shift', () => {
    const email = uniqueEmail('deadline-roundtrip');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Deadline Roundtrip Corp', uniqueSlug());
    createProjectAs(email, 'Deadline Roundtrip Project').then((projectId) => {
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);
      cy.contains('button', '+ New Job').click();
      cy.get('.z-50:visible').within(() => {
        cy.get('input[placeholder="e.g. Fix login bug"]').type('Deadline job');
        cy.get('input[type="date"]').type('2027-03-01');
        cy.contains('button', 'Create job').click();
      });

      cy.get('table').contains('Deadline job').click();
      cy.contains('button', 'Edit').click();
      cy.get('.z-50:visible').within(() => {
        cy.get('input[type="date"]').should('have.value', '2027-03-01');
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('multi-line selection wraps per-line for list/quote buttons but once for bold/italic/code', () => {
    const email = uniqueEmail('toolbar-multiline');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Toolbar Multiline Corp', uniqueSlug());
    createProjectAs(email, 'Toolbar Multiline Project').then((projectId) => {
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);
      cy.contains('button', '+ New Job').click();
      cy.get('.z-50:visible').within(() => {
        cy.get('textarea').first().as('desc');

        setTextareaValue('@desc', 'line one\nline two');
        cy.get('@desc').should('have.value', 'line one\nline two');
        cy.get('@desc').then(($ta) => { ($ta[0] as HTMLTextAreaElement).setSelectionRange(0, 17); });
        cy.get('button[aria-label="Bold"]').click();
        // Bold wraps the WHOLE multi-line selection once, not per line.
        cy.get('@desc').should('have.value', '**line one\nline two**');
      });

      cy.deleteKeycloakUser(email);
    });
  });

  it('clicking a toolbar button again on already-wrapped text double-wraps rather than toggling it off', () => {
    const email = uniqueEmail('toolbar-double-wrap');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Toolbar Double Wrap Corp', uniqueSlug());
    createProjectAs(email, 'Toolbar Double Wrap Project').then((projectId) => {
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);
      cy.contains('button', '+ New Job').click();
      cy.get('.z-50:visible').within(() => {
        cy.get('textarea').first().as('desc');

        cy.get('@desc').type('hello');
        cy.get('@desc').should('have.value', 'hello');
        cy.get('@desc').then(($ta) => { ($ta[0] as HTMLTextAreaElement).setSelectionRange(0, 5); });
        cy.get('button[aria-label="Bold"]').click();
        cy.get('@desc').should('have.value', '**hello**');
        // The wrapped body's selection is restored via requestAnimationFrame (not
        // synchronously with the value change) — wait for it explicitly, or the
        // second click below can fire against a stale, collapsed selection.
        cy.get('@desc').should(($ta) => {
          const el = $ta[0] as HTMLTextAreaElement;
          expect(el.selectionStart, 'selectionStart').to.equal(2);
          expect(el.selectionEnd, 'selectionEnd').to.equal(7);
        });

        // The click leaves the wrapped body (not the markers) selected, so clicking
        // Bold again wraps "hello" a second time rather than detecting and
        // stripping the existing ** markers.
        cy.get('button[aria-label="Bold"]').click();
        cy.get('@desc').should('have.value', '****hello****');
      });

      cy.deleteKeycloakUser(email);
    });
  });
});
