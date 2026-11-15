import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import Modal from '../../../components/Modal';
import Button from '../../../components/Button';
import { useDecideApproval } from '../useApprovals';
import type { JobStatus } from '../../../types';

const STATUS_LABEL_KEYS: Record<JobStatus, string> = {
  NEW: 'shared2:status.new',
  IN_PROGRESS: 'shared2:status.inProgress',
  BLOCKED: 'shared2:status.blocked',
  COMPLETED: 'shared2:status.completed',
};

interface Props {
  open: boolean;
  onClose: () => void;
  onSuccess?: () => void;
  projectId: string;
  jobId: string;
  approvalId: string;
  decision: 'APPROVED' | 'REJECTED';
}

export default function ApprovalDecisionModal({
  open,
  onClose,
  onSuccess,
  projectId,
  jobId,
  approvalId,
  decision,
}: Props) {
  const { t } = useTranslation(['jobsComponents', 'common', 'shared2']);
  const [comment, setComment] = useState('');
  const { mutate: decide, isPending, error, reset } = useDecideApproval(projectId, jobId);

  const is409 = (error as { response?: { status?: number } } | null)?.response?.status === 409;

  function handleClose() {
    setComment('');
    reset();
    onClose();
  }

  function handleSubmit() {
    decide(
      { approvalId, status: decision, comment: comment.trim() || undefined },
      {
        onSuccess: (approval) => {
          // ADR-0052: an approved completion approval is "not applied" when the job left
          // In Progress meanwhile (e.g. it was blocked). The approver is told right away.
          if (approval.completionApplied === false) {
            const reason = approval.completionNotAppliedReason;
            const statusKey = reason && reason in STATUS_LABEL_KEYS ? STATUS_LABEL_KEYS[reason as JobStatus] : null;
            toast.warning(
              t('jobsComponents:approvalDecisionModal.completionNotApplied', {
                status: statusKey ? t(statusKey) : (reason ?? '—'),
              }),
            );
          }
          handleClose();
          onSuccess?.();
        },
      },
    );
  }

  const isApprove = decision === 'APPROVED';

  return (
    <Modal open={open} onClose={handleClose} title={isApprove ? t('jobsComponents:approvalDecisionModal.approveTitle') : t('jobsComponents:approvalDecisionModal.rejectTitle')}>
      <div className="space-y-4">
        {is409 && (
          <p className="text-sm text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-900 rounded-lg px-3 py-2">
            {t('jobsComponents:approvalDecisionModal.alreadyDecided')}
          </p>
        )}
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {t('jobsComponents:approvalDecisionModal.commentLabel')} <span className="text-gray-400 font-normal">({t('common:optional')})</span>
          </label>
          <textarea
            rows={3}
            className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent resize-none"
            placeholder={isApprove ? t('jobsComponents:approvalDecisionModal.approveCommentPlaceholder') : t('jobsComponents:approvalDecisionModal.rejectCommentPlaceholder')}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            autoFocus
          />
        </div>

        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={handleClose}>
            {t('common:cancel')}
          </Button>
          <Button
            variant={isApprove ? 'primary' : 'danger'}
            onClick={handleSubmit}
            loading={isPending}
          >
            {isApprove ? t('jobsComponents:approvalDecisionModal.approve') : t('jobsComponents:approvalDecisionModal.reject')}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
