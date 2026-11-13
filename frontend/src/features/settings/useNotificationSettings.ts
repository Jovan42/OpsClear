import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { notificationsApi } from '../../api/notifications';
import type { NotificationSettings, NotificationType } from '../../types';
import { toggleMuted } from '../notifications/notificationContent';

export const NOTIFICATION_SETTINGS_KEY = ['user', 'notification-settings'];

/** One toggle: turn `type` on (`enabled`) or off. */
export interface NotificationToggle {
  type: NotificationType;
  enabled: boolean;
}

interface SaveQueueState {
  /** Saves clicked but not yet settled (running or queued). */
  pending: number;
  /** The cache may differ from the server; refetch once the queue drains. */
  dirty: boolean;
}

// Per QueryClient, so tests (and any second client) don't share state. Our own counter
// rather than `isMutating()`: TanStack still counts a mutation as pending while its
// `onSettled` runs, which is exactly when the resync starts.
const queues = new WeakMap<QueryClient, SaveQueueState>();

function queueFor(queryClient: QueryClient): SaveQueueState {
  let state = queues.get(queryClient);
  if (!state) {
    state = { pending: 0, dirty: false };
    queues.set(queryClient, state);
  }
  return state;
}

function cachedMuted(queryClient: QueryClient): NotificationType[] {
  return queryClient.getQueryData<NotificationSettings>(NOTIFICATION_SETTINGS_KEY)?.muted ?? [];
}

function setMuted(queryClient: QueryClient, muted: NotificationType[]) {
  queryClient.setQueryData<NotificationSettings>(NOTIFICATION_SETTINGS_KEY, { muted });
}

/**
 * Query options for the settings. A fetch that lands while saves are pending keeps the
 * cache (the pending flips, which queued saves read their body from) and marks it for a
 * resync once the queue drains. Exported for the unit tests.
 */
export function notificationSettingsQueryOptions(queryClient: QueryClient) {
  return {
    queryKey: NOTIFICATION_SETTINGS_KEY,
    queryFn: async (): Promise<NotificationSettings> => {
      const settings = await notificationsApi.getSettings();
      const queue = queueFor(queryClient);
      const cached = queryClient.getQueryData<NotificationSettings>(NOTIFICATION_SETTINGS_KEY);
      if (queue.pending > 0 && cached) {
        queue.dirty = true;
        return cached;
      }
      queue.dirty = false;
      return settings;
    },
  };
}

/** The caller's muted notification types (ADR-0051 §Muting). Server-stored, every device. */
export function useNotificationSettings() {
  const queryClient = useQueryClient();
  return useQuery(notificationSettingsQueryOptions(queryClient));
}

/**
 * Mutation options for saving one toggle. Exported for the unit tests; components use
 * `useUpdateNotificationSettings`.
 *
 * - Optimistic: `onMutate` flips the toggle in the cache at click time.
 * - Saves run one at a time (`scope`), so the server applies them in click order. Each PUT
 *   replaces the whole list, so parallel saves could finish out of order and lose a change.
 * - Each PUT body is read from the cache when the save actually runs, so it carries every
 *   toggle made so far, and not one that already failed and was reverted.
 * - A failed save reverts only its own toggle, keeping later queued toggles. A body can
 *   also carry toggles clicked after its own, so the server may hold a reverted change;
 *   after any failure the settings are refetched once the queue drains, so the screen
 *   ends up matching the server.
 */
export function notificationSettingsMutationOptions(queryClient: QueryClient, onSaveError?: () => void) {
  const queue = queueFor(queryClient);
  return {
    mutationKey: NOTIFICATION_SETTINGS_KEY,
    scope: { id: 'notification-settings' },
    mutationFn: () => notificationsApi.updateSettings(cachedMuted(queryClient)),
    onMutate: async ({ type, enabled }: NotificationToggle) => {
      queue.pending += 1;
      await queryClient.cancelQueries({ queryKey: NOTIFICATION_SETTINGS_KEY });
      setMuted(queryClient, toggleMuted(cachedMuted(queryClient), type, enabled));
    },
    onError: (_error: unknown, { type, enabled }: NotificationToggle) => {
      setMuted(queryClient, toggleMuted(cachedMuted(queryClient), type, !enabled));
      queue.dirty = true;
      onSaveError?.();
    },
    onSuccess: (settings: NotificationSettings) => {
      // A queued save will send a newer list; until then, keep its optimistic value.
      if (queue.pending <= 1) {
        queryClient.setQueryData(NOTIFICATION_SETTINGS_KEY, settings);
      }
    },
    onSettled: () => {
      queue.pending -= 1;
      if (queue.pending === 0 && queue.dirty) {
        void queryClient.invalidateQueries({ queryKey: NOTIFICATION_SETTINGS_KEY });
      }
    },
  };
}

/**
 * Saves one toggle (see `notificationSettingsMutationOptions`). `onSaveError` runs for
 * every failed save, also when later toggles were made while it was in flight. The API
 * client shows the error toast.
 */
export function useUpdateNotificationSettings(onSaveError?: () => void) {
  const queryClient = useQueryClient();
  return useMutation(notificationSettingsMutationOptions(queryClient, onSaveError));
}
