import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notesApi } from '../../api/notes';
import { useProject } from '../projects/useProjects';
import { useSectionVisibility } from './useSectionVisibility';

export function useNotes(projectId: string, jobId: string) {
  const { data: project } = useProject(projectId);
  const { isVisible } = useSectionVisibility(project);
  return useQuery({
    queryKey: ['jobs', projectId, jobId, 'notes'],
    queryFn: () => notesApi.listByJob(projectId, jobId),
    enabled: !!projectId && !!jobId && isVisible('NOTES'),
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
