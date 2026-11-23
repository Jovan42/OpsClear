import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import keycloak from '../auth/keycloak';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../hooks/useTheme';
import ThemeToggleButton from './ThemeToggleButton';
import LanguageToggleButton from './LanguageToggleButton';
import Logo from './Logo';

/**
 * Shared header for the public pages (Landing, Features, and the Guide when logged out). Unlike the
 * authenticated app shell (AppLayout), nothing here calls useTheme() by default —
 * these pages previously rendered outside any dark-mode-aware context, so a visitor
 * toggling theme from here needs this component to apply it itself.
 */
export default function PublicNav() {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const { t: tGuide } = useTranslation('guide');
  const { authenticated } = useAuth();
  useTheme();

  return (
    <nav className="px-6 h-14 flex items-center justify-between shrink-0" style={{ backgroundColor: 'var(--brand)' }}>
      <Link to="/" aria-label="OpsClear" className="text-lg text-white"><Logo /></Link>
      <div className="flex items-center gap-3">
        <Link to="/guide" className="text-sm text-white/80 hover:text-white transition-colors">
          {tGuide('entry.nav')}
        </Link>
        {/* Logged-in visitors already have theme/language controls in Settings —
            only surface the quick switcher here for visitors who don't. */}
        {!authenticated && (
          <>
            <ThemeToggleButton />
            <LanguageToggleButton />
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
