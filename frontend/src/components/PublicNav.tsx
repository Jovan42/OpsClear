import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import keycloak from '../auth/keycloak';
import { useAuth } from '../auth/AuthContext';
import { usePreferences, type Theme } from '../hooks/usePreferences';
import { useTheme } from '../hooks/useTheme';

const THEME_ORDER: Theme[] = ['light', 'dark', 'system'];
const THEME_ICON: Record<Theme, string> = { light: '☀️', dark: '🌙', system: '🖥️' };

/**
 * Shared header for the public marketing pages (Landing, Features). Unlike the
 * authenticated app shell (AppLayout), nothing here calls useTheme() by default —
 * these pages previously rendered outside any dark-mode-aware context, so a visitor
 * toggling theme from here needs this component to apply it itself.
 */
export default function PublicNav() {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const { authenticated } = useAuth();
  const { prefs, update } = usePreferences();
  useTheme();

  function cycleTheme() {
    const idx = THEME_ORDER.indexOf(prefs.theme);
    update({ theme: THEME_ORDER[(idx + 1) % THEME_ORDER.length] });
  }

  function toggleLocale() {
    update({ locale: prefs.locale === 'en' ? 'sr' : 'en' });
  }

  return (
    <nav className="px-6 h-14 flex items-center justify-between shrink-0" style={{ backgroundColor: 'var(--brand)' }}>
      <Link to="/" className="font-semibold text-lg tracking-tight text-white">OpsClear</Link>
      <div className="flex items-center gap-3">
        {/* Logged-in visitors already have theme/language controls in Settings —
            only surface the quick switcher here for visitors who don't. */}
        {!authenticated && (
          <>
            <button
              onClick={cycleTheme}
              aria-label={t(`settings.appearance.theme.${prefs.theme}`)}
              title={t(`settings.appearance.theme.${prefs.theme}`)}
              className="w-8 h-8 flex items-center justify-center rounded-md text-sm text-white/80 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
            >
              {THEME_ICON[prefs.theme]}
            </button>
            <button
              onClick={toggleLocale}
              aria-label={t('settings.appearance.language.label')}
              title={t('settings.appearance.language.label')}
              className="px-2.5 py-1 text-xs font-semibold rounded-md border border-white/30 text-white/80 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
            >
              {prefs.locale.toUpperCase()}
            </button>
          </>
        )}
        {authenticated ? (
          <Link to="/projects" className="text-sm text-white/80 hover:text-white transition-colors">
            {t('landing.goToApp')}
          </Link>
        ) : (
          <button onClick={() => keycloak.login()} className="text-sm text-white/80 hover:text-white transition-colors">
            {t('landing.logIn')}
          </button>
        )}
      </div>
    </nav>
  );
}
