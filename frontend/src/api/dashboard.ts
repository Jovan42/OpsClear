import apiClient from './client';
import type { DashboardResponse } from '../types';

export const dashboardApi = {
  get: (projectId: string) =>
    apiClient
      .get<DashboardResponse>(`/api/projects/${projectId}/dashboard`)
      .then((r) => r.data),
};
