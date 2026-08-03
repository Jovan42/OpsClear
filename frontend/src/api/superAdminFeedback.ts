import apiClient from './client';
import type { FeedbackSubmissionResponse } from '../types';

export const superAdminFeedbackApi = {
  listAll: () =>
    apiClient.get<FeedbackSubmissionResponse[]>('/api/super-admin/feedback').then((r) => r.data),

  decline: (id: string) =>
    apiClient.patch<void>(`/api/super-admin/feedback/${id}/decline`),
};
