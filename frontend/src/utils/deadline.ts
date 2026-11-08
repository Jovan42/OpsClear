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
