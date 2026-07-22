import { Link } from 'react-router-dom';
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

const CARDS: FeatureCard[] = [
  {
    id: 'job-tracking',
    name: 'Job tracking',
    description: 'See all your jobs across milestones in one place. Filter by status, priority, or assignee. Group by milestone to track progress at a glance — no spreadsheets, no chasing.',
    screenshot: '/screenshots/job-tracking.png',
  },
  {
    id: 'blockage',
    name: 'Blockage visibility',
    description: "When a job is blocked, you see exactly why, who flagged it, and since when. No more hunting through messages to find out what's stuck.",
    screenshot: '/screenshots/blockage.png',
  },
  {
    id: 'dashboard',
    name: 'Dashboard',
    description: 'Status distribution, overdue jobs, and pending approvals in one screen. Open the dashboard and you know exactly where the project stands.',
    screenshot: '/screenshots/dashboard.png',
    addonKey: 'DASHBOARD',
  },
  {
    id: 'approvals',
    name: 'Approvals',
    description: 'Structured sign-off directly on the job. Employees request, you decide, everything is logged permanently with your comment. No ambiguity about what was decided or when.',
    screenshot: '/screenshots/approvals.png',
    addonKey: 'APPROVALS',
  },
  {
    id: 'notes',
    name: 'Notes',
    description: "Add decisions and agreements directly to any job. Notes are permanent and timestamped — your record of what was said and decided. Once written, they're part of the permanent record.",
    screenshot: '/screenshots/notes.png',
    addonKey: 'NOTES',
  },
  {
    id: 'history',
    name: 'Job status history',
    description: 'See the complete history of every status change on any job — who moved it, when, and why it was blocked. Full accountability, no gaps.',
    screenshot: '/screenshots/job-status-history.png',
    addonKey: 'JOB_STATUS_HISTORY',
  },
  {
    id: 'milestones',
    name: 'Milestones',
    description: 'Organise jobs into delivery phases with deadlines and progress tracking. See exactly how much of each milestone is done at a glance.',
    screenshot: '/screenshots/milestones-overview.png',
    addonKey: 'MILESTONES',
  },
  {
    id: 'relationships',
    name: 'Job relationships',
    description: 'Define how jobs relate to each other — blocked by, blocks, or related to. See what a blocked job is waiting on and trace dependencies across your project.',
    screenshot: '/screenshots/relationships.png',
    addonKey: 'JOB_RELATIONSHIPS',
  },
  {
    id: 'templates',
    name: 'Job templates',
    description: 'Save frequently created jobs as templates and spin them up in one click. Consistent structure, zero repeated effort.',
    addonKey: 'JOB_TEMPLATES',
  },
  {
    id: 'recurring',
    name: 'Recurring scheduling',
    description: 'Set up recurring jobs that create themselves automatically — daily, weekly, or monthly. Requires Job templates.',
    addonKey: 'RECURRING_SCHEDULING',
  },
  {
    id: 'api-keys',
    name: 'API keys',
    description: 'Generate scoped API keys for integrations and automation — pull job data into your own tools or scripts without going through the UI.',
    addonKey: 'API_KEYS',
  },
];

type Badge = 'base' | 'addon' | 'coming-soon';

const BADGE_LABEL: Record<Badge, string> = {
  base: 'Included',
  addon: 'Add-on',
  'coming-soon': 'Coming soon',
};

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
  const comingSoon = badge === 'coming-soon';

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
          <span className="text-gray-300 dark:text-gray-600 text-sm">No preview yet</span>
        </div>
      )}
      <div className="p-6 flex flex-col gap-3 flex-1">
        <div className="flex items-center gap-2 flex-wrap">
          <h3 className="font-semibold text-gray-900 dark:text-white">{card.name}</h3>
          <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${BADGE_CLASS[badge]}`}>
            {BADGE_LABEL[badge]}
          </span>
        </div>
        <p className="text-sm text-gray-500 dark:text-gray-400 leading-relaxed">{card.description}</p>
      </div>
    </div>
  );
}

export default function FeaturesPage() {
  const { data: catalog } = useCatalog();

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
          Log in
        </button>
      </nav>

      <main className="flex-1 px-6 py-16">
        <div className="max-w-5xl mx-auto">
          <div className="text-center mb-12">
            <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-3">Everything OpsClear can do</h1>
            <p className="text-gray-500 dark:text-gray-400 max-w-xl mx-auto">
              Start with the base plan and add only what your team needs.
            </p>
            <Link
              to="/"
              className="inline-block mt-4 text-sm font-medium hover:underline"
              style={{ color: 'var(--brand)' }}
            >
              ← Back to home
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
              Get started
            </button>
            <p className="text-sm text-gray-400 mt-3">No credit card required.</p>
          </div>
        </div>
      </main>
    </div>
  );
}
