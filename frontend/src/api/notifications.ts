import apiClient, { API_BASE_URL, getAccessToken } from './client';
import type {
  NotificationPageResponse,
  NotificationResponse,
  NotificationSettings,
  NotificationType,
} from '../types';

export interface NotificationListParams {
  unread?: boolean;
  type?: NotificationType | null;
  /** Zero-based. */
  page?: number;
  size?: number;
}

// The payload is a stored JSON snapshot. Normalise a missing one to {} so renderers only
// ever deal with missing keys, not a missing object. Exported for the stream's
// `notification` events, which carry the same shape.
export function withPayload(n: NotificationResponse): NotificationResponse {
  return { ...n, payload: n.payload ?? {} } as NotificationResponse;
}

export const notificationsApi = {
  // `signal` (from TanStack Query) aborts the request when the query is cancelled, e.g.
  // when a stream event replaces a fetch that may predate it.
  list: ({ unread, type, page, size }: NotificationListParams, signal?: AbortSignal) =>
    apiClient
      .get<NotificationPageResponse>('/api/notifications', {
        params: {
          unread: unread || undefined,
          type: type ?? undefined,
          page,
          size,
        },
        signal,
      })
      .then((r) => ({ ...r.data, items: r.data.items.map(withPayload) })),

  unreadCount: (signal?: AbortSignal) =>
    apiClient
      .get<{ count: number }>('/api/notifications/unread-count', { signal })
      .then((r) => r.data.count),

  markRead: (id: string, read: boolean) =>
    apiClient
      .patch<NotificationResponse>(`/api/notifications/${id}`, { read })
      .then((r) => withPayload(r.data)),

  markAllRead: () => apiClient.post<void>('/api/notifications/read-all').then(() => undefined),

  getSettings: () =>
    apiClient.get<NotificationSettings>('/api/user/notification-settings').then((r) => r.data),

  /** Replaces the whole muted list. `SUBSCRIPTION_PAST_DUE` must not be in it (400). */
  updateSettings: (muted: NotificationType[]) =>
    apiClient
      .put<NotificationSettings>('/api/user/notification-settings', { muted })
      .then((r) => r.data),

  /**
   * Opens the SSE stream (ADR-0051) and resolves with the raw fetch response; the caller
   * reads and parses `body`. fetch, not axios or `EventSource`: `EventSource` can't send
   * the Authorization header, and axios can't hand back a streaming body in the browser.
   * Each call takes a fresh token, so a reconnect after the server's 30-minute close
   * never reuses an expired one.
   */
  openStream: async (signal: AbortSignal): Promise<Response> => {
    const token = await getAccessToken();
    return fetch(`${API_BASE_URL}/api/notifications/stream`, {
      headers: {
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      cache: 'no-store',
      signal,
    });
  },
};
