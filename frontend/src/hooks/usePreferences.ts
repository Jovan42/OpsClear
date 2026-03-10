import { createContext, useContext } from 'react';

const STORAGE_KEY = 'opsclear:preferences';

export type Theme = 'light' | 'dark' | 'system';

export interface Preferences {
  theme: Theme;
}

export const defaults: Preferences = { theme: 'system' };

export const PREFERENCES_STORAGE_KEY = STORAGE_KEY;

export interface PreferencesContextValue {
  prefs: Preferences;
  update: (patch: Partial<Preferences>) => void;
}

export const PreferencesContext = createContext<PreferencesContextValue | null>(null);

export function usePreferences(): PreferencesContextValue {
  const ctx = useContext(PreferencesContext);
  if (!ctx) throw new Error('usePreferences must be used inside PreferencesProvider');
  return ctx;
}
