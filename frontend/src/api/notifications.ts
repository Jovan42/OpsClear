import apiClient from './client';
import type { NotificationPageResponse, NotificationResponse, NotificationType } from '../types';

export interface NotificationListParams {
  unread?: boolean;
  type?: NotificationType | null;
  /** Zero-based. */
  page?: number;
  size?: number;
}

// The payload is a stored JSON snapshot. Normalise a missing one to {} so renderers only
// ever deal with missing keys, not a missing object.
function withPayload(n: NotificationResponse): NotificationResponse {
  return { ...n, payload: n.payload ?? {} } as NotificationResponse;
}

export const notificationsApi = {
  list: ({ unread, type, page, size }: NotificationListParams) =>
    apiClient
      .get<NotificationPageResponse>('/api/notifications', {
        params: {
          unread: unread || undefined,
          type: type ?? undefined,
          page,
          size,
        },
      })
      .then((r) => ({ ...r.data, items: r.data.items.map(withPayload) })),

  unreadCount: () =>
    apiClient.get<{ count: number }>('/api/notifications/unread-count').then((r) => r.data.count),

  markRead: (id: string, read: boolean) =>
    apiClient
      .patch<NotificationResponse>(`/api/notifications/${id}`, { read })
      .then((r) => withPayload(r.data)),

  markAllRead: () => apiClient.post<void>('/api/notifications/read-all').then(() => undefined),
};
