import apiClient from './client';
import type { JobPriority, JobResponse, JobStatus } from '../types';

export const jobsApi = {
  list: (projectId: string, q?: string, priority?: JobPriority) =>
    apiClient
      .get<JobResponse[]>(`/api/projects/${projectId}/jobs`, {
        params: { ...(q ? { q } : {}), ...(priority ? { priority } : {}) },
      })
      .then((r) => r.data),

  get: (projectId: string, jobId: string) =>
    apiClient
      .get<JobResponse>(`/api/projects/${projectId}/jobs/${jobId}`)
      .then((r) => r.data),

  create: (
    projectId: string,
    body: { title: string; description?: string; client?: string; assignedTo?: string; deadline?: string; priority?: JobPriority },
  ) =>
    apiClient
      .post<JobResponse>(`/api/projects/${projectId}/jobs`, body)
      .then((r) => r.data),

  update: (
    projectId: string,
    jobId: string,
    body: { title: string; description?: string; client?: string; assignedTo?: string; deadline?: string; priority?: JobPriority },
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
};
