import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationsApi } from '../../api/notifications';
import { useAuth } from '../../auth/AuthContext';
import { useCurrentOrg } from '../org/OrgContext';
import { useOrgMembers } from '../org/useOrganisation';
import type { NotificationListFilters, OrgRole } from '../../types';
import { useNotificationStreamConnected } from './useNotificationStream';

/** Page size of the Notifications page; the bell dropdown asks for its own 10. */
export const NOTIFICATIONS_PAGE_SIZE = 20;
export const DROPDOWN_SIZE = 10;

const ALL_FILTERS: NotificationListFilters = { unread: false, type: null };

// Kept current by the notification stream (useNotificationStream). Only while it's down
// (backend restart, network loss, backing off) do these queries fall back to refetching
// on window focus, which is off app-wide in main.tsx.
export function useUnreadCount() {
  const streamConnected = useNotificationStreamConnected();
  return useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: notificationsApi.unreadCount,
    refetchOnWindowFocus: !streamConnected,
  });
}

/** `page` is zero-based. */
export function useNotifications(
  filters: NotificationListFilters = ALL_FILTERS,
  page = 0,
  size = NOTIFICATIONS_PAGE_SIZE,
  enabled = true,
) {
  const streamConnected = useNotificationStreamConnected();
  return useQuery({
    queryKey: ['notifications', 'list', filters, page, size],
    queryFn: () => notificationsApi.list({ unread: filters.unread, type: filters.type, page, size }),
    placeholderData: keepPreviousData,
    refetchOnWindowFocus: !streamConnected,
    enabled,
  });
}

function useInvalidateNotifications() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['notifications', 'list'] });
    void queryClient.invalidateQueries({ queryKey: ['notifications', 'unread-count'] });
  };
}

export function useMarkRead() {
  const invalidate = useInvalidateNotifications();
  return useMutation({
    mutationFn: ({ id, read }: { id: string; read: boolean }) => notificationsApi.markRead(id, read),
    onSuccess: invalidate,
  });
}

export function useMarkAllRead() {
  const invalidate = useInvalidateNotifications();
  return useMutation({
    mutationFn: notificationsApi.markAllRead,
    onSuccess: invalidate,
  });
}

/**
 * The viewer's role in their organisation, read the same way as UserMenu (members list,
 * already cached by the header). Null while loading or without an org.
 */
export function useViewerOrgRole(): OrgRole | null {
  const { org } = useCurrentOrg();
  const { userId } = useAuth();
  const { data: members = [] } = useOrgMembers(org?.id ?? null);
  return members.find((m) => m.userId === userId)?.role ?? null;
}
