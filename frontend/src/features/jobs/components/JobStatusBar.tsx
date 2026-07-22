import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from '../../../components/Button';
import ConfirmModal from '../../../components/ConfirmModal';
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
  projectCompleted?: boolean;
}

export default function JobStatusBar({
  job,
  role,
  userId,
  onStatusChange,
  onBlock,
  onRequestApproval,
  isPending,
  projectCompleted,
}: Props) {
  const { t } = useTranslation('jobsComponents');
  const isOwnerOrAdmin = role === 'OWNER' || role === 'ADMIN';
  const isAssigned = job.assignedTo === userId;
  const canAct = isOwnerOrAdmin || isAssigned;

  const [confirmComplete, setConfirmComplete] = useState(false);
  const [confirmUnblock, setConfirmUnblock] = useState(false);

  if (!canAct && !isOwnerOrAdmin) return null;
  if (projectCompleted) return null;

  return (
    <>
      <div className="flex flex-wrap gap-2">
        {job.status === 'NEW' && canAct && (
          <Button onClick={() => onStatusChange('IN_PROGRESS')} loading={isPending} size="sm">
            {t('jobsComponents:jobStatusBar.start')}
          </Button>
        )}

        {job.status === 'IN_PROGRESS' && canAct && (
          <>
            <Button onClick={() => setConfirmComplete(true)} loading={isPending} size="sm">
              {t('jobsComponents:jobStatusBar.complete')}
            </Button>
            <Button variant="secondary" onClick={onBlock} size="sm">
              {t('jobsComponents:jobStatusBar.block')}
            </Button>
          </>
        )}

        {job.status === 'BLOCKED' && canAct && (
          <Button onClick={() => setConfirmUnblock(true)} loading={isPending} size="sm">
            {t('jobsComponents:jobStatusBar.unblock')}
          </Button>
        )}

        {job.status === 'COMPLETED' && isOwnerOrAdmin && (
          <Button
            variant="secondary"
            onClick={() => onStatusChange('IN_PROGRESS')}
            loading={isPending}
            size="sm"
          >
            {t('jobsComponents:jobStatusBar.reopen')}
          </Button>
        )}

        {job.status !== 'COMPLETED' && canAct && (
          <Button variant="secondary" onClick={onRequestApproval} size="sm">
            {t('jobsComponents:jobStatusBar.requestApproval')}
          </Button>
        )}
      </div>

      <ConfirmModal
        open={confirmComplete}
        onClose={() => setConfirmComplete(false)}
        onConfirm={() => { setConfirmComplete(false); onStatusChange('COMPLETED'); }}
        title={t('jobsComponents:jobStatusBar.completeModal.title')}
        message={t('jobsComponents:jobStatusBar.completeModal.message')}
        confirmLabel={t('jobsComponents:jobStatusBar.completeModal.confirmLabel')}
        isPending={isPending}
      />

      <ConfirmModal
        open={confirmUnblock}
        onClose={() => setConfirmUnblock(false)}
        onConfirm={() => { setConfirmUnblock(false); onStatusChange('IN_PROGRESS'); }}
        title={t('jobsComponents:jobStatusBar.unblockModal.title')}
        message={t('jobsComponents:jobStatusBar.unblockModal.message')}
        confirmLabel={t('jobsComponents:jobStatusBar.unblock')}
        isPending={isPending}
      />
    </>
  );
}
