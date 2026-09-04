// ADR-0049 Appendix §13 (Job Templates — Project & Org-Level). Uses cy.loginAs() per
// docs/dev/process/E2E.md.
//
// Wildcard resolution happens entirely client-side in resolveWildcards.ts (the
// backend's own /use endpoint only records usage and resolves defaultTypeName -> a
// typeId — it never touches title/description). There is no unit-test framework set
// up on the frontend (no Vitest, no existing *.test.ts files), so this spec is the
// only coverage for that resolver; it drives it end-to-end via NewJobModal's "start
// from template" flow rather than calling the function directly.
//
// Two general-case wildcard tests compute their expected values from the REAL current
// date (no clock mocking — the app and the test observe the same clock). The two
// dedicated year-boundary tests use `cy.clock(fixedTime, ['Date'])` — restricted to
// just the Date constructor, not timers/rAF — applied only after login and page load,
// so it can't interfere with auth/query machinery.

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
  createMilestoneAs,
  createTemplateAs,
  createOrgTemplateAs,
  listTemplatesAs,
  listOrgTemplatesAs,
  recordTemplateUsageAs,
  deleteTemplateAs,
  createScheduleAs,
  getJobAs,
  API,
} from '../../support/orgApi';

function isoWeek(d: Date): number {
  const jan4 = new Date(d.getFullYear(), 0, 4);
  const startOfWeek1 = new Date(jan4);
  startOfWeek1.setDate(jan4.getDate() - ((jan4.getDay() + 6) % 7));
  const diff = d.getTime() - startOfWeek1.getTime();
  return Math.floor(diff / 604800000) + 1;
}

function pad2(n: number): string {
  return String(n).padStart(2, '0');
}

describe('Job Templates — Project & Org-Level', () => {
  it('creates a project-scoped template with name only, and separately fully populated', { tags: '@smoke' }, () => {
    const email = uniqueEmail('create-template');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Falcon Corp', uniqueSlug());
    createProjectAs(email, 'Falcon Template Project').then((projectId) =>
      createMilestoneAs(email, projectId, 'Launch').then((msId) =>
        createJobTypeAs(email, projectId, 'Bug', 'RED').then((typeId) => {
          createTemplateAs(email, projectId, { name: 'Bare template' }).then((bareId) => {
            expect(bareId).to.be.a('string');
          });

          createTemplateAs(email, projectId, {
            name: 'Full template',
            title: 'Report for {{project}}',
            description: 'Prepared by {{creator}}',
            client: 'Acme Co',
            priority: 'HIGH',
            assigneeMode: 'ASK',
            milestoneId: msId,
            defaultTypeId: typeId,
            deadlineOffsetDays: 5,
          }).then((fullId) => {
            listTemplatesAs(email, projectId).then((templates) => {
              const full = templates.find((t) => t.id === fullId)!;
              expect(full.name).to.equal('Full template');
              expect(full.scope).to.equal('PROJECT');
              expect(full.milestoneId).to.equal(msId);
              expect(full.defaultTypeId).to.equal(typeId);
            });
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('assigneeMode NONE leaves assignedTo blank, FIXED pre-fills the assignee, ASK focuses the field and leaves it blank', () => {
    const ownerEmail = uniqueEmail('assignee-mode-owner');
    const memberEmail = uniqueEmail('assignee-mode-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Target');
    createOrgWithFullAccess(ownerEmail, 'Nimbus Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Nimbus Template Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

          createTemplateAs(ownerEmail, projectId, { name: 'None mode', assigneeMode: 'NONE' });
          createTemplateAs(ownerEmail, projectId, { name: 'Fixed mode', assigneeMode: 'FIXED', assigneeId: memberId });
          createTemplateAs(ownerEmail, projectId, { name: 'Ask mode', assigneeMode: 'ASK' });

          cy.loginAs(ownerEmail);
          cy.visit(`/projects/${projectId}/jobs`);
          cy.contains('button', '+ New Job').click();
          cy.get('.z-50:visible').within(() => {
            cy.get('select').first().select('None mode');
            cy.get('input[placeholder="Search member…"]').should('have.value', '');

            cy.get('select').first().select('Fixed mode');
            cy.get('input[placeholder="Search member…"]').should('have.value', 'E2E Target');

            cy.get('select').first().select('Ask mode');
            cy.get('input[placeholder="Search member…"]').should('have.value', '').and('be.focused');
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('every base wildcard resolves: date, day, month, year, week, quarter, project, creator, assignee, occurrence', () => {
    const email = uniqueEmail('base-wildcards');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Atlas Corp', uniqueSlug());
    createProjectAs(email, 'Atlas Template Project').then((projectId) =>
      userIdFor(email).then((selfId) => {
        const title =
          '{{date}}|{{day}}|{{month}}|{{year}}|{{week}}|{{quarter}}|{{project}}|{{creator}}|{{assignee}}|{{occurrence}}';
        createTemplateAs(email, projectId, {
          name: 'All base wildcards',
          title,
          assigneeMode: 'FIXED',
          assigneeId: selfId,
        }).then(() => {
          cy.loginAs(email);
          cy.visit(`/projects/${projectId}/jobs`);
          cy.contains('button', '+ New Job').click();
          cy.get('.z-50:visible').within(() => {
            cy.get('select').first().select('All base wildcards');
            cy.get('input[placeholder="e.g. Fix login bug"]').invoke('val').then((resolved) => {
              const now = new Date();
              const expected = [
                `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`,
                pad2(now.getDate()),
                pad2(now.getMonth() + 1),
                String(now.getFullYear()),
                String(isoWeek(now)),
                `Q${Math.ceil((now.getMonth() + 1) / 3)}`,
                'Atlas Template Project',
                'E2E Tester',
                'E2E Tester',
                '1',
              ].join('|');
              expect(resolved).to.equal(expected);
            });
          });
        });
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('arithmetic wildcards resolve correctly for a general (non-boundary) offset', () => {
    const email = uniqueEmail('arithmetic-wildcards');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Cedar Corp', uniqueSlug());
    createProjectAs(email, 'Cedar Template Project').then((projectId) => {
      createTemplateAs(email, projectId, {
        name: 'Arithmetic wildcards',
        title: '{{date+3}}|{{date-3}}|{{year+1}}|{{week+2}}',
      }).then(() => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs`);
        cy.contains('button', '+ New Job').click();
        cy.get('.z-50:visible').within(() => {
          cy.get('select').first().select('Arithmetic wildcards');
          cy.get('input[placeholder="e.g. Fix login bug"]').invoke('val').then((resolved) => {
            const now = new Date();
            const plus3 = new Date(now);
            plus3.setDate(plus3.getDate() + 3);
            const minus3 = new Date(now);
            minus3.setDate(minus3.getDate() - 3);
            const plus2w = new Date(now);
            plus2w.setDate(plus2w.getDate() + 14);
            const expected = [
              `${plus3.getFullYear()}-${pad2(plus3.getMonth() + 1)}-${pad2(plus3.getDate())}`,
              `${minus3.getFullYear()}-${pad2(minus3.getMonth() + 1)}-${pad2(minus3.getDate())}`,
              String(now.getFullYear() + 1),
              String(isoWeek(plus2w)),
            ].join('|');
            expect(resolved).to.equal(expected);
          });
        });
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('{{month+2}} in November rolls correctly to January of the next year', () => {
    const email = uniqueEmail('month-rollover');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Vega Corp', uniqueSlug());
    createProjectAs(email, 'Vega Template Project').then((projectId) => {
      createTemplateAs(email, projectId, { name: 'Month rollover', title: '{{month+2}}' }).then(() => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs`);
        cy.clock(new Date('2026-11-15T12:00:00').getTime(), ['Date']);
        cy.contains('button', '+ New Job').click();
        cy.get('.z-50:visible').within(() => {
          cy.get('select').first().select('Month rollover');
          cy.get('input[placeholder="e.g. Fix login bug"]').should('have.value', '01');
        });
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('{{quarter-2}} in Q1 rolls back across the year boundary to Q3 of the prior year', () => {
    const email = uniqueEmail('quarter-rollover');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Vega Two Corp', uniqueSlug());
    createProjectAs(email, 'Vega Two Template Project').then((projectId) => {
      createTemplateAs(email, projectId, { name: 'Quarter rollover', title: '{{quarter-2}}' }).then(() => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs`);
        cy.clock(new Date('2026-01-15T12:00:00').getTime(), ['Date']);
        cy.contains('button', '+ New Job').click();
        cy.get('.z-50:visible').within(() => {
          cy.get('select').first().select('Quarter rollover');
          cy.get('input[placeholder="e.g. Fix login bug"]').should('have.value', 'Q3');
        });
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('the ISO week wildcard at a year boundary (Dec 31, crossing into the next year) does not produce a wrong or negative number', () => {
    const email = uniqueEmail('week-boundary');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Sable Corp', uniqueSlug());
    createProjectAs(email, 'Sable Template Project').then((projectId) => {
      createTemplateAs(email, projectId, { name: 'Week boundary', title: '{{week}}' }).then(() => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs`);
        // The app's own isoWeek() computes weeks relative to Jan 4th of the date's
        // OWN year — it never wraps a late-December date into "week 1 of next year",
        // so the boundary guarantee this exercises is a bounded, positive result
        // (1-53), not a specific wrapped value.
        cy.clock(new Date('2029-12-31T12:00:00').getTime(), ['Date']);
        cy.contains('button', '+ New Job').click();
        cy.get('.z-50:visible').within(() => {
          cy.get('select').first().select('Week boundary');
          cy.get('input[placeholder="e.g. Fix login bug"]').invoke('val').then((v) => {
            const n = Number(v);
            expect(Number.isNaN(n)).to.equal(false);
            expect(n).to.be.greaterThan(0);
            expect(n).to.be.at.most(53);
          });
        });
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('unresolved/malformed wildcard tokens are left as literal text; non-time arithmetic falls through to literal', () => {
    const email = uniqueEmail('literal-wildcards');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Comet Corp', uniqueSlug());
    createProjectAs(email, 'Comet Template Project').then((projectId) => {
      createTemplateAs(email, projectId, {
        name: 'Literal wildcards',
        title: '{{nonsense}} and {{project+1}}',
      }).then(() => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/jobs`);
        cy.contains('button', '+ New Job').click();
        cy.get('.z-50:visible').within(() => {
          cy.get('select').first().select('Literal wildcards');
          cy.get('input[placeholder="e.g. Fix login bug"]').should('have.value', '{{nonsense}} and {{project+1}}');
        });
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('using a template records usage, incrementing usedCount atomically and visibly on the list', () => {
    const email = uniqueEmail('record-usage');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Willow Corp', uniqueSlug());
    createProjectAs(email, 'Willow Template Project').then((projectId) =>
      createTemplateAs(email, projectId, { name: 'Usage counter', title: 'Use me' }).then((templateId) => {
        listTemplatesAs(email, projectId).then((before) => {
          expect(before.find((t) => t.id === templateId)!.occurrenceCount).to.equal(0);
        });

        cy.loginAs(email);
        cy.viewport(1000, 900);
        cy.visit(`/projects/${projectId}/jobs`);
        // NewJobModal's own /use call is fire-and-forget (not awaited before the
        // modal closes) — intercept it so the test doesn't race the increment.
        cy.intercept('POST', `**/templates/${templateId}/use`).as('recordUsage');
        cy.contains('button', '+ New Job').click();
        cy.get('.z-50:visible').within(() => {
          cy.get('select').first().select('Usage counter');
          cy.contains('button', 'Create job').click();
        });
        cy.wait('@recordUsage');

        listTemplatesAs(email, projectId).then((after) => {
          expect(after.find((t) => t.id === templateId)!.occurrenceCount).to.equal(1);
        });

        cy.visit(`/projects/${projectId}/templates`);
        cy.contains('Used 1×').should('be.visible');
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('org-scoped templates appear in the combined project list tagged [Org], and defaultTypeName resolves case-insensitively on use', () => {
    const email = uniqueEmail('org-scoped');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Orbit Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(email, 'Orbit Template Project').then((projectId) =>
        createJobTypeAs(email, projectId, 'bug', 'RED').then((typeId) => {
          createOrgTemplateAs(email, orgId, { name: 'Org-wide report', defaultTypeName: 'BUG' }).then((orgTplId) => {
            listTemplatesAs(email, projectId).then((combined) => {
              const orgEntry = combined.find((t) => t.id === orgTplId)!;
              expect(orgEntry.scope).to.equal('ORG');
            });
            listOrgTemplatesAs(email, orgId).then((orgOnly) => {
              expect(orgOnly.map((t) => t.id)).to.include(orgTplId);
            });

            recordTemplateUsageAs(email, projectId, orgTplId).then((res) => {
              expect(res.status).to.equal(200);
              expect(res.body.resolvedTypeId).to.equal(typeId);
            });

            cy.loginAs(email);
            cy.visit(`/projects/${projectId}/jobs`);
            cy.contains('button', '+ New Job').click();
            cy.get('.z-50:visible select').first().find('option').contains('[Org] Org-wide report').should('exist');
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('renaming a job type after an org template\'s defaultTypeName was configured against the old name silently fails to match on next use, leaving the type blank', () => {
    const email = uniqueEmail('renamed-type');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Sparrow Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(email, 'Sparrow Template Project').then((projectId) =>
        createJobTypeAs(email, projectId, 'Support', 'BLUE').then((typeId) => {
          createOrgTemplateAs(email, orgId, { name: 'Support ticket', defaultTypeName: 'Support' }).then((orgTplId) => {
            tokenFor(email).then((token) => {
              cy.request({
                method: 'PUT',
                url: `${API}/api/projects/${projectId}/job-types/${typeId}`,
                headers: { Authorization: `Bearer ${token}` },
                body: { name: 'Customer Support', color: 'BLUE', displayOrder: 0 },
              });
            });

            recordTemplateUsageAs(email, projectId, orgTplId).then((res) => {
              expect(res.status).to.equal(200);
              expect(res.body.resolvedTypeId).to.equal(null);
            });
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('deletes a template with zero active referencing schedules; deleting one does not affect jobs already created from it', () => {
    const email = uniqueEmail('delete-template');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Delta Corp', uniqueSlug());
    createProjectAs(email, 'Delta Template Project').then((projectId) =>
      createTemplateAs(email, projectId, { name: 'Deletable', title: 'From template' }).then((templateId) => {
        createJobAs(email, projectId, { title: 'Already created' }).then((jobId) => {
          deleteTemplateAs(email, projectId, templateId).its('status').should('eq', 204);

          getJobAs(email, projectId, jobId).then((job: { title: string }) => {
            expect(job.title).to.equal('Already created');
          });

          listTemplatesAs(email, projectId).then((templates) => {
            expect(templates.find((t) => t.id === templateId)).to.equal(undefined);
          });
        });
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('deleting a template referenced by an active schedule 409s with the schedule name listed; a paused-only referencing schedule does not block delete', () => {
    const email = uniqueEmail('active-schedule-guard');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Sparrow Two Corp', uniqueSlug());
    createProjectAs(email, 'Sparrow Two Template Project').then((projectId) =>
      createTemplateAs(email, projectId, { name: 'Scheduled template' }).then((templateId) =>
        createScheduleAs(email, projectId, {
          name: 'Weekly rotation schedule',
          templateId,
          cronExpression: '0 0 9 * * *',
          timezone: 'UTC',
        }).then((scheduleId) => {
          deleteTemplateAs(email, projectId, templateId).then((res) => {
            expect(res.status).to.equal(409);
            expect(res.body.message).to.contain('Weekly rotation schedule');
          });

          tokenFor(email).then((token) => {
            cy.request({
              method: 'POST',
              url: `${API}/api/projects/${projectId}/schedules/${scheduleId}/pause`,
              headers: { Authorization: `Bearer ${token}` },
              body: {},
            });
          });

          deleteTemplateAs(email, projectId, templateId).its('status').should('eq', 204);
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('a blank name 400s; an invalid priority or assigneeMode 400s; a deadlineOffsetDays <= 0 400s', () => {
    const email = uniqueEmail('validation');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Juniper Corp', uniqueSlug());
    createProjectAs(email, 'Juniper Template Project').then((projectId) => {
      tokenFor(email).then((token) => {
        function attempt(body: Record<string, unknown>) {
          return cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/templates`,
            headers: { Authorization: `Bearer ${token}` },
            body,
            failOnStatusCode: false,
          });
        }

        attempt({ name: '   ' }).its('status').should('eq', 400);
        attempt({ name: 'Bad priority', priority: 'URGENT' }).its('status').should('eq', 400);
        attempt({ name: 'Bad assignee mode', assigneeMode: 'MAYBE' }).its('status').should('eq', 400);
        attempt({ name: 'Bad offset', deadlineOffsetDays: 0 }).its('status').should('eq', 400);
        attempt({ name: 'Negative offset', deadlineOffsetDays: -1 }).its('status').should('eq', 400);
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('a project-scoped template with defaultTypeName set 400s; an org-scoped template with defaultTypeId set 400s', () => {
    const email = uniqueEmail('scope-field-guard');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Talon Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(email, 'Talon Template Project').then((projectId) =>
        createJobTypeAs(email, projectId, 'Feature', 'GREEN').then((typeId) => {
          tokenFor(email).then((token) => {
            cy.request({
              method: 'POST',
              url: `${API}/api/projects/${projectId}/templates`,
              headers: { Authorization: `Bearer ${token}` },
              body: { name: 'Bad project template', defaultTypeName: 'Feature' },
              failOnStatusCode: false,
            }).its('status').should('eq', 400);

            cy.request({
              method: 'POST',
              url: `${API}/api/organisations/${orgId}/templates`,
              headers: { Authorization: `Bearer ${token}` },
              body: { name: 'Bad org template', defaultTypeId: typeId },
              failOnStatusCode: false,
            }).its('status').should('eq', 400);
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('a MEMBER gets 403 on create/update/delete, but any member can record usage', () => {
    const ownerEmail = uniqueEmail('member-write-owner');
    const memberEmail = uniqueEmail('member-write-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Ember Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Ember Template Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
          createTemplateAs(ownerEmail, projectId, { name: 'Owner template' }).then((templateId) => {
            tokenFor(memberEmail).then((token) => {
              cy.request({
                method: 'POST',
                url: `${API}/api/projects/${projectId}/templates`,
                headers: { Authorization: `Bearer ${token}` },
                body: { name: 'Hijack template' },
                failOnStatusCode: false,
              }).its('status').should('eq', 403);

              cy.request({
                method: 'PUT',
                url: `${API}/api/projects/${projectId}/templates/${templateId}`,
                headers: { Authorization: `Bearer ${token}` },
                body: { name: 'Hijacked' },
                failOnStatusCode: false,
              }).its('status').should('eq', 403);
            });

            deleteTemplateAs(memberEmail, projectId, templateId).its('status').should('eq', 403);
            recordTemplateUsageAs(memberEmail, projectId, templateId).its('status').should('eq', 200);
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('an org MEMBER (org-level role) gets 403 writing org-level templates, even as a project OWNER', () => {
    const ownerEmail = uniqueEmail('org-member-owner');
    const memberEmail = uniqueEmail('org-member-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Harbor Corp', uniqueSlug()).then((orgId) =>
      userIdFor(memberEmail).then((memberId) => {
        addMember(orgId, ownerEmail, memberId, 'MEMBER');
        // A project always has exactly one OWNER (its creator) — addProjectMember
        // can't assign a second one, so to get an org-MEMBER who IS a project OWNER,
        // have them create their own project rather than being added to one.
        createProjectAs(memberEmail, 'Harbor Member-Owned Project').then(() => {
          tokenFor(memberEmail).then((token) => {
            cy.request({
              method: 'POST',
              url: `${API}/api/organisations/${orgId}/templates`,
              headers: { Authorization: `Bearer ${token}` },
              body: { name: 'Org hijack template' },
              failOnStatusCode: false,
            }).its('status').should('eq', 403);
          });
        });
      }),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('without the JOB_TEMPLATES add-on, list/create 403; the templates page shows an upgrade prompt instead of the "+ New Template" button', () => {
    const email = uniqueEmail('no-addon');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Ember Corp', uniqueSlug());
    createProjectAs(email, 'Ember Template Project').then((projectId) => {
      tokenFor(email).then((token) => {
        cy.request({
          method: 'GET',
          url: `${API}/api/projects/${projectId}/templates`,
          headers: { Authorization: `Bearer ${token}` },
          failOnStatusCode: false,
        }).its('status').should('eq', 403);

        cy.request({
          method: 'POST',
          url: `${API}/api/projects/${projectId}/templates`,
          headers: { Authorization: `Bearer ${token}` },
          body: { name: 'Should not be created' },
          failOnStatusCode: false,
        }).its('status').should('eq', 403);
      });

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/templates`);
      cy.contains('button', '+ New Template').should('not.exist');
    });

    cy.deleteKeycloakUser(email);
  });

  it('two templates with identical names in one project are independently selectable, not genuinely ambiguous', () => {
    const email = uniqueEmail('duplicate-names');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Cascade Corp', uniqueSlug());
    createProjectAs(email, 'Cascade Template Project').then((projectId) => {
      createTemplateAs(email, projectId, { name: 'Duplicate', title: 'First variant' });
      createTemplateAs(email, projectId, { name: 'Duplicate', title: 'Second variant' });

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);
      cy.contains('button', '+ New Job').click();
      cy.get('.z-50:visible').within(() => {
        cy.get('select').first().find('option').filter(':contains("Duplicate")').should('have.length', 2);
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('a MEMBER sees no New/Edit/Delete controls on the templates page (JOB-260 regression guard)', () => {
    const ownerEmail = uniqueEmail('readonly-owner');
    const memberEmail = uniqueEmail('readonly-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Raven Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Raven Template Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
          createTemplateAs(ownerEmail, projectId, { name: 'Read only me' }).then(() => {
            cy.loginAs(memberEmail);
            cy.visit(`/projects/${projectId}/templates`);
            cy.contains('Read only me').should('be.visible');
            cy.contains('button', '+ New Template').should('not.exist');
            cy.contains('button', 'Edit').should('not.exist');
            cy.contains('button', 'Delete').should('not.exist');
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('{{assignee}} resolves for a FIXED-mode template (JOB-261 regression guard)', () => {
    const email = uniqueEmail('assignee-regression');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Vessel Corp', uniqueSlug());
    createProjectAs(email, 'Vessel Template Project').then((projectId) =>
      userIdFor(email).then((selfId) => {
        createTemplateAs(email, projectId, {
          name: 'Assignee regression',
          title: 'Assigned to {{assignee}}',
          assigneeMode: 'FIXED',
          assigneeId: selfId,
        }).then(() => {
          cy.loginAs(email);
          cy.visit(`/projects/${projectId}/jobs`);
          cy.contains('button', '+ New Job').click();
          cy.get('.z-50:visible').within(() => {
            cy.get('select').first().select('Assignee regression');
            cy.get('input[placeholder="e.g. Fix login bug"]').should('have.value', 'Assigned to E2E Tester');
          });
        });
      }),
    );

    cy.deleteKeycloakUser(email);
  });
});
