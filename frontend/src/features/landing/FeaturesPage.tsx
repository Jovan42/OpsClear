import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import keycloak from '../../auth/keycloak';
import PublicNav from '../../components/PublicNav';
import { useCatalog } from '../org/useSubscription';
import DemoTrigger from '../../demo/DemoTrigger';
import type { DemoSlide } from '../../demo/types';
import type { AddonCode, SubscriptionAddonResponse } from '../../types';

interface FeatureCard {
  id: string;
  name: string;
  description: string;
  screenshot?: string;
  // Omitted for base features that aren't gated by any subscription add-on.
  addonKey?: AddonCode;
  // Live interactive demo (ADR-0040), incrementally added card by card — most cards
  // still fall back to the static `screenshot` until their own job wires this up.
  loadSlides?: () => Promise<{ default: DemoSlide[] }>;
  // Slide 0's approximate natural content size, passed through to DemoTrigger — every
  // card's wrapped real page/component differs wildly in size, so these are tuned
  // per card rather than sharing one global default.
  previewNaturalWidth?: number;
  previewNaturalHeight?: number;
  previewScale?: number;
  previewBoxHeight?: string;
}

function buildCards(t: TFunction): FeatureCard[] {
  return [
    {
      id: 'job-tracking',
      name: t('featuresPage.cards.job-tracking.name'),
      description: t('featuresPage.cards.job-tracking.description'),
      screenshot: '/screenshots/job-tracking.png',
      loadSlides: () => import('../../demo/JobTrackingDemo'),
      previewNaturalWidth: 900,
      previewNaturalHeight: 520,
      previewScale: 0.4,
    },
    {
      id: 'feedback-credits',
      name: t('featuresPage.cards.feedback-credits.name'),
      description: t('featuresPage.cards.feedback-credits.description'),
      loadSlides: () => import('../../demo/FeedbackCreditsDemo'),
      previewNaturalWidth: 1000,
      previewNaturalHeight: 560,
      previewScale: 0.36,
    },
    {
      id: 'dashboard',
      name: t('featuresPage.cards.dashboard.name'),
      description: t('featuresPage.cards.dashboard.description'),
      screenshot: '/screenshots/dashboard.png',
      addonKey: 'DASHBOARD',
      loadSlides: () => import('../../demo/DashboardDemo'),
      previewNaturalWidth: 900,
      previewNaturalHeight: 560,
      previewScale: 0.36,
    },
    {
      id: 'approvals',
      name: t('featuresPage.cards.approvals.name'),
      description: t('featuresPage.cards.approvals.description'),
      screenshot: '/screenshots/approvals.png',
      addonKey: 'APPROVALS',
      loadSlides: () => import('../../demo/ApprovalsDemo'),
    },
    {
      id: 'notes',
      name: t('featuresPage.cards.notes.name'),
      description: t('featuresPage.cards.notes.description'),
      screenshot: '/screenshots/notes.png',
      addonKey: 'NOTES',
      loadSlides: () => import('../../demo/NotesDemo'),
      previewNaturalWidth: 700,
      previewNaturalHeight: 420,
      previewScale: 0.5,
    },
    {
      id: 'history',
      name: t('featuresPage.cards.history.name'),
      description: t('featuresPage.cards.history.description'),
      screenshot: '/screenshots/job-status-history.png',
      addonKey: 'JOB_STATUS_HISTORY',
      loadSlides: () => import('../../demo/HistoryDemo'),
      previewNaturalWidth: 700,
      previewNaturalHeight: 520,
      previewScale: 0.42,
    },
    {
      id: 'milestones',
      name: t('featuresPage.cards.milestones.name'),
      description: t('featuresPage.cards.milestones.description'),
      screenshot: '/screenshots/milestones-overview.png',
      addonKey: 'MILESTONES',
      loadSlides: () => import('../../demo/MilestonesDemo'),
      previewNaturalWidth: 800,
      previewNaturalHeight: 440,
      previewScale: 0.45,
    },
    {
      id: 'relationships',
      name: t('featuresPage.cards.relationships.name'),
      description: t('featuresPage.cards.relationships.description'),
      screenshot: '/screenshots/relationships.png',
      addonKey: 'JOB_RELATIONSHIPS',
      loadSlides: () => import('../../demo/RelationshipsDemo'),
      previewNaturalWidth: 700,
      previewNaturalHeight: 360,
      previewScale: 0.5,
    },
    {
      id: 'templates',
      name: t('featuresPage.cards.templates.name'),
      description: t('featuresPage.cards.templates.description'),
      addonKey: 'JOB_TEMPLATES',
      loadSlides: () => import('../../demo/TemplatesDemo'),
      previewNaturalWidth: 700,
      previewNaturalHeight: 500,
      previewScale: 0.42,
    },
    {
      id: 'recurring',
      name: t('featuresPage.cards.recurring.name'),
      description: t('featuresPage.cards.recurring.description'),
      addonKey: 'RECURRING_SCHEDULING',
      loadSlides: () => import('../../demo/RecurringDemo'),
      previewNaturalWidth: 800,
      previewNaturalHeight: 560,
      previewScale: 0.38,
    },
    {
      id: 'api-keys',
      name: t('featuresPage.cards.api-keys.name'),
      description: t('featuresPage.cards.api-keys.description'),
      addonKey: 'API_KEYS',
      loadSlides: () => import('../../demo/ApiKeysDemo'),
      previewNaturalWidth: 700,
      previewNaturalHeight: 400,
      previewScale: 0.48,
    },
    {
      id: 'links',
      name: t('featuresPage.cards.links.name'),
      description: t('featuresPage.cards.links.description'),
      addonKey: 'JOB_LINKS',
      loadSlides: () => import('../../demo/LinksDemo'),
      previewNaturalWidth: 700,
      previewNaturalHeight: 340,
      previewScale: 0.5,
    },
    {
      id: 'job-types',
      name: t('featuresPage.cards.job-types.name'),
      description: t('featuresPage.cards.job-types.description'),
      addonKey: 'JOB_TYPES',
      loadSlides: () => import('../../demo/JobTypesDemo'),
      previewNaturalWidth: 700,
      previewNaturalHeight: 460,
      previewScale: 0.45,
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

  const preview = card.screenshot ? (
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
  );

  return (
    <div className={`bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 overflow-hidden flex flex-col ${comingSoon ? 'opacity-60' : ''}`}>
      {card.loadSlides ? (
        <DemoTrigger
          loadSlides={card.loadSlides}
          fallback={preview}
          previewNaturalWidth={card.previewNaturalWidth}
          previewNaturalHeight={card.previewNaturalHeight}
          previewScale={card.previewScale}
        />
      ) : preview}
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
  // Memoized so each card's `loadSlides` function keeps a stable identity across
  // re-renders (e.g. useCatalog() resolving from loading to loaded triggers one).
  // DemoTrigger's mount effect depends on `loadSlides`, and an unstable reference
  // there caused its cleanup+re-run to fire on every FeaturesPage re-render — a burst
  // of release-then-acquire calls across all 7 cards that could stop and restart the
  // shared MSW worker mid-request, producing the "creating one record creates several,
  // and the count is random" bug.
  const CARDS = useMemo(() => buildCards(t), [t]);

  return (
    <div className="min-h-screen flex flex-col bg-white dark:bg-gray-900">
      <PublicNav />

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
