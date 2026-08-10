import { http, HttpResponse, passthrough } from 'msw';
import {
  DEMO_BASE_PROJECT_ID,
  DEMO_CURRENT_USER,
  DEMO_ORG_ID,
  DEMO_PROJECT_ID,
  demoStore,
  ORG_NAME,
  type DemoApproval,
} from './mockData';
import { detectPreset, parseCronParams } from '../utils/cron';
import type {
  ApiKeyResponse,
  ApprovalResponse,
  ApprovalStatus,
  AssigneeMode,
  CreateApiKeyResponse,
  CronPreviewResponse,
  DashboardResponse,
  FeedbackSubmissionResponse,
  FeedbackType,
  JobHistoryEntry,
  JobPriority,
  JobRelationshipType,
  JobRelationshipView,
  JobResponse,
  JobStatus,
  JobTemplateResponse,
  JobTypeColor,
  JobTypeResponse,
  LinkResponse,
  MilestoneResponse,
  NoteResponse,
  PendingApprovalResponse,
  RecurringScheduleResponse,
  ScheduleStatus,
} from '../types';

/**
 * Handlers matched against the shared mock dataset (ADR-0040). Every path is prefixed
 * with the exact literal DEMO_PROJECT_ID rather than a `:projectId` wildcard param —
 * a real logged-in user's real project ID can never collide with or be intercepted by
 * these, even if a real app tab happened to be open in the same browser at the same time.
 */
const base = `*/api/projects/${DEMO_PROJECT_ID}`;

/**
 * The Job tracking card's dedicated, deliberately milestone-free project (JOB-146
 * polish) — see DEMO_BASE_PROJECT_ID in mockData.ts for why it's separate from the
 * shared dataset every other card uses.
 */
const trackingBase = `*/api/projects/${DEMO_BASE_PROJECT_ID}`;

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

function resolveTrackingJobId(idOrFriendlyId: string): string | null {
  const job = demoStore.trackingJobs.find((j) => j.id === idOrFriendlyId || j.friendlyId === idOrFriendlyId);
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

const DOW_INDEX: Record<string, number> = { SUN: 0, MON: 1, TUE: 2, WED: 3, THU: 4, FRI: 5, SAT: 6 };

/**
 * Best-effort mock cron preview — the real backend uses a proper cron engine, which
 * isn't worth pulling into the browser bundle just for this demo's preview panel.
 * Good enough to show plausible upcoming dates for the daily/weekly/monthly presets
 * ScheduleFormModal actually offers; 'advanced' free-form cron falls back to a daily
 * cadence at the parsed time, which is a reasonable approximation for a mock.
 */
function computeNextRuns(cronExpression: string, count = 5): string[] {
  const preset = detectPreset(cronExpression);
  const { time, dow, dom } = parseCronParams(cronExpression);
  const [hh, mm] = time.split(':').map((n) => parseInt(n, 10));
  const runs: string[] = [];

  if (preset === 'weekly') {
    const targetDow = DOW_INDEX[dow] ?? 1;
    const cursor = new Date();
    cursor.setHours(hh, mm, 0, 0);
    while (runs.length < count) {
      cursor.setDate(cursor.getDate() + 1);
      if (cursor.getDay() === targetDow) runs.push(cursor.toISOString());
    }
  } else if (preset === 'monthly') {
    const cursor = new Date();
    cursor.setDate(dom);
    cursor.setHours(hh, mm, 0, 0);
    if (cursor.getTime() <= Date.now()) cursor.setMonth(cursor.getMonth() + 1);
    for (let i = 0; i < count; i += 1) {
      runs.push(cursor.toISOString());
      cursor.setMonth(cursor.getMonth() + 1);
    }
  } else {
    const cursor = new Date();
    cursor.setHours(hh, mm, 0, 0);
    if (cursor.getTime() <= Date.now()) cursor.setDate(cursor.getDate() + 1);
    for (let i = 0; i < count; i += 1) {
      runs.push(cursor.toISOString());
      cursor.setDate(cursor.getDate() + 1);
    }
  }
  return runs;
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
  // The demo worker runs with `onUnhandledRequest: 'error'` (see browser.ts) so a
  // genuine mock-data gap fails loudly rather than silently hitting a real backend —
  // but that also blocks legitimate external requests that aren't OpsClear API calls,
  // like LinkIcon's <img> favicon fetch for a link whose host isn't one of the
  // hardcoded known services (e.g. youtube.com, google.com fall through to Google's
  // favicon service). Explicitly pass those through to the real network instead of
  // tightening the global setting.
  http.get('https://www.google.com/s2/favicons*', () => passthrough()),

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

  http.get(`${base}/dashboard`, () => {
    const now = Date.now();
    const isOverdue = (j: JobResponse) => j.status !== 'COMPLETED' && !!j.deadline && new Date(j.deadline).getTime() < now;
    const toSummary = (j: JobResponse) => ({
      id: j.id,
      title: j.title,
      client: j.client,
      assignedTo: j.assignedTo,
      assignedToName: j.assignedToName,
      deadline: j.deadline,
      status: j.status,
      blockedReason: j.blockedReason,
      blockedAt: j.blockedAt,
      blockedBy: j.blockedBy,
    });

    const jobs = demoStore.jobs;
    const pendingApprovals = demoStore.approvals.filter((a) => a.status === 'PENDING');

    const response: DashboardResponse = {
      summary: {
        total: jobs.length,
        newCount: jobs.filter((j) => j.status === 'NEW').length,
        inProgressCount: jobs.filter((j) => j.status === 'IN_PROGRESS').length,
        blockedCount: jobs.filter((j) => j.status === 'BLOCKED').length,
        completedCount: jobs.filter((j) => j.status === 'COMPLETED').length,
        overdueCount: jobs.filter(isOverdue).length,
        pendingApprovalsCount: pendingApprovals.length,
      },
      blockedJobs: jobs.filter((j) => j.status === 'BLOCKED').map(toSummary),
      overdueJobs: jobs.filter(isOverdue).map(toSummary),
      pendingApprovals: pendingApprovals.map(({ id, jobId, jobTitle, requesterId, description, requestedAt }) => ({
        id,
        jobId,
        jobTitle,
        requesterId,
        description,
        requestedAt,
      })),
      typeBreakdown: [],
    };

    return HttpResponse.json(response);
  }),

  http.get(`${base}/templates`, () => HttpResponse.json(demoStore.templates)),

  http.post(`${base}/templates`, async ({ request }) => {
    const body = (await request.json()) as {
      name: string;
      title?: string;
      description?: string;
      client?: string;
      priority?: JobPriority;
      assigneeMode?: AssigneeMode;
      assigneeId?: string;
      milestoneId?: string;
      deadlineOffsetDays?: number;
    };
    const assignee = body.assigneeId ? demoStore.members.find((m) => m.userId === body.assigneeId) : undefined;
    const milestone = body.milestoneId ? demoStore.milestones.find((m) => m.id === body.milestoneId) : undefined;
    const now = new Date().toISOString();

    const template: JobTemplateResponse = {
      id: uniqueId('demo-template'),
      friendlyId: `TPL-D${demoStore.templates.length + 1}`,
      projectId: DEMO_PROJECT_ID,
      orgId: null,
      scope: 'PROJECT',
      name: body.name,
      title: body.title ?? null,
      description: body.description ?? null,
      client: body.client ?? null,
      priority: body.priority ?? null,
      assigneeMode: body.assigneeMode ?? 'NONE',
      assigneeId: assignee?.userId ?? null,
      assigneeName: assignee?.userName ?? null,
      milestoneId: milestone?.id ?? null,
      milestoneName: milestone?.name ?? null,
      defaultTypeId: null,
      defaultTypeName: null,
      deadlineOffsetDays: body.deadlineOffsetDays ?? null,
      occurrenceCount: 0,
      createdBy: DEMO_CURRENT_USER.id,
      createdAt: now,
      updatedAt: now,
    };
    demoStore.templates.push(template);

    return HttpResponse.json(template, { status: 201 });
  }),

  http.put(`${base}/templates/:templateId`, async ({ params, request }) => {
    const template = demoStore.templates.find((t) => t.id === params.templateId);
    if (!template) return new HttpResponse(null, { status: 404 });
    const body = (await request.json()) as Partial<{
      name: string;
      title: string;
      description: string;
      client: string;
      priority: JobPriority;
      assigneeMode: AssigneeMode;
      assigneeId: string;
      milestoneId: string;
      deadlineOffsetDays: number;
    }>;

    if (body.name !== undefined) template.name = body.name;
    if (body.title !== undefined) template.title = body.title ?? null;
    if (body.description !== undefined) template.description = body.description ?? null;
    if (body.client !== undefined) template.client = body.client ?? null;
    if (body.priority !== undefined) template.priority = body.priority ?? null;
    if (body.assigneeMode !== undefined) template.assigneeMode = body.assigneeMode;
    if (body.assigneeId !== undefined) {
      const assignee = demoStore.members.find((m) => m.userId === body.assigneeId);
      template.assigneeId = assignee?.userId ?? null;
      template.assigneeName = assignee?.userName ?? null;
    }
    if (body.milestoneId !== undefined) {
      const milestone = demoStore.milestones.find((m) => m.id === body.milestoneId);
      template.milestoneId = milestone?.id ?? null;
      template.milestoneName = milestone?.name ?? null;
    }
    if (body.deadlineOffsetDays !== undefined) template.deadlineOffsetDays = body.deadlineOffsetDays ?? null;
    template.updatedAt = new Date().toISOString();

    return HttpResponse.json(template);
  }),

  http.delete(`${base}/templates/:templateId`, ({ params }) => {
    const index = demoStore.templates.findIndex((t) => t.id === params.templateId);
    if (index === -1) return new HttpResponse(null, { status: 404 });
    demoStore.templates.splice(index, 1);
    return new HttpResponse(null, { status: 204 });
  }),

  http.get(`${base}/job-types`, () =>
    HttpResponse.json(demoStore.jobTypes.slice().sort((a, b) => a.displayOrder - b.displayOrder))),

  http.post(`${base}/job-types`, async ({ request }) => {
    const body = (await request.json()) as { name: string; color: JobTypeColor };
    const nextOrder = demoStore.jobTypes.length > 0
      ? Math.max(...demoStore.jobTypes.map((t) => t.displayOrder)) + 1
      : 0;
    const type: JobTypeResponse = {
      id: uniqueId('demo-job-type'),
      projectId: DEMO_PROJECT_ID,
      name: body.name,
      color: body.color,
      displayOrder: nextOrder,
      createdAt: new Date().toISOString(),
    };
    demoStore.jobTypes.push(type);

    return HttpResponse.json(type, { status: 201 });
  }),

  http.put(`${base}/job-types/:typeId`, async ({ params, request }) => {
    const type = demoStore.jobTypes.find((t) => t.id === params.typeId);
    if (!type) return new HttpResponse(null, { status: 404 });
    const body = (await request.json()) as { name: string; color: JobTypeColor; displayOrder: number };
    type.name = body.name;
    type.color = body.color;
    type.displayOrder = body.displayOrder;

    return HttpResponse.json(type);
  }),

  http.delete(`${base}/job-types/:typeId`, ({ params }) => {
    const index = demoStore.jobTypes.findIndex((t) => t.id === params.typeId);
    if (index === -1) return new HttpResponse(null, { status: 404 });
    demoStore.jobTypes.splice(index, 1);
    return new HttpResponse(null, { status: 204 });
  }),

  http.get(`${base}/schedules`, () => HttpResponse.json(demoStore.schedules)),

  http.get(`${base}/schedules/:scheduleId`, ({ params }) => {
    const schedule = demoStore.schedules.find((s) => s.id === params.scheduleId);
    if (!schedule) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(schedule);
  }),

  http.post(`${base}/schedules`, async ({ request }) => {
    const body = (await request.json()) as {
      name: string;
      templateId: string;
      cronExpression: string;
      timezone: string;
      pausedUntil?: string | null;
      expiresAt?: string | null;
      assigneeIds: string[];
    };
    const template = demoStore.templates.find((t) => t.id === body.templateId);
    const assignees = body.assigneeIds
      .map((userId, order) => {
        const member = demoStore.members.find((m) => m.userId === userId);
        return member ? { userId: member.userId, userName: member.userName, order } : null;
      })
      .filter((a): a is { userId: string; userName: string; order: number } => a !== null);
    const now = new Date().toISOString();

    const schedule: RecurringScheduleResponse = {
      id: uniqueId('demo-schedule'),
      projectId: DEMO_PROJECT_ID,
      templateId: body.templateId,
      templateName: template?.name ?? null,
      name: body.name,
      cronExpression: body.cronExpression,
      timezone: body.timezone,
      pausedUntil: body.pausedUntil ?? null,
      expiresAt: body.expiresAt ?? null,
      currentRotationIndex: 0,
      nextRunAt: computeNextRuns(body.cronExpression, 1)[0],
      lastRunAt: null,
      assignees,
      status: assignees.length > 0 ? 'ACTIVE' : 'PAUSED_NO_ASSIGNEES',
      createdAt: now,
      updatedAt: now,
    };
    demoStore.schedules.push(schedule);
    demoStore.missedRunsByScheduleId[schedule.id] = [];

    return HttpResponse.json(schedule, { status: 201 });
  }),

  http.put(`${base}/schedules/:scheduleId`, async ({ params, request }) => {
    const schedule = demoStore.schedules.find((s) => s.id === params.scheduleId);
    if (!schedule) return new HttpResponse(null, { status: 404 });
    const body = (await request.json()) as {
      name: string;
      templateId: string;
      cronExpression: string;
      timezone: string;
      pausedUntil?: string | null;
      expiresAt?: string | null;
      assigneeIds: string[];
    };
    const template = demoStore.templates.find((t) => t.id === body.templateId);
    const assignees = body.assigneeIds
      .map((userId, order) => {
        const member = demoStore.members.find((m) => m.userId === userId);
        return member ? { userId: member.userId, userName: member.userName, order } : null;
      })
      .filter((a): a is { userId: string; userName: string; order: number } => a !== null);

    schedule.name = body.name;
    schedule.templateId = body.templateId;
    schedule.templateName = template?.name ?? null;
    schedule.cronExpression = body.cronExpression;
    schedule.timezone = body.timezone;
    schedule.pausedUntil = body.pausedUntil ?? null;
    schedule.expiresAt = body.expiresAt ?? null;
    schedule.nextRunAt = computeNextRuns(body.cronExpression, 1)[0];
    schedule.assignees = assignees;
    if (schedule.status !== 'PAUSED') {
      schedule.status = assignees.length > 0 ? 'ACTIVE' : 'PAUSED_NO_ASSIGNEES';
    }
    schedule.updatedAt = new Date().toISOString();

    return HttpResponse.json(schedule);
  }),

  http.delete(`${base}/schedules/:scheduleId`, ({ params }) => {
    const index = demoStore.schedules.findIndex((s) => s.id === params.scheduleId);
    if (index === -1) return new HttpResponse(null, { status: 404 });
    demoStore.schedules.splice(index, 1);
    delete demoStore.missedRunsByScheduleId[params.scheduleId as string];
    return new HttpResponse(null, { status: 204 });
  }),

  http.post(`${base}/schedules/:scheduleId/pause`, async ({ params, request }) => {
    const schedule = demoStore.schedules.find((s) => s.id === params.scheduleId);
    if (!schedule) return new HttpResponse(null, { status: 404 });
    const body = (await request.json()) as { until?: string | null };

    schedule.status = 'PAUSED' as ScheduleStatus;
    schedule.pausedUntil = body.until ?? null;
    schedule.updatedAt = new Date().toISOString();

    return HttpResponse.json(schedule);
  }),

  http.post(`${base}/schedules/:scheduleId/resume`, ({ params }) => {
    const schedule = demoStore.schedules.find((s) => s.id === params.scheduleId);
    if (!schedule) return new HttpResponse(null, { status: 404 });

    schedule.pausedUntil = null;
    schedule.status = schedule.assignees.length > 0 ? 'ACTIVE' : 'PAUSED_NO_ASSIGNEES';
    schedule.updatedAt = new Date().toISOString();

    return HttpResponse.json(schedule);
  }),

  http.get(`${base}/schedules/:scheduleId/missed-runs`, ({ params }) => {
    const runs = demoStore.missedRunsByScheduleId[params.scheduleId as string] ?? [];
    return HttpResponse.json(runs);
  }),

  http.post(`${base}/schedules/:scheduleId/missed-runs/:missedRunId/materialize`, ({ params }) => {
    const { scheduleId, missedRunId } = params as { scheduleId: string; missedRunId: string };
    const schedule = demoStore.schedules.find((s) => s.id === scheduleId);
    const runs = demoStore.missedRunsByScheduleId[scheduleId] ?? [];
    const runIndex = runs.findIndex((r) => r.id === missedRunId);
    if (!schedule || runIndex === -1) return new HttpResponse(null, { status: 404 });
    runs.splice(runIndex, 1);

    const template = demoStore.templates.find((t) => t.id === schedule.templateId);
    const now = new Date().toISOString();
    const job: JobResponse = {
      id: uniqueId('demo-job'),
      friendlyId: `DEMO-${100 + demoStore.jobs.length + 1}`,
      projectId: DEMO_PROJECT_ID,
      title: template?.title ?? template?.name ?? schedule.name,
      description: template?.description ?? null,
      client: template?.client ?? null,
      assignedTo: schedule.assignees[0]?.userId ?? null,
      assignedToName: schedule.assignees[0]?.userName ?? null,
      deadline: null,
      status: 'NEW',
      priority: template?.priority ?? 'MEDIUM',
      createdBy: DEMO_CURRENT_USER.id,
      createdAt: now,
      updatedAt: now,
      blockedBy: null,
      blockedReason: null,
      blockedAt: null,
      milestoneId: template?.milestoneId ?? null,
      milestoneName: template?.milestoneName ?? null,
      typeId: null,
      typeName: null,
      typeColor: null,
      relationships: [],
      links: [],
      sourceScheduleId: schedule.id,
    };
    demoStore.jobs.push(job);

    return HttpResponse.json(job, { status: 201 });
  }),

  http.delete(`${base}/schedules/:scheduleId/missed-runs/:missedRunId`, ({ params }) => {
    const { scheduleId, missedRunId } = params as { scheduleId: string; missedRunId: string };
    const runs = demoStore.missedRunsByScheduleId[scheduleId] ?? [];
    const index = runs.findIndex((r) => r.id === missedRunId);
    if (index === -1) return new HttpResponse(null, { status: 404 });
    runs.splice(index, 1);
    return new HttpResponse(null, { status: 204 });
  }),

  http.delete(`${base}/schedules/:scheduleId/missed-runs`, ({ params }) => {
    demoStore.missedRunsByScheduleId[params.scheduleId as string] = [];
    return new HttpResponse(null, { status: 204 });
  }),

  http.post('*/api/schedules/preview', async ({ request }) => {
    const body = (await request.json()) as { cronExpression: string; timezone: string };
    const response: CronPreviewResponse = { nextRuns: computeNextRuns(body.cronExpression, 5) };
    return HttpResponse.json(response);
  }),

  http.get('*/api/user/api-keys', () => HttpResponse.json(demoStore.apiKeys)),

  http.post('*/api/user/api-keys', async ({ request }) => {
    const body = (await request.json()) as { name: string };
    const now = new Date().toISOString();
    const keyPrefix = uniqueId('opck_demo').slice(0, 16);

    const key: ApiKeyResponse = {
      id: uniqueId('demo-apikey'),
      name: body.name,
      keyPrefix,
      createdAt: now,
      lastUsedAt: null,
      expiresAt: null,
      revokedAt: null,
    };
    demoStore.apiKeys.push(key);

    const response: CreateApiKeyResponse = {
      id: key.id,
      name: key.name,
      key: `${keyPrefix}_${Math.random().toString(36).slice(2, 26)}`,
      keyPrefix,
      createdAt: key.createdAt,
      expiresAt: null,
    };
    return HttpResponse.json(response, { status: 201 });
  }),

  http.delete('*/api/user/api-keys/:keyId', ({ params }) => {
    const index = demoStore.apiKeys.findIndex((k) => k.id === params.keyId);
    if (index === -1) return new HttpResponse(null, { status: 404 });
    demoStore.apiKeys.splice(index, 1);
    return new HttpResponse(null, { status: 204 });
  }),

  http.get('*/api/feedback/mine', () => HttpResponse.json(demoStore.feedbackSubmissions)),

  http.post('*/api/feedback', async ({ request }) => {
    const body = (await request.json()) as { type: FeedbackType; title: string; description: string };
    const submission: FeedbackSubmissionResponse = {
      id: uniqueId('demo-feedback'),
      orgId: DEMO_ORG_ID,
      orgName: ORG_NAME,
      submittedBy: DEMO_CURRENT_USER.id,
      submitterName: DEMO_CURRENT_USER.name,
      submitterEmail: DEMO_CURRENT_USER.email,
      type: body.type,
      title: body.title,
      description: body.description,
      status: 'PENDING',
      createdAt: new Date().toISOString(),
    };
    // Newest first, matching the real backend's findBySubmittedBy ordering.
    demoStore.feedbackSubmissions.unshift(submission);
    return HttpResponse.json(submission, { status: 201 });
  }),

  http.get(`${base}/jobs`, ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get('q')?.toLowerCase();
    const priority = url.searchParams.get('priority');
    const milestoneId = url.searchParams.get('milestoneId');
    const typeId = url.searchParams.get('typeId');

    let jobs = demoStore.jobs;
    if (q) jobs = jobs.filter((j) => j.title.toLowerCase().includes(q));
    if (priority) jobs = jobs.filter((j) => j.priority === priority);
    if (milestoneId) jobs = jobs.filter((j) => j.milestoneId === milestoneId);
    if (typeId) jobs = jobs.filter((j) => j.typeId === typeId);

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
      typeId?: string;
    };

    const assignee = body.assignedTo ? demoStore.members.find((m) => m.userId === body.assignedTo) : undefined;
    const milestone = body.milestoneId ? demoStore.milestones.find((m) => m.id === body.milestoneId) : undefined;
    const type = body.typeId ? demoStore.jobTypes.find((t) => t.id === body.typeId) : undefined;
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
      typeId: type?.id ?? null,
      typeName: type?.name ?? null,
      typeColor: type?.color ?? null,
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
      typeId: string;
    }>;

    // A PUT is a full replace (matches the real backend) — milestoneId/typeId are
    // resolved to their derived name/color fields explicitly rather than via a blind
    // Object.assign, which would set the *Id field but leave the old *Name/*Color
    // stale (or wrongly keep them when the id was cleared).
    const { milestoneId, typeId, ...rest } = body;
    Object.assign(job, rest);
    if ('milestoneId' in body) {
      const milestone = milestoneId ? demoStore.milestones.find((m) => m.id === milestoneId) : undefined;
      job.milestoneId = milestone?.id ?? null;
      job.milestoneName = milestone?.name ?? null;
    }
    if ('typeId' in body) {
      const type = typeId ? demoStore.jobTypes.find((t) => t.id === typeId) : undefined;
      job.typeId = type?.id ?? null;
      job.typeName = type?.name ?? null;
      job.typeColor = type?.color ?? null;
    }
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

  http.post(`${base}/jobs/:jobId/links`, async ({ params, request }) => {
    const jobId = resolveJobId(params.jobId as string);
    const job = demoStore.jobs.find((j) => j.id === jobId);
    if (!job) return new HttpResponse(null, { status: 404 });
    const body = (await request.json()) as { url: string; label?: string };
    const now = new Date().toISOString();

    const link: LinkResponse = {
      id: uniqueId('demo-link'),
      url: body.url,
      label: body.label ?? null,
      createdBy: DEMO_CURRENT_USER.id,
      createdAt: now,
      updatedAt: now,
    };
    job.links = [...job.links, link];

    return HttpResponse.json(link, { status: 201 });
  }),

  http.put(`${base}/jobs/:jobId/links/:linkId`, async ({ params, request }) => {
    const jobId = resolveJobId(params.jobId as string);
    const job = demoStore.jobs.find((j) => j.id === jobId);
    if (!job) return new HttpResponse(null, { status: 404 });
    const link = job.links.find((l) => l.id === params.linkId);
    if (!link) return new HttpResponse(null, { status: 404 });
    const body = (await request.json()) as { url: string; label?: string };

    link.url = body.url;
    link.label = body.label ?? null;
    link.updatedAt = new Date().toISOString();

    return HttpResponse.json(link);
  }),

  http.delete(`${base}/jobs/:jobId/links/:linkId`, ({ params }) => {
    const jobId = resolveJobId(params.jobId as string);
    const job = demoStore.jobs.find((j) => j.id === jobId);
    if (!job) return new HttpResponse(null, { status: 404 });
    const index = job.links.findIndex((l) => l.id === params.linkId);
    if (index === -1) return new HttpResponse(null, { status: 404 });
    job.links.splice(index, 1);
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

  // ---- Job tracking demo's dedicated project (JOB-146 polish) ----
  // Deliberately no milestones endpoint returning real data — GET always returns []
  // (see DEMO_BASE_PROJECT_ID in mockData.ts), and no notes/history/relationships/
  // approvals/links handlers are needed here at all: this card runs under
  // mockOrgStateNoAddons, so JobDetailPage never renders those sections and never
  // fetches them — except useApprovals, which fires unconditionally regardless of
  // the addon (only the accordion's *rendering* is gated), so that one still needs
  // a handler to avoid an unhandled-request error.

  http.get(trackingBase, () => HttpResponse.json(demoStore.trackingProject)),

  http.get(`${trackingBase}/members`, () => HttpResponse.json(demoStore.members)),

  http.get(`${trackingBase}/milestones`, () => HttpResponse.json([])),

  http.get(`${trackingBase}/job-types`, () =>
    HttpResponse.json(demoStore.trackingJobTypes.slice().sort((a, b) => a.displayOrder - b.displayOrder))),

  http.post(`${trackingBase}/job-types`, async ({ request }) => {
    const body = (await request.json()) as { name: string; color: JobTypeColor };
    const nextOrder = demoStore.trackingJobTypes.length > 0
      ? Math.max(...demoStore.trackingJobTypes.map((t) => t.displayOrder)) + 1
      : 0;
    const type: JobTypeResponse = {
      id: uniqueId('demo-basejob-type'),
      projectId: DEMO_BASE_PROJECT_ID,
      name: body.name,
      color: body.color,
      displayOrder: nextOrder,
      createdAt: new Date().toISOString(),
    };
    demoStore.trackingJobTypes.push(type);

    return HttpResponse.json(type, { status: 201 });
  }),

  http.put(`${trackingBase}/job-types/:typeId`, async ({ params, request }) => {
    const type = demoStore.trackingJobTypes.find((t) => t.id === params.typeId);
    if (!type) return new HttpResponse(null, { status: 404 });
    const body = (await request.json()) as { name: string; color: JobTypeColor; displayOrder: number };
    type.name = body.name;
    type.color = body.color;
    type.displayOrder = body.displayOrder;

    return HttpResponse.json(type);
  }),

  http.delete(`${trackingBase}/job-types/:typeId`, ({ params }) => {
    const index = demoStore.trackingJobTypes.findIndex((t) => t.id === params.typeId);
    if (index === -1) return new HttpResponse(null, { status: 404 });
    demoStore.trackingJobTypes.splice(index, 1);
    return new HttpResponse(null, { status: 204 });
  }),

  http.get(`${trackingBase}/jobs`, ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get('q')?.toLowerCase();
    const priority = url.searchParams.get('priority');
    const typeId = url.searchParams.get('typeId');

    let jobs = demoStore.trackingJobs;
    if (q) jobs = jobs.filter((j) => j.title.toLowerCase().includes(q));
    if (priority) jobs = jobs.filter((j) => j.priority === priority);
    if (typeId) jobs = jobs.filter((j) => j.typeId === typeId);

    return HttpResponse.json(jobs);
  }),

  http.post(`${trackingBase}/jobs`, async ({ request }) => {
    const body = (await request.json()) as {
      title: string;
      description?: string;
      client?: string;
      assignedTo?: string;
      deadline?: string;
      priority?: JobPriority;
      typeId?: string;
    };

    const assignee = body.assignedTo ? demoStore.members.find((m) => m.userId === body.assignedTo) : undefined;
    const type = body.typeId ? demoStore.trackingJobTypes.find((t) => t.id === body.typeId) : undefined;
    const now = new Date().toISOString();

    const job: JobResponse = {
      id: uniqueId('demo-basejob'),
      friendlyId: `BASE-${200 + demoStore.trackingJobs.length + 1}`,
      projectId: DEMO_BASE_PROJECT_ID,
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
      milestoneId: null,
      milestoneName: null,
      typeId: type?.id ?? null,
      typeName: type?.name ?? null,
      typeColor: type?.color ?? null,
      relationships: [],
      links: [],
      sourceScheduleId: null,
    };
    demoStore.trackingJobs.push(job);

    return HttpResponse.json(job, { status: 201 });
  }),

  http.get(`${trackingBase}/jobs/:jobId`, ({ params }) => {
    const jobId = resolveTrackingJobId(params.jobId as string);
    const job = demoStore.trackingJobs.find((j) => j.id === jobId);
    if (!job) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(job);
  }),

  http.patch(`${trackingBase}/jobs/:jobId/status`, async ({ params, request }) => {
    const jobId = resolveTrackingJobId(params.jobId as string);
    const job = demoStore.trackingJobs.find((j) => j.id === jobId);
    if (!job) return new HttpResponse(null, { status: 404 });
    const body = (await request.json()) as { status: JobStatus; reason?: string };

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

    return HttpResponse.json(job);
  }),

  http.get(`${trackingBase}/jobs/:jobId/approvals`, () => HttpResponse.json([])),
];
