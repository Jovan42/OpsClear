import apiClient from './client';
import type { NoteResponse } from '../types';

export const notesApi = {
  listByJob: (projectId: string, jobId: string) =>
    apiClient
      .get<NoteResponse[]>(`/api/projects/${projectId}/jobs/${jobId}/notes`)
      .then((r) => r.data),

  create: (projectId: string, jobId: string, body: { content: string }) =>
    apiClient
      .post<NoteResponse>(`/api/projects/${projectId}/jobs/${jobId}/notes`, body)
      .then((r) => r.data),
};
