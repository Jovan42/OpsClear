import { useTranslation } from 'react-i18next';
import keycloak from '../../auth/keycloak';
import { useAuth } from '../../auth/AuthContext';
import Button from '../../components/Button';
import EmailSignupForm from './EmailSignupForm';

/**
 * ADR-0066: shown on /onboarding and /org/new while sign-up is closed. The signed-in user can put
 * their account email on the launch list (after ticking the consent box) or log out.
 */
export default function SignupClosedCard() {
  const { t } = useTranslation('earlyAccess');
  const { authenticated, email } = useAuth();

  return (
    <div
      data-testid="signup-closed-card"
      className="relative bg-white dark:bg-gray-800 rounded-2xl border border-gray-200 dark:border-gray-700 p-6 shadow-sm space-y-5"
    >
      <div className="text-center">
        <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">{t('closed.heading')}</h1>
        <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">{t('closed.body')}</p>
      </div>

      <div className="space-y-3">
        {email && <p className="text-sm text-gray-600 dark:text-gray-300">{t('closed.joinHint')}</p>}
        <EmailSignupForm list="early-access" fixedEmail={email ?? undefined} submitLabel={t('closed.joinList')} />
      </div>

      {authenticated && (
        <div className="pt-4 border-t border-gray-200 dark:border-gray-700">
          <Button
            variant="secondary"
            onClick={() => keycloak.logout({ redirectUri: `${window.location.origin}/` })}
            style={{ width: '100%', justifyContent: 'center' }}
          >
            {t('closed.logOut')}
          </Button>
        </div>
      )}
    </div>
  );
}
