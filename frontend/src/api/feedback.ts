import apiClient from './client';
import type { FeedbackSubmissionResponse, FeedbackType } from '../types';

export const feedbackApi = {
  submit: (body: { type: FeedbackType; title: string; description: string }) =>
    apiClient.post<FeedbackSubmissionResponse>('/api/feedback', body).then((r) => r.data),

  listMine: () =>
    apiClient.get<FeedbackSubmissionResponse[]>('/api/feedback/mine').then((r) => r.data),
};
