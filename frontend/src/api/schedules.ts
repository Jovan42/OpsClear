import apiClient from './client';
import type { RecurringScheduleResponse, CronPreviewResponse } from '../types';

export interface ScheduleBody {
  name: string;
  templateId: string;
  cronExpression: string;
  timezone: string;
  pausedUntil?: string | null;
  expiresAt?: string | null;
  assigneeIds: string[];
}

export interface PauseBody {
  until?: string | null;
}

export const schedulesApi = {
  list: (projectId: string) =>
    apiClient.get<RecurringScheduleResponse[]>(`/api/projects/${projectId}/schedules`).then((r) => r.data),

  get: (projectId: string, scheduleId: string) =>
    apiClient.get<RecurringScheduleResponse>(`/api/projects/${projectId}/schedules/${scheduleId}`).then((r) => r.data),

  create: (projectId: string, body: ScheduleBody) =>
    apiClient.post<RecurringScheduleResponse>(`/api/projects/${projectId}/schedules`, body).then((r) => r.data),

  update: (projectId: string, scheduleId: string, body: ScheduleBody) =>
    apiClient.put<RecurringScheduleResponse>(`/api/projects/${projectId}/schedules/${scheduleId}`, body).then((r) => r.data),

  delete: (projectId: string, scheduleId: string) =>
    apiClient.delete(`/api/projects/${projectId}/schedules/${scheduleId}`),

  pause: (projectId: string, scheduleId: string, body: PauseBody) =>
    apiClient.post<RecurringScheduleResponse>(`/api/projects/${projectId}/schedules/${scheduleId}/pause`, body).then((r) => r.data),

  resume: (projectId: string, scheduleId: string) =>
    apiClient.post<RecurringScheduleResponse>(`/api/projects/${projectId}/schedules/${scheduleId}/resume`).then((r) => r.data),

  preview: (cronExpression: string, timezone: string) =>
    apiClient
      .post<CronPreviewResponse>(`/api/schedules/preview`, { cronExpression, timezone })
      .then((r) => r.data),
};
