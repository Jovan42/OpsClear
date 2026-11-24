import { useQuery } from '@tanstack/react-query';
import { approvalsApi } from '../../api/approvals';
import { useCurrentOrg } from '../org/OrgContext';
import { useProjectRole } from '../projects/useProjects';
import { APPROVAL_QUEUE_POLL_MS } from './approvalRefresh';

// Shared by both pending queues. New requests also arrive through the notification stream
// (refreshApprovalsForNotification); decisions and withdrawals by others don't, so these are
// the fallback: refetch on every window focus, and poll while the queue is shown.
const queueRefresh = {
  refetchOnWindowFocus: 'always',
  refetchInterval: APPROVAL_QUEUE_POLL_MS,
} as const;

export function useApprovalQueue(projectId: string) {
  const { hasAddon } = useCurrentOrg();
  // Gated on role, not just the addon — the backend's pending queue is
  // Owner/Admin-only, so a plain Member would otherwise fire this on mount
  // (before ApprovalQueuePage's role redirect runs) and get a 403 toast.
  const role = useProjectRole(projectId);
  return useQuery({
    queryKey: ['approvals', projectId, 'pending'],
    queryFn: () => approvalsApi.listPending(projectId),
    ...queueRefresh,
    enabled: !!projectId && hasAddon('APPROVALS') && (role === 'OWNER' || role === 'ADMIN'),
  });
}

export function usePendingApprovalsAcrossOrgs() {
  const { hasAddon } = useCurrentOrg();
  return useQuery({
    queryKey: ['approvals', 'pending', 'cross-org'],
    queryFn: () => approvalsApi.listPendingAcrossOrgs(),
    ...queueRefresh,
    enabled: hasAddon('APPROVALS'),
  });
}
