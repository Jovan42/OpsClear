import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationsApi } from '../../api/notifications';
import type { NotificationListFilters } from '../../types';

/** Page size of the Notifications page; the bell dropdown asks for its own 10. */
export const NOTIFICATIONS_PAGE_SIZE = 20;
export const DROPDOWN_SIZE = 10;

const ALL_FILTERS: NotificationListFilters = { unread: false, type: null };

// No live push yet (Frontend 2, SSE). Until then these queries refetch on window focus
// (off app-wide in main.tsx), and NotificationBell refreshes stale ones on navigation
// and when the dropdown opens.
export function useUnreadCount() {
  return useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: notificationsApi.unreadCount,
    refetchOnWindowFocus: true,
  });
}

/** `page` is zero-based. */
export function useNotifications(
  filters: NotificationListFilters = ALL_FILTERS,
  page = 0,
  size = NOTIFICATIONS_PAGE_SIZE,
  enabled = true,
) {
  return useQuery({
    queryKey: ['notifications', 'list', filters, page, size],
    queryFn: () => notificationsApi.list({ unread: filters.unread, type: filters.type, page, size }),
    placeholderData: keepPreviousData,
    refetchOnWindowFocus: true,
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
