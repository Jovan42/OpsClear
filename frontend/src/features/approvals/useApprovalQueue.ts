import { useQuery } from '@tanstack/react-query';
import { approvalsApi } from '../../api/approvals';

export function useApprovalQueue(projectId: string) {
  return useQuery({
    queryKey: ['approvals', projectId, 'pending'],
    queryFn: () => approvalsApi.listPending(projectId),
    staleTime: 5 * 60 * 1000,
    enabled: !!projectId,
  });
}
