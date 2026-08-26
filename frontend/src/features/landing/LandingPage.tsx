import { useEffect } from 'react';
import { Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../auth/AuthContext';
import keycloak from '../../auth/keycloak';
import PublicNav from '../../components/PublicNav';
import ProductAndPricing from './ProductAndPricing';
import { POST_LOGIN_REDIRECT_KEY } from '../../auth/postLoginRedirect';

function Hero() {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  return (
    <section className="px-6 py-24 text-center">
      <div className="max-w-3xl mx-auto">
        <h1 className="text-4xl sm:text-5xl font-bold leading-tight mb-6" style={{ color: 'var(--brand)' }}>
          {t('landing.hero.headlineLine1')}<br className="hidden sm:block" /> {t('landing.hero.headlineLine2')}
        </h1>
        <p className="text-xl text-gray-500 dark:text-gray-400 mb-10 max-w-xl mx-auto">
          {t('landing.hero.subtitle')}
        </p>
        <button
          onClick={() => keycloak.register()}
          className="px-8 py-3 rounded-lg text-white font-semibold text-lg hover:opacity-90 transition-opacity"
          style={{ backgroundColor: 'var(--brand)' }}
        >
          {t('landing.hero.getStarted')}
        </button>
      </div>
    </section>
  );
}

function ProblemStatement() {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  return (
    <section className="bg-gray-100 dark:bg-gray-800 px-6 py-20">
      <div className="max-w-2xl mx-auto text-center space-y-5">
        <h2 className="text-2xl font-bold text-gray-900 dark:text-white">
          {t('landing.problem.heading')}
        </h2>
        <p className="text-gray-600 dark:text-gray-400 leading-relaxed">
          {t('landing.problem.body')}
        </p>
        <p className="text-sm text-gray-500 dark:text-gray-500">
          {t('landing.problem.footer')}
        </p>
      </div>
    </section>
  );
}

function CTAFooter() {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  return (
    <footer className="px-6 py-20 text-center" style={{ backgroundColor: 'var(--brand)' }}>
      <h2 className="text-3xl font-bold text-white mb-3">{t('landing.cta.heading')}</h2>
      <p className="text-white/70 mb-8">{t('landing.cta.subtitle')}</p>
      <button
        onClick={() => keycloak.register()}
        className="px-8 py-3 bg-white rounded-lg font-semibold text-lg hover:opacity-90 transition-opacity"
        style={{ color: 'var(--brand)' }}
      >
        {t('landing.cta.button')}
      </button>
    </footer>
  );
}

export default function LandingPage() {
  const { authenticated } = useAuth();

  // JOB-237: clearing the saved key is a side effect and belongs in an effect, not the
  // render body — doing both the read and the clear inline here made this component
  // impure, which broke under StrictMode's double-render (the first pass consumed the
  // key, so the second pass always saw null and fell through to the default).
  useEffect(() => {
    if (authenticated) sessionStorage.removeItem(POST_LOGIN_REDIRECT_KEY);
  }, [authenticated]);

  // TODO MIL-012: branch on subscription state → setup wall
  if (authenticated) {
    // JOB-237: RequireAuth saved the originally-requested deep link before bouncing
    // through Keycloak's real (full-page) login — land back there instead of always
    // the generic default, when one was saved.
    const savedPath = sessionStorage.getItem(POST_LOGIN_REDIRECT_KEY);
    return <Navigate to={savedPath || '/projects'} replace />;
  }

  return (
    <div className="min-h-screen flex flex-col">
      <PublicNav />
      <main className="flex-1 bg-white dark:bg-gray-900">
        <Hero />
        <ProblemStatement />
        <ProductAndPricing />
      </main>
      <CTAFooter />
    </div>
  );
}
