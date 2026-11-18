const DAY_MS = 86_400_000;

/**
 * Deadlines are date-only, stored as midnight UTC of the chosen date, and cover that
 * whole day: a deadline is past once `at` reaches the next UTC midnight. Same rule as
 * the backend's overdue checks.
 */
export function isPastDeadline(deadline: string, at: Date = new Date()): boolean {
  const d = new Date(deadline);
  const dayStart = Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate());
  return at.getTime() >= dayStart + DAY_MS;
}

export type DeadlineState = 'overdue' | 'dueSoon' | 'normal';

/**
 * How a job's deadline should look (ADR-0061). Overdue wins, and a completed job is never
 * highlighted. Due soon means not completed, not overdue, and the deadline's date is at most
 * `dueSoonDays` calendar days from today in the viewer's zone (today included);
 * `dueSoonDays = 0` turns it off.
 */
export function deadlineState(
  deadline: string | null | undefined,
  status: string | null | undefined,
  dueSoonDays: number,
  at: Date = new Date(),
): DeadlineState {
  if (!deadline || status === 'COMPLETED') return 'normal';
  if (isPastDeadline(deadline, at)) return 'overdue';
  if (dueSoonDays <= 0) return 'normal';
  const d = new Date(deadline);
  // The deadline's date is its UTC date (stored as UTC midnight); today is the viewer's.
  const deadlineDay = Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate());
  const today = Date.UTC(at.getFullYear(), at.getMonth(), at.getDate());
  const daysLeft = Math.round((deadlineDay - today) / DAY_MS);
  return daysLeft <= dueSoonDays ? 'dueSoon' : 'normal';
}

/** Text colour for a deadline in `state`; `normal` is the caller's usual colour. */
export function deadlineTextClass(state: DeadlineState, normal: string): string {
  if (state === 'overdue') return 'text-red-600 dark:text-red-400 font-medium';
  if (state === 'dueSoon') return 'text-amber-600 dark:text-amber-400 font-medium';
  return normal;
}
