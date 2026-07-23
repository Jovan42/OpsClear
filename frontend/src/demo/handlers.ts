import { http, HttpResponse } from 'msw';
import { DEMO_CURRENT_USER, DEMO_PROJECT_ID, demoStore, type DemoApproval } from './mockData';
import type {
  ApprovalResponse,
  ApprovalStatus,
  JobHistoryEntry,
  JobPriority,
  JobRelationshipType,
  JobRelationshipView,
  JobResponse,
  JobStatus,
  MilestoneResponse,
  NoteResponse,
  PendingApprovalResponse,
} from '../types';

/**
 * Handlers matched against the shared mock dataset (ADR-0040). Every path is prefixed
 * with the exact literal DEMO_PROJECT_ID rather than a `:projectId` wildcard param —
 * a real logged-in user's real project ID can never collide with or be intercepted by
 * these, even if a real app tab happened to be open in the same browser at the same time.
 */
const base = `*/api/projects/${DEMO_PROJECT_ID}`;

/**
 * The real backend resolves job URLs by either raw id or friendlyId interchangeably
 * (FriendlyIdResolver) — job list/detail links use friendlyId (e.g. "DEMO-101"), so
 * every job-scoped handler needs to resolve the URL param the same way, not just
 * match against the raw `.id` used as the mock store's dictionary key.
 */
function resolveJobId(idOrFriendlyId: string): string | null {
  const job = demoStore.jobs.find((j) => j.id === idOrFriendlyId || j.friendlyId === idOrFriendlyId);
  return job?.id ?? null;
}

/**
 * A plain `Date.now()`-based id collides if two records get created within the same
 * millisecond (a real occurrence — a stale Service Worker registration left over from
 * an earlier dev session can double-intercept a single request, or React can invoke a
 * handler twice in some dev-mode edge cases). A monotonic counter can't collide.
 */
let idCounter = 0;
function uniqueId(prefix: string): string {
  idCounter += 1;
  return `${prefix}-${Date.now()}-${idCounter}`;
}

/** Drops the internal-only `jobTitle` field to match the real ApprovalResponse shape. */
function toApprovalResponse(a: DemoApproval): ApprovalResponse {
  return {
    id: a.id,
    jobId: a.jobId,
    requesterId: a.requesterId,
    approverId: a.approverId,
    description: a.description,
    status: a.status,
    comment: a.comment,
    requestedAt: a.requestedAt,
    decidedAt: a.decidedAt,
  };
}

export const demoHandlers = [
  http.get(base, () => HttpResponse.json(demoStore.project)),

  http.get(`${base}/members`, () => HttpResponse.json(demoStore.members)),

  http.get(`${base}/milestones`, () => HttpResponse.json(demoStore.milestones)),

  http.post(`${base}/milestones`, async ({ request }) => {
    const body = (await request.json()) as { name: string; description?: string; deadline?: string };

    const milestone: MilestoneResponse = {
      id: uniqueId('demo-milestone'),
      friendlyId: `MIL-D${demoStore.milestones.length + 1}`,
      projectId: DEMO_PROJECT_ID,
      name: body.name,
      description: body.description ?? null,
      deadline: body.deadline ?? null,
      createdAt: new Date().toISOString(),
    };
    demoStore.milestones.push(milestone);

    return HttpResponse.json(milestone, { status: 201 });
  }),

  http.put(`${base}/milestones/:milestoneId`, async ({ params, request }) => {
    const milestone = demoStore.milestones.find((m) => m.id === params.milestoneId);
    if (!milestone) return new HttpResponse(null, { status: 404 });
    const body = (await request.json()) as { name: string; description?: string; deadline?: string };

    milestone.name = body.name;
    milestone.description = body.description ?? null;
    milestone.deadline = body.deadline ?? null;

    return HttpResponse.json(milestone);
  }),

  http.delete(`${base}/milestones/:milestoneId`, ({ params }) => {
    const index = demoStore.milestones.findIndex((m) => m.id === params.milestoneId);
    if (index === -1) return new HttpResponse(null, { status: 404 });
    const [removed] = demoStore.milestones.splice(index, 1);

    // Unassign any job that pointed at the deleted milestone, matching the real
    // backend's ON DELETE SET NULL behavior for job.milestone_id.
    for (const job of demoStore.jobs) {
      if (job.milestoneId === removed.id) {
        job.milestoneId = null;
        job.milestoneName = null;
      }
    }

    return new HttpResponse(null, { status: 204 });
  }),

  // Not part of the mock dataset yet (templates/recurring get their own smaller slice
  // in a later job per ADR-0040) — an empty list is enough to satisfy any real
  // component that happens to fetch it (e.g. a "create from template" dropdown)
  // without an unhandled-request error.
  http.get(`${base}/templates`, () => HttpResponse.json([])),

  http.get(`${base}/jobs`, ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get('q')?.toLowerCase();
    const priority = url.searchParams.get('priority');
    const milestoneId = url.searchParams.get('milestoneId');

    let jobs = demoStore.jobs;
    if (q) jobs = jobs.filter((j) => j.title.toLowerCase().includes(q));
    if (priority) jobs = jobs.filter((j) => j.priority === priority);
    if (milestoneId) jobs = jobs.filter((j) => j.milestoneId === milestoneId);

    return HttpResponse.json(jobs);
  }),

  http.post(`${base}/jobs`, async ({ request }) => {
    const body = (await request.json()) as {
      title: string;
      description?: string;
      client?: string;
      assignedTo?: string;
      deadline?: string;
      priority?: JobPriority;
      milestoneId?: string;
    };

    const assignee = body.assignedTo ? demoStore.members.find((m) => m.userId === body.assignedTo) : undefined;
    const milestone = body.milestoneId ? demoStore.milestones.find((m) => m.id === body.milestoneId) : undefined;
    const now = new Date().toISOString();

    const job: JobResponse = {
      id: uniqueId('demo-job'),
      friendlyId: `DEMO-${100 + demoStore.jobs.length + 1}`,
      projectId: DEMO_PROJECT_ID,
      title: body.title,
      description: body.description ?? null,
      client: body.client ?? null,
      assignedTo: body.assignedTo ?? null,
      assignedToName: assignee?.userName ?? null,
      deadline: body.deadline ?? null,
      status: 'NEW',
      priority: body.priority ?? 'MEDIUM',
      createdBy: DEMO_CURRENT_USER.id,
      createdAt: now,
      updatedAt: now,
      blockedBy: null,
      blockedReason: null,
      blockedAt: null,
      milestoneId: milestone?.id ?? null,
      milestoneName: milestone?.name ?? null,
      relationships: [],
      links: [],
      sourceScheduleId: null,
    };
    demoStore.jobs.push(job);

    return HttpResponse.json(job, { status: 201 });
  }),

  http.get(`${base}/jobs/:jobId`, ({ params }) => {
    const jobId = resolveJobId(params.jobId as string);
    const job = demoStore.jobs.find((j) => j.id === jobId);
    if (!job) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(job);
  }),

  http.get(`${base}/jobs/:jobId/notes`, ({ params }) => {
    const jobId = resolveJobId(params.jobId as string);
    const notes = jobId ? demoStore.notesByJobId[jobId] ?? [] : [];
    return HttpResponse.json(notes);
  }),

  http.post(`${base}/jobs/:jobId/notes`, async ({ params, request }) => {
    const jobId = resolveJobId(params.jobId as string);
    if (!jobId) return new HttpResponse(null, { status: 404 });
    const body = (await request.json()) as { content: string };

    const note: NoteResponse = {
      id: uniqueId(`demo-note-${jobId}`),
      jobId,
      authorId: DEMO_CURRENT_USER.id,
      content: body.content,
      createdAt: new Date().toISOString(),
    };
    demoStore.notesByJobId[jobId] = [...(demoStore.notesByJobId[jobId] ?? []), note];

    return HttpResponse.json(note, { status: 201 });
  }),

  http.patch(`${base}/jobs/:jobId/status`, async ({ params, request }) => {
    const jobId = resolveJobId(params.jobId as string);
    const job = demoStore.jobs.find((j) => j.id === jobId);
    if (!job) return new HttpResponse(null, { status: 404 });
    const body = (await request.json()) as { status: JobStatus; reason?: string };

    const previousStatus = job.status;
    job.status = body.status;
    job.updatedAt = new Date().toISOString();
    if (body.status === 'BLOCKED') {
      job.blockedBy = DEMO_CURRENT_USER.id;
      job.blockedReason = body.reason ?? null;
      job.blockedAt = job.updatedAt;
    } else {
      job.blockedBy = null;
      job.blockedReason = null;
      job.blockedAt = null;
    }

    const entry: JobHistoryEntry = {
      id: uniqueId(`demo-hist-${job.id}`),
      jobId: job.id,
      changedFrom: previousStatus,
      changedTo: body.status,
      changedBy: DEMO_CURRENT_USER.id,
      changedByName: DEMO_CURRENT_USER.name,
      changedAt: job.updatedAt,
      blockReason: body.status === 'BLOCKED' ? body.reason ?? null : null,
    };
    demoStore.historyByJobId[job.id] = [...(demoStore.historyByJobId[job.id] ?? []), entry];

    return HttpResponse.json(job);
  }),

  http.put(`${base}/jobs/:jobId`, async ({ params, request }) => {
    const jobId = resolveJobId(params.jobId as string);
    const job = demoStore.jobs.find((j) => j.id === jobId);
    if (!job) return new HttpResponse(null, { status: 404 });
    const body = (await request.json()) as Partial<{
      title: string;
      description: string;
      client: string;
      assignedTo: string;
      deadline: string;
      priority: JobPriority;
      milestoneId: string;
    }>;

    Object.assign(job, body);
    job.updatedAt = new Date().toISOString();

    return HttpResponse.json(job);
  }),

  http.delete(`${base}/jobs/:jobId`, ({ params }) => {
    const jobId = resolveJobId(params.jobId as string);
    const index = demoStore.jobs.findIndex((j) => j.id === jobId);
    if (index === -1) return new HttpResponse(null, { status: 404 });
    demoStore.jobs.splice(index, 1);
    return new HttpResponse(null, { status: 204 });
  }),

  http.post(`${base}/jobs/:jobId/relationships`, async ({ params, request }) => {
    const jobId = resolveJobId(params.jobId as string);
    const job = demoStore.jobs.find((j) => j.id === jobId);
    if (!job || !jobId) return new HttpResponse(null, { status: 404 });
    const body = (await request.json()) as { targetJobId: string; type: JobRelationshipType };

    const targetJob = demoStore.jobs.find((j) => j.id === body.targetJobId);
    if (!targetJob) return new HttpResponse(null, { status: 404 });

    const pairId = uniqueId('demo-rel');
    const outgoing: JobRelationshipView = {
      id: `${pairId}-out`,
      type: body.type,
      direction: 'OUTGOING',
      job: { id: targetJob.id, friendlyId: targetJob.friendlyId, title: targetJob.title, status: targetJob.status },
    };
    const incoming: JobRelationshipView = {
      id: `${pairId}-in`,
      type: body.type,
      direction: 'INCOMING',
      job: { id: job.id, friendlyId: job.friendlyId, title: job.title, status: job.status },
    };
    job.relationships = [...job.relationships, outgoing];
    targetJob.relationships = [...targetJob.relationships, incoming];

    return HttpResponse.json(outgoing, { status: 201 });
  }),

  http.delete(`${base}/jobs/:jobId/relationships/:relationshipId`, ({ params }) => {
    const jobId = resolveJobId(params.jobId as string);
    const job = demoStore.jobs.find((j) => j.id === jobId);
    const { relationshipId } = params as { relationshipId: string };
    if (!job) return new HttpResponse(null, { status: 404 });

    const relIndex = job.relationships.findIndex((r) => r.id === relationshipId);
    if (relIndex === -1) return new HttpResponse(null, { status: 404 });
    const [removed] = job.relationships.splice(relIndex, 1);

    // Remove the reciprocal entry on the other side (the one pointing back at this
    // job with the same type) — reasonable for our seed data, where a job never has
    // more than one relationship of a given type pointing at the same other job.
    const otherJob = demoStore.jobs.find((j) => j.id === removed.job.id);
    if (otherJob) {
      otherJob.relationships = otherJob.relationships.filter(
        (r) => !(r.job.id === job.id && r.type === removed.type),
      );
    }

    return new HttpResponse(null, { status: 204 });
  }),

  http.get(`${base}/jobs/:jobId/history`, ({ params }) => {
    const jobId = resolveJobId(params.jobId as string);
    const history = jobId ? demoStore.historyByJobId[jobId] ?? [] : [];
    return HttpResponse.json(history);
  }),

  http.get(`${base}/jobs/:jobId/approvals`, ({ params }) => {
    const jobId = resolveJobId(params.jobId as string);
    const approvals = demoStore.approvals.filter((a) => a.jobId === jobId).map(toApprovalResponse);
    return HttpResponse.json(approvals);
  }),

  http.post(`${base}/jobs/:jobId/approvals`, async ({ params, request }) => {
    const jobId = resolveJobId(params.jobId as string);
    const job = demoStore.jobs.find((j) => j.id === jobId);
    if (!job || !jobId) return new HttpResponse(null, { status: 404 });
    const body = (await request.json()) as { description: string };

    const approval: DemoApproval = {
      id: uniqueId(`demo-approval-${jobId}`),
      jobId,
      jobTitle: job.title,
      requesterId: DEMO_CURRENT_USER.id,
      approverId: null,
      description: body.description,
      status: 'PENDING',
      comment: null,
      requestedAt: new Date().toISOString(),
      decidedAt: null,
    };
    demoStore.approvals.push(approval);

    return HttpResponse.json(toApprovalResponse(approval), { status: 201 });
  }),

  http.get(`${base}/approvals/pending`, () => {
    const pending: PendingApprovalResponse[] = demoStore.approvals
      .filter((a) => a.status === 'PENDING')
      .map(({ id, jobId, jobTitle, requesterId, description, requestedAt }) => ({
        id,
        jobId,
        jobTitle,
        requesterId,
        description,
        requestedAt,
      }));
    return HttpResponse.json(pending);
  }),

  http.patch(`${base}/jobs/:jobId/approvals/:approvalId/status`, async ({ params, request }) => {
    const jobId = resolveJobId(params.jobId as string);
    const { approvalId } = params as { approvalId: string };
    if (!jobId) return new HttpResponse(null, { status: 404 });
    const body = (await request.json()) as { status: ApprovalStatus; comment?: string };

    const approval = demoStore.approvals.find((a) => a.id === approvalId);
    if (!approval || approval.status !== 'PENDING') {
      return HttpResponse.json(
        { error: 'Conflict', message: 'Already decided.', timestamp: new Date().toISOString() },
        { status: 409 },
      );
    }

    approval.status = body.status;
    approval.comment = body.comment ?? null;
    approval.approverId = DEMO_CURRENT_USER.id;
    approval.decidedAt = new Date().toISOString();

    return HttpResponse.json(toApprovalResponse(approval));
  }),
];
