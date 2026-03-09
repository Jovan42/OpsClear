import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notesApi } from '../../api/notes';

export function useNotes(projectId: string, jobId: string) {
  return useQuery({
    queryKey: ['jobs', projectId, jobId, 'notes'],
    queryFn: () => notesApi.listByJob(projectId, jobId),
  });
}

export function useAddNote(projectId: string, jobId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (content: string) => notesApi.create(projectId, jobId, { content }),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId, jobId, 'notes'] }),
  });
}
