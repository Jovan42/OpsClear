import type { TFunction } from 'i18next';
import type { JobStatus } from '../types';

/**
 * i18n keys for each job status label: the single mapping behind StatusBadge, the status history
 * duration line, notifications and the approval "not applied" reason, so they can't drift.
 */
export const STATUS_KEYS: Record<JobStatus, string> = {
  NEW: 'shared2:status.new',
  IN_PROGRESS: 'shared2:status.inProgress',
  BLOCKED: 'shared2:status.blocked',
  COMPLETED: 'shared2:status.completed',
};

/**
 * A job status sent as a plain string (e.g. a history entry's `changedTo`, or a completion
 * approval's "not applied" reason) as its translated label. An unknown value is shown as is, a
 * missing one as a dash.
 */
export function jobStatusLabel(status: string | null | undefined, t: TFunction): string {
  if (!status) return '—';
  return status in STATUS_KEYS ? t(STATUS_KEYS[status as JobStatus]) : status;
}
