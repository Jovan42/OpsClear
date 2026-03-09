import { useQuery } from '@tanstack/react-query';
import { jobsApi } from '../../api/jobs';

export function useBlockReasons(projectId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['block-reasons', projectId],
    queryFn: () => jobsApi.blockReasons(projectId),
    enabled,
  });
}
