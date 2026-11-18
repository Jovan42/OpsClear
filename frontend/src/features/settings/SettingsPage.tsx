import type { MouseEvent, ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import ApiKeySection from './ApiKeySection';
import NotificationTypeSettings from './NotificationTypeSettings';
import UpgradeCard from '../../components/UpgradeCard';
import { usePreferences } from '../../hooks/usePreferences';
import { useCurrentOrg } from '../org/OrgContext';
import type {
  Theme, Locale, ViewMode, AccordionState, StatusTab,
  SortOrder, DeadlineFormat, ProjectPage, ProgressFormat, DateFormat, RowDensity,
} from '../../hooks/usePreferences';
import { usePageTitle } from '../../hooks/usePageTitle';

// ---- helpers ----

interface SegmentedControlProps<T extends string> {
  value: T;
  options: { value: T; label: string }[];
  onChange: (value: T) => void;
}

function SegmentedControl<T extends string>({ value, options, onChange }: SegmentedControlProps<T>) {
  return (
    <div className="flex rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden shrink-0">
      {options.map((opt) => (
        <button
          key={opt.value}
          onClick={() => onChange(opt.value)}
          className={`px-3 py-1.5 text-sm font-medium transition-colors cursor-pointer ${
            value === opt.value
              ? 'bg-gray-900 text-white dark:bg-white dark:text-gray-900'
              : 'bg-white text-gray-600 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
          }`}
        >
          {opt.label}
        </button>
      ))}
    </div>
  );
}

interface SelectControlProps<T extends string> {
  value: T;
  options: { value: T; label: string }[];
  onChange: (value: T) => void;
  label?: string;
}

function SelectControl<T extends string>({ value, options, onChange, label }: SelectControlProps<T>) {
  return (
    <select
      aria-label={label}
      value={value}
      onChange={(e) => onChange(e.target.value as T)}
      className="rounded-lg border border-gray-200 dark:border-gray-700 px-3 py-1.5 text-sm bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:border-transparent shrink-0"
    >
      {options.map((opt) => (
        <option key={opt.value} value={opt.value}>{opt.label}</option>
      ))}
    </select>
  );
}

interface SettingRowProps {
  readonly label: string;
  readonly description: string;
  readonly children: ReactNode;
}

function SettingRow({ label, description, children }: SettingRowProps) {
  return (
    <div className="flex items-center justify-between gap-6 p-5">
      <div className="min-w-0">
        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{label}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{description}</p>
      </div>
      {children}
    </div>
  );
}

// ---- sections (ADR-0061) ----

// Page order. Stats joins (between Milestones and Notifications) once it has a row.
const SECTION_IDS = ['appearance', 'jobs', 'dashboard', 'navigation', 'milestones', 'notifications', 'api-keys'] as const;
type SectionId = (typeof SECTION_IDS)[number];

const SECTION_LABEL_KEYS: Record<SectionId, string> = {
  appearance: 'appearance',
  jobs: 'jobs',
  dashboard: 'dashboard',
  navigation: 'navigation',
  milestones: 'milestones',
  notifications: 'notifications',
  'api-keys': 'apiKeys',
};

interface SettingsSectionProps {
  readonly id: SectionId;
  readonly heading: string;
  readonly children: ReactNode;
}

function SettingsSection({ id, heading, children }: SettingsSectionProps) {
  return (
    <section id={id} aria-labelledby={`${id}-heading`} className="scroll-mt-6">
      <h2
        id={`${id}-heading`}
        className="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3"
      >
        {heading}
      </h2>
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
        {children}
      </div>
    </section>
  );
}

interface SectionIndexProps {
  readonly sections: { id: SectionId; label: string }[];
  readonly label: string;
}

/** Links to each section: anchor chips under the title on mobile, a sticky list on desktop. */
function SectionIndex({ sections, label }: SectionIndexProps) {
  const scrollTo = (event: MouseEvent<HTMLAnchorElement>, id: SectionId) => {
    const target = document.getElementById(id);
    if (!target) return;
    // Scroll without a router navigation; keep the hash so the link can be shared.
    event.preventDefault();
    target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    window.history.replaceState(window.history.state, '', `#${id}`);
  };

  return (
    <nav aria-label={label} data-testid="settings-section-index" className="md:w-44 md:shrink-0">
      <ul className="flex gap-2 overflow-x-auto pb-1 -mx-4 px-4 md:mx-0 md:px-0 md:pb-0 md:flex-col md:gap-0.5 md:sticky md:top-6">
        {sections.map((section) => (
          <li key={section.id} className="shrink-0">
            <a
              href={`#${section.id}`}
              onClick={(event) => scrollTo(event, section.id)}
              className="block whitespace-nowrap rounded-full border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-1 text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 md:rounded-lg md:border-0 md:bg-transparent md:dark:bg-transparent md:px-3 md:py-1.5 hover:text-gray-900 dark:hover:text-gray-100 transition-colors"
            >
              {section.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}

// ---- page ----

export default function SettingsPage() {
  const { t } = useTranslation(['approvalsDashboardSettingsLanding']);
  usePageTitle(t('approvalsDashboardSettingsLanding:settings.pageTitle'));
  const { prefs, update } = usePreferences();
  const { hasAddon } = useCurrentOrg();

  const THEME_OPTIONS: { value: Theme; label: string }[] = [
    { value: 'light', label: t('settings.appearance.theme.light') },
    { value: 'dark', label: t('settings.appearance.theme.dark') },
    { value: 'system', label: t('settings.appearance.theme.system') },
  ];

  const LOCALE_OPTIONS: { value: Locale; label: string }[] = [
    { value: 'en', label: 'English' },
    { value: 'sr', label: 'Srpski' },
  ];

  const DATE_FORMAT_OPTIONS: { value: DateFormat; label: string }[] = [
    { value: 'AUTO', label: t('settings.appearance.dateFormat.auto') },
    { value: 'DMY_DOT', label: t('settings.appearance.dateFormat.dmyDot') },
    { value: 'DMY_SLASH', label: t('settings.appearance.dateFormat.dmySlash') },
    { value: 'MDY_SLASH', label: t('settings.appearance.dateFormat.mdySlash') },
  ];

  const ROW_DENSITY_OPTIONS: { value: RowDensity; label: string }[] = [
    { value: 'COMFORTABLE', label: t('settings.appearance.rowDensity.comfortable') },
    { value: 'COMPACT', label: t('settings.appearance.rowDensity.compact') },
  ];

  const VIEW_MODE_OPTIONS: { value: ViewMode; label: string }[] = [
    { value: 'GROUPED', label: t('settings.jobList.defaultViewMode.grouped') },
    { value: 'FLAT', label: t('settings.jobList.defaultViewMode.flat') },
  ];

  const ACCORDION_OPTIONS: { value: AccordionState; label: string }[] = [
    { value: 'EXPANDED', label: t('settings.jobList.milestoneAccordion.expanded') },
    { value: 'COLLAPSED', label: t('settings.jobList.milestoneAccordion.collapsed') },
  ];

  const STATUS_TAB_OPTIONS: { value: StatusTab; label: string }[] = [
    { value: 'ALL', label: t('settings.jobList.defaultStatusTab.all') },
    { value: 'NEW', label: t('settings.jobList.defaultStatusTab.new') },
    { value: 'IN_PROGRESS', label: t('settings.jobList.defaultStatusTab.inProgress') },
    { value: 'BLOCKED', label: t('settings.jobList.defaultStatusTab.blocked') },
    { value: 'COMPLETED', label: t('settings.jobList.defaultStatusTab.completed') },
  ];

  const HIDE_COMPLETED_OPTIONS: { value: 'true' | 'false'; label: string }[] = [
    { value: 'false', label: t('settings.showHide.show') },
    { value: 'true', label: t('settings.showHide.hide') },
  ];

  const SORT_ORDER_OPTIONS: { value: SortOrder; label: string }[] = [
    { value: 'DEADLINE_ASC', label: t('settings.jobList.defaultSortOrder.deadlineAsc') },
    { value: 'DEADLINE_DESC', label: t('settings.jobList.defaultSortOrder.deadlineDesc') },
    { value: 'PRIORITY_DESC', label: t('settings.jobList.defaultSortOrder.priority') },
    { value: 'CREATED_DESC', label: t('settings.jobList.defaultSortOrder.newestFirst') },
  ];

  const DEADLINE_FORMAT_OPTIONS: { value: DeadlineFormat; label: string }[] = [
    { value: 'ABSOLUTE', label: t('settings.appearance.deadlineFormat.absolute') },
    { value: 'RELATIVE', label: t('settings.appearance.deadlineFormat.relative') },
  ];

  const SHOW_HIDE_OPTIONS: { value: 'true' | 'false'; label: string }[] = [
    { value: 'true', label: t('settings.showHide.show') },
    { value: 'false', label: t('settings.showHide.hide') },
  ];

  const PROJECT_PAGE_OPTIONS: { value: ProjectPage; label: string }[] = [
    { value: 'DASHBOARD', label: t('settings.navigation.defaultProjectPage.dashboard') },
    { value: 'JOBS', label: t('settings.navigation.defaultProjectPage.jobs') },
    { value: 'APPROVALS', label: t('settings.navigation.defaultProjectPage.approvals') },
  ];

  const PROGRESS_FORMAT_OPTIONS: { value: ProgressFormat; label: string }[] = [
    { value: 'FRACTION', label: t('settings.milestonesSection.progressFormat.fraction') },
    { value: 'PERCENTAGE', label: t('settings.milestonesSection.progressFormat.percentage') },
  ];

  const SOUND_OPTIONS: { value: 'true' | 'false'; label: string }[] = [
    { value: 'true', label: t('settings.notificationsSection.sound.on') },
    { value: 'false', label: t('settings.notificationsSection.sound.off') },
  ];

  const sections: { id: SectionId; label: string }[] = SECTION_IDS.map((id) => ({
    id,
    label: t(`settings.sections.${SECTION_LABEL_KEYS[id]}`),
  }));

  return (
    <div className="max-w-4xl mx-auto px-4 py-10">
      <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">{t('settings.pageTitle')}</h1>

      <div className="mt-4 md:mt-8 md:flex md:gap-10">
        <SectionIndex sections={sections} label={t('settings.sections.indexLabel')} />

        <div className="min-w-0 flex-1 max-w-lg space-y-8 mt-6 md:mt-0">
          <SettingsSection id="appearance" heading={t('settings.sections.appearance')}>
            <SettingRow label={t('settings.appearance.theme.label')} description={t('settings.appearance.theme.description')}>
              <SegmentedControl value={prefs.theme} options={THEME_OPTIONS} onChange={(v) => update({ theme: v })} />
            </SettingRow>
            <SettingRow label={t('settings.appearance.language.label')} description={t('settings.appearance.language.description')}>
              <SegmentedControl value={prefs.locale} options={LOCALE_OPTIONS} onChange={(v) => update({ locale: v })} />
            </SettingRow>
            <SettingRow label={t('settings.appearance.dateFormat.label')} description={t('settings.appearance.dateFormat.description')}>
              <SelectControl
                label={t('settings.appearance.dateFormat.label')}
                value={prefs.dateFormat}
                options={DATE_FORMAT_OPTIONS}
                onChange={(v) => update({ dateFormat: v })}
              />
            </SettingRow>
            <SettingRow label={t('settings.appearance.rowDensity.label')} description={t('settings.appearance.rowDensity.description')}>
              <SegmentedControl value={prefs.rowDensity} options={ROW_DENSITY_OPTIONS} onChange={(v) => update({ rowDensity: v })} />
            </SettingRow>
          </SettingsSection>

          <SettingsSection id="jobs" heading={t('settings.sections.jobs')}>
            <SettingRow label={t('settings.jobList.defaultViewMode.label')} description={t('settings.jobList.defaultViewMode.description')}>
              <SegmentedControl value={prefs.defaultViewMode} options={VIEW_MODE_OPTIONS} onChange={(v) => update({ defaultViewMode: v })} />
            </SettingRow>
            <SettingRow label={t('settings.jobList.defaultStatusTab.label')} description={t('settings.jobList.defaultStatusTab.description')}>
              <SelectControl value={prefs.defaultStatusTab} options={STATUS_TAB_OPTIONS} onChange={(v) => update({ defaultStatusTab: v })} />
            </SettingRow>
            <SettingRow label={t('settings.jobList.hideCompleted.label')} description={t('settings.jobList.hideCompleted.description')}>
              <SegmentedControl
                value={prefs.hideCompletedFromAll ? 'true' : 'false'}
                options={HIDE_COMPLETED_OPTIONS}
                onChange={(v) => update({ hideCompletedFromAll: v === 'true' })}
              />
            </SettingRow>
            <SettingRow label={t('settings.jobList.defaultSortOrder.label')} description={t('settings.jobList.defaultSortOrder.description')}>
              <SelectControl value={prefs.defaultSortOrder} options={SORT_ORDER_OPTIONS} onChange={(v) => update({ defaultSortOrder: v })} />
            </SettingRow>
            <SettingRow label={t('settings.appearance.deadlineFormat.label')} description={t('settings.appearance.deadlineFormat.description')}>
              <SegmentedControl value={prefs.deadlineFormat} options={DEADLINE_FORMAT_OPTIONS} onChange={(v) => update({ deadlineFormat: v })} />
            </SettingRow>
          </SettingsSection>

          <SettingsSection id="dashboard" heading={t('settings.sections.dashboard')}>
            <SettingRow label={t('settings.dashboardSection.blockedSection.label')} description={t('settings.dashboardSection.blockedSection.description')}>
              <SegmentedControl
                value={prefs.showBlockedSection ? 'true' : 'false'}
                options={SHOW_HIDE_OPTIONS}
                onChange={(v) => update({ showBlockedSection: v === 'true' })}
              />
            </SettingRow>
            <SettingRow label={t('settings.dashboardSection.overdueSection.label')} description={t('settings.dashboardSection.overdueSection.description')}>
              <SegmentedControl
                value={prefs.showOverdueSection ? 'true' : 'false'}
                options={SHOW_HIDE_OPTIONS}
                onChange={(v) => update({ showOverdueSection: v === 'true' })}
              />
            </SettingRow>
            <SettingRow label={t('settings.dashboardSection.pendingApprovalsSection.label')} description={t('settings.dashboardSection.pendingApprovalsSection.description')}>
              <SegmentedControl
                value={prefs.showPendingApprovalsSection ? 'true' : 'false'}
                options={SHOW_HIDE_OPTIONS}
                onChange={(v) => update({ showPendingApprovalsSection: v === 'true' })}
              />
            </SettingRow>
          </SettingsSection>

          <SettingsSection id="navigation" heading={t('settings.sections.navigation')}>
            <SettingRow label={t('settings.navigation.defaultProjectPage.label')} description={t('settings.navigation.defaultProjectPage.description')}>
              <SegmentedControl value={prefs.defaultProjectPage} options={PROJECT_PAGE_OPTIONS} onChange={(v) => update({ defaultProjectPage: v })} />
            </SettingRow>
          </SettingsSection>

          <SettingsSection id="milestones" heading={t('settings.sections.milestones')}>
            <SettingRow label={t('settings.jobList.milestoneAccordion.label')} description={t('settings.jobList.milestoneAccordion.description')}>
              <SegmentedControl value={prefs.milestoneAccordionState} options={ACCORDION_OPTIONS} onChange={(v) => update({ milestoneAccordionState: v })} />
            </SettingRow>
            <SettingRow label={t('settings.milestonesSection.progressFormat.label')} description={t('settings.milestonesSection.progressFormat.description')}>
              <SegmentedControl value={prefs.milestoneProgressFormat} options={PROGRESS_FORMAT_OPTIONS} onChange={(v) => update({ milestoneProgressFormat: v })} />
            </SettingRow>
          </SettingsSection>

          {/* Stats: hidden until it has a row (the default range, Frontend 4 of ADR-0061). */}

          <SettingsSection id="notifications" heading={t('settings.sections.notifications')}>
            <SettingRow label={t('settings.notificationsSection.sound.label')} description={t('settings.notificationsSection.sound.description')}>
              <SegmentedControl
                value={prefs.notificationSound ? 'true' : 'false'}
                options={SOUND_OPTIONS}
                onChange={(v) => update({ notificationSound: v === 'true' })}
              />
            </SettingRow>
            <NotificationTypeSettings />
          </SettingsSection>

          {/* ApiKeySection and UpgradeCard bring their own heading. */}
          <div id="api-keys" className="scroll-mt-6">
            {hasAddon('API_KEYS') ? <ApiKeySection /> : <UpgradeCard featureName={t('settings.apiKeysFeatureName')} />}
          </div>
        </div>
      </div>
    </div>
  );
}
