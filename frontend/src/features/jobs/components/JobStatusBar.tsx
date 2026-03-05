import Button from '../../../components/Button';
import type { JobResponse, JobStatus } from '../../../types';

type Role = 'OWNER' | 'ADMIN' | 'MEMBER' | null;

interface Props {
  job: JobResponse;
  role: Role;
  userId: string | null;
  onStatusChange: (status: JobStatus) => void;
  onBlock: () => void;
  onRequestApproval: () => void;
  isPending: boolean;
}

export default function JobStatusBar({
  job,
  role,
  userId,
  onStatusChange,
  onBlock,
  onRequestApproval,
  isPending,
}: Props) {
  const isOwnerOrAdmin = role === 'OWNER' || role === 'ADMIN';
  const isAssigned = job.assignedTo === userId;
  const canAct = isOwnerOrAdmin || isAssigned;

  const handleComplete = () => {
    if (
      window.confirm(
        'Mark this job as complete? This cannot be undone by the assigned member.',
      )
    ) {
      onStatusChange('COMPLETED');
    }
  };

  const handleUnblock = () => {
    if (window.confirm('Unblock this job and resume work?')) {
      onStatusChange('IN_PROGRESS');
    }
  };

  if (!canAct && !isOwnerOrAdmin) return null;

  return (
    <div className="flex flex-wrap gap-2">
      {job.status === 'NEW' && canAct && (
        <Button onClick={() => onStatusChange('IN_PROGRESS')} loading={isPending} size="sm">
          Start
        </Button>
      )}

      {job.status === 'IN_PROGRESS' && canAct && (
        <>
          <Button onClick={handleComplete} loading={isPending} size="sm">
            Complete
          </Button>
          <Button variant="secondary" onClick={onBlock} size="sm">
            Block
          </Button>
        </>
      )}

      {job.status === 'BLOCKED' && canAct && (
        <Button onClick={handleUnblock} loading={isPending} size="sm">
          Unblock
        </Button>
      )}

      {job.status === 'COMPLETED' && isOwnerOrAdmin && (
        <Button variant="secondary" onClick={() => onStatusChange('IN_PROGRESS')} loading={isPending} size="sm">
          Reopen
        </Button>
      )}

      {job.status !== 'COMPLETED' && canAct && (
        <Button variant="secondary" onClick={onRequestApproval} size="sm">
          Request Approval
        </Button>
      )}
    </div>
  );
}
