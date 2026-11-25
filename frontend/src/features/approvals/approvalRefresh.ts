import type { QueryClient } from '@tanstack/react-query';
import type { NotificationType } from '../../types';

/** Prefix of both pending queues: the project queue and the cross-org Overview list. */
export const APPROVALS_KEY = ['approvals'] as const;

/**
 * How often an open pending queue refetches. The backend notifies only the requester when a
 * request is decided, and nobody when it's withdrawn, so another admin's open queue can't
 * learn about those from the notification stream. Polling (paused while the tab is hidden)
 * and the window-focus refetch cover them, only on the screens that show the queue
 * (`useApprovalQueue`'s `live` option).
 */
export const APPROVAL_QUEUE_POLL_MS = 60_000;

const APPROVAL_NOTIFICATION_TYPES: ReadonlySet<NotificationType> = new Set(['APPROVAL_REQUESTED', 'APPROVAL_DECIDED']);

/**
 * A live notification arrived: when it's about an approval (requested or decided),
 * refresh the pending queues, so a new request shows up and a decided one leaves without a
 * reload. Mounted queues refetch now, the others when they're next shown.
 */
export function refreshApprovalsForNotification(queryClient: QueryClient, type: NotificationType) {
  if (!APPROVAL_NOTIFICATION_TYPES.has(type)) return;
  void queryClient.invalidateQueries({ queryKey: APPROVALS_KEY });
}

/**
 * The notification stream (re)connected: the server doesn't replay what it sent while the
 * stream was down, so an approval request sent meanwhile would otherwise only show on the next
 * poll or focus. Refresh the pending queues, like the notification caches.
 */
export function refreshApprovalsAfterConnect(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: APPROVALS_KEY });
}

function isConflict(error: unknown): boolean {
  return (error as { response?: { status?: number } } | null)?.response?.status === 409;
}

/**
 * A decision or withdrawal failed with 409: someone else already decided or withdrew the
 * request. Refresh the pending queues and the project's job data (its approvals list), so the
 * stale card leaves the queue instead of staying there as pending.
 */
export function refreshApprovalsAfterConflict(queryClient: QueryClient, projectId: string, error: unknown) {
  if (!isConflict(error)) return;
  void queryClient.invalidateQueries({ queryKey: APPROVALS_KEY });
  void queryClient.invalidateQueries({ queryKey: ['jobs', projectId] });
}
