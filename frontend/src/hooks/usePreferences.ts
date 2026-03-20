import { createContext, useContext } from 'react';

const STORAGE_KEY = 'opsclear:preferences';

export type Theme = 'light' | 'dark' | 'system';
export type ViewMode = 'GROUPED' | 'FLAT';
export type AccordionState = 'EXPANDED' | 'COLLAPSED';
export type StatusTab = 'ALL' | 'NEW' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED';
export type SortOrder = 'DEADLINE_ASC' | 'DEADLINE_DESC' | 'PRIORITY_DESC' | 'CREATED_DESC';
export type ProjectPage = 'DASHBOARD' | 'JOBS' | 'APPROVALS';

export interface Preferences {
  theme: Theme;
  defaultViewMode: ViewMode;
  milestoneAccordionState: AccordionState;
  defaultStatusTab: StatusTab;
  hideCompletedFromAll: boolean;
  defaultSortOrder: SortOrder;
  showBlockedSection: boolean;
  showOverdueSection: boolean;
  showPendingApprovalsSection: boolean;
  defaultProjectPage: ProjectPage;
}

export const defaults: Preferences = {
  theme: 'system',
  defaultViewMode: 'GROUPED',
  milestoneAccordionState: 'EXPANDED',
  defaultStatusTab: 'ALL',
  hideCompletedFromAll: false,
  defaultSortOrder: 'DEADLINE_ASC',
  showBlockedSection: true,
  showOverdueSection: true,
  showPendingApprovalsSection: true,
  defaultProjectPage: 'DASHBOARD',
};

export const PREFERENCES_STORAGE_KEY = STORAGE_KEY;

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
