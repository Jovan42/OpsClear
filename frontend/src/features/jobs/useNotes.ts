import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notesApi } from '../../api/notes';
import { useCurrentOrg } from '../org/OrgContext';

export function useNotes(projectId: string, jobId: string) {
  const { hasAddon } = useCurrentOrg();
  return useQuery({
    queryKey: ['jobs', projectId, jobId, 'notes'],
    queryFn: () => notesApi.listByJob(projectId, jobId),
    enabled: !!projectId && !!jobId && hasAddon('NOTES'),
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
