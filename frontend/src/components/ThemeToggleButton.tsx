import { useTranslation } from 'react-i18next';
import { usePreferences, type Theme } from '../hooks/usePreferences';

const THEME_ORDER: Theme[] = ['light', 'dark', 'system'];
const THEME_ICON: Record<Theme, string> = { light: '☀️', dark: '🌙', system: '🖥️' };

/**
 * Quick theme switch for a brand-coloured top bar: cycles light, dark, system. It only saves the
 * preference; the page must call useTheme() to apply it.
 */
export default function ThemeToggleButton() {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const { prefs, update } = usePreferences();

  function cycleTheme() {
    const idx = THEME_ORDER.indexOf(prefs.theme);
    update({ theme: THEME_ORDER[(idx + 1) % THEME_ORDER.length] });
  }

  return (
    <button
      type="button"
      onClick={cycleTheme}
      aria-label={t(`settings.appearance.theme.${prefs.theme}`)}
      title={t(`settings.appearance.theme.${prefs.theme}`)}
      className="w-8 h-8 flex items-center justify-center rounded-md text-sm text-white/80 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
    >
      {THEME_ICON[prefs.theme]}
    </button>
  );
}
