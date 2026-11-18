import type { JobStatus } from '../types';
import { usePreferences } from './usePreferences';
import { isPastDeadline } from '../utils/deadline';
import { formatDate, type DatePrefs } from '../utils/formatDate';

export interface FormattedDeadline {
  text: string;
  overdue: boolean;
}

function formatAbsolute(deadline: string, prefs: DatePrefs): string {
  return formatDate(deadline, prefs);
}

function formatRelative(deadline: string, prefs: DatePrefs, status?: JobStatus): FormattedDeadline {
  const diffDays = Math.ceil((new Date(deadline).getTime() - Date.now()) / 86_400_000);
  const isCompleted = status === 'COMPLETED';

  if (!isCompleted && isPastDeadline(deadline)) {
    const n = Math.max(1, Math.abs(diffDays));
    return { text: `${n} day${n === 1 ? '' : 's'} overdue`, overdue: true };
  }
  if (diffDays <= 0) return { text: formatAbsolute(deadline, prefs), overdue: false };
  if (diffDays === 1) return { text: 'in 1 day', overdue: false };
  return { text: `in ${diffDays} days`, overdue: false };
}

export function useFormatDeadline() {
  const { prefs } = usePreferences();

  return (deadline: string | null, status?: JobStatus): FormattedDeadline => {
    if (!deadline) return { text: '—', overdue: false };

    if (prefs.deadlineFormat === 'RELATIVE') {
      return formatRelative(deadline, prefs, status);
    }

    const overdue = status !== 'COMPLETED' && isPastDeadline(deadline);
    return { text: formatAbsolute(deadline, prefs), overdue };
  };
}
