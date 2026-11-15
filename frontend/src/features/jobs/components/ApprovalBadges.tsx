import { useTranslation } from 'react-i18next';
import type { ApprovalKind, ApprovalStatus } from '../../../types';
import { isApprovedNotApplied } from '../completionApproval';

// ADR-0052: shared approval badges for every list that shows approvals (job approvals section,
// project queue, /overview, Dashboard), so completion approvals look the same everywhere.

const BADGE = 'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium whitespace-nowrap';
const PURPLE = 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300';

/** "Completion" for completion approvals; nothing for manual ones. */
export function ApprovalKindBadge({
  kind,
  className = '',
}: Readonly<{ kind: ApprovalKind | undefined; className?: string }>) {
  const { t } = useTranslation('jobsComponents');
  if (kind !== 'COMPLETION') return null;
  return (
    <span className={`${BADGE} ${PURPLE} ${className}`} data-testid="approval-kind-completion">
      {t('approvalState.completion')}
    </span>
  );
}

/** "Awaiting completion approval", for a job with a pending completion approval (job list). */
export function AwaitingCompletionBadge() {
  const { t } = useTranslation('jobsComponents');
  return (
    <span className={`${BADGE} ${PURPLE}`} data-testid="awaiting-completion-badge">
      {t('approvalState.awaitingCompletion')}
    </span>
  );
}

const STATUS_CLASSES: Record<ApprovalStatus, string> = {
  PENDING: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
  APPROVED: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  REJECTED: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  CANCELLED: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300',
};

const NOT_APPLIED_CLASSES = 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400';

const STATUS_KEYS: Record<ApprovalStatus, string> = {
  PENDING: 'approvalList.status.pending',
  APPROVED: 'approvalList.status.approved',
  REJECTED: 'approvalList.status.rejected',
  CANCELLED: 'approvalList.status.cancelled',
};

/** The approval's status; an approved completion approval that didn't complete the job reads "Approved, not applied". */
export function ApprovalStatusBadge({
  approval,
}: Readonly<{
  approval: { status: ApprovalStatus; kind?: ApprovalKind; completionApplied?: boolean | null };
}>) {
  const { t } = useTranslation('jobsComponents');
  if (isApprovedNotApplied(approval)) {
    return <span className={`${BADGE} ${NOT_APPLIED_CLASSES}`}>{t('approvalState.approvedNotApplied')}</span>;
  }
  return <span className={`${BADGE} ${STATUS_CLASSES[approval.status]}`}>{t(STATUS_KEYS[approval.status])}</span>;
}
