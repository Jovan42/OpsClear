import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import keycloak from '../../auth/keycloak';
import { useCatalog } from '../org/useSubscription';
import type { AddonCode, SubscriptionAddonResponse } from '../../types';

interface FeatureCard {
  id: string;
  name: string;
  description: string;
  screenshot?: string;
  // Omitted for base features that aren't gated by any subscription add-on.
  addonKey?: AddonCode;
}

function buildCards(t: TFunction): FeatureCard[] {
  return [
    {
      id: 'job-tracking',
      name: t('featuresPage.cards.job-tracking.name'),
      description: t('featuresPage.cards.job-tracking.description'),
      screenshot: '/screenshots/job-tracking.png',
    },
    {
      id: 'blockage',
      name: t('featuresPage.cards.blockage.name'),
      description: t('featuresPage.cards.blockage.description'),
      screenshot: '/screenshots/blockage.png',
    },
    {
      id: 'dashboard',
      name: t('featuresPage.cards.dashboard.name'),
      description: t('featuresPage.cards.dashboard.description'),
      screenshot: '/screenshots/dashboard.png',
      addonKey: 'DASHBOARD',
    },
    {
      id: 'approvals',
      name: t('featuresPage.cards.approvals.name'),
      description: t('featuresPage.cards.approvals.description'),
      screenshot: '/screenshots/approvals.png',
      addonKey: 'APPROVALS',
    },
    {
      id: 'notes',
      name: t('featuresPage.cards.notes.name'),
      description: t('featuresPage.cards.notes.description'),
      screenshot: '/screenshots/notes.png',
      addonKey: 'NOTES',
    },
    {
      id: 'history',
      name: t('featuresPage.cards.history.name'),
      description: t('featuresPage.cards.history.description'),
      screenshot: '/screenshots/job-status-history.png',
      addonKey: 'JOB_STATUS_HISTORY',
    },
    {
      id: 'milestones',
      name: t('featuresPage.cards.milestones.name'),
      description: t('featuresPage.cards.milestones.description'),
      screenshot: '/screenshots/milestones-overview.png',
      addonKey: 'MILESTONES',
    },
    {
      id: 'relationships',
      name: t('featuresPage.cards.relationships.name'),
      description: t('featuresPage.cards.relationships.description'),
      screenshot: '/screenshots/relationships.png',
      addonKey: 'JOB_RELATIONSHIPS',
    },
    {
      id: 'templates',
      name: t('featuresPage.cards.templates.name'),
      description: t('featuresPage.cards.templates.description'),
      addonKey: 'JOB_TEMPLATES',
    },
    {
      id: 'recurring',
      name: t('featuresPage.cards.recurring.name'),
      description: t('featuresPage.cards.recurring.description'),
      addonKey: 'RECURRING_SCHEDULING',
    },
    {
      id: 'api-keys',
      name: t('featuresPage.cards.api-keys.name'),
      description: t('featuresPage.cards.api-keys.description'),
      addonKey: 'API_KEYS',
    },
  ];
}

type Badge = 'base' | 'addon' | 'coming-soon';

const BADGE_CLASS: Record<Badge, string> = {
  base: 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400',
  addon: 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-400',
  'coming-soon': 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400',
};

function badgeFor(card: FeatureCard, addons: SubscriptionAddonResponse[]): Badge {
  if (!card.addonKey) return 'base';
  const addon = addons.find((a) => a.key === card.addonKey);
  return addon?.available ? 'addon' : 'coming-soon';
}

function Card({ card, badge }: { card: FeatureCard; badge: Badge }) {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const comingSoon = badge === 'coming-soon';
  const badgeLabel = badge === 'base' ? t('featuresPage.badges.base') : badge === 'addon' ? t('featuresPage.badges.addon') : t('featuresPage.badges.comingSoon');

  return (
    <div className={`bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 overflow-hidden flex flex-col ${comingSoon ? 'opacity-60' : ''}`}>
      {card.screenshot ? (
        <div className="bg-gray-100 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
          <img
            src={card.screenshot}
            alt={card.name}
            className="w-full object-contain object-top max-h-56"
          />
        </div>
      ) : (
        <div className="h-40 bg-gray-100 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 flex items-center justify-center">
          <span className="text-gray-300 dark:text-gray-600 text-sm">{t('featuresPage.noPreview')}</span>
        </div>
      )}
      <div className="p-6 flex flex-col gap-3 flex-1">
        <div className="flex items-center gap-2 flex-wrap">
          <h3 className="font-semibold text-gray-900 dark:text-white">{card.name}</h3>
          <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${BADGE_CLASS[badge]}`}>
            {badgeLabel}
          </span>
        </div>
        <p className="text-sm text-gray-500 dark:text-gray-400 leading-relaxed">{card.description}</p>
      </div>
    </div>
  );
}

export default function FeaturesPage() {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const { data: catalog } = useCatalog();
  const CARDS = buildCards(t);

  return (
    <div className="min-h-screen flex flex-col bg-white dark:bg-gray-900">
      {/* Nav */}
      <nav
        className="px-6 h-14 flex items-center justify-between shrink-0"
        style={{ backgroundColor: 'var(--brand)' }}
      >
        <Link to="/" className="font-semibold text-lg tracking-tight text-white">OpsClear</Link>
        <button
          onClick={() => keycloak.login()}
          className="text-sm text-white/80 hover:text-white transition-colors"
        >
          {t('landing.logIn')}
        </button>
      </nav>

      <main className="flex-1 px-6 py-16">
        <div className="max-w-5xl mx-auto">
          <div className="text-center mb-12">
            <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-3">{t('featuresPage.pageHeading')}</h1>
            <p className="text-gray-500 dark:text-gray-400 max-w-xl mx-auto">
              {t('productPricing.subheading')}
            </p>
            <Link
              to="/"
              className="inline-block mt-4 text-sm font-medium hover:underline"
              style={{ color: 'var(--brand)' }}
            >
              {t('featuresPage.backToHome')}
            </Link>
          </div>

          {catalog && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
              {CARDS.map(card => <Card key={card.id} card={card} badge={badgeFor(card, catalog.addons)} />)}
            </div>
          )}

          <div className="text-center mt-16">
            <button
              onClick={() => keycloak.register()}
              className="px-8 py-3 rounded-lg text-white font-semibold text-lg hover:opacity-90 transition-opacity"
              style={{ backgroundColor: 'var(--brand)' }}
            >
              {t('landing.hero.getStarted')}
            </button>
            <p className="text-sm text-gray-400 mt-3">{t('featuresPage.noCreditCard')}</p>
          </div>
        </div>
      </main>
    </div>
  );
}
