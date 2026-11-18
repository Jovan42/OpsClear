// ADR-0062 (JOB-358): the sample workspace the Daily work, Planning, Admin & settings and
// Account specs photograph: "Acme Plumbing", one project with a dozen jobs in every status,
// milestones, types, notes, links, relationships, a template and a recurring schedule.
// Everything goes in through the API (fast, and no UI step to break), as the sample people.
// Each spec seeds its own copy in `before`, and deletes the Keycloak users in `after`.

import {
  addMember,
  addProjectMember,
  createJobAs,
  createJobLinkAs,
  createJobTypeAs,
  createMilestoneAs,
  createNoteAs,
  createOrgAs,
  createProjectAs,
  createProjectLinkAs,
  createRelationshipAs,
  createOrgTemplateAs,
  createScheduleAs,
  insertMissedRunAs,
  pauseScheduleAs,
  createTemplateAs,
  makeOrgRealisticSubscription,
  requestApprovalAs,
  updateJobStatusAs,
  userIdFor,
} from '../../support/orgApi';
import { setPreferencesAs } from '../../support/preferences';
import { APPROVAL_TEXT, daysFromNow, guideEmail, guideLang, JOBS, ORG_NAME, PEOPLE, PROJECT_NAME } from './sample';
import { freeSampleSlug } from './steps';

export const ORG_SLUG = 'ACM';

export interface Person {
  email: string;
  first: string;
  last: string;
  id: string;
}

export interface World {
  orgId: string;
  projectId: string;
  /** The org's owner, an org admin, and two members, all with the sample names. */
  owner: Person;
  admin: Person;
  teammate: Person;
  member: Person;
  jobs: Record<string, string>;
  milestones: Record<string, string>;
  types: Record<string, string>;
  templateId: string;
  safetyTemplateId: string;
  scheduleId: string;
}

export function emptyWorld(): World {
  const person = (key: keyof typeof PEOPLE): Person => ({
    email: guideEmail(key),
    first: PEOPLE[key].first,
    last: PEOPLE[key].last,
    id: '',
  });
  return {
    orgId: '',
    projectId: '',
    owner: person('owner'),
    admin: person('approver'),
    teammate: person('teammate'),
    member: person('member'),
    jobs: {},
    milestones: {},
    types: {},
    templateId: '',
    safetyTemplateId: '',
    scheduleId: '',
  };
}

export const people = (w: World) => [w.owner, w.admin, w.teammate, w.member];

export const NOTES = [
  'Parts delivered, starting on site tomorrow morning.',
  'Customer would like the old pipework left for them to see.',
  'Agreed with the supplier: delivery moves to Thursday.',
];

/** Creates the Keycloak users (everyone's language follows the run's), the org with a paid
 *  subscription and every add-on, the team, and the project with its jobs. Call from `before`. */
export function seedWorld(w: World) {
  const lang = guideLang();
  freeSampleSlug();
  for (const p of people(w)) cy.createKeycloakUser(p.email, p.first, p.last);
  for (const p of people(w)) {
    cy.then(() => userIdFor(p.email)).then((id) => {
      p.id = id;
    });
  }
  cy.then(() => createOrgAs(w.owner.email, ORG_NAME, ORG_SLUG)).then((id) => {
    w.orgId = id;
  });
  cy.then(() => makeOrgRealisticSubscription(w.orgId));
  cy.then(() => addMember(w.orgId, w.owner.email, w.admin.id, 'ADMIN'));
  cy.then(() => addMember(w.orgId, w.owner.email, w.teammate.id, 'MEMBER'));
  cy.then(() => addMember(w.orgId, w.owner.email, w.member.id, 'MEMBER'));
  cy.then(() => {
    for (const p of people(w)) setPreferencesAs(p.email, { locale: lang, theme: 'light' });
  });

  cy.then(() => createProjectAs(w.owner.email, PROJECT_NAME)).then((id) => {
    w.projectId = id;
  });
  cy.then(() => addProjectMember(w.projectId, w.owner.email, w.admin.id, 'ADMIN'));
  cy.then(() => addProjectMember(w.projectId, w.owner.email, w.teammate.id, 'MEMBER'));
  cy.then(() => addProjectMember(w.projectId, w.owner.email, w.member.id, 'MEMBER'));

  const milestones: Array<[string, string, string]> = [
    ['demolition', 'Strip out', daysFromNow(-3)],
    ['firstFix', 'First fix', daysFromNow(6)],
    ['finish', 'Second fix and finish', daysFromNow(24)],
  ];
  for (const [key, name, deadline] of milestones) {
    cy.then(() => createMilestoneAs(w.owner.email, w.projectId, name, deadline)).then((id) => {
      w.milestones[key] = id;
    });
  }
  const types: Array<[string, string, string]> = [
    ['plumbing', 'Plumbing', 'BLUE'],
    ['gas', 'Gas', 'ORANGE'],
    ['joinery', 'Joinery', 'GREEN'],
  ];
  for (const [key, name, color] of types) {
    cy.then(() => createJobTypeAs(w.owner.email, w.projectId, name, color)).then((id) => {
      w.types[key] = id;
    });
  }

  type Status = 'NEW' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED';
  type Seed = {
    key: string;
    title: string;
    client: string;
    deadline: string;
    priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
    who: Person | null;
    status: Status;
    milestone: string;
    type: string;
  };
  const owner = w.owner;
  const seeds: Seed[] = [
    { key: 'strip', title: 'Strip out the old kitchen', client: 'Hartley Family', deadline: daysFromNow(-9), priority: 'MEDIUM', who: w.teammate, status: 'COMPLETED', milestone: 'demolition', type: 'joinery' },
    { key: 'cap', title: 'Cap the old gas supply', client: 'Hartley Family', deadline: daysFromNow(-8), priority: 'HIGH', who: w.teammate, status: 'COMPLETED', milestone: 'demolition', type: 'gas' },
    { key: 'pipes', title: 'Run the new water pipes', client: 'Hartley Family', deadline: daysFromNow(-1), priority: 'HIGH', who: w.teammate, status: 'IN_PROGRESS', milestone: 'firstFix', type: 'plumbing' },
    { key: 'valve', ...JOBS.done, deadline: daysFromNow(2), priority: 'CRITICAL', who: w.teammate, status: 'IN_PROGRESS', milestone: 'firstFix', type: 'plumbing' },
    { key: 'worktops', title: 'Order the worktops', client: 'Hartley Family', deadline: daysFromNow(3), priority: 'MEDIUM', who: w.member, status: 'IN_PROGRESS', milestone: 'firstFix', type: 'joinery' },
    { key: 'dishwasher', ...JOBS.blocked, priority: 'MEDIUM', who: w.member, status: 'BLOCKED', milestone: 'finish', type: 'joinery' },
    { key: 'pressure', title: 'Pressure test the heating loop', client: 'Okafor Bakery', deadline: daysFromNow(5), priority: 'HIGH', who: w.teammate, status: 'BLOCKED', milestone: 'firstFix', type: 'plumbing' },
    { key: 'sink', ...JOBS.main, priority: 'HIGH', who: w.teammate, status: 'NEW', milestone: 'finish', type: 'plumbing' },
    { key: 'gas', ...JOBS.approval, priority: 'MEDIUM', who: w.member, status: 'NEW', milestone: 'firstFix', type: 'gas' },
    { key: 'splashback', title: 'Tile the splashback', client: 'Hartley Family', deadline: daysFromNow(20), priority: 'LOW', who: w.admin, status: 'NEW', milestone: 'finish', type: 'joinery' },
    { key: 'extractor', title: 'Fit the extractor fan', client: 'Hartley Family', deadline: daysFromNow(18), priority: 'MEDIUM', who: null, status: 'NEW', milestone: 'finish', type: 'plumbing' },
    { key: 'inspection', title: 'Final inspection with the client', client: 'Hartley Family', deadline: daysFromNow(26), priority: 'MEDIUM', who: owner, status: 'NEW', milestone: 'finish', type: 'plumbing' },
  ];
  for (const seed of seeds) {
    cy.then(() =>
      createJobAs(owner.email, w.projectId, {
        title: seed.title,
        client: seed.client,
        deadline: `${seed.deadline}T00:00:00Z`,
        priority: seed.priority,
        assignedTo: seed.who?.id,
        milestoneId: w.milestones[seed.milestone],
        typeId: w.types[seed.type],
      }),
    ).then((id) => {
      w.jobs[seed.key] = id;
    });
    if (seed.status !== 'NEW' && seed.who) {
      const actor = seed.who;
      cy.then(() => updateJobStatusAs(actor.email, w.projectId, w.jobs[seed.key], 'IN_PROGRESS'));
      if (seed.status === 'BLOCKED') {
        cy.then(() =>
          updateJobStatusAs(actor.email, w.projectId, w.jobs[seed.key], 'BLOCKED', 'Waiting for the supplier to deliver'),
        );
      }
      if (seed.status === 'COMPLETED') {
        cy.then(() => updateJobStatusAs(actor.email, w.projectId, w.jobs[seed.key], 'COMPLETED'));
      }
    }
  }

  // Notes, links and relationships on the jobs the pages talk about
  NOTES.forEach((text, i) => {
    const author = [w.teammate, w.owner, w.teammate][i];
    cy.then(() => createNoteAs(author.email, w.projectId, w.jobs.pipes, text));
  });
  cy.then(() =>
    createJobLinkAs(w.owner.email, w.projectId, w.jobs.pipes, 'https://example.com/pipe-layout.pdf', 'Pipe layout drawing'),
  );
  cy.then(() =>
    createJobLinkAs(w.owner.email, w.projectId, w.jobs.pipes, 'https://example.com/supplier-order', 'Supplier order'),
  );
  cy.then(() =>
    createProjectLinkAs(w.owner.email, w.projectId, 'https://example.com/kitchen-plans', 'Kitchen plans'),
  );
  cy.then(() => createRelationshipAs(w.owner.email, w.projectId, w.jobs.sink, w.jobs.pipes, 'BLOCKED_BY'));
  cy.then(() => createRelationshipAs(w.owner.email, w.projectId, w.jobs.dishwasher, w.jobs.sink, 'RELATED_TO'));

  cy.then(() =>
    requestApprovalAs(w.member.email, w.projectId, w.jobs.gas, APPROVAL_TEXT).its('status').should('eq', 201),
  );

  // Templates (one for the project, one shared by the whole organisation) and the schedules
  // that use them: one running with a missed run to catch up on, one paused
  cy.then(() =>
    createTemplateAs(w.owner.email, w.projectId, {
      name: 'Boiler service visit',
      title: 'Boiler service',
      description: 'Annual service: flush, check pressure, test the safety valve.',
      priority: 'MEDIUM',
      assigneeMode: 'FIXED',
      assigneeId: w.teammate.id,
      defaultTypeId: w.types.plumbing,
      deadlineOffsetDays: 7,
    }),
  ).then((id) => {
    w.templateId = id;
  });
  cy.then(() =>
    createTemplateAs(w.owner.email, w.projectId, {
      name: 'Site safety check',
      title: 'Site safety check',
      priority: 'HIGH',
      assigneeMode: 'ASK',
      deadlineOffsetDays: 2,
    }),
  ).then((id) => {
    w.safetyTemplateId = id;
  });
  cy.then(() =>
    createOrgTemplateAs(w.owner.email, w.orgId, {
      name: 'Customer follow-up call',
      title: 'Call the customer',
      description: 'Ask how the work is holding up and note anything to fix.',
      priority: 'LOW',
      assigneeMode: 'NONE',
      deadlineOffsetDays: 14,
    }),
  );
  cy.then(() =>
    createScheduleAs(w.owner.email, w.projectId, {
      name: 'Monthly boiler service',
      templateId: w.templateId,
      cronExpression: '0 0 8 1 * *',
      timezone: 'UTC',
      assigneeIds: [w.teammate.id],
    }),
  ).then((id) => {
    w.scheduleId = id;
  });
  cy.then(() =>
    createScheduleAs(w.owner.email, w.projectId, {
      name: 'Weekly site safety check',
      templateId: w.safetyTemplateId,
      cronExpression: '0 0 7 * * MON',
      timezone: 'UTC',
      assigneeIds: [w.member.id],
    }),
  ).then((id) => {
    pauseScheduleAs(w.owner.email, w.projectId, id, `${daysFromNow(9)}T00:00:00Z`);
  });
  cy.then(() => insertMissedRunAs(w.scheduleId, `${daysFromNow(-4)}T08:00:00Z`));
}

export function cleanUpWorld(w: World) {
  for (const p of people(w)) cy.deleteKeycloakUser(p.email);
}
