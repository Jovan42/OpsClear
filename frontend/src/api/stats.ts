import apiClient from './client';
import type { StatsResponse, StatsWeeks } from '../types';

export interface StatsParams {
  weeks: StatsWeeks;
  tz: string;
  memberId?: string;
}

export const statsApi = {
  get: (projectId: string, { weeks, tz, memberId }: StatsParams) =>
    apiClient
      .get<StatsResponse>(`/api/projects/${projectId}/stats`, { params: { weeks, tz, memberId } })
      .then((r) => r.data),
};
