import apiClient from './client';
import type { JobTypeColor, JobTypeResponse } from '../types';

export interface CreateJobTypeBody {
  name: string;
  color: JobTypeColor;
}

export interface UpdateJobTypeBody {
  name: string;
  color: JobTypeColor;
  displayOrder: number;
}

export const jobTypesApi = {
  list: (projectId: string) =>
    apiClient.get<JobTypeResponse[]>(`/api/projects/${projectId}/job-types`).then((r) => r.data),

  create: (projectId: string, body: CreateJobTypeBody) =>
    apiClient.post<JobTypeResponse>(`/api/projects/${projectId}/job-types`, body).then((r) => r.data),

  update: (projectId: string, typeId: string, body: UpdateJobTypeBody) =>
    apiClient.put<JobTypeResponse>(`/api/projects/${projectId}/job-types/${typeId}`, body).then((r) => r.data),

  delete: (projectId: string, typeId: string) =>
    apiClient.delete(`/api/projects/${projectId}/job-types/${typeId}`),
};
