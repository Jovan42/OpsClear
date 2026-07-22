import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import Button from '../../../components/Button';
import ApprovalDecisionModal from './ApprovalDecisionModal';
import { useApprovals } from '../useApprovals';
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

function StatusBadge({ status, t }: { status: ApprovalResponse['status']; t: TFunction }) {
  const config = {
    PENDING: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
    APPROVED: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    REJECTED: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  };
  const labels = {
    PENDING: t('jobsComponents:approvalList.status.pending'),
    APPROVED: t('jobsComponents:approvalList.status.approved'),
    REJECTED: t('jobsComponents:approvalList.status.rejected'),
  };
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${config[status]}`}>
      {labels[status]}
    </span>
  );
}

interface Props {
  projectId: string;
  jobId: string;
  role: Role;
  members: ProjectMemberResponse[];
}

export default function ApprovalList({ projectId, jobId, role, members }: Props) {
  const { t } = useTranslation(['jobsComponents', 'common']);
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
            <p className="text-sm font-medium text-gray-900 dark:text-gray-100 mb-1">{approval.description}</p>
            <div className="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400 mb-2">
              <span>{t('jobsComponents:approvalList.requestedBy', { name: memberName(members, approval.requesterId, t) })}</span>
              <span>·</span>
              <span>{formatDateTime(approval.requestedAt)}</span>
              <StatusBadge status={approval.status} t={t} />
            </div>

            {approval.status !== 'PENDING' && (
              <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                {approval.approverId && (
                  <span>
                    {approval.status === 'APPROVED'
                      ? t('jobsComponents:approvalList.approvedBy', { name: memberName(members, approval.approverId, t) })
                      : t('jobsComponents:approvalList.rejectedBy', { name: memberName(members, approval.approverId, t) })}
                    {approval.decidedAt && ` · ${formatDateTime(approval.decidedAt)}`}
                  </span>
                )}
                {approval.comment && (
                  <p className="mt-1 italic">"{approval.comment}"</p>
                )}
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
