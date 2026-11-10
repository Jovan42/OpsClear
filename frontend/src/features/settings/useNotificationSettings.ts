import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationsApi } from '../../api/notifications';
import type { NotificationSettings, NotificationType } from '../../types';

const NOTIFICATION_SETTINGS_KEY = ['user', 'notification-settings'];

/** The caller's muted notification types (ADR-0051 §Muting). Server-stored, every device. */
export function useNotificationSettings() {
  return useQuery({
    queryKey: NOTIFICATION_SETTINGS_KEY,
    queryFn: notificationsApi.getSettings,
  });
}

/**
 * Replaces the muted list. Optimistic: the toggle flips at once and is put back if the
 * save fails (the API client shows the error toast).
 */
export function useUpdateNotificationSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: NOTIFICATION_SETTINGS_KEY,
    mutationFn: (muted: NotificationType[]) => notificationsApi.updateSettings(muted),
    onMutate: async (muted) => {
      await queryClient.cancelQueries({ queryKey: NOTIFICATION_SETTINGS_KEY });
      const previous = queryClient.getQueryData<NotificationSettings>(NOTIFICATION_SETTINGS_KEY);
      queryClient.setQueryData<NotificationSettings>(NOTIFICATION_SETTINGS_KEY, { muted });
      return { previous };
    },
    onError: (_error, _muted, context) => {
      if (context?.previous) {
        queryClient.setQueryData(NOTIFICATION_SETTINGS_KEY, context.previous);
      }
      // A later toggle may have saved while this one was in flight, so resync with the
      // server rather than trust the snapshot alone.
      void queryClient.invalidateQueries({ queryKey: NOTIFICATION_SETTINGS_KEY });
    },
    onSuccess: (settings) => {
      // With quick toggles, an older response mustn't overwrite a newer optimistic value,
      // so the server's answer is only written when no other save is in flight.
      if (queryClient.isMutating({ mutationKey: NOTIFICATION_SETTINGS_KEY }) <= 1) {
        queryClient.setQueryData(NOTIFICATION_SETTINGS_KEY, settings);
      }
    },
  });
}
