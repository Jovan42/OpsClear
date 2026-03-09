import { useState } from 'react';
import Modal from '../../../components/Modal';
import Button from '../../../components/Button';
import { useDecideApproval } from '../useApprovals';

interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
  jobId: string;
  approvalId: string;
  decision: 'APPROVED' | 'REJECTED';
}

export default function ApprovalDecisionModal({
  open,
  onClose,
  projectId,
  jobId,
  approvalId,
  decision,
}: Props) {
  const [comment, setComment] = useState('');
  const { mutate: decide, isPending } = useDecideApproval(projectId, jobId);

  function handleClose() {
    setComment('');
    onClose();
  }

  function handleSubmit() {
    decide(
      { approvalId, status: decision, comment: comment.trim() || undefined },
      { onSuccess: handleClose },
    );
  }

  const isApprove = decision === 'APPROVED';

  return (
    <Modal open={open} onClose={handleClose} title={isApprove ? 'Approve Request' : 'Reject Request'}>
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Comment <span className="text-gray-400 font-normal">(optional)</span>
          </label>
          <textarea
            rows={3}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:border-transparent resize-none"
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
