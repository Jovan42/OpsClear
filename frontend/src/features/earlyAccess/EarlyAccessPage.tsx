import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import PublicNav from '../../components/PublicNav';
import SupportLink from '../../components/SupportLink';
import { usePageTitle } from '../../hooks/usePageTitle';
import EmailSignupForm from './EmailSignupForm';

/** ADR-0066: the public launch list page, linked from the closed Sign up buttons, posts and ads. */
export default function EarlyAccessPage() {
  const { t } = useTranslation('earlyAccess');
  usePageTitle(t('pageTitle'));

  return (
    <div className="min-h-screen flex flex-col bg-gray-50 dark:bg-gray-900">
      <PublicNav />
      <main className="flex-1 flex items-start sm:items-center justify-center px-4 py-10 sm:py-16">
        <div className="w-full max-w-md">
          <div className="mb-8 text-center">
            <h1 className="text-3xl font-bold leading-tight" style={{ color: 'var(--brand)' }}>
              {t('heading')}
            </h1>
            <p className="mt-3 text-gray-600 dark:text-gray-400">{t('intro')}</p>
          </div>

          <div className="relative bg-white dark:bg-gray-800 rounded-2xl border border-gray-200 dark:border-gray-700 p-6 shadow-sm">
            <EmailSignupForm list="early-access" />
          </div>

          <div className="mt-6 text-center">
            <Link to="/" className="text-sm font-medium hover:underline" style={{ color: 'var(--brand)' }}>
              {t('backToHome')}
            </Link>
          </div>
        </div>
      </main>
      <footer className="shrink-0 border-t border-gray-200 dark:border-gray-800 px-6 py-6">
        <SupportLink />
      </footer>
    </div>
  );
}
