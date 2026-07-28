import apiClient from './client';
import type { JobPriority, JobRelationshipType, JobRelationshipView, JobResponse, JobStatus } from '../types';

export const jobsApi = {
  list: (projectId: string, q?: string, priority?: JobPriority, milestoneId?: string, typeId?: string) =>
    apiClient
      .get<JobResponse[]>(`/api/projects/${projectId}/jobs`, {
        params: {
          ...(q ? { q } : {}),
          ...(priority ? { priority } : {}),
          ...(milestoneId ? { milestoneId } : {}),
          ...(typeId ? { typeId } : {}),
        },
      })
      .then((r) => r.data),

  get: (projectId: string, jobId: string) =>
    apiClient
      .get<JobResponse>(`/api/projects/${projectId}/jobs/${jobId}`)
      .then((r) => r.data),

  create: (
    projectId: string,
    body: { title: string; description?: string; client?: string; assignedTo?: string; deadline?: string; priority?: JobPriority; milestoneId?: string; typeId?: string },
  ) =>
    apiClient
      .post<JobResponse>(`/api/projects/${projectId}/jobs`, body)
      .then((r) => r.data),

  update: (
    projectId: string,
    jobId: string,
    body: { title: string; description?: string; client?: string; assignedTo?: string; deadline?: string; priority?: JobPriority; milestoneId?: string; typeId?: string },
  ) =>
    apiClient
      .put<JobResponse>(`/api/projects/${projectId}/jobs/${jobId}`, body)
      .then((r) => r.data),

  updateStatus: (
    projectId: string,
    jobId: string,
    body: { status: JobStatus; reason?: string },
  ) =>
    apiClient
      .patch<JobResponse>(`/api/projects/${projectId}/jobs/${jobId}/status`, body)
      .then((r) => r.data),

  delete: (projectId: string, jobId: string) =>
    apiClient.delete(`/api/projects/${projectId}/jobs/${jobId}`),

  blockReasons: (projectId: string) =>
    apiClient
      .get<{ id: string; reason: string }[]>(`/api/projects/${projectId}/block-reasons`)
      .then((r) => r.data),

  deleteBlockReason: (projectId: string, reasonId: string) =>
    apiClient.delete(`/api/projects/${projectId}/block-reasons/${reasonId}`),

  createRelationship: (projectId: string, jobId: string, body: { targetJobId: string; type: JobRelationshipType }) =>
    apiClient
      .post<JobRelationshipView>(`/api/projects/${projectId}/jobs/${jobId}/relationships`, body)
      .then((r) => r.data),

  deleteRelationship: (projectId: string, jobId: string, relationshipId: string) =>
    apiClient.delete(`/api/projects/${projectId}/jobs/${jobId}/relationships/${relationshipId}`),

  history: (projectId: string, jobId: string) =>
    apiClient
      .get<import('../types').JobHistoryEntry[]>(`/api/projects/${projectId}/jobs/${jobId}/history`)
      .then((r) => r.data),
};
