import apiClient from './client';
import type { LinkResponse } from '../types';

export const jobLinksApi = {
  create: (projectId: string, jobId: string, body: { url: string; label?: string }) =>
    apiClient
      .post<LinkResponse>(`/api/projects/${projectId}/jobs/${jobId}/links`, body)
      .then((r) => r.data),

  update: (projectId: string, jobId: string, linkId: string, body: { url: string; label?: string }) =>
    apiClient
      .put<LinkResponse>(`/api/projects/${projectId}/jobs/${jobId}/links/${linkId}`, body)
      .then((r) => r.data),

  delete: (projectId: string, jobId: string, linkId: string) =>
    apiClient.delete(`/api/projects/${projectId}/jobs/${jobId}/links/${linkId}`),
};
