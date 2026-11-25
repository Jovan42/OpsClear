import { useQuery } from '@tanstack/react-query';
import { approvalsApi } from '../../api/approvals';
import { useCurrentOrg } from '../org/OrgContext';
import { useProjectRole } from '../projects/useProjects';
import { APPROVAL_QUEUE_POLL_MS } from './approvalRefresh';

export interface ApprovalQueueOptions {
  /**
   * Set by the screens that show the queue itself (the project Approvals page and the
   * Overview's Pending approvals section). Off by default, so other callers, like the project
   * nav's pending badge, don't poll on every project page.
   */
  live?: boolean;
}

// New requests arrive through the notification stream (refreshApprovalsForNotification);
// decisions and withdrawals by others don't, so while a queue is shown it refetches on every
// window focus and polls (TanStack pauses the poll while the tab is hidden). TanStack uses the
// shortest interval among a query's mounted observers, so the nav badge shares the fresh data
// while the Approvals page is open, and the poll stops when the page closes.
const liveRefresh = {
  refetchOnWindowFocus: 'always',
  refetchInterval: APPROVAL_QUEUE_POLL_MS,
} as const;

// Everywhere else, the earlier behaviour: cached for 5 minutes, no poll.
const cachedRefresh = {
  staleTime: 5 * 60 * 1000,
} as const;

export function approvalQueueRefresh({ live = false }: ApprovalQueueOptions = {}) {
  return live ? liveRefresh : cachedRefresh;
}

export function useApprovalQueue(projectId: string, options?: ApprovalQueueOptions) {
  const { hasAddon } = useCurrentOrg();
  // Gated on role, not just the addon — the backend's pending queue is
  // Owner/Admin-only, so a plain Member would otherwise fire this on mount
  // (before ApprovalQueuePage's role redirect runs) and get a 403 toast.
  const role = useProjectRole(projectId);
  return useQuery({
    queryKey: ['approvals', projectId, 'pending'],
    queryFn: () => approvalsApi.listPending(projectId),
    ...approvalQueueRefresh(options),
    enabled: !!projectId && hasAddon('APPROVALS') && (role === 'OWNER' || role === 'ADMIN'),
  });
}

export function usePendingApprovalsAcrossOrgs(options?: ApprovalQueueOptions) {
  const { hasAddon } = useCurrentOrg();
  return useQuery({
    queryKey: ['approvals', 'pending', 'cross-org'],
    queryFn: () => approvalsApi.listPendingAcrossOrgs(),
    ...approvalQueueRefresh(options),
    enabled: hasAddon('APPROVALS'),
  });
}
