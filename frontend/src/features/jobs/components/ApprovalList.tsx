import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import Button from '../../../components/Button';
import ApprovalDecisionModal from './ApprovalDecisionModal';
import { ApprovalKindBadge, ApprovalStatusBadge } from './ApprovalBadges';
import { useApprovals } from '../useApprovals';
import { approvalDescription, autoCancelReason, isApprovedNotApplied } from '../completionApproval';
import { jobStatusLabel } from '../../notifications/notificationContent';
import type { ApprovalResponse, ProjectMemberResponse } from '../../../types';

type Role = 'OWNER' | 'ADMIN' | 'MEMBER' | null;

function formatDateTime(dateStr: string) {
  return new Date(dateStr).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function memberName(members: ProjectMemberResponse[], userId: string, t: TFunction): string {
  return members.find((m) => m.userId === userId)?.userName ?? t('jobsComponents:unknownUser');
}

// ADR-0052: a CANCELLED completion approval records who cancelled it as the approver: the
// requester or an Owner/Admin withdrawing it, or the Owner/Admin who completed or deleted the job.
const DECIDED_BY_KEYS = {
  APPROVED: 'jobsComponents:approvalList.approvedBy',
  REJECTED: 'jobsComponents:approvalList.rejectedBy',
  CANCELLED: 'jobsComponents:approvalList.cancelledBy',
} as const;

// The backend's English comment on an automatic cancellation, as a translated sentence.
const AUTO_CANCEL_KEYS = {
  COMPLETED_DIRECTLY: 'jobsComponents:approvalState.completedDirectly',
  JOB_DELETED: 'jobsComponents:approvalState.jobDeleted',
} as const;

/** The decision comment, quoted; an automatic cancellation's reason is translated instead. */
function DecisionComment({ approval, t }: { approval: ApprovalResponse; t: TFunction }) {
  if (!approval.comment) return null;
  const reason = approval.status === 'CANCELLED' ? autoCancelReason(approval.comment) : null;
  if (reason) return <p className="mt-1">{t(AUTO_CANCEL_KEYS[reason])}</p>;
  return <p className="mt-1 italic">"{approval.comment}"</p>;
}

interface Props {
  projectId: string;
  jobId: string;
  role: Role;
  members: ProjectMemberResponse[];
}

export default function ApprovalList({ projectId, jobId, role, members }: Props) {
  const { t } = useTranslation(['jobsComponents', 'common', 'shared2']);
  const { data: approvals = [] } = useApprovals(projectId, jobId);
  const [decision, setDecision] = useState<{
    approvalId: string;
    type: 'APPROVED' | 'REJECTED';
  } | null>(null);

  const isOwnerOrAdmin = role === 'OWNER' || role === 'ADMIN';

  if (approvals.length === 0) {
    return <p className="text-sm text-gray-400 dark:text-gray-500 py-2">{t('jobsComponents:approvalList.noRequests')}</p>;
  }

  return (
    <>
      <div className="space-y-3">
        {approvals.map((approval) => (
          <div key={approval.id} className="border border-gray-200 dark:border-gray-700 rounded-lg px-4 py-3">
            <p className="text-sm font-medium text-gray-900 dark:text-gray-100 mb-1">{approvalDescription(approval, t)}</p>
            <div className="flex flex-wrap items-center gap-2 text-xs text-gray-500 dark:text-gray-400 mb-2">
              <span>{t('jobsComponents:approvalList.requestedBy', { name: memberName(members, approval.requesterId, t) })}</span>
              <span>·</span>
              <span>{formatDateTime(approval.requestedAt)}</span>
              <ApprovalKindBadge kind={approval.kind} />
              <ApprovalStatusBadge approval={approval} />
            </div>

            {approval.status !== 'PENDING' && (
              <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                {approval.approverId && (
                  <span>
                    {t(DECIDED_BY_KEYS[approval.status], { name: memberName(members, approval.approverId, t) })}
                    {approval.decidedAt && ` · ${formatDateTime(approval.decidedAt)}`}
                  </span>
                )}
                {isApprovedNotApplied(approval) && (
                  <p className="mt-1 text-amber-700 dark:text-amber-400">
                    {t('jobsComponents:approvalState.notAppliedReason', {
                      status: jobStatusLabel(approval.completionNotAppliedReason, t),
                    })}
                  </p>
                )}
                <DecisionComment approval={approval} t={t} />
              </div>
            )}

            {approval.status === 'PENDING' && isOwnerOrAdmin && (
              <div className="flex gap-2 mt-2">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => setDecision({ approvalId: approval.id, type: 'REJECTED' })}
                  style={{ color: '#dc2626' }}
                >
                  {t('jobsComponents:approvalDecisionModal.reject')}
                </Button>
                <Button
                  size="sm"
                  onClick={() => setDecision({ approvalId: approval.id, type: 'APPROVED' })}
                >
                  {t('jobsComponents:approvalDecisionModal.approve')}
                </Button>
              </div>
            )}
          </div>
        ))}
      </div>

      {decision && (
        <ApprovalDecisionModal
          open
          onClose={() => setDecision(null)}
          projectId={projectId}
          jobId={jobId}
          approvalId={decision.approvalId}
          decision={decision.type}
        />
      )}
    </>
  );
}
