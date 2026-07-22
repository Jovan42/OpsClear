import { useEffect, useState, type ReactNode } from 'react';
import i18n from '../i18n';
import {
  PreferencesContext,
  defaults,
  PREFERENCES_STORAGE_KEY,
  type Preferences,
} from './usePreferences';

function load(): Preferences {
  try {
    const raw = localStorage.getItem(PREFERENCES_STORAGE_KEY);
    return raw ? { ...defaults, ...JSON.parse(raw) } : defaults;
  } catch {
    return defaults;
  }
}

export default function PreferencesProvider({ children }: { readonly children: ReactNode }) {
  const [prefs, setPrefs] = useState<Preferences>(load);

  useEffect(() => {
    void i18n.changeLanguage(prefs.locale);
  }, [prefs.locale]);

  function update(patch: Partial<Preferences>) {
    const next = { ...prefs, ...patch };
    localStorage.setItem(PREFERENCES_STORAGE_KEY, JSON.stringify(next));
    setPrefs(next);
  }

  return (
    <PreferencesContext.Provider value={{ prefs, update }}>
      {children}
    </PreferencesContext.Provider>
  );
}
