import type { JobStatus } from '../types';
import { usePreferences } from './usePreferences';
import { deadlineState, type DeadlineState } from '../utils/deadline';
import { formatDate, type DatePrefs } from '../utils/formatDate';

export interface FormattedDeadline {
  text: string;
  /** Overdue, due soon (ADR-0061) or normal; see `deadlineState`. */
  state: DeadlineState;
}

function formatAbsolute(deadline: string, prefs: DatePrefs): string {
  return formatDate(deadline, prefs);
}

function formatRelative(deadline: string, prefs: DatePrefs, state: DeadlineState): string {
  const diffDays = Math.ceil((new Date(deadline).getTime() - Date.now()) / 86_400_000);

  if (state === 'overdue') {
    const n = Math.max(1, Math.abs(diffDays));
    return `${n} day${n === 1 ? '' : 's'} overdue`;
  }
  if (diffDays <= 0) return formatAbsolute(deadline, prefs);
  if (diffDays === 1) return 'in 1 day';
  return `in ${diffDays} days`;
}

export function useFormatDeadline() {
  const { prefs } = usePreferences();

  return (deadline: string | null, status?: JobStatus): FormattedDeadline => {
    if (!deadline) return { text: '—', state: 'normal' };

    const state = deadlineState(deadline, status, prefs.dueSoonDays);
    const text = prefs.deadlineFormat === 'RELATIVE'
      ? formatRelative(deadline, prefs, state)
      : formatAbsolute(deadline, prefs);
    return { text, state };
  };
}
