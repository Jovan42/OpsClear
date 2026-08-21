import { useQuery } from '@tanstack/react-query';
import { approvalsApi } from '../../api/approvals';
import { useCurrentOrg } from '../org/OrgContext';
import { useProjectRole } from '../projects/useProjects';

export function useApprovalQueue(projectId: string) {
  const { hasAddon } = useCurrentOrg();
  // Gated on role, not just the addon — the backend's pending queue is
  // Owner/Admin-only, so a plain Member would otherwise fire this on mount
  // (before ApprovalQueuePage's role redirect runs) and get a 403 toast.
  const role = useProjectRole(projectId);
  return useQuery({
    queryKey: ['approvals', projectId, 'pending'],
    queryFn: () => approvalsApi.listPending(projectId),
    staleTime: 5 * 60 * 1000,
    enabled: !!projectId && hasAddon('APPROVALS') && (role === 'OWNER' || role === 'ADMIN'),
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
