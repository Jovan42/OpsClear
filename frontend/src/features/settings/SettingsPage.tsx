import type { ReactNode } from 'react';
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

// ---- option lists ----

const THEME_OPTIONS: { value: Theme; label: string }[] = [
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
  { value: 'system', label: 'System' },
];

const LOCALE_OPTIONS: { value: Locale; label: string }[] = [
  { value: 'en', label: 'English' },
  { value: 'sr', label: 'Srpski' },
];

const VIEW_MODE_OPTIONS: { value: ViewMode; label: string }[] = [
  { value: 'GROUPED', label: 'Grouped' },
  { value: 'FLAT', label: 'Flat' },
];

const ACCORDION_OPTIONS: { value: AccordionState; label: string }[] = [
  { value: 'EXPANDED', label: 'Expanded' },
  { value: 'COLLAPSED', label: 'Collapsed' },
];

const STATUS_TAB_OPTIONS: { value: StatusTab; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'NEW', label: 'New' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'BLOCKED', label: 'Blocked' },
  { value: 'COMPLETED', label: 'Completed' },
];

const HIDE_COMPLETED_OPTIONS: { value: 'true' | 'false'; label: string }[] = [
  { value: 'false', label: 'Show' },
  { value: 'true', label: 'Hide' },
];

const SORT_ORDER_OPTIONS: { value: SortOrder; label: string }[] = [
  { value: 'DEADLINE_ASC', label: 'Deadline ↑' },
  { value: 'DEADLINE_DESC', label: 'Deadline ↓' },
  { value: 'PRIORITY_DESC', label: 'Priority' },
  { value: 'CREATED_DESC', label: 'Newest first' },
];

const DEADLINE_FORMAT_OPTIONS: { value: DeadlineFormat; label: string }[] = [
  { value: 'ABSOLUTE', label: 'Absolute' },
  { value: 'RELATIVE', label: 'Relative' },
];

const SHOW_HIDE_OPTIONS: { value: 'true' | 'false'; label: string }[] = [
  { value: 'true', label: 'Show' },
  { value: 'false', label: 'Hide' },
];

const PROJECT_PAGE_OPTIONS: { value: ProjectPage; label: string }[] = [
  { value: 'DASHBOARD', label: 'Dashboard' },
  { value: 'JOBS', label: 'Jobs' },
  { value: 'APPROVALS', label: 'Approvals' },
];

const PROGRESS_FORMAT_OPTIONS: { value: ProgressFormat; label: string }[] = [
  { value: 'FRACTION', label: 'Fraction' },
  { value: 'PERCENTAGE', label: 'Percentage' },
];

// ---- page ----

export default function SettingsPage() {
  usePageTitle('Settings');
  const { prefs, update } = usePreferences();
  const { hasAddon } = useCurrentOrg();

  return (
    <div className="max-w-lg mx-auto px-4 py-10 space-y-8">
      <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">Settings</h1>

      {/* Appearance */}
      <section>
        <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3">
          Appearance
        </h2>
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
          <SettingRow label="Theme" description="Choose how OpsClear looks on this device.">
            <SegmentedControl value={prefs.theme} options={THEME_OPTIONS} onChange={(v) => update({ theme: v })} />
          </SettingRow>
          <SettingRow label="Language" description="Choose the language used across OpsClear.">
            <SegmentedControl value={prefs.locale} options={LOCALE_OPTIONS} onChange={(v) => update({ locale: v })} />
          </SettingRow>
          <SettingRow label="Deadline format" description="How deadlines are displayed across the app.">
            <SegmentedControl value={prefs.deadlineFormat} options={DEADLINE_FORMAT_OPTIONS} onChange={(v) => update({ deadlineFormat: v })} />
          </SettingRow>
        </div>
      </section>

      {/* Job list */}
      <section>
        <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3">
          Job list
        </h2>
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
          <SettingRow label="Default view mode" description="How jobs are grouped when you open a project.">
            <SegmentedControl value={prefs.defaultViewMode} options={VIEW_MODE_OPTIONS} onChange={(v) => update({ defaultViewMode: v })} />
          </SettingRow>
          <SettingRow label="Milestone accordion" description="Whether milestone groups start expanded or collapsed.">
            <SegmentedControl value={prefs.milestoneAccordionState} options={ACCORDION_OPTIONS} onChange={(v) => update({ milestoneAccordionState: v })} />
          </SettingRow>
          <SettingRow label="Default status tab" description="Which tab is selected when you open the job list.">
            <SelectControl value={prefs.defaultStatusTab} options={STATUS_TAB_OPTIONS} onChange={(v) => update({ defaultStatusTab: v })} />
          </SettingRow>
          <SettingRow label="Completed jobs in All tab" description="Whether completed jobs appear when the All tab is active.">
            <SegmentedControl
              value={prefs.hideCompletedFromAll ? 'true' : 'false'}
              options={HIDE_COMPLETED_OPTIONS}
              onChange={(v) => update({ hideCompletedFromAll: v === 'true' })}
            />
          </SettingRow>
          <SettingRow label="Default sort order" description="How jobs are sorted when you open the job list.">
            <SelectControl value={prefs.defaultSortOrder} options={SORT_ORDER_OPTIONS} onChange={(v) => update({ defaultSortOrder: v })} />
          </SettingRow>
        </div>
      </section>

      {/* Dashboard */}
      <section>
        <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3">
          Dashboard
        </h2>
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
          <SettingRow label="Blocked jobs section" description="Show or hide the blocked jobs list on the dashboard.">
            <SegmentedControl
              value={prefs.showBlockedSection ? 'true' : 'false'}
              options={SHOW_HIDE_OPTIONS}
              onChange={(v) => update({ showBlockedSection: v === 'true' })}
            />
          </SettingRow>
          <SettingRow label="Overdue jobs section" description="Show or hide the overdue jobs list on the dashboard.">
            <SegmentedControl
              value={prefs.showOverdueSection ? 'true' : 'false'}
              options={SHOW_HIDE_OPTIONS}
              onChange={(v) => update({ showOverdueSection: v === 'true' })}
            />
          </SettingRow>
          <SettingRow label="Pending approvals section" description="Show or hide the pending approvals list on the dashboard.">
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
          Navigation
        </h2>
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
          <SettingRow label="Default project page" description="Which page opens when you navigate to a project.">
            <SegmentedControl value={prefs.defaultProjectPage} options={PROJECT_PAGE_OPTIONS} onChange={(v) => update({ defaultProjectPage: v })} />
          </SettingRow>
        </div>
      </section>

      {/* Milestones */}
      <section>
        <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3">
          Milestones
        </h2>
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
          <SettingRow label="Progress format" description="How milestone progress is displayed (e.g. 4/6 or 67%).">
            <SegmentedControl value={prefs.milestoneProgressFormat} options={PROGRESS_FORMAT_OPTIONS} onChange={(v) => update({ milestoneProgressFormat: v })} />
          </SettingRow>
        </div>
      </section>

      {hasAddon('API_KEYS') ? <ApiKeySection /> : <UpgradeCard featureName="API Keys" />}
    </div>
  );
}
