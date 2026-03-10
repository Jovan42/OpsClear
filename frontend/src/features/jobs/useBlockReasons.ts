import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { jobsApi } from '../../api/jobs';

export function useBlockReasons(projectId: string, enabled = true) {
  return useQuery({
    queryKey: ['block-reasons', projectId],
    queryFn: () => jobsApi.blockReasons(projectId),
    enabled,
  });
}

export function useDeleteBlockReason(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (reasonId: string) => jobsApi.deleteBlockReason(projectId, reasonId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['block-reasons', projectId] }),
  });
}
