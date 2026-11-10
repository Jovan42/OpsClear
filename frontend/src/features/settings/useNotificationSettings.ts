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

/** The caller's muted notification types (ADR-0051 §Muting). Server-stored, every device. */
export function useNotificationSettings() {
  return useQuery({
    queryKey: NOTIFICATION_SETTINGS_KEY,
    queryFn: notificationsApi.getSettings,
  });
}

function cachedMuted(queryClient: QueryClient): NotificationType[] {
  return queryClient.getQueryData<NotificationSettings>(NOTIFICATION_SETTINGS_KEY)?.muted ?? [];
}

function setMuted(queryClient: QueryClient, muted: NotificationType[]) {
  queryClient.setQueryData<NotificationSettings>(NOTIFICATION_SETTINGS_KEY, { muted });
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
 * - A failed save reverts only its own toggle, keeping later queued toggles.
 */
export function notificationSettingsMutationOptions(queryClient: QueryClient, onSaveError?: () => void) {
  const othersPending = () => queryClient.isMutating({ mutationKey: NOTIFICATION_SETTINGS_KEY }) > 1;
  return {
    mutationKey: NOTIFICATION_SETTINGS_KEY,
    scope: { id: 'notification-settings' },
    mutationFn: () => notificationsApi.updateSettings(cachedMuted(queryClient)),
    onMutate: async ({ type, enabled }: NotificationToggle) => {
      await queryClient.cancelQueries({ queryKey: NOTIFICATION_SETTINGS_KEY });
      setMuted(queryClient, toggleMuted(cachedMuted(queryClient), type, enabled));
    },
    onError: (_error: unknown, { type, enabled }: NotificationToggle) => {
      setMuted(queryClient, toggleMuted(cachedMuted(queryClient), type, !enabled));
      onSaveError?.();
    },
    onSuccess: (settings: NotificationSettings) => {
      // A queued save will send a newer list; until then, keep its optimistic value.
      if (!othersPending()) {
        queryClient.setQueryData(NOTIFICATION_SETTINGS_KEY, settings);
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
