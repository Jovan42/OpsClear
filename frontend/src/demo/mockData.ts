import type {
  JobHistoryEntry,
  JobResponse,
  LinkResponse,
  MilestoneResponse,
  NoteResponse,
  ProjectMemberResponse,
  ProjectResponse,
} from '../types';

/**
 * The one shared fictional dataset for every /features interactive demo (ADR-0040).
 * Fixed, easily-recognizable IDs (never real UUIDs) so demo MSW handlers can match on
 * an exact literal path rather than a wildcard — a real logged-in user's own project ID
 * can never accidentally collide with (or be intercepted as) this data.
 *
 * All functions here mutate an in-memory store, reset via resetDemoData() every time
 * the demo overlay opens — per ADR-0040, there is no persistence between opens.
 */

const ORG_NAME = 'Nimbus Creative Studio';

// Must match the app's PROJECT_ID_RE (either a raw UUID or a friendly `XX-123` id) —
// useProject()/useProjectMembers() gate their queries on this shape and would
// otherwise silently never fire for the demo project, leaving members/role
// unresolved (surfaced as "Unknown user" and missing Approve/Reject buttons).
export const DEMO_PROJECT_ID = 'DEMO-1';

export const DEMO_USERS = {
  ana: { id: 'demo-user-ana', name: 'John Doe', email: 'john.doe@example.com' },
  marko: { id: 'demo-user-marko', name: 'Jane Smith', email: 'jane.smith@example.com' },
  iva: { id: 'demo-user-iva', name: 'Alex Johnson', email: 'alex.johnson@example.com' },
} as const;

/** The demo's "you" persona — logged in as the project owner. See mockAuthState.ts. */
export const DEMO_CURRENT_USER = DEMO_USERS.ana;

function hoursAgo(hours: number): string {
  return new Date(Date.now() - hours * 60 * 60 * 1000).toISOString();
}

function daysFromNow(days: number): string {
  return new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString();
}

const BASE_MEMBERS: ProjectMemberResponse[] = [
  {
    id: 'demo-member-ana',
    userId: DEMO_USERS.ana.id,
    projectId: DEMO_PROJECT_ID,
    userName: DEMO_USERS.ana.name,
    userEmail: DEMO_USERS.ana.email,
    role: 'OWNER',
    joinedAt: hoursAgo(24 * 90),
  },
  {
    id: 'demo-member-marko',
    userId: DEMO_USERS.marko.id,
    projectId: DEMO_PROJECT_ID,
    userName: DEMO_USERS.marko.name,
    userEmail: DEMO_USERS.marko.email,
    role: 'ADMIN',
    joinedAt: hoursAgo(24 * 60),
  },
  {
    id: 'demo-member-iva',
    userId: DEMO_USERS.iva.id,
    projectId: DEMO_PROJECT_ID,
    userName: DEMO_USERS.iva.name,
    userEmail: DEMO_USERS.iva.email,
    role: 'MEMBER',
    joinedAt: hoursAgo(24 * 45),
  },
];

const BASE_PROJECT: ProjectResponse = {
  id: DEMO_PROJECT_ID,
  friendlyId: DEMO_PROJECT_ID,
  name: 'Website Redesign',
  description: `${ORG_NAME}'s public site redesign — new homepage, mobile nav, and checkout flow.`,
  ownerId: DEMO_USERS.ana.id,
  ownerName: DEMO_USERS.ana.name,
  status: 'ACTIVE',
  createdAt: hoursAgo(24 * 90),
  links: [
    {
      id: 'demo-link-figma',
      url: 'https://figma.com/file/demo-website-redesign',
      label: 'Figma designs',
      createdBy: DEMO_USERS.ana.id,
      createdAt: hoursAgo(24 * 80),
      updatedAt: hoursAgo(24 * 80),
    },
  ],
};

const BASE_MILESTONES: MilestoneResponse[] = [
  {
    id: 'demo-milestone-beta',
    friendlyId: 'MIL-D1',
    projectId: DEMO_PROJECT_ID,
    name: 'Beta launch',
    description: 'Internal team can use the redesigned site end to end.',
    deadline: daysFromNow(5),
    createdAt: hoursAgo(24 * 30),
  },
  {
    id: 'demo-milestone-ga',
    friendlyId: 'MIL-D2',
    projectId: DEMO_PROJECT_ID,
    name: 'Public launch',
    description: null,
    deadline: daysFromNow(20),
    createdAt: hoursAgo(24 * 30),
  },
];

interface DemoJobSeed {
  id: string;
  friendlyId: string;
  title: string;
  description: string;
  status: JobResponse['status'];
  priority: JobResponse['priority'];
  assignedTo: string | null;
  assignedToName: string | null;
  milestoneId: string | null;
  milestoneName: string | null;
  blockedReason?: string;
  blockedHoursAgo?: number;
  links?: LinkResponse[];
}

const JOB_SEEDS: DemoJobSeed[] = [
  {
    id: 'demo-job-01',
    friendlyId: 'DEMO-101',
    title: 'Redesign homepage hero section',
    description: 'New hero with updated copy and a live product screenshot.',
    status: 'IN_PROGRESS',
    priority: 'HIGH',
    assignedTo: DEMO_USERS.marko.id,
    assignedToName: DEMO_USERS.marko.name,
    milestoneId: 'demo-milestone-beta',
    milestoneName: 'Beta launch',
  },
  {
    id: 'demo-job-02',
    friendlyId: 'DEMO-102',
    title: 'Fix mobile navigation bug',
    description: 'Hamburger menu overlaps the logo below 375px width.',
    status: 'BLOCKED',
    priority: 'CRITICAL',
    assignedTo: DEMO_USERS.iva.id,
    assignedToName: DEMO_USERS.iva.name,
    milestoneId: 'demo-milestone-beta',
    milestoneName: 'Beta launch',
    blockedReason: 'Waiting on design review from John',
    blockedHoursAgo: 6,
  },
  {
    id: 'demo-job-03',
    friendlyId: 'DEMO-103',
    title: 'Set up analytics tracking',
    description: 'Wire up event tracking for the new checkout funnel.',
    status: 'NEW',
    priority: 'MEDIUM',
    assignedTo: null,
    assignedToName: null,
    milestoneId: 'demo-milestone-ga',
    milestoneName: 'Public launch',
  },
  {
    id: 'demo-job-04',
    friendlyId: 'DEMO-104',
    title: 'Launch beta to internal team',
    description: 'Send invite + walkthrough doc to the internal testing group.',
    status: 'COMPLETED',
    priority: 'MEDIUM',
    assignedTo: DEMO_USERS.ana.id,
    assignedToName: DEMO_USERS.ana.name,
    milestoneId: 'demo-milestone-beta',
    milestoneName: 'Beta launch',
  },
  {
    id: 'demo-job-05',
    friendlyId: 'DEMO-105',
    title: 'Migrate CMS content',
    description: 'Move existing blog posts and case studies into the new CMS.',
    status: 'IN_PROGRESS',
    priority: 'MEDIUM',
    assignedTo: DEMO_USERS.iva.id,
    assignedToName: DEMO_USERS.iva.name,
    milestoneId: 'demo-milestone-ga',
    milestoneName: 'Public launch',
  },
  {
    id: 'demo-job-06',
    friendlyId: 'DEMO-106',
    title: 'Rewrite checkout flow copy',
    description: 'Simplify the microcopy across all three checkout steps.',
    status: 'IN_PROGRESS',
    priority: 'LOW',
    assignedTo: DEMO_USERS.iva.id,
    assignedToName: DEMO_USERS.iva.name,
    milestoneId: null,
    milestoneName: null,
  },
  {
    id: 'demo-job-07',
    friendlyId: 'DEMO-107',
    title: 'Fix broken image links in blog archive',
    description: 'A handful of older blog posts point at images that no longer exist.',
    status: 'COMPLETED',
    priority: 'MEDIUM',
    assignedTo: DEMO_USERS.marko.id,
    assignedToName: DEMO_USERS.marko.name,
    milestoneId: null,
    milestoneName: null,
  },
];

function buildJob(seed: DemoJobSeed): JobResponse {
  return {
    id: seed.id,
    friendlyId: seed.friendlyId,
    projectId: DEMO_PROJECT_ID,
    title: seed.title,
    description: seed.description,
    client: null,
    assignedTo: seed.assignedTo,
    assignedToName: seed.assignedToName,
    deadline: seed.status === 'COMPLETED' ? null : daysFromNow(7),
    status: seed.status,
    priority: seed.priority,
    createdBy: DEMO_USERS.ana.id,
    createdAt: hoursAgo(24 * 20),
    updatedAt: hoursAgo(2),
    blockedBy: seed.blockedReason ? DEMO_USERS.ana.id : null,
    blockedReason: seed.blockedReason ?? null,
    blockedAt: seed.blockedHoursAgo != null ? hoursAgo(seed.blockedHoursAgo) : null,
    milestoneId: seed.milestoneId,
    milestoneName: seed.milestoneName,
    relationships: [],
    links: seed.links ?? [],
    sourceScheduleId: null,
  };
}

const BASE_JOBS: JobResponse[] = JOB_SEEDS.map(buildJob);

// job-05 blocks job-06 — a simple BLOCKED_BY pair, both still in progress.
BASE_JOBS[4].relationships = [
  {
    id: 'demo-rel-05-06',
    type: 'BLOCKED_BY',
    direction: 'OUTGOING',
    job: { id: 'demo-job-06', friendlyId: 'DEMO-106', title: 'Rewrite checkout flow copy', status: 'IN_PROGRESS' },
  },
];

// job-06 is the relationships demo card's subject — deliberately given every
// direction/type/status combination in one job: blocked by a job that's already
// completed (closed), blocking a not-yet-started job, related to an in-progress job,
// plus the simple pair with job-05 above.
BASE_JOBS[5].relationships = [
  {
    id: 'demo-rel-06-05',
    type: 'BLOCKED_BY',
    direction: 'INCOMING',
    job: { id: 'demo-job-05', friendlyId: 'DEMO-105', title: 'Migrate CMS content', status: 'IN_PROGRESS' },
  },
  {
    id: 'demo-rel-06-04',
    type: 'BLOCKED_BY',
    direction: 'OUTGOING',
    job: { id: 'demo-job-04', friendlyId: 'DEMO-104', title: 'Launch beta to internal team', status: 'COMPLETED' },
  },
  {
    id: 'demo-rel-06-03',
    type: 'BLOCKED_BY',
    direction: 'INCOMING',
    job: { id: 'demo-job-03', friendlyId: 'DEMO-103', title: 'Set up analytics tracking', status: 'NEW' },
  },
  {
    id: 'demo-rel-06-01',
    type: 'RELATED_TO',
    direction: 'OUTGOING',
    job: { id: 'demo-job-01', friendlyId: 'DEMO-101', title: 'Redesign homepage hero section', status: 'IN_PROGRESS' },
  },
];

// Reciprocal entries on the other side of each job-06 relationship, for data
// consistency (only job-06 itself is shown by the relationships demo card).
BASE_JOBS[3].relationships = [
  {
    id: 'demo-rel-04-06',
    type: 'BLOCKED_BY',
    direction: 'INCOMING',
    job: { id: 'demo-job-06', friendlyId: 'DEMO-106', title: 'Rewrite checkout flow copy', status: 'IN_PROGRESS' },
  },
];
BASE_JOBS[2].relationships = [
  {
    id: 'demo-rel-03-06',
    type: 'BLOCKED_BY',
    direction: 'OUTGOING',
    job: { id: 'demo-job-06', friendlyId: 'DEMO-106', title: 'Rewrite checkout flow copy', status: 'IN_PROGRESS' },
  },
];
BASE_JOBS[0].relationships = [
  {
    id: 'demo-rel-01-06',
    type: 'RELATED_TO',
    direction: 'INCOMING',
    job: { id: 'demo-job-06', friendlyId: 'DEMO-106', title: 'Rewrite checkout flow copy', status: 'IN_PROGRESS' },
  },
];

const BASE_NOTES: Record<string, NoteResponse[]> = {
  'demo-job-01': [
    {
      id: 'demo-note-01-1',
      jobId: 'demo-job-01',
      authorId: DEMO_USERS.marko.id,
      content: 'First pass is up on staging — feedback welcome.',
      createdAt: hoursAgo(4),
    },
  ],
  'demo-job-02': [
    {
      id: 'demo-note-02-1',
      jobId: 'demo-job-02',
      authorId: DEMO_USERS.iva.id,
      content: 'Reproduced on iPhone SE and a Pixel 5, both under 375px.',
      createdAt: hoursAgo(7),
    },
  ],
};

const BASE_HISTORY: Record<string, JobHistoryEntry[]> = {
  'demo-job-02': [
    {
      id: 'demo-hist-02-1',
      jobId: 'demo-job-02',
      changedFrom: null,
      changedTo: 'NEW',
      changedBy: DEMO_USERS.ana.id,
      changedByName: DEMO_USERS.ana.name,
      changedAt: hoursAgo(24 * 3),
      blockReason: null,
    },
    {
      id: 'demo-hist-02-2',
      jobId: 'demo-job-02',
      changedFrom: 'NEW',
      changedTo: 'IN_PROGRESS',
      changedBy: DEMO_USERS.iva.id,
      changedByName: DEMO_USERS.iva.name,
      changedAt: hoursAgo(24 * 2),
      blockReason: null,
    },
    {
      id: 'demo-hist-02-3',
      jobId: 'demo-job-02',
      changedFrom: 'IN_PROGRESS',
      changedTo: 'BLOCKED',
      changedBy: DEMO_USERS.ana.id,
      changedByName: DEMO_USERS.ana.name,
      changedAt: hoursAgo(6),
      blockReason: 'Waiting on design review from John',
    },
  ],
  // demo-job-07's whole point is walking through every status in one timeline
  // (New → In Progress → Blocked → In Progress → Completed) for the history demo card.
  'demo-job-07': [
    {
      id: 'demo-hist-07-1',
      jobId: 'demo-job-07',
      changedFrom: null,
      changedTo: 'NEW',
      changedBy: DEMO_USERS.ana.id,
      changedByName: DEMO_USERS.ana.name,
      changedAt: hoursAgo(24 * 6),
      blockReason: null,
    },
    {
      id: 'demo-hist-07-2',
      jobId: 'demo-job-07',
      changedFrom: 'NEW',
      changedTo: 'IN_PROGRESS',
      changedBy: DEMO_USERS.marko.id,
      changedByName: DEMO_USERS.marko.name,
      changedAt: hoursAgo(24 * 5),
      blockReason: null,
    },
    {
      id: 'demo-hist-07-3',
      jobId: 'demo-job-07',
      changedFrom: 'IN_PROGRESS',
      changedTo: 'BLOCKED',
      changedBy: DEMO_USERS.marko.id,
      changedByName: DEMO_USERS.marko.name,
      changedAt: hoursAgo(24 * 4),
      blockReason: 'Waiting on the CDN migration to finish first',
    },
    {
      id: 'demo-hist-07-4',
      jobId: 'demo-job-07',
      changedFrom: 'BLOCKED',
      changedTo: 'IN_PROGRESS',
      changedBy: DEMO_USERS.ana.id,
      changedByName: DEMO_USERS.ana.name,
      changedAt: hoursAgo(24 * 2),
      blockReason: null,
    },
    {
      id: 'demo-hist-07-5',
      jobId: 'demo-job-07',
      changedFrom: 'IN_PROGRESS',
      changedTo: 'COMPLETED',
      changedBy: DEMO_USERS.marko.id,
      changedByName: DEMO_USERS.marko.name,
      changedAt: hoursAgo(24),
      blockReason: null,
    },
  ],
};

/**
 * A single canonical record per approval request — covers both the project-wide
 * pending queue (GET /approvals/pending, shaped as PendingApprovalResponse) and a
 * job's own approval list (GET /jobs/:jobId/approvals, shaped as ApprovalResponse).
 * Requesting a new approval or deciding one just mutates this one array; both
 * endpoints derive their response shape from it, so a newly requested approval shows
 * up correctly in both places rather than needing to be kept in sync by hand.
 */
export interface DemoApproval {
  id: string;
  jobId: string;
  jobTitle: string;
  requesterId: string;
  approverId: string | null;
  description: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  comment: string | null;
  requestedAt: string;
  decidedAt: string | null;
}

const BASE_APPROVALS: DemoApproval[] = [
  {
    id: 'demo-approval-01',
    jobId: 'demo-job-05',
    jobTitle: 'Migrate CMS content',
    requesterId: DEMO_USERS.iva.id,
    approverId: null,
    description: 'Ready to delete the old CMS export — confirming before I remove it.',
    status: 'PENDING',
    comment: null,
    requestedAt: hoursAgo(3),
    decidedAt: null,
  },
  {
    id: 'demo-approval-02',
    jobId: 'demo-job-06',
    jobTitle: 'Rewrite checkout flow copy',
    requesterId: DEMO_USERS.iva.id,
    approverId: null,
    description: 'New copy is drafted — OK to publish to the live checkout page?',
    status: 'PENDING',
    comment: null,
    requestedAt: hoursAgo(1),
    decidedAt: null,
  },
  // Already-decided (one approved, one rejected) on the completed "Launch beta" job —
  // used by the Approvals card's second slide to show decided history in context.
  {
    id: 'demo-approval-decided-01',
    jobId: 'demo-job-04',
    jobTitle: 'Launch beta to internal team',
    requesterId: DEMO_USERS.iva.id,
    approverId: DEMO_USERS.ana.id,
    description: 'OK to send the beta invite to the full internal team (12 people)?',
    status: 'APPROVED',
    comment: 'Go ahead — make sure the walkthrough doc link works first.',
    requestedAt: hoursAgo(30),
    decidedAt: hoursAgo(28),
  },
  {
    id: 'demo-approval-decided-02',
    jobId: 'demo-job-04',
    jobTitle: 'Launch beta to internal team',
    requesterId: DEMO_USERS.iva.id,
    approverId: DEMO_USERS.ana.id,
    description: 'Can we skip the staging environment test and deploy straight to prod for this one?',
    status: 'REJECTED',
    comment: "No, let's not skip staging even for something this small.",
    requestedAt: hoursAgo(26),
    decidedAt: hoursAgo(25),
  },
];

// ---- mutable in-memory store, reset per demo-overlay open ----

export interface DemoStore {
  project: ProjectResponse;
  members: ProjectMemberResponse[];
  milestones: MilestoneResponse[];
  jobs: JobResponse[];
  notesByJobId: Record<string, NoteResponse[]>;
  historyByJobId: Record<string, JobHistoryEntry[]>;
  approvals: DemoApproval[];
}

function freshStore(): DemoStore {
  return {
    project: structuredClone(BASE_PROJECT),
    members: structuredClone(BASE_MEMBERS),
    milestones: structuredClone(BASE_MILESTONES),
    jobs: structuredClone(BASE_JOBS),
    notesByJobId: structuredClone(BASE_NOTES),
    historyByJobId: structuredClone(BASE_HISTORY),
    approvals: structuredClone(BASE_APPROVALS),
  };
}

export let demoStore: DemoStore = freshStore();

/** Resets every mock entity back to its baseline — called each time a demo overlay opens. */
export function resetDemoData(): void {
  demoStore = freshStore();
}
