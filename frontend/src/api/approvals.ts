import apiClient from './client';
import type { ApprovalResponse, ApprovalStatus, PendingApprovalResponse } from '../types';

export const approvalsApi = {
  listByJob: (projectId: string, jobId: string) =>
    apiClient
      .get<ApprovalResponse[]>(`/api/projects/${projectId}/jobs/${jobId}/approvals`)
      .then((r) => r.data),

  request: (projectId: string, jobId: string, body: { description: string }) =>
    apiClient
      .post<ApprovalResponse>(`/api/projects/${projectId}/jobs/${jobId}/approvals`, body)
      .then((r) => r.data),

  decide: (
    projectId: string,
    jobId: string,
    approvalId: string,
    body: { status: ApprovalStatus; comment?: string },
  ) =>
    apiClient
      .patch<ApprovalResponse>(
        `/api/projects/${projectId}/jobs/${jobId}/approvals/${approvalId}/status`,
        body,
      )
      .then((r) => r.data),

  listPending: (projectId: string) =>
    apiClient
      .get<PendingApprovalResponse[]>(`/api/projects/${projectId}/approvals/pending`)
      .then((r) => r.data),
};
