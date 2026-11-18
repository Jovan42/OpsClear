import apiClient from './client';
import type { UiPreferenceValue, UserPreferencesResponse } from '../types';

// ADR-0061. PreferencesProvider handles failures itself (keeps the local value, shows one
// "Couldn't save" toast), so these skip the API client's generic error toast.
export const preferencesApi = {
  get: () =>
    apiClient
      .get<UserPreferencesResponse>('/api/user/preferences', { skipErrorToast: true })
      .then((r) => r.data),

  /** Merges `ui` into the stored preferences; a `null` value removes the key. */
  update: (ui: Record<string, UiPreferenceValue>) =>
    apiClient
      .patch<UserPreferencesResponse>('/api/user/preferences', { ui }, { skipErrorToast: true })
      .then((r) => r.data),
};
