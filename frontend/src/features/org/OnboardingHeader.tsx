import { useTranslation } from 'react-i18next';
import keycloak from '../../auth/keycloak';
import { useAuth } from '../../auth/AuthContext';
import { useTheme } from '../../hooks/useTheme';
import ThemeToggleButton from '../../components/ThemeToggleButton';
import LanguageToggleButton from '../../components/LanguageToggleButton';
import Logo from '../../components/Logo';

/**
 * JOB-395: top bar for the create-organisation page, which is routed outside AppLayout. The user
 * has no organisation yet, so there's no navigation, only the theme and language switches and a
 * way to sign out (e.g. signed up with the wrong account, or waiting for an invite).
 */
export default function OnboardingHeader() {
  const { t } = useTranslation('shared1');
  const { authenticated } = useAuth();
  useTheme();

  return (
    <nav
      className="px-4 sm:px-6 h-14 flex items-center justify-between shrink-0 text-white"
      style={{ backgroundColor: 'var(--brand)' }}
    >
      <span className="text-lg"><Logo /></span>
      <div className="flex items-center gap-3">
        <ThemeToggleButton />
        <LanguageToggleButton />
        {authenticated && (
          <button
            type="button"
            onClick={() => keycloak.logout({ redirectUri: `${window.location.origin}/` })}
            className="text-sm text-white/80 hover:text-white transition-colors cursor-pointer"
          >
            {t('signOut')}
          </button>
        )}
      </div>
    </nav>
  );
}
