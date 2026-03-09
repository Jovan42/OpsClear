import { useState } from 'react';
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

function memberName(members: ProjectMemberResponse[], userId: string): string {
  return members.find((m) => m.userId === userId)?.userName ?? 'Unknown user';
}

function StatusBadge({ status }: { status: ApprovalResponse['status'] }) {
  const config = {
    PENDING: 'bg-orange-100 text-orange-700',
    APPROVED: 'bg-green-100 text-green-700',
    REJECTED: 'bg-red-100 text-red-700',
  };
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${config[status]}`}>
      {status.charAt(0) + status.slice(1).toLowerCase()}
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
  const { data: approvals = [] } = useApprovals(projectId, jobId);
  const [decision, setDecision] = useState<{
    approvalId: string;
    type: 'APPROVED' | 'REJECTED';
  } | null>(null);

  const isOwnerOrAdmin = role === 'OWNER' || role === 'ADMIN';

  if (approvals.length === 0) {
    return <p className="text-sm text-gray-400 py-2">No approval requests.</p>;
  }

  return (
    <>
      <div className="space-y-3">
        {approvals.map((approval) => (
          <div key={approval.id} className="border border-gray-200 rounded-lg px-4 py-3">
            <p className="text-sm font-medium text-gray-900 mb-1">{approval.description}</p>
            <div className="flex items-center gap-2 text-xs text-gray-500 mb-2">
              <span>Requested by {memberName(members, approval.requesterId)}</span>
              <span>·</span>
              <span>{formatDateTime(approval.requestedAt)}</span>
              <StatusBadge status={approval.status} />
            </div>

            {approval.status !== 'PENDING' && (
              <div className="text-xs text-gray-500 mt-1">
                {approval.approverId && (
                  <span>
                    {approval.status === 'APPROVED' ? 'Approved' : 'Rejected'} by{' '}
                    {memberName(members, approval.approverId)}
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
                  Reject
                </Button>
                <Button
                  size="sm"
                  onClick={() => setDecision({ approvalId: approval.id, type: 'APPROVED' })}
                >
                  Approve
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
