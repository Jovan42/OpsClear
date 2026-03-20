import type { JobStatus } from '../types';
import { usePreferences } from './usePreferences';

export interface FormattedDeadline {
  text: string;
  overdue: boolean;
}

function formatAbsolute(deadline: string): string {
  return new Date(deadline).toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

function formatRelative(deadline: string, status?: JobStatus): FormattedDeadline {
  const diffDays = Math.ceil((new Date(deadline).getTime() - Date.now()) / 86_400_000);
  const isCompleted = status === 'COMPLETED';

  if (diffDays < 0 && !isCompleted) {
    const n = Math.abs(diffDays);
    return { text: `${n} day${n === 1 ? '' : 's'} overdue`, overdue: true };
  }
  if (diffDays <= 0) return { text: formatAbsolute(deadline), overdue: false };
  if (diffDays === 1) return { text: 'in 1 day', overdue: false };
  return { text: `in ${diffDays} days`, overdue: false };
}

export function useFormatDeadline() {
  const { prefs } = usePreferences();

  return (deadline: string | null, status?: JobStatus): FormattedDeadline => {
    if (!deadline) return { text: '—', overdue: false };

    if (prefs.deadlineFormat === 'RELATIVE') {
      return formatRelative(deadline, status);
    }

    const overdue = status !== 'COMPLETED' && new Date(deadline) < new Date();
    return { text: formatAbsolute(deadline), overdue };
  };
}
