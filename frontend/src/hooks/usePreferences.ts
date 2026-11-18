import { createContext, useContext } from 'react';

export type Theme = 'light' | 'dark' | 'system';
export type Locale = 'en' | 'sr';
export type ViewMode = 'GROUPED' | 'FLAT';
export type AccordionState = 'EXPANDED' | 'COLLAPSED';
export type StatusTab = 'ALL' | 'NEW' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED';
export type SortOrder = 'DEADLINE_ASC' | 'DEADLINE_DESC' | 'PRIORITY_DESC' | 'CREATED_DESC';
export type ProjectPage = 'DASHBOARD' | 'JOBS' | 'APPROVALS';
export type DeadlineFormat = 'ABSOLUTE' | 'RELATIVE';
export type ProgressFormat = 'FRACTION' | 'PERCENTAGE';
/** Days ahead a deadline counts as "due soon"; 0 turns the highlight off (ADR-0061). */
export type DueSoonDays = 0 | 1 | 3 | 5 | 7;
export type AssigneeFilter = 'EVERYONE' | 'MINE';
export type RowDensity = 'COMFORTABLE' | 'COMPACT';
export type DateFormat = 'AUTO' | 'DMY_DOT' | 'DMY_SLASH' | 'MDY_SLASH';
export type LandingProject = 'LIST' | 'LAST' | 'SPECIFIC';
export type StatsRange = 4 | 12 | 26;

export interface Preferences {
  theme: Theme;
  locale: Locale;
  defaultViewMode: ViewMode;
  milestoneAccordionState: AccordionState;
  defaultStatusTab: StatusTab;
  hideCompletedFromAll: boolean;
  defaultSortOrder: SortOrder;
  showBlockedSection: boolean;
  showOverdueSection: boolean;
  showPendingApprovalsSection: boolean;
  defaultProjectPage: ProjectPage;
  deadlineFormat: DeadlineFormat;
  milestoneProgressFormat: ProgressFormat;
  /** Chime for new live notifications (JOB-282). */
  notificationSound: boolean;
  dueSoonDays: DueSoonDays;
  defaultAssigneeFilter: AssigneeFilter;
  rowDensity: RowDensity;
  dateFormat: DateFormat;
  landingProject: LandingProject;
  /** Project UUID, used when `landingProject` is `SPECIFIC`. */
  landingProjectId: string | null;
  statsDefaultRange: StatsRange;
  showOverviewApprovals: boolean;
}

export const defaults: Preferences = {
  theme: 'system',
  locale: 'en',
  defaultViewMode: 'GROUPED',
  milestoneAccordionState: 'EXPANDED',
  defaultStatusTab: 'ALL',
  hideCompletedFromAll: false,
  defaultSortOrder: 'DEADLINE_ASC',
  showBlockedSection: true,
  showOverdueSection: true,
  showPendingApprovalsSection: true,
  defaultProjectPage: 'DASHBOARD',
  deadlineFormat: 'ABSOLUTE',
  milestoneProgressFormat: 'FRACTION',
  notificationSound: true,
  dueSoonDays: 3,
  defaultAssigneeFilter: 'EVERYONE',
  rowDensity: 'COMFORTABLE',
  dateFormat: 'AUTO',
  landingProject: 'LIST',
  landingProjectId: null,
  statsDefaultRange: 12,
  showOverviewApprovals: true,
};

// ---- sanitising (ADR-0061) ----

const oneOf =
  <T extends string | number>(...allowed: readonly T[]) =>
  (value: unknown): value is T =>
    allowed.includes(value as T);

const isBoolean = (value: unknown): value is boolean => typeof value === 'boolean';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const isProjectId = (value: unknown): value is string | null =>
  value === null || (typeof value === 'string' && UUID.test(value));

/** One check per field: a stored value is kept only if it passes. */
const validators: { [K in keyof Preferences]: (value: unknown) => value is Preferences[K] } = {
  theme: oneOf<Theme>('light', 'dark', 'system'),
  locale: oneOf<Locale>('en', 'sr'),
  defaultViewMode: oneOf<ViewMode>('GROUPED', 'FLAT'),
  milestoneAccordionState: oneOf<AccordionState>('EXPANDED', 'COLLAPSED'),
  defaultStatusTab: oneOf<StatusTab>('ALL', 'NEW', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED'),
  hideCompletedFromAll: isBoolean,
  defaultSortOrder: oneOf<SortOrder>('DEADLINE_ASC', 'DEADLINE_DESC', 'PRIORITY_DESC', 'CREATED_DESC'),
  showBlockedSection: isBoolean,
  showOverdueSection: isBoolean,
  showPendingApprovalsSection: isBoolean,
  defaultProjectPage: oneOf<ProjectPage>('DASHBOARD', 'JOBS', 'APPROVALS'),
  deadlineFormat: oneOf<DeadlineFormat>('ABSOLUTE', 'RELATIVE'),
  milestoneProgressFormat: oneOf<ProgressFormat>('FRACTION', 'PERCENTAGE'),
  notificationSound: isBoolean,
  dueSoonDays: oneOf<DueSoonDays>(0, 1, 3, 5, 7),
  defaultAssigneeFilter: oneOf<AssigneeFilter>('EVERYONE', 'MINE'),
  rowDensity: oneOf<RowDensity>('COMFORTABLE', 'COMPACT'),
  dateFormat: oneOf<DateFormat>('AUTO', 'DMY_DOT', 'DMY_SLASH', 'MDY_SLASH'),
  landingProject: oneOf<LandingProject>('LIST', 'LAST', 'SPECIFIC'),
  landingProjectId: isProjectId,
  statsDefaultRange: oneOf<StatsRange>(4, 12, 26),
  showOverviewApprovals: isBoolean,
};

const PREFERENCE_KEYS = Object.keys(defaults) as (keyof Preferences)[];

/**
 * Only the known keys of `raw` whose values are valid; anything that isn't a plain object
 * gives `{}`. The one-time upload sends this, so only what the user actually set is stored.
 */
export function sanitizePartial(raw: unknown): Partial<Preferences> {
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) return {};
  const source = raw as Record<string, unknown>;
  const result: Record<string, unknown> = {};
  for (const key of PREFERENCE_KEYS) {
    if (Object.prototype.hasOwnProperty.call(source, key) && validators[key](source[key])) {
      result[key] = source[key];
    }
  }
  return result as Partial<Preferences>;
}

/**
 * Full preferences from whatever was stored (server or cache): unknown keys are dropped,
 * and missing or invalid values fall back to their defaults, so a bad stored value can't
 * break the UI.
 */
export function sanitize(raw: unknown): Preferences {
  return { ...defaults, ...sanitizePartial(raw) };
}

// ---- context ----

export interface PreferencesContextValue {
  prefs: Preferences;
  update: (patch: Partial<Preferences>) => void;
}

export const PreferencesContext = createContext<PreferencesContextValue | null>(null);

export function usePreferences(): PreferencesContextValue {
  const ctx = useContext(PreferencesContext);
  if (!ctx) throw new Error('usePreferences must be used inside PreferencesProvider');
  return ctx;
}
