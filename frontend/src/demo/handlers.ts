import { http, HttpResponse } from 'msw';
import { DEMO_CURRENT_USER, DEMO_PROJECT_ID, demoStore } from './mockData';
import type { ApprovalResponse, ApprovalStatus } from '../types';

/**
 * Handlers matched against the shared mock dataset (ADR-0040). Every path is prefixed
 * with the exact literal DEMO_PROJECT_ID rather than a `:projectId` wildcard param —
 * a real logged-in user's real project ID can never collide with or be intercepted by
 * these, even if a real app tab happened to be open in the same browser at the same time.
 */
const base = `*/api/projects/${DEMO_PROJECT_ID}`;

export const demoHandlers = [
  http.get(base, () => HttpResponse.json(demoStore.project)),

  http.get(`${base}/members`, () => HttpResponse.json(demoStore.members)),

  http.get(`${base}/milestones`, () => HttpResponse.json(demoStore.milestones)),

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

  http.get(`${base}/jobs/:jobId`, ({ params }) => {
    const job = demoStore.jobs.find((j) => j.id === params.jobId);
    if (!job) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(job);
  }),

  http.get(`${base}/jobs/:jobId/notes`, ({ params }) => {
    const notes = demoStore.notesByJobId[params.jobId as string] ?? [];
    return HttpResponse.json(notes);
  }),

  http.get(`${base}/jobs/:jobId/history`, ({ params }) => {
    const history = demoStore.historyByJobId[params.jobId as string] ?? [];
    return HttpResponse.json(history);
  }),

  http.get(`${base}/jobs/:jobId/approvals`, ({ params }) => {
    const approvals = demoStore.approvalsByJobId[params.jobId as string] ?? [];
    return HttpResponse.json(approvals);
  }),

  http.get(`${base}/approvals/pending`, () => HttpResponse.json(demoStore.pendingApprovals)),

  http.patch(`${base}/jobs/:jobId/approvals/:approvalId/status`, async ({ params, request }) => {
    const { jobId, approvalId } = params as { jobId: string; approvalId: string };
    const body = (await request.json()) as { status: ApprovalStatus; comment?: string };

    const pendingIndex = demoStore.pendingApprovals.findIndex((a) => a.id === approvalId);
    if (pendingIndex === -1) {
      return HttpResponse.json(
        { error: 'Conflict', message: 'Already decided.', timestamp: new Date().toISOString() },
        { status: 409 },
      );
    }
    const pending = demoStore.pendingApprovals[pendingIndex];
    demoStore.pendingApprovals.splice(pendingIndex, 1);

    const decided: ApprovalResponse = {
      id: pending.id,
      jobId,
      requesterId: pending.requesterId,
      approverId: DEMO_CURRENT_USER.id,
      description: pending.description,
      status: body.status,
      comment: body.comment ?? null,
      requestedAt: pending.requestedAt,
      decidedAt: new Date().toISOString(),
    };
    demoStore.approvalsByJobId[jobId] = [...(demoStore.approvalsByJobId[jobId] ?? []), decided];

    return HttpResponse.json(decided);
  }),
];
