import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../auth/AuthContext';
import keycloak from '../../auth/keycloak';
import PublicNav from '../../components/PublicNav';
import SupportLink from '../../components/SupportLink';
import ProductAndPricing from './ProductAndPricing';
import SocialLinks from './SocialLinks';
import { POST_LOGIN_REDIRECT_KEY } from '../../auth/postLoginRedirect';
import { clearPendingInvite, readPendingInvite } from '../org/inviteLink';
import { usePreferences } from '../../hooks/usePreferences';
import { getMostRecentProject } from '../../hooks/useRecentProjects';
import { useProjectList } from '../projects/useProjects';
import LegalLinks from '../legal/LegalLinks';
import { PROJECT_LIST_PATH, landingPath, landingProjectRef } from './landingTarget';

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
  const { t: tGuide } = useTranslation('guide');
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
      <div className="mt-10">
        <Link to="/guide" className="text-sm text-white/80 hover:text-white underline-offset-4 hover:underline">
          {tGuide('entry.nav')}
        </Link>
      </div>
      <LegalLinks
        className="mt-4"
        linkClassName="text-sm text-white/80 hover:text-white underline-offset-4 hover:underline"
      />
      <div className="mt-8">
        <SocialLinks />
      </div>
      <SupportLink className="mt-6 text-sm text-white/70" linkClassName="text-white/90 hover:text-white underline underline-offset-4" />
    </footer>
  );
}

/**
 * ADR-0061: the landing project (list, last opened or a chosen one) once the user's
 * preferences have loaded from the server, so a fresh login in a new browser doesn't land
 * on the list before its saved choice arrives.
 */
function LandingProjectRedirect() {
  const { prefs, synced } = usePreferences();
  // Read once: the visit recorded on the next page must not change the target.
  const [mostRecent] = useState(getMostRecentProject);
  const ref = synced ? landingProjectRef(prefs.landingProject, prefs.landingProjectId, mostRecent) : null;
  const { data: projects, isError } = useProjectList('ACTIVE', { enabled: ref !== null });

  if (!synced) return <div className="min-h-screen bg-gray-50 dark:bg-gray-900" />;
  if (ref === null || isError) return <Navigate to={PROJECT_LIST_PATH} replace />;
  if (!projects) return <div className="min-h-screen bg-gray-50 dark:bg-gray-900" />;
  return <Navigate to={landingPath(ref, projects, prefs.defaultProjectPage)} replace />;
}

function AuthenticatedLanding() {
  // JOB-237: RequireAuth saved the originally-requested deep link before bouncing
  // through Keycloak's real (full-page) login — land back there instead of the
  // landing project, when one was saved. Read once into state and cleared in an
  // effect: clearing it during render broke under StrictMode's double render (the
  // first pass consumed the key, so the second pass always saw null).
  const [savedPath] = useState(() => sessionStorage.getItem(POST_LOGIN_REDIRECT_KEY));
  // JOB-399: signed up or logged in from an invite page, but Keycloak brought the user here
  // instead (email verified in another browser): finish on the invite page, which joins.
  const [pendingInvite] = useState(() => readPendingInvite());
  useEffect(() => {
    sessionStorage.removeItem(POST_LOGIN_REDIRECT_KEY);
    clearPendingInvite();
  }, []);

  if (pendingInvite) return <Navigate to={`/invite/${pendingInvite}`} replace />;
  if (savedPath) return <Navigate to={savedPath} replace />;
  return <LandingProjectRedirect />;
}

export default function LandingPage() {
  const { authenticated } = useAuth();

  // TODO MIL-012: branch on subscription state → setup wall
  if (authenticated) return <AuthenticatedLanding />;

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
