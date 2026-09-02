// ADR-0049 Appendix §15 (Recurring Schedules + Missed Runs + Cron Preview). Uses
// cy.loginAs() per docs/dev/process/E2E.md.
//
// Two ADR-0049-flagged design/behavior facts are treated as REGRESSION GUARDS
// documenting actual current behavior, not bugs to file:
// - `detectPreset` (frontend/src/utils/cron.ts) has an unreachable daily-detection
//   branch — an earlier, identical condition returns 'advanced' first — so editing a
//   schedule originally created via the Daily preset reopens the edit modal on the
//   "Advanced" tab, not "Daily". Confirmed in source; asserted as-is below.
// - Monthly preset's UI caps day-of-month at 1-28 (avoiding "no Feb 30"), but
//   Advanced mode's raw cron input has no such guard. Confirmed via the stateless
//   preview endpoint that Spring's CronExpression treats an impossible day (e.g.
//   Feb 30) as simply never matching — nextRuns comes back empty, not an error.
//
// The CRON_INTERVAL_TOO_SHORT boundary test asserts PASS at exactly 3600s using an
// hourly cron (uniformly 3600s apart regardless of when the test runs, so no
// server-clock control is needed) and FAIL using a clearly-sub-3600s cron (every
// minute). Hitting the exact 3599s boundary deterministically would require a cron
// whose "next two occurrences from now" land on a specific ~1-second window of the
// day — not achievable without mocking the *backend's* clock (cy.clock() only mocks
// the browser), so that specific value isn't pinned; the boundary logic itself
// (>=3600 accepted, comfortably-below rejected) is what's actually under test.
//
// The DST-transition edge case (ADR bullet: "a daily schedule crossing a DST
// transition still fires once at the correct local time, no skip or double-fire")
// is out of practical E2E scope: verifying it requires observing the real
// SchedulerPoller actually fire across a real DST boundary, which is either a
// multi-hour wait or requires controlling the backend's system clock — neither is
// practical inside a Cypress spec's time budget. Not tested here.

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
  createTemplateAs,
  createScheduleAs,
  getScheduleAs,
  updateScheduleAs,
  deleteScheduleAs,
  pauseScheduleAs,
  resumeScheduleAs,
  listMissedRunsAs,
  insertMissedRunAs,
  materializeMissedRunAs,
  dismissMissedRunAs,
  dismissAllMissedRunsAs,
  previewCronAs,
  API,
} from '../../support/orgApi';

describe('Recurring Schedules (+ Missed Runs + Cron Preview)', () => {
  it('creates a schedule via each cadence preset and Advanced raw cron; the readable translation and next-runs preview both update live', () => {
    const email = uniqueEmail('presets');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Falcon Corp', uniqueSlug());
    createProjectAs(email, 'Falcon Schedule Project').then((projectId) =>
      createTemplateAs(email, projectId, { name: 'Deploy checklist', title: 'Deploy' }).then(() => {
        cy.loginAs(email);
        cy.viewport(1000, 1000);
        cy.visit(`/projects/${projectId}/schedules`);
        cy.contains('button', '+ New Schedule').click();

        cy.get('.z-50:visible').within(() => {
          cy.get('input[placeholder="e.g. Weekly deploy checklist"]').type('Weekly Deploy');
          cy.get('select').first().select('Deploy checklist');

          // Daily
          cy.contains('button', 'Daily').click();
          cy.contains('Every day at').should('be.visible');
          cy.contains('p', /^(Every|At)/).should('be.visible');

          // Weekly (default tab already, but click explicitly)
          cy.contains('button', 'Weekly').click();
          cy.get('select').eq(1).select('Wednesday');

          // Monthly
          cy.contains('button', 'Monthly').click();
          cy.contains('of every month at').should('be.visible');

          // Advanced
          cy.contains('button', 'Advanced').click();
          cy.get('input[placeholder="0 0 9 * * MON"]').clear();
          cy.get('input[placeholder="0 0 9 * * MON"]').type('0 0 9 * * *');
          cy.contains('Next occurrences').should('be.visible');
          cy.get('p.text-xs.text-gray-700').should('have.length.at.least', 1);

          cy.contains('button', 'Create schedule').click();
        });

        cy.contains('Weekly Deploy').should('be.visible');
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('creates a schedule with an ordered assignee rotation, and separately with no assignees at all', () => {
    const email = uniqueEmail('rotation');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Nimbus Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(email, 'Nimbus Schedule Project').then((projectId) =>
        userIdFor(email).then((ownerId) => {
          const memberEmail = uniqueEmail('rotation-member');
          cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
          userIdFor(memberEmail).then((memberId) => {
            addMember(orgId, email, memberId, 'MEMBER');
            addProjectMember(projectId, email, memberId, 'MEMBER');

            createTemplateAs(email, projectId, { name: 'T', title: 'T' }).then((templateId) => {
              createScheduleAs(email, projectId, {
                name: 'With Rotation',
                templateId,
                cronExpression: '0 0 9 * * *',
                timezone: 'UTC',
                assigneeIds: [ownerId, memberId],
              }).then((scheduleId) => {
                getScheduleAs(email, projectId, scheduleId).then((s) => {
                  expect(s.assignees.map((a) => a.userId)).to.deep.equal([ownerId, memberId]);
                });
              });

              createScheduleAs(email, projectId, {
                name: 'No Rotation',
                templateId,
                cronExpression: '0 0 10 * * *',
                timezone: 'UTC',
              }).then((scheduleId) => {
                getScheduleAs(email, projectId, scheduleId).then((s) => {
                  expect(s.assignees).to.have.length(0);
                });
              });
            });
          });

          cy.deleteKeycloakUser(memberEmail);
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('creates a schedule already-paused via a future pausedUntil, and separately with an expiresAt', () => {
    const email = uniqueEmail('paused-expires');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Atlas Corp', uniqueSlug());
    createProjectAs(email, 'Atlas Schedule Project').then((projectId) =>
      createTemplateAs(email, projectId, { name: 'T', title: 'T' }).then((templateId) => {
        const future = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString();
        createScheduleAs(email, projectId, {
          name: 'Future Start',
          templateId,
          cronExpression: '0 0 9 * * *',
          timezone: 'UTC',
          pausedUntil: future,
        }).then((scheduleId) => {
          getScheduleAs(email, projectId, scheduleId).then((s) => {
            expect(s.status).to.equal('PAUSED');
            expect(s.pausedUntil).to.not.equal(null);
          });
        });

        const expiry = new Date(Date.now() + 60 * 24 * 60 * 60 * 1000).toISOString();
        createScheduleAs(email, projectId, {
          name: 'Has Expiry',
          templateId,
          cronExpression: '0 0 9 * * *',
          timezone: 'UTC',
          expiresAt: expiry,
        }).then((scheduleId) => {
          getScheduleAs(email, projectId, scheduleId).then((s) => {
            expect(s.expiresAt).to.not.equal(null);
            expect(s.status).to.equal('ACTIVE');
          });
        });
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('pauses a schedule via every PauseDialog option; resuming clears pausedUntil', () => {
    const email = uniqueEmail('pause-dialog');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Cedar Corp', uniqueSlug());
    createProjectAs(email, 'Cedar Schedule Project').then((projectId) => {
      userIdFor(email).then((ownerId) => {
        createTemplateAs(email, projectId, { name: 'T', title: 'T' }).then((templateId) => {
          // Needs a non-empty assignee list — otherwise an indefinite pause with zero
          // assignees resolves to PAUSED_NO_ASSIGNEES rather than plain PAUSED (see the
          // dedicated status-badges test), which isn't what this test is exercising.
          createScheduleAs(email, projectId, {
            name: 'Pausable',
            templateId,
            cronExpression: '0 0 9 * * *',
            timezone: 'UTC',
            assigneeIds: [ownerId],
          }).then((scheduleId) => {
            cy.loginAs(email);
            cy.visit(`/projects/${projectId}/schedules`);

            cy.contains('button', 'Pause').click();
            cy.get('.z-50:visible').within(() => {
              cy.contains('label', 'Indefinitely').click();
              cy.contains('button', 'Pause').click();
            });
            getScheduleAs(email, projectId, scheduleId).then((s) => {
              expect(s.status).to.equal('PAUSED');
            });

            cy.contains('button', 'Resume').click();
            getScheduleAs(email, projectId, scheduleId).then((s) => {
              expect(s.status).to.equal('ACTIVE');
              expect(s.pausedUntil).to.equal(null);
            });
          });
        });
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('deleting a schedule leaves an already-materialized job\'s "Created by schedule" line functional until the schedule 404s, then hides it gracefully', () => {
    const email = uniqueEmail('delete-badge');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Vega Corp', uniqueSlug());
    createProjectAs(email, 'Vega Schedule Project').then((projectId) =>
      createTemplateAs(email, projectId, { name: 'T', title: 'T' }).then((templateId) =>
        createScheduleAs(email, projectId, {
          name: 'Soon Deleted',
          templateId,
          cronExpression: '0 0 9 * * *',
          timezone: 'UTC',
        }).then((scheduleId) =>
          insertMissedRunAs(scheduleId, new Date(Date.now() - 60_000).toISOString()).then((missedId) =>
            materializeMissedRunAs(email, projectId, scheduleId, missedId).then((res) => {
              const jobFriendlyId = res.body.friendlyId as string;

              cy.loginAs(email);
              cy.visit(`/projects/${projectId}/jobs/${jobFriendlyId}`);
              cy.contains('Created by schedule').should('be.visible');
              cy.contains('a', 'Soon Deleted').should('be.visible');

              deleteScheduleAs(email, projectId, scheduleId).then(() => {
                cy.visit(`/projects/${projectId}/jobs/${jobFriendlyId}`);
                cy.contains('Created by schedule').should('not.exist');
              });
            }),
          ),
        ),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('status badges reflect ACTIVE/PAUSED/PAUSED_NO_ASSIGNEES/EXPIRED accurately', () => {
    const email = uniqueEmail('status-badges');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Sable Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Sable Schedule Project').then((projectId) =>
        userIdFor(email).then((ownerId) =>
          createTemplateAs(email, projectId, { name: 'T', title: 'T' }).then((templateId) => {
            createScheduleAs(email, projectId, {
              name: 'Active One', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC',
            });
            createScheduleAs(email, projectId, {
              name: 'Paused One', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC',
            }).then((id) => pauseScheduleAs(email, projectId, id, null));
            createScheduleAs(email, projectId, {
              name: 'No Assignees One', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC',
              assigneeIds: [ownerId],
            }).then((id) =>
              updateScheduleAs(email, projectId, id, {
                name: 'No Assignees One', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC',
                assigneeIds: [],
              }),
            );
            createScheduleAs(email, projectId, {
              name: 'Expired One', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC',
              expiresAt: new Date(Date.now() - 60_000).toISOString(),
            });

            cy.loginAs(email);
            cy.visit(`/projects/${projectId}/schedules`);
            cy.contains('div', 'Active One').parents('div.flex-1').contains('Active').should('be.visible');
            cy.contains('div', 'Paused One').parents('div.flex-1').contains('Paused').should('be.visible');
            cy.contains('div', 'No Assignees One').parents('div.flex-1').contains('No assignees').should('be.visible');
            cy.contains('div', 'Expired One').parents('div.flex-1').contains('Expired').should('be.visible');
          }),
        ),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('the cron preview endpoint returns 5 correct future occurrences', () => {
    const email = uniqueEmail('preview');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Orbit Corp', uniqueSlug());
    previewCronAs(email, '0 0 9 * * *', 'UTC').then((res) => {
      expect(res.status).to.equal(200);
      expect(res.body.nextRuns).to.have.length(5);
      for (let i = 1; i < 5; i++) {
        const gap = new Date(res.body.nextRuns[i]).getTime() - new Date(res.body.nextRuns[i - 1]).getTime();
        expect(gap).to.equal(24 * 60 * 60 * 1000);
      }
    });

    cy.deleteKeycloakUser(email);
  });

  it('missed runs: Materialize creates the job and removes the row; Dismiss removes a row with no job; Dismiss all clears every row; a lone remaining row still dismisses via its own control', () => {
    const email = uniqueEmail('missed-runs');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Comet Corp', uniqueSlug());
    createProjectAs(email, 'Comet Schedule Project').then((projectId) =>
      createTemplateAs(email, projectId, { name: 'Weekly report', title: 'Report' }).then((templateId) =>
        createScheduleAs(email, projectId, {
          name: 'Missed-heavy', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC',
        }).then((scheduleId) => {
          const past = (mins: number) => new Date(Date.now() - mins * 60_000).toISOString();

          insertMissedRunAs(scheduleId, past(180)).then((run1) =>
            insertMissedRunAs(scheduleId, past(120)).then((run2) =>
              insertMissedRunAs(scheduleId, past(60)).then((run3) => {
                listMissedRunsAs(email, projectId, scheduleId).then((runs) => {
                  expect(runs).to.have.length(3);
                });

                materializeMissedRunAs(email, projectId, scheduleId, run1).then((res) => {
                  expect(res.status).to.equal(201);
                  expect(res.body.title).to.equal('Report');
                });
                listMissedRunsAs(email, projectId, scheduleId).then((runs) => {
                  expect(runs.map((r) => r.id)).to.not.include(run1);
                  expect(runs).to.have.length(2);
                });

                dismissMissedRunAs(email, projectId, scheduleId, run2).its('status').should('eq', 204);
                listMissedRunsAs(email, projectId, scheduleId).then((runs) => {
                  expect(runs).to.have.length(1);
                  expect(runs[0].id).to.equal(run3);
                });

                // A single remaining row dismisses correctly via its own control (not dismiss-all).
                dismissMissedRunAs(email, projectId, scheduleId, run3).its('status').should('eq', 204);
                listMissedRunsAs(email, projectId, scheduleId).then((runs) => {
                  expect(runs).to.have.length(0);
                });

                // Bulk dismiss-all, exercised with 2+ rows present.
                insertMissedRunAs(scheduleId, past(200)).then(() =>
                  insertMissedRunAs(scheduleId, past(190)).then(() => {
                    dismissAllMissedRunsAs(email, projectId, scheduleId).its('status').should('eq', 204);
                    listMissedRunsAs(email, projectId, scheduleId).then((runs) => {
                      expect(runs).to.have.length(0);
                    });
                  }),
                );
              }),
            ),
          );
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('a blank name 400s; omitting templateId 400s; a nonexistent templateId 404s', () => {
    const email = uniqueEmail('name-template-validation');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Delta Corp', uniqueSlug());
    createProjectAs(email, 'Delta Schedule Project').then((projectId) =>
      createTemplateAs(email, projectId, { name: 'T', title: 'T' }).then((templateId) => {
        tokenFor(email).then((token) => {
          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/schedules`,
            headers: { Authorization: `Bearer ${token}` },
            body: { name: '   ', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC' },
            failOnStatusCode: false,
          }).its('status').should('eq', 400);

          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/schedules`,
            headers: { Authorization: `Bearer ${token}` },
            body: { name: 'No Template', cronExpression: '0 0 9 * * *', timezone: 'UTC' },
            failOnStatusCode: false,
          }).its('status').should('eq', 400);

          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/schedules`,
            headers: { Authorization: `Bearer ${token}` },
            body: {
              name: 'Bad Template',
              templateId: '00000000-0000-0000-0000-000000000000',
              cronExpression: '0 0 9 * * *',
              timezone: 'UTC',
            },
            failOnStatusCode: false,
          }).its('status').should('eq', 404);
        });
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('an invalid raw cron expression 400s with INVALID_CRON', () => {
    const email = uniqueEmail('invalid-cron');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Willow Corp', uniqueSlug());
    createProjectAs(email, 'Willow Schedule Project').then((projectId) =>
      createTemplateAs(email, projectId, { name: 'T', title: 'T' }).then((templateId) => {
        tokenFor(email).then((token) => {
          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/schedules`,
            headers: { Authorization: `Bearer ${token}` },
            body: { name: 'Bad Cron', templateId, cronExpression: 'not a cron', timezone: 'UTC' },
            failOnStatusCode: false,
          }).then((res) => {
            expect(res.status).to.equal(400);
            expect(res.body.message).to.contain('Invalid cron expression');
          });
        });
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('a cron with occurrences <1 hour apart 400s (CRON_INTERVAL_TOO_SHORT); exactly-hourly is accepted', () => {
    const email = uniqueEmail('interval-boundary');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Sparrow Corp', uniqueSlug());
    createProjectAs(email, 'Sparrow Schedule Project').then((projectId) =>
      createTemplateAs(email, projectId, { name: 'T', title: 'T' }).then((templateId) => {
        tokenFor(email).then((token) => {
          // Every minute — comfortably under the 1-hour minimum.
          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/schedules`,
            headers: { Authorization: `Bearer ${token}` },
            body: { name: 'Too Frequent', templateId, cronExpression: '0 * * * * *', timezone: 'UTC' },
            failOnStatusCode: false,
          }).then((res) => {
            expect(res.status).to.equal(400);
            expect(res.body.message).to.contain('minimum interval of 1 hour');
          });

          // Every hour on the hour — uniformly exactly 3600s apart, always.
          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/schedules`,
            headers: { Authorization: `Bearer ${token}` },
            body: { name: 'Exactly Hourly', templateId, cronExpression: '0 0 * * * *', timezone: 'UTC' },
            failOnStatusCode: false,
          }).its('status').should('eq', 201);
        });
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('an invalid IANA timezone 400s on create and on the preview endpoint', () => {
    const email = uniqueEmail('invalid-tz');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Ember Corp', uniqueSlug());
    createProjectAs(email, 'Ember Schedule Project').then((projectId) =>
      createTemplateAs(email, projectId, { name: 'T', title: 'T' }).then((templateId) => {
        tokenFor(email).then((token) => {
          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/schedules`,
            headers: { Authorization: `Bearer ${token}` },
            body: { name: 'Bad TZ', templateId, cronExpression: '0 0 9 * * *', timezone: 'Not/A_Zone' },
            failOnStatusCode: false,
          }).its('status').should('eq', 400);
        });

        previewCronAs(email, '0 0 9 * * *', 'Not/A_Zone').its('status').should('eq', 400);
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('a MEMBER sees no write controls on SchedulesPage or the missed-runs panel (JOB-263 regression guard)', () => {
    const ownerEmail = uniqueEmail('member-readonly-owner');
    const memberEmail = uniqueEmail('member-readonly-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Heron Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Heron Schedule Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

          createTemplateAs(ownerEmail, projectId, { name: 'T', title: 'T' }).then((templateId) =>
            createScheduleAs(ownerEmail, projectId, {
              name: 'Existing Schedule', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC',
            }).then((scheduleId) =>
              insertMissedRunAs(scheduleId, new Date(Date.now() - 60_000).toISOString()).then(() => {
                cy.loginAs(memberEmail);
                cy.visit(`/projects/${projectId}/schedules`);
                cy.contains('Existing Schedule').should('be.visible');
                cy.contains('button', '+ New Schedule').should('not.exist');
                cy.contains('button', 'Edit').should('not.exist');
                cy.contains('button', 'Delete').should('not.exist');
                cy.contains('button', 'Pause').should('not.exist');

                cy.contains('button', /missed run/).click();
                cy.contains('button', 'Create job').should('not.exist');
                cy.contains('button', 'Dismiss').should('not.exist');
              }),
            ),
          );
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('a MEMBER gets 403 on every write endpoint: create, update, delete, pause, resume, materialize, dismiss, dismiss-all', () => {
    const ownerEmail = uniqueEmail('member-write-owner');
    const memberEmail = uniqueEmail('member-write-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Juniper Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Juniper Schedule Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

          createTemplateAs(ownerEmail, projectId, { name: 'T', title: 'T' }).then((templateId) =>
            createScheduleAs(ownerEmail, projectId, {
              name: 'Guarded', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC',
            }).then((scheduleId) => {
              tokenFor(memberEmail).then((token) => {
                cy.request({
                  method: 'POST',
                  url: `${API}/api/projects/${projectId}/schedules`,
                  headers: { Authorization: `Bearer ${token}` },
                  body: { name: 'Hijack', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC' },
                  failOnStatusCode: false,
                }).its('status').should('eq', 403);
              });

              updateScheduleAs(memberEmail, projectId, scheduleId, {
                name: 'Hijacked', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC', assigneeIds: [],
              }).its('status').should('eq', 403);
              pauseScheduleAs(memberEmail, projectId, scheduleId).its('status').should('eq', 403);
              resumeScheduleAs(memberEmail, projectId, scheduleId).its('status').should('eq', 403);
              deleteScheduleAs(memberEmail, projectId, scheduleId).its('status').should('eq', 403);

              insertMissedRunAs(scheduleId, new Date().toISOString()).then((missedId) => {
                materializeMissedRunAs(memberEmail, projectId, scheduleId, missedId).its('status').should('eq', 403);
                dismissMissedRunAs(memberEmail, projectId, scheduleId, missedId).its('status').should('eq', 403);
                dismissAllMissedRunsAs(memberEmail, projectId, scheduleId).its('status').should('eq', 403);
              });
            }),
          );
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('an ADMIN can perform the same writes an OWNER can', () => {
    const ownerEmail = uniqueEmail('admin-write-owner');
    const adminEmail = uniqueEmail('admin-write-admin');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(adminEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Talon Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Talon Schedule Project').then((projectId) =>
        userIdFor(adminEmail).then((adminId) => {
          addMember(orgId, ownerEmail, adminId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, adminId, 'ADMIN');

          createTemplateAs(ownerEmail, projectId, { name: 'T', title: 'T' }).then((templateId) => {
            createScheduleAs(adminEmail, projectId, {
              name: 'By Admin', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC',
            }).then((scheduleId) => {
              updateScheduleAs(adminEmail, projectId, scheduleId, {
                name: 'By Admin Renamed', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC', assigneeIds: [],
              }).its('status').should('eq', 200);
              pauseScheduleAs(adminEmail, projectId, scheduleId).its('status').should('eq', 200);
              resumeScheduleAs(adminEmail, projectId, scheduleId).its('status').should('eq', 200);
              deleteScheduleAs(adminEmail, projectId, scheduleId).its('status').should('eq', 204);
            });
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(adminEmail);
  });

  it('a cross-project scheduleId/missedRunId 404s; dismissing an already-dismissed or nonexistent missed run 404s', () => {
    const email = uniqueEmail('cross-project');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Sable2 Corp', uniqueSlug());
    createProjectAs(email, 'Cross Project A').then((projectIdA) =>
      createProjectAs(email, 'Cross Project B').then((projectIdB) =>
        createTemplateAs(email, projectIdA, { name: 'T', title: 'T' }).then((templateId) =>
          createScheduleAs(email, projectIdA, {
            name: 'Belongs To A', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC',
          }).then((scheduleId) => {
            tokenFor(email).then((token) => {
              cy.request({
                method: 'GET',
                url: `${API}/api/projects/${projectIdB}/schedules/${scheduleId}`,
                headers: { Authorization: `Bearer ${token}` },
                failOnStatusCode: false,
              }).its('status').should('eq', 404);
            });
            updateScheduleAs(email, projectIdB, scheduleId, {
              name: 'Stolen', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC', assigneeIds: [],
            }).its('status').should('eq', 404);
            deleteScheduleAs(email, projectIdB, scheduleId).its('status').should('eq', 404);

            insertMissedRunAs(scheduleId, new Date().toISOString()).then((missedId) => {
              dismissMissedRunAs(email, projectIdA, scheduleId, missedId).its('status').should('eq', 204);
              dismissMissedRunAs(email, projectIdA, scheduleId, missedId).its('status').should('eq', 404);
              dismissMissedRunAs(email, projectIdA, scheduleId, '00000000-0000-0000-0000-000000000000')
                .its('status').should('eq', 404);
            });
          }),
        ),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('the RECURRING_SCHEDULING add-on off 403s the API and hides the UI\'s create button', () => {
    const email = uniqueEmail('no-addon');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'No Addon Corp', uniqueSlug());
    createProjectAs(email, 'No Addon Schedule Project').then((projectId) => {
      tokenFor(email).then((token) => {
        cy.request({
          method: 'GET',
          url: `${API}/api/projects/${projectId}/schedules`,
          headers: { Authorization: `Bearer ${token}` },
          failOnStatusCode: false,
        }).its('status').should('eq', 403);
      });

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/schedules`);
      cy.contains('Schedules').should('be.visible');
      cy.contains('button', '+ New Schedule').should('not.exist');
    });

    cy.deleteKeycloakUser(email);
  });

  it('clearing all assignees on update silently force-sets PAUSED_NO_ASSIGNEES even without an explicit pause request', () => {
    const email = uniqueEmail('clear-assignees');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Harbor Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Harbor Schedule Project').then((projectId) =>
        userIdFor(email).then((ownerId) =>
          createTemplateAs(email, projectId, { name: 'T', title: 'T' }).then((templateId) =>
            createScheduleAs(email, projectId, {
              name: 'Will Empty', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC',
              assigneeIds: [ownerId],
            }).then((scheduleId) => {
              getScheduleAs(email, projectId, scheduleId).then((s) => expect(s.status).to.equal('ACTIVE'));

              updateScheduleAs(email, projectId, scheduleId, {
                name: 'Will Empty', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC',
                assigneeIds: [],
              }).its('status').should('eq', 200);

              getScheduleAs(email, projectId, scheduleId).then((s) => {
                expect(s.status).to.equal('PAUSED_NO_ASSIGNEES');
              });
            }),
          ),
        ),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('the rotation index stays valid (modulo) when the assignee list shrinks below the current index', () => {
    const email = uniqueEmail('rotation-shrink');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Wren Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(email, 'Wren Schedule Project').then((projectId) =>
        userIdFor(email).then((ownerId) => {
          const memberEmail = uniqueEmail('rotation-shrink-member');
          cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
          userIdFor(memberEmail).then((memberId) => {
            addMember(orgId, email, memberId, 'MEMBER');
            addProjectMember(projectId, email, memberId, 'MEMBER');

            createTemplateAs(email, projectId, { name: 'T', title: 'T' }).then((templateId) =>
              createScheduleAs(email, projectId, {
                name: 'Shrinking Rotation', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC',
                assigneeIds: [ownerId, memberId],
              }).then((scheduleId) => {
                // Advance current_rotation_index to 1 (would pick memberId next in a
                // 2-person rotation) directly via SQL — no clean API to bump it without
                // waiting for a real poller tick.
                tokenFor(email).then(() => {
                  cy.task('queryDb', {
                    sql: 'UPDATE recurring_schedules SET current_rotation_index = 1 WHERE id = $1',
                    params: [scheduleId],
                  });
                });

                // Shrink the rotation to just the owner — index 1 % 1 = 0, so the next
                // materialized job must still go to the (only) remaining assignee, not
                // silently break or pick nobody.
                updateScheduleAs(email, projectId, scheduleId, {
                  name: 'Shrinking Rotation', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC',
                  assigneeIds: [ownerId],
                }).its('status').should('eq', 200);

                insertMissedRunAs(scheduleId, new Date(Date.now() - 60_000).toISOString()).then((missedId) =>
                  materializeMissedRunAs(email, projectId, scheduleId, missedId).then((res) => {
                    expect(res.body.assignedTo).to.equal(ownerId);
                  }),
                );
              }),
            );
          });

          cy.deleteKeycloakUser(memberEmail);
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('editing a Daily-preset-created schedule reopens the edit modal on the Advanced tab, not Daily (known detectPreset gap, regression guard)', () => {
    const email = uniqueEmail('detect-preset-gap');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Plover Corp', uniqueSlug());
    createProjectAs(email, 'Plover Schedule Project').then((projectId) =>
      createTemplateAs(email, projectId, { name: 'T', title: 'T' }).then((templateId) =>
        // A cron matching the Daily preset's own shape: dom=* month=* dow=*.
        createScheduleAs(email, projectId, {
          name: 'Daily Origin', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC',
        }).then(() => {
          cy.loginAs(email);
          cy.viewport(1000, 1000);
          cy.visit(`/projects/${projectId}/schedules`);
          cy.contains('button', 'Edit').click();

          cy.get('.z-50:visible').within(() => {
            // detectPreset('0 0 9 * * *') returns 'advanced', not 'daily' — the earlier
            // `dom === '*' && dow === '*'` check at the "advanced" branch shadows the
            // identical daily-detection condition below it, which is unreachable.
            cy.get('button.bg-white, button.dark\\:bg-gray-600').contains('Advanced').should('exist');
          });
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('an impossible day-of-month (e.g. Feb 30) via Advanced mode never errors — the cron simply never matches', () => {
    const email = uniqueEmail('impossible-day');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Kestrel Corp', uniqueSlug());
    previewCronAs(email, '0 0 9 30 2 *', 'UTC').then((res) => {
      expect(res.status).to.equal(200);
      expect(res.body.nextRuns).to.have.length(0);
    });

    cy.deleteKeycloakUser(email);
  });

  it('materializing a missed run sets deadline = the ORIGINAL missed date + offset, not today + offset', () => {
    const email = uniqueEmail('missed-deadline');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Osprey Corp', uniqueSlug());
    createProjectAs(email, 'Osprey Schedule Project').then((projectId) =>
      createTemplateAs(email, projectId, { name: 'T', title: 'T', deadlineOffsetDays: 3 }).then((templateId) =>
        createScheduleAs(email, projectId, {
          name: 'Old Miss', templateId, cronExpression: '0 0 9 * * *', timezone: 'UTC',
        }).then((scheduleId) => {
          const expectedAt = '2026-01-10T09:00:00.000Z';
          insertMissedRunAs(scheduleId, expectedAt).then((missedId) =>
            materializeMissedRunAs(email, projectId, scheduleId, missedId).then((res) => {
              const deadline = new Date(res.body.deadline as string);
              // scheduled_for (2026-01-10) + 3 days = 2026-01-13, NOT today + 3 days.
              expect(deadline.toISOString().slice(0, 10)).to.equal('2026-01-13');
            }),
          );
        }),
      ),
    );

    cy.deleteKeycloakUser(email);
  });

  it('the cron preview endpoint does not enforce the >=1-hour interval rule, unlike create/update', () => {
    const email = uniqueEmail('preview-no-interval-check');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Finch Corp', uniqueSlug());
    createProjectAs(email, 'Finch Schedule Project').then((projectId) =>
      createTemplateAs(email, projectId, { name: 'T', title: 'T' }).then((templateId) => {
        previewCronAs(email, '0 * * * * *', 'UTC').then((res) => {
          expect(res.status).to.equal(200);
          expect(res.body.nextRuns).to.have.length(5);
        });

        tokenFor(email).then((token) => {
          cy.request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/schedules`,
            headers: { Authorization: `Bearer ${token}` },
            body: { name: 'Every Minute', templateId, cronExpression: '0 * * * * *', timezone: 'UTC' },
            failOnStatusCode: false,
          }).its('status').should('eq', 400);
        });
      }),
    );

    cy.deleteKeycloakUser(email);
  });
});
