import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import ApiKeySection from './ApiKeySection';
import UpgradeCard from '../../components/UpgradeCard';
import { usePreferences } from '../../hooks/usePreferences';
import { useCurrentOrg } from '../org/OrgContext';
import type {
  Theme, Locale, ViewMode, AccordionState, StatusTab,
  SortOrder, DeadlineFormat, ProjectPage, ProgressFormat,
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
}

function SelectControl<T extends string>({ value, options, onChange }: SelectControlProps<T>) {
  return (
    <select
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

  return (
    <div className="max-w-lg mx-auto px-4 py-10 space-y-8">
      <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">{t('settings.pageTitle')}</h1>

      {/* Appearance */}
      <section>
        <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3">
          {t('settings.appearance.heading')}
        </h2>
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
          <SettingRow label={t('settings.appearance.theme.label')} description={t('settings.appearance.theme.description')}>
            <SegmentedControl value={prefs.theme} options={THEME_OPTIONS} onChange={(v) => update({ theme: v })} />
          </SettingRow>
          <SettingRow label={t('settings.appearance.language.label')} description={t('settings.appearance.language.description')}>
            <SegmentedControl value={prefs.locale} options={LOCALE_OPTIONS} onChange={(v) => update({ locale: v })} />
          </SettingRow>
          <SettingRow label={t('settings.appearance.deadlineFormat.label')} description={t('settings.appearance.deadlineFormat.description')}>
            <SegmentedControl value={prefs.deadlineFormat} options={DEADLINE_FORMAT_OPTIONS} onChange={(v) => update({ deadlineFormat: v })} />
          </SettingRow>
        </div>
      </section>

      {/* Job list */}
      <section>
        <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3">
          {t('settings.jobList.heading')}
        </h2>
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
          <SettingRow label={t('settings.jobList.defaultViewMode.label')} description={t('settings.jobList.defaultViewMode.description')}>
            <SegmentedControl value={prefs.defaultViewMode} options={VIEW_MODE_OPTIONS} onChange={(v) => update({ defaultViewMode: v })} />
          </SettingRow>
          <SettingRow label={t('settings.jobList.milestoneAccordion.label')} description={t('settings.jobList.milestoneAccordion.description')}>
            <SegmentedControl value={prefs.milestoneAccordionState} options={ACCORDION_OPTIONS} onChange={(v) => update({ milestoneAccordionState: v })} />
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
        </div>
      </section>

      {/* Dashboard */}
      <section>
        <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3">
          {t('settings.dashboardSection.heading')}
        </h2>
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
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
        </div>
      </section>

      {/* Navigation */}
      <section>
        <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3">
          {t('settings.navigation.heading')}
        </h2>
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
          <SettingRow label={t('settings.navigation.defaultProjectPage.label')} description={t('settings.navigation.defaultProjectPage.description')}>
            <SegmentedControl value={prefs.defaultProjectPage} options={PROJECT_PAGE_OPTIONS} onChange={(v) => update({ defaultProjectPage: v })} />
          </SettingRow>
        </div>
      </section>

      {/* Milestones */}
      <section>
        <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3">
          {t('settings.milestonesSection.heading')}
        </h2>
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
          <SettingRow label={t('settings.milestonesSection.progressFormat.label')} description={t('settings.milestonesSection.progressFormat.description')}>
            <SegmentedControl value={prefs.milestoneProgressFormat} options={PROGRESS_FORMAT_OPTIONS} onChange={(v) => update({ milestoneProgressFormat: v })} />
          </SettingRow>
        </div>
      </section>

      {hasAddon('API_KEYS') ? <ApiKeySection /> : <UpgradeCard featureName={t('settings.apiKeysFeatureName')} />}
    </div>
  );
}
