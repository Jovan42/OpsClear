import { useState } from 'react';

const STORAGE_KEY = 'opsclear:preferences';

export type Theme = 'light' | 'dark' | 'system';

export interface Preferences {
  theme: Theme;
}

const defaults: Preferences = { theme: 'system' };

export function usePreferences() {
  const [prefs, setPrefs] = useState<Preferences>(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? { ...defaults, ...JSON.parse(raw) } : defaults;
    } catch {
      return defaults;
    }
  });

  const update = (patch: Partial<Preferences>) => {
    const next = { ...prefs, ...patch };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    setPrefs(next);
  };

  return { prefs, update };
}
