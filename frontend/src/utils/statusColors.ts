/**
 * Value colour for the summary numbers on the Dashboard (SummaryCard) and the Stats page (Tile).
 * Shared so the two pages read as one system and can't drift (ADR-0050, JOB-322).
 */
export const SUMMARY_VALUE_CLASSES = {
  NEW: 'text-amber-600',
  IN_PROGRESS: 'text-blue-600',
  BLOCKED: 'text-red-600',
  COMPLETED: 'text-green-600',
  OVERDUE: 'text-orange-600',
  PENDING_APPROVALS: 'text-purple-600',
} as const;

/** Neutral value colour, for a metric with no value ("—"). */
export const SUMMARY_VALUE_NEUTRAL_CLASS = 'text-gray-900 dark:text-gray-100';
