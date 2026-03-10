import { useState } from 'react';
import Modal from '../../../components/Modal';
import Button from '../../../components/Button';
import { useDecideApproval } from '../useApprovals';

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
      { onSuccess: () => { handleClose(); onSuccess?.(); } },
    );
  }

  const isApprove = decision === 'APPROVED';

  return (
    <Modal open={open} onClose={handleClose} title={isApprove ? 'Approve Request' : 'Reject Request'}>
      <div className="space-y-4">
        {is409 && (
          <p className="text-sm text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-900 rounded-lg px-3 py-2">
            This approval was already decided by another user.
          </p>
        )}
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            Comment <span className="text-gray-400 font-normal">(optional)</span>
          </label>
          <textarea
            rows={3}
            className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent resize-none"
            placeholder={isApprove ? 'e.g. Approved, order within budget' : 'e.g. Use alternative supplier'}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            autoFocus
          />
        </div>

        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={handleClose}>
            Cancel
          </Button>
          <Button
            variant={isApprove ? 'primary' : 'danger'}
            onClick={handleSubmit}
            loading={isPending}
          >
            {isApprove ? 'Approve' : 'Reject'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
