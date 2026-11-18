// ADR-0049 §6 / Appendix §26 — Golden-Path Simulation: Full Project Lifecycle. Not a
// feature-area catalog like every other spec in this suite: one continuous 13-step
// narrative across five actors, run as a single test with no state reset in between.
// The point is the sequence itself — does the Dashboard reflect a live sequence of
// cross-user mutations correctly, does state survive several actors handing a job
// back and forth — bugs that are structurally invisible to per-area specs no matter
// how exhaustive they are.
//
// Identity pool: a dedicated set of `uniqueEmail('golden-*')` users, not the standard
// seeded demo users (testuser/alice/bob/carol/dave@example.com) — those already belong
// to existing demo orgs/projects and are reused across this whole E2E suite, so
// reusing them here would either skip org creation or corrupt their state for every
// other spec depending on them (§6). Confirmed `scripts/seed.sh` has no golden-path-
// specific fixture provisioning of its own — this per-test identity/DB-seed approach
// is the only existing pattern for this, not a shortcut around a missing one.
//
// Subscription: Alice's org gets a realistic-shaped ACTIVE `org_subscriptions` row
// (`makeOrgRealisticSubscription`, orgApi.ts) immediately after UI-driven org
// creation — never `is_internal: true`, which would skip `hasAddon()`/
// `hasRealBilling()` entirely, a different code path than a real paying customer
// hits (§6's "Alternatives considered": both a live Paddle sandbox checkout and the
// `is_internal` bypass were rejected for this scenario). No live Paddle call anywhere
// in this spec.
//
// UI vs. API: the narrative beats the ADR calls out by name — org creation, invite
// acceptance, status transitions, notes, approvals, dashboard checks — are driven
// through the real UI. Pure setup scaffolding with no narrative value (adding org/
// project members, job types, milestones, the template, two of the three jobs) is
// API-driven via orgApi.ts helpers, the same way every other spec in this suite
// separates fixture setup from the behavior under test.
//
// JOB-267: while building this spec, driving Carol's "attach a link" beat through the
// UI surfaced a real bug — an assigned MEMBER couldn't add the FIRST link to a job
// (the empty-state "+ Add link" CTA was gated to OWNER/ADMIN, contradicting the
// backend's actual any-member-can-add permission, ADR-0035). Filed, fixed, and given
// a regression test on a separate branch/PR (not merged here). This spec routes
// around the not-yet-merged fix by having Bob (Admin) seed one link on Carol's job
// first, so Carol's own turn always lands on the unconditional "+ Add link" button
// rather than the empty-state one — narratively unchanged (a link ends up attached
// during Carol's turn, via the UI, with service-icon auto-detection exercised), and
// correct on both patched and unpatched `main`.

import {
  API,
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  userIdFor,
  makeOrgRealisticSubscription,
  createProjectAs,
  addProjectMember,
  createJobTypeAs,
  createMilestoneAs,
  createTemplateAs,
  createJobAs,
  createJobLinkAs,
  addMember,
  inviteTokenFor,
  listApprovalsByJobAs,
} from '../../support/orgApi';

describe('Golden-Path Simulation: Full Project Lifecycle (pre-merge)', () => {
  it(
    'takes Alice, Bob, Carol, Dave, and a fifth member from org creation through project completion',
    { tags: '@smoke' },
    () => {
      const alice = uniqueEmail('golden-alice');
      const bob = uniqueEmail('golden-bob');
      const carol = uniqueEmail('golden-carol');
      const dave = uniqueEmail('golden-dave');
      const erin = uniqueEmail('golden-erin');
      const orgName = 'Golden Path Corp';

      // uniqueSlug()'s keyspace is only ~18k 2-3-letter combos and is never cleaned
      // up (see its own doc comment in orgApi.ts) — a collision on repeated local
      // runs of this spec is a real, observed occurrence, not hypothetical. Retries
      // with a fresh slug on the form's own 409 message, the UI-driven equivalent of
      // createOrgRequest()'s existing retry-on-409 behavior.
      function submitOrgForm(attempt = 1) {
        cy.get('#org-slug').clear();
        cy.get('#org-slug').type(uniqueSlug());
        cy.contains('button', 'Create organisation').click();
        cy.get('body').then(($body) => {
          if (attempt < 5 && $body.text().includes('An organisation with this slug already exists')) {
            submitOrgForm(attempt + 1);
          }
        });
      }

      let orgId = '';
      let bobUserId = '';
      let carolUserId = '';
      let daveUserId = '';
      let erinUserId = '';
      let projectId = '';
      let bugTypeId = '';
      let featureTypeId = '';
      let milestone1Id = '';
      let milestone2Id = '';
      let carolJobId = '';
      let daveJobId = '';
      let erinJobId = '';
      let inviteToken = '';
      let approvalDescription = '';

      cy.createKeycloakUser(alice, 'Alice', 'Anderson');
      cy.createKeycloakUser(bob, 'Bob', 'Baxter');
      cy.createKeycloakUser(carol, 'Carol', 'Chen');
      cy.createKeycloakUser(dave, 'Dave', 'Dawson');
      cy.createKeycloakUser(erin, 'Erin', 'Ellis');

      // ---- Step 1: Alice creates the org via the UI; fixture-seed a realistic ----
      // ---- ACTIVE subscription immediately after — no live Paddle checkout. ----
      cy.loginAs(alice);
      cy.visit('/projects');
      cy.location('pathname', { timeout: 10000 }).should('eq', '/onboarding');
      cy.get('#org-name').type(orgName);
      submitOrgForm();
      cy.location('pathname', { timeout: 10000 }).should('eq', '/org/settings');

      cy.then(() =>
        tokenFor(alice).then((token) =>
          cy.request({
            method: 'GET',
            url: `${API}/api/organisations/mine`,
            headers: { Authorization: `Bearer ${token}` },
          }),
        ),
      ).then((res) => {
        orgId = res.body.id;
      });

      cy.then(() => makeOrgRealisticSubscription(orgId));

      // Fully unlocked org from this point on — the settings page itself only
      // renders past OrgRequiredRoute's SubscriptionWall once a subscription exists.
      cy.visit('/org/settings');
      cy.contains('Organisation Settings').should('be.visible');
      cy.get('input').first().should('have.value', orgName);

      // ---- Step 2: Alice invites Bob by email via the UI; Bob accepts via the UI ----
      cy.visit('/org/invites');
      cy.get('input[type=email]').type(bob);
      cy.contains('button', 'Send invite').click();
      cy.contains('td', bob).should('be.visible');

      cy.then(() =>
        tokenFor(alice).then((token) =>
          cy.request({
            method: 'GET',
            url: `${API}/api/organisations/${orgId}/invites`,
            headers: { Authorization: `Bearer ${token}` },
          }),
        ),
      ).then((res) => {
        const invite = (res.body as Array<{ id: string; email: string }>).find((i) => i.email === bob)!;
        return inviteTokenFor(invite.id);
      }).then((token) => {
        inviteToken = token;
      });

      cy.loginAs(bob);
      cy.then(() => cy.visit(`/invite/${inviteToken}`));
      cy.contains('button', 'Accept invite').click();
      cy.contains("You've joined the organisation", { timeout: 10000 }).should('be.visible');

      // Bob accepted as a MEMBER (invites carry no role) — Alice promotes him to Admin.
      cy.loginAs(alice);
      cy.visit('/org/members');
      cy.contains('tr', bob).find('select').select('Admin');
      cy.contains('tr', bob).find('select').should('have.value', 'ADMIN');

      // ---- Step 3: Carol, Dave, and Erin added to the org as Members (scaffolding) ----
      cy.then(() => userIdFor(bob)).then((id) => { bobUserId = id; });
      cy.then(() => userIdFor(carol)).then((id) => { carolUserId = id; });
      cy.then(() => userIdFor(dave)).then((id) => { daveUserId = id; });
      cy.then(() => userIdFor(erin)).then((id) => { erinUserId = id; });
      cy.then(() => addMember(orgId, alice, carolUserId, 'MEMBER'));
      cy.then(() => addMember(orgId, alice, daveUserId, 'MEMBER'));
      cy.then(() => addMember(orgId, alice, erinUserId, 'MEMBER'));

      // ---- Step 4: Alice creates a project, adds Bob (Admin) + all Members ----
      cy.then(() => createProjectAs(alice, 'Golden Path Project')).then((id) => { projectId = id; });
      cy.then(() => addProjectMember(projectId, alice, bobUserId, 'ADMIN'));
      cy.then(() => addProjectMember(projectId, alice, carolUserId, 'MEMBER'));
      cy.then(() => addProjectMember(projectId, alice, daveUserId, 'MEMBER'));
      cy.then(() => addProjectMember(projectId, alice, erinUserId, 'MEMBER'));

      // ---- Step 5: Bob defines job types and one job template (scaffolding) ----
      cy.then(() => createJobTypeAs(bob, projectId, 'Bug', 'RED')).then((id) => { bugTypeId = id; });
      cy.then(() => createJobTypeAs(bob, projectId, 'Feature', 'BLUE')).then((id) => { featureTypeId = id; });
      cy.then(() =>
        createTemplateAs(bob, projectId, {
          name: 'Standard Task',
          title: 'Task for {{project}}',
          priority: 'MEDIUM',
          assigneeMode: 'ASK',
        }),
      );

      // ---- Step 6: Alice creates two milestones (scaffolding) ----
      cy.then(() => createMilestoneAs(alice, projectId, 'Phase 1')).then((id) => { milestone1Id = id; });
      cy.then(() => createMilestoneAs(alice, projectId, 'Phase 2')).then((id) => { milestone2Id = id; });

      // ---- Step 7: Alice creates three jobs, one per Member ----
      // Carol's job is created from the template via the UI, exercising wildcard
      // resolution for free. Dave's and Erin's are created directly via the API.
      cy.intercept('GET', '**/api/projects/*/templates').as('templatesFetch');
      cy.then(() => cy.visit(`/projects/${projectId}/jobs`));
      // Wait for the project itself (not just the job list) to be cached before
      // opening the modal — NewJobModal's own useProject(projectId) call resolves
      // instantly from the TanStack Query cache once this has loaded, avoiding a
      // race where the {{project}} wildcard resolves before the name has arrived.
      cy.title({ timeout: 10000 }).should('include', 'Golden Path Project');
      // NewJobModal's own useTemplates(projectId) is gated `enabled: hasAddon(...)`,
      // which only flips true once AppLayout's own subscription fetch has resolved
      // and propagated through OrgContext — wait for the resulting templates fetch
      // (with actual data) to land before opening the modal.
      cy.wait('@templatesFetch').its('response.body').should('have.length', 1);
      cy.contains('button', '+ New Job').click();
      cy.get('.z-50:visible').within(() => {
        // With the template + priority + milestone + type sections all present at
        // once, this modal's content is taller than Cypress's default 660px
        // viewport — the outer wrapper is `fixed inset-0` with no internal scroll
        // container, so overflowing fields are genuinely unreachable by scrolling
        // (confirmed via a raw HTML dump: the template `<option>` is correctly
        // present with the right data, just outside the viewport). `force: true`
        // bypasses the actionability visibility check; the interaction itself is
        // otherwise a normal select on a real, correctly-populated element.
        cy.get('select').eq(0).select('Standard Task', { force: true });
        cy.get('input[placeholder="e.g. Fix login bug"]').should('have.value', 'Task for Golden Path Project');
        cy.get('select').eq(1).select('High', { force: true });
        cy.get('select').eq(2).select('Phase 1', { force: true });
        cy.get('select').eq(3).select('Bug', { force: true });
        cy.get('input[placeholder="Search member…"]').type('Carol', { force: true });
      });
      cy.contains('li button', carol).click({ force: true });
      cy.get('.z-50:visible').within(() => cy.contains('button', 'Create job').click({ force: true }));
      cy.get('.z-50:visible').should('not.exist');

      // JobListPage renders both a `md:hidden` mobile card list and a desktop table
      // for the same jobs simultaneously — rather than disambiguate which copy is
      // actually visible at this viewport width, fetch the created job's friendlyId
      // directly via the API (the same list the page itself just rendered from).
      cy.then(() =>
        tokenFor(alice).then((token) =>
          cy.request({
            method: 'GET',
            url: `${API}/api/projects/${projectId}/jobs`,
            headers: { Authorization: `Bearer ${token}` },
          }),
        ),
      ).then((res) => {
        const created = (res.body as Array<{ friendlyId: string; title: string }>).find(
          (j) => j.title === 'Task for Golden Path Project',
        )!;
        carolJobId = created.friendlyId;
      });

      cy.then(() =>
        createJobAs(alice, projectId, {
          title: 'Ship the Q3 feature rollout',
          assignedTo: daveUserId,
          milestoneId: milestone2Id,
          typeId: featureTypeId,
          priority: 'CRITICAL',
        }),
      ).then((id) => { daveJobId = id; });

      cy.then(() =>
        createJobAs(alice, projectId, {
          title: 'Patch the staging environment',
          assignedTo: erinUserId,
          milestoneId: milestone1Id,
          typeId: bugTypeId,
          priority: 'LOW',
        }),
      ).then((id) => { erinJobId = id; });

      // ---- Step 8: Carol's job — straightforward path ----
      // Bob (Admin) seeds one link first so Carol's own turn always uses the
      // unconditional "+ Add link" button, not the empty-state one gated behind
      // JOB-267 (see file header). Carol still attaches her own link via the UI.
      cy.then(() => createJobLinkAs(bob, projectId, carolJobId, 'https://example.com/spec-doc', 'Spec doc'));

      cy.loginAs(carol);
      // JobDetailPage auto-expands/collapses the Notes accordion based on whether
      // notes have already loaded (`setNotesExpanded(notes.length > 0)`, fired once
      // notesLoading flips false) — clicking the header to expand it manually before
      // that fetch resolves risks the effect firing afterward and collapsing it right
      // back, mid-interaction. Waited on explicitly rather than trusting timing.
      cy.intercept('GET', '**/api/projects/*/jobs/*/notes').as('carolNotesFetch');
      cy.then(() => cy.visit(`/projects/${projectId}/jobs/${carolJobId}`));
      cy.wait('@carolNotesFetch');
      cy.contains('span', 'New').should('be.visible');
      cy.contains('button', 'Start').click();
      cy.contains('span', 'In Progress').should('be.visible');

      cy.contains('Notes').click();
      cy.get('textarea[placeholder="Add a note… (markdown supported)"]').type('Kicking this off — looks straightforward.');
      cy.contains('button', 'Add Note').click();
      cy.get('.z-50:visible').within(() => cy.contains('button', 'Add Note').click());
      cy.contains('Kicking this off').should('be.visible');

      cy.contains('button', '+ Add link').click();
      cy.get('input[placeholder="https://…"]').type('https://github.com/opsclear/opsclear/pull/42');
      cy.get('input[placeholder="Label (optional)"]').should('have.value', 'GitHub');
      cy.get('.z-50:visible').should('not.exist');
      cy.contains('button', 'Save').click();
      cy.contains('GitHub').should('be.visible');

      cy.contains('button', 'Complete').click();
      cy.get('.z-50:visible').within(() => cy.contains('button', 'Mark Complete').click());
      cy.contains('span', 'Completed').should('be.visible');

      // Spot check: no Member (not even the assignee) can reopen a Completed job.
      cy.contains('button', 'Reopen').should('not.exist');

      // Dashboard reflects the completion; Completed count is 1.
      cy.loginAs(alice);
      cy.then(() => cy.visit(`/projects/${projectId}/dashboard`));
      cy.contains('button', 'Completed').find('span.text-2xl').should('have.text', '1');

      // ---- Step 9: Dave's job — friction path ----
      cy.loginAs(dave);
      cy.then(() => cy.visit(`/projects/${projectId}/jobs/${daveJobId}`));
      cy.contains('button', 'Start').click();
      cy.contains('span', 'In Progress').should('be.visible');
      cy.contains('button', 'Block').click();
      cy.get('.z-50:visible').within(() => {
        cy.get('input[placeholder="Select or type a reason…"]').type('Waiting on vendor hardware');
        cy.contains('button', 'Block Job').click();
      });
      cy.contains('span', 'Blocked').should('be.visible');
      cy.contains('Waiting on vendor hardware').should('be.visible');

      // Dashboard, mid-flow: Alice sees the Blocked section reflect Dave's job.
      cy.loginAs(alice);
      cy.then(() => cy.visit(`/projects/${projectId}/dashboard`));
      cy.contains('h2', 'Blocked').parent().parent().within(() => {
        cy.contains('Ship the Q3 feature rollout').should('be.visible');
      });
      cy.contains('button', 'Blocked').find('span.text-2xl').should('have.text', '1');

      // Step 13 (part 1): the project can't be completed while a job is still open.
      // ProjectService.updateStatus's "open jobs" check only counts IN_PROGRESS/
      // BLOCKED (not NEW) — Dave's job must actually be blocked, not merely created,
      // for this to 409; a project with only NEW (never-started) jobs is currently
      // completable despite the settings page's own "All jobs must be closed first"
      // copy. Noted as a product-behavior finding, not fixed here (see PR body).
      cy.then(() =>
        tokenFor(alice).then((token) =>
          cy.request({
            method: 'PATCH',
            url: `${API}/api/projects/${projectId}/status`,
            headers: { Authorization: `Bearer ${token}` },
            body: { status: 'COMPLETED' },
            failOnStatusCode: false,
          }),
        ),
      ).its('status').should('eq', 409);

      // Bob checks in with a note (a different actor than the assignee) and links a
      // BLOCKED_BY relationship to Carol's (already completed) job.
      cy.loginAs(bob);
      cy.intercept('GET', '**/api/projects/*/jobs/*/notes').as('bobNotesFetch');
      cy.then(() => cy.visit(`/projects/${projectId}/jobs/${daveJobId}`));
      cy.wait('@bobNotesFetch');
      cy.contains('Notes').click();
      cy.get('textarea[placeholder="Add a note… (markdown supported)"]').type('Checking in — any update on the vendor?');
      cy.contains('button', 'Add Note').click();
      cy.get('.z-50:visible').should('not.exist');
      cy.contains('Checking in').should('be.visible');

      cy.get('div[role="button"]').contains('Relationships').click();
      cy.contains('button', '+ Add').click();
      cy.get('.z-50:visible').within(() => {
        cy.contains('label', 'Blocked by').click();
        cy.get('input[placeholder="Search jobs…"]').type('Task for');
        cy.contains('button', 'Task for Golden Path Project').click();
        cy.contains('button', 'Add').click();
      });
      cy.contains('Blocked by').should('be.visible');
      cy.contains('Task for Golden Path Project').should('be.visible');

      // Dave unblocks and completes his job.
      cy.loginAs(dave);
      cy.then(() => cy.visit(`/projects/${projectId}/jobs/${daveJobId}`));
      cy.contains('button', 'Unblock').click();
      cy.get('.z-50:visible').within(() => cy.contains('button', 'Unblock').click());
      cy.contains('span', 'In Progress').should('be.visible');
      cy.contains('button', 'Complete').click();
      cy.get('.z-50:visible').within(() => cy.contains('button', 'Mark Complete').click());
      cy.contains('span', 'Completed').should('be.visible');

      cy.loginAs(alice);
      cy.then(() => cy.visit(`/projects/${projectId}/dashboard`));
      cy.contains('button', 'Completed').find('span.text-2xl').should('have.text', '2');
      cy.contains('button', 'Blocked').find('span.text-2xl').should('have.text', '0');

      // Spot check: no Member can delete the project.
      cy.then(() =>
        tokenFor(dave).then((token) =>
          cy.request({
            method: 'DELETE',
            url: `${API}/api/projects/${projectId}`,
            headers: { Authorization: `Bearer ${token}` },
            failOnStatusCode: false,
          }),
        ),
      ).its('status').should('eq', 403);

      // ---- Step 10: Erin's job — gated path ----
      cy.loginAs(erin);
      cy.then(() => cy.visit(`/projects/${projectId}/jobs/${erinJobId}`));
      cy.contains('button', 'Start').click();
      cy.contains('span', 'In Progress').should('be.visible');
      cy.contains('button', 'Request Approval').click();
      approvalDescription = 'Need sign-off before touching staging config';
      cy.get('.z-50:visible').within(() => {
        cy.get('textarea').type(approvalDescription);
        cy.contains('button', 'Submit').click();
      });
      cy.get('.z-50:visible').should('not.exist');
      // The Approvals accordion's auto-expand effect already fired (and collapsed,
      // since there were 0 approvals) on page load, before this request existed —
      // adding one via this same session doesn't re-trigger it, so it needs an
      // explicit click.
      cy.contains('Approvals').click();
      cy.contains(approvalDescription).should('be.visible');

      // Dashboard, mid-flow: Alice sees the Pending Approvals section reflect it.
      cy.loginAs(alice);
      cy.then(() => cy.visit(`/projects/${projectId}/dashboard`));
      cy.contains('h2', 'Pending Approvals').parent().parent().within(() => {
        cy.contains(approvalDescription).should('be.visible');
      });

      // Spot check: Carol cannot decide Erin's pending approval — 403, and no such
      // control is rendered to her (the queue redirects a MEMBER away entirely).
      cy.then(() =>
        listApprovalsByJobAs(alice, projectId, erinJobId).then((approvals) => {
          const approvalId = approvals.find((a) => a.status === 'PENDING')!.id;
          return tokenFor(carol).then((token) =>
            cy.request({
              method: 'PATCH',
              url: `${API}/api/projects/${projectId}/jobs/${erinJobId}/approvals/${approvalId}/status`,
              headers: { Authorization: `Bearer ${token}` },
              body: { status: 'APPROVED' },
              failOnStatusCode: false,
            }),
          );
        }),
      ).its('status').should('eq', 403);

      cy.loginAs(carol);
      cy.then(() => cy.visit(`/projects/${projectId}/approvals`));
      cy.url().should('not.include', '/approvals');

      // Alice reviews the approval queue, sees the request, and approves it.
      cy.loginAs(alice);
      cy.then(() => cy.visit(`/projects/${projectId}/approvals`));
      cy.contains(approvalDescription).should('be.visible');
      cy.contains(approvalDescription).parent().contains('button', 'Approve').click();
      cy.get('.z-50:visible').within(() => cy.contains('button', 'Approve').click());
      cy.contains(approvalDescription).should('not.exist');

      // Approving a request only clears the gate — it doesn't itself transition the
      // job (ApprovalService has no such side effect). Erin still completes her job
      // explicitly, same as the other two.
      cy.loginAs(erin);
      cy.then(() => cy.visit(`/projects/${projectId}/jobs/${erinJobId}`));
      cy.contains('button', 'Complete').click();
      cy.get('.z-50:visible').within(() => cy.contains('button', 'Mark Complete').click());
      cy.contains('span', 'Completed').should('be.visible');

      // ---- Step 11 (final): Dashboard reflects all three completions ----
      cy.loginAs(alice);
      cy.then(() => cy.visit(`/projects/${projectId}/dashboard`));
      cy.contains('button', 'Completed').find('span.text-2xl').should('have.text', '3');
      cy.contains('h2', 'Pending Approvals').should('not.exist');

      // ---- Step 13 (part 2): all jobs closed — completing the project now succeeds ----
      cy.then(() => cy.visit(`/projects/${projectId}/settings`));
      cy.contains('button', 'Complete project').click();
      cy.contains('This project is completed and no longer accepts changes.').should('be.visible');
      cy.contains('button', 'Reactivate').should('be.visible');

      cy.deleteKeycloakUser(alice);
      cy.deleteKeycloakUser(bob);
      cy.deleteKeycloakUser(carol);
      cy.deleteKeycloakUser(dave);
      cy.deleteKeycloakUser(erin);
    },
  );
});
