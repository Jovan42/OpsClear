import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { feedbackApi } from '../../api/feedback';
import type { FeedbackType } from '../../types';

export function useMyFeedbackSubmissions() {
  return useQuery({
    queryKey: ['feedback', 'mine'],
    queryFn: feedbackApi.listMine,
  });
}

export function useSubmitFeedback() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { type: FeedbackType; title: string; description: string }) =>
      feedbackApi.submit(body),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['feedback', 'mine'] }),
  });
}
