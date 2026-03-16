import apiClient from './client';
import type { MilestoneResponse } from '../types';

export const milestonesApi = {
  list: (projectId: string) =>
    apiClient.get<MilestoneResponse[]>(`/api/projects/${projectId}/milestones`).then((r) => r.data),

  create: (projectId: string, body: { name: string; description?: string; deadline?: string }) =>
    apiClient.post<MilestoneResponse>(`/api/projects/${projectId}/milestones`, body).then((r) => r.data),

  update: (projectId: string, milestoneId: string, body: { name: string; description?: string; deadline?: string }) =>
    apiClient.put<MilestoneResponse>(`/api/projects/${projectId}/milestones/${milestoneId}`, body).then((r) => r.data),

  delete: (projectId: string, milestoneId: string) =>
    apiClient.delete(`/api/projects/${projectId}/milestones/${milestoneId}`),
};
