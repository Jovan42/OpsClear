import { useQuery } from '@tanstack/react-query';
import { approvalsApi } from '../../api/approvals';
import { useCurrentOrg } from '../org/OrgContext';

export function useApprovalQueue(projectId: string) {
  const { hasAddon } = useCurrentOrg();
  return useQuery({
    queryKey: ['approvals', projectId, 'pending'],
    queryFn: () => approvalsApi.listPending(projectId),
    staleTime: 5 * 60 * 1000,
    enabled: !!projectId && hasAddon('APPROVALS'),
  });
}

export function usePendingApprovalsAcrossOrgs() {
  const { hasAddon } = useCurrentOrg();
  return useQuery({
    queryKey: ['approvals', 'pending', 'cross-org'],
    queryFn: () => approvalsApi.listPendingAcrossOrgs(),
    staleTime: 5 * 60 * 1000,
    enabled: hasAddon('APPROVALS'),
  });
}
