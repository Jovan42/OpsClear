// ADR-0062: the user guide's page list, in sidebar and prev/next order. It's also the
// coverage map: `coverage` lists the app files each page documents (globs relative to the
// repo root), so a PR that changes one of them can be checked for a matching guide update.
// The globs are a first pass; the jobs that write each page finalise them.

export type GuideSection = 'getting-started' | 'daily-work' | 'planning' | 'admin-settings' | 'account-more';

export const GUIDE_SECTIONS: readonly GuideSection[] = [
  'getting-started',
  'daily-work',
  'planning',
  'admin-settings',
  'account-more',
];

/** Card ids on `/features` (FeaturesPage's `buildCards`). */
export type FeatureCardId =
  | 'job-tracking'
  | 'notifications'
  | 'feedback-credits'
  | 'dashboard'
  | 'approvals'
  | 'notes'
  | 'history'
  | 'milestones'
  | 'relationships'
  | 'templates'
  | 'recurring'
  | 'api-keys'
  | 'links'
  | 'job-types';

export interface GuidePageEntry {
  slug: string;
  section: GuideSection;
  /** i18n key in the `guide` namespace, used in the sidebar, prev/next and search results. */
  titleKey: string;
  /** App files this page documents, as globs from the repo root. */
  coverage: readonly string[];
  /** `/features` cards this page documents. The first is the page's "See it in action"
   *  target, and each card links back here with "How to use it". */
  featureCards?: readonly FeatureCardId[];
}

const page = (
  slug: string,
  section: GuideSection,
  coverage: readonly string[],
  featureCards?: readonly FeatureCardId[],
): GuidePageEntry => ({ slug, section, titleKey: `pages.${slug}`, coverage, featureCards });

export const GUIDE_PAGES: readonly GuidePageEntry[] = [
  // Getting started (the onboarding journey)
  page('index', 'getting-started', ['frontend/src/features/landing/LandingPage.tsx']),
  page('sign-up', 'getting-started', [
    'keycloak/themes/opsclear/login/register.ftl',
    'keycloak/themes/opsclear/login/login-verify-email.ftl',
    'keycloak/themes/opsclear/login/messages/**',
  ]),
  page('create-organisation', 'getting-started', ['frontend/src/features/org/CreateOrgPage.tsx']),
  page('invite-team', 'getting-started', [
    'frontend/src/features/org/OrgInvitesPage.tsx',
    'frontend/src/features/org/AcceptInvitePage.tsx',
    'frontend/src/features/org/OrgMembersPage.tsx',
    'keycloak/themes/opsclear/login/register.ftl',
  ]),
  page('first-project', 'getting-started', [
    'frontend/src/features/projects/ProjectListPage.tsx',
    'frontend/src/features/projects/NewProjectModal.tsx',
    'frontend/src/features/projects/AddMemberForm.tsx',
  ]),
  page('create-job', 'getting-started', [
    'frontend/src/features/jobs/NewJobModal.tsx',
    'frontend/src/features/jobs/JobListPage.tsx',
  ]),
  page(
    'job-done',
    'getting-started',
    [
      'frontend/src/features/jobs/JobDetailPage.tsx',
      'frontend/src/features/jobs/components/JobStatusBar.tsx',
      'frontend/src/features/jobs/components/NoteThread.tsx',
    ],
    ['job-tracking'],
  ),
  page('job-blocked', 'getting-started', [
    'frontend/src/features/jobs/components/BlockModal.tsx',
    'frontend/src/features/jobs/components/BlockedBanner.tsx',
    'frontend/src/features/dashboard/DashboardPage.tsx',
  ]),
  page(
    'job-approval',
    'getting-started',
    [
      'frontend/src/features/jobs/components/RequestApprovalModal.tsx',
      'frontend/src/features/jobs/components/RequestCompletionModal.tsx',
      'frontend/src/features/jobs/components/ApprovalDecisionModal.tsx',
      'frontend/src/features/jobs/components/CompletionRequestBanner.tsx',
      'frontend/src/features/approvals/ApprovalQueuePage.tsx',
    ],
    ['approvals'],
  ),

  // Daily work
  page('finding-jobs', 'daily-work', ['frontend/src/features/jobs/JobListPage.tsx']),
  page('dashboard', 'daily-work', ['frontend/src/features/dashboard/**'], ['dashboard']),
  page('notifications', 'daily-work', ['frontend/src/features/notifications/*.tsx'], ['notifications']),
  page(
    'notes-links-history',
    'daily-work',
    [
      'frontend/src/features/jobs/components/NoteThread.tsx',
      'frontend/src/features/jobs/components/LinksSection.tsx',
      'frontend/src/features/jobs/components/StatusHistory.tsx',
    ],
    ['notes', 'links', 'history'],
  ),

  // Planning
  page('milestones', 'planning', ['frontend/src/features/milestones/**'], ['milestones']),
  page(
    'templates-and-types',
    'planning',
    ['frontend/src/features/templates/**', 'frontend/src/features/jobTypes/**'],
    ['templates', 'job-types'],
  ),
  page('recurring-jobs', 'planning', ['frontend/src/features/schedules/**'], ['recurring']),
  page(
    'job-relationships',
    'planning',
    [
      'frontend/src/features/jobs/components/RelationshipsSection.tsx',
      'frontend/src/features/jobs/components/AddRelationshipModal.tsx',
    ],
    ['relationships'],
  ),

  // Admin & settings
  page('roles-permissions', 'admin-settings', [
    'frontend/src/components/RoleBadge.tsx',
    'frontend/src/features/org/OrgMembersPage.tsx',
    'frontend/src/features/projects/AddMemberForm.tsx',
  ]),
  page('project-settings', 'admin-settings', [
    'frontend/src/features/projects/ProjectSettingsPage.tsx',
    'frontend/src/features/projects/CompletionApprovalSetting.tsx',
    'frontend/src/features/projects/JobSectionsSetting.tsx',
  ]),
  page('organisation-settings', 'admin-settings', [
    'frontend/src/features/org/OrgSettingsPage.tsx',
    'frontend/src/features/org/IdPrefixesSection.tsx',
    'frontend/src/features/org/OrgMembersPage.tsx',
    'frontend/src/features/org/OrgInvitesPage.tsx',
  ]),
  page('preferences', 'admin-settings', [
    'frontend/src/features/settings/SettingsPage.tsx',
    'frontend/src/features/settings/NotificationTypeSettings.tsx',
  ]),

  // Account & more
  page('subscription', 'account-more', [
    'frontend/src/components/SubscriptionWall.tsx',
    'frontend/src/components/UpgradeCard.tsx',
    'frontend/src/features/org/SubscriptionSection.tsx',
    'frontend/src/features/org/PaddleBillingSection.tsx',
    'frontend/src/features/org/BillingHistorySection.tsx',
  ]),
  page('stats-overview', 'account-more', ['frontend/src/features/stats/**', 'frontend/src/features/overview/**']),
  page(
    'api-keys',
    'account-more',
    ['frontend/src/features/settings/ApiKeySection.tsx', 'frontend/src/features/settings/NewApiKeyModal.tsx'],
    ['api-keys'],
  ),
  page('faq', 'account-more', ['keycloak/themes/opsclear/login/**']),
];

export const GUIDE_INDEX_SLUG = 'index';

/** The route for a guide page; the index lives at `/guide` itself. */
export function guidePath(slug: string, anchor?: string): string {
  const base = slug === GUIDE_INDEX_SLUG ? '/guide' : `/guide/${slug}`;
  return anchor ? `${base}#${anchor}` : base;
}

export function findGuidePage(slug: string): GuidePageEntry | undefined {
  return GUIDE_PAGES.find((p) => p.slug === slug);
}

/** The guide page that documents a `/features` card, if any. */
export function guidePageForCard(card: string): GuidePageEntry | undefined {
  return GUIDE_PAGES.find((p) => p.featureCards?.includes(card as FeatureCardId));
}

/** "See it in action": the card on `/features`, which gives each card its id as an anchor. */
export function featureLinkPath(card: FeatureCardId): string {
  return `/features#${card}`;
}
