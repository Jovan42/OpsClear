import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Button from '../../components/Button';
import PageError from '../../components/PageError';
import Skeleton from '../../components/Skeleton';
import ApprovalDecisionModal from '../jobs/components/ApprovalDecisionModal';
import { useApprovalQueue } from './useApprovalQueue';
import { useProject, useProjectMembers, useProjectRole } from '../projects/useProjects';
import { usePageTitle } from '../../hooks/usePageTitle';
import type { PendingApprovalResponse, ProjectMemberResponse } from '../../types';

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

type DecisionState = {
  approvalId: string;
  jobId: string;
  type: 'APPROVED' | 'REJECTED';
} | null;

interface ApprovalCardProps {
  approval: PendingApprovalResponse;
  members: ProjectMemberResponse[];
  isOwnerOrAdmin: boolean;
  onDecide: (approvalId: string, jobId: string, type: 'APPROVED' | 'REJECTED') => void;
}

function ApprovalCard({ approval, members, isOwnerOrAdmin, onDecide }: ApprovalCardProps) {
  return (
    <div className="border border-gray-200 dark:border-gray-700 rounded-lg px-4 py-3 bg-white dark:bg-gray-800">
      <p className="text-sm font-medium text-gray-900 dark:text-gray-100 mb-1">{approval.description}</p>
      <p className="text-xs text-gray-500 dark:text-gray-400 mb-3">
        Requested by {memberName(members, approval.requesterId)} · {formatDateTime(approval.requestedAt)}
      </p>
      {isOwnerOrAdmin && (
        <div className="flex gap-2">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => onDecide(approval.id, approval.jobId, 'REJECTED')}
            style={{ color: '#dc2626' }}
          >
            Reject
          </Button>
          <Button
            size="sm"
            onClick={() => onDecide(approval.id, approval.jobId, 'APPROVED')}
          >
            Approve
          </Button>
        </div>
      )}
    </div>
  );
}

export default function ApprovalQueuePage() {
  const { projectId = '' } = useParams();
  const navigate = useNavigate();
  const { data: project } = useProject(projectId);
  const role = useProjectRole(projectId);
  const { data: approvals = [], isLoading, isError, refetch } = useApprovalQueue(projectId);
  const { data: members = [] } = useProjectMembers(projectId);
  usePageTitle('Approvals', project?.name);
  const [decision, setDecision] = useState<DecisionState>(null);

  if (role === 'MEMBER') {
    navigate(`/projects/${projectId}/jobs`, { replace: true });
    return null;
  }

  if (isLoading) {
    return (
      <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
        <Skeleton className="h-7 w-32 mb-6" />
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="border border-gray-200 dark:border-gray-700 rounded-lg px-4 py-3 bg-white dark:bg-gray-800 space-y-2">
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-3 w-1/2" />
              <div className="flex gap-2 pt-1">
                <Skeleton className="h-7 w-16 rounded-lg" />
                <Skeleton className="h-7 w-16 rounded-lg" />
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (isError) {
    return <PageError message="Failed to load approvals." onRetry={() => void refetch()} />;
  }

  const groupMap = new Map<string, { jobTitle: string; approvals: PendingApprovalResponse[] }>();
  for (const a of approvals) {
    if (!groupMap.has(a.jobId)) {
      groupMap.set(a.jobId, { jobTitle: a.jobTitle, approvals: [] });
    }
    groupMap.get(a.jobId)!.approvals.push(a);
  }

  for (const group of groupMap.values()) {
    group.approvals.sort(
      (a, b) => new Date(a.requestedAt).getTime() - new Date(b.requestedAt).getTime(),
    );
  }

  const groups = [...groupMap.entries()].sort(([, a], [, b]) => {
    const oldestA = Math.min(...a.approvals.map((x) => new Date(x.requestedAt).getTime()));
    const oldestB = Math.min(...b.approvals.map((x) => new Date(x.requestedAt).getTime()));
    return oldestA - oldestB;
  });

  const isOwnerOrAdmin = role === 'OWNER' || role === 'ADMIN';
  const totalPending = approvals.length;

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">
          Approvals
          {totalPending > 0 && (
            <span className="ml-2 text-sm font-medium px-2 py-0.5 rounded-full bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400">
              {totalPending} pending
            </span>
          )}
        </h1>
      </div>

      {groups.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 text-center">
          <p className="text-gray-500 dark:text-gray-400 text-sm">All caught up — no pending approvals.</p>
        </div>
      ) : (
        <div className="space-y-8">
          {groups.map(([jobId, group]) => (
            <div key={jobId}>
              <div className="flex items-center justify-between mb-3">
                <h2 className="text-sm font-semibold text-gray-800 dark:text-gray-200">{group.jobTitle}</h2>
                <button
                  onClick={() => navigate(`/projects/${projectId}/jobs/${jobId}`)}
                  className="text-xs text-brand hover:underline cursor-pointer"
                >
                  → Job
                </button>
              </div>
              <div className="space-y-3">
                {group.approvals.map((approval) => (
                  <ApprovalCard
                    key={approval.id}
                    approval={approval}
                    members={members}
                    isOwnerOrAdmin={isOwnerOrAdmin}
                    onDecide={(approvalId, jId, type) =>
                      setDecision({ approvalId, jobId: jId, type })
                    }
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {decision && (
        <ApprovalDecisionModal
          open
          onClose={() => setDecision(null)}
          projectId={projectId}
          jobId={decision.jobId}
          approvalId={decision.approvalId}
          decision={decision.type}
        />
      )}
    </div>
  );
}
