import { useEffect } from 'react';
import { usePreferences } from './usePreferences';

export function useTheme() {
  const { prefs } = usePreferences();

  useEffect(() => {
    const root = document.documentElement;
    const apply = (dark: boolean) =>
      dark ? root.classList.add('dark') : root.classList.remove('dark');

    if (prefs.theme === 'dark') {
      apply(true);
      return;
    }
    if (prefs.theme === 'light') {
      apply(false);
      return;
    }

    // system — follow OS preference and react to changes
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    apply(mq.matches);
    const handler = (e: MediaQueryListEvent) => apply(e.matches);
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, [prefs.theme]);
}
