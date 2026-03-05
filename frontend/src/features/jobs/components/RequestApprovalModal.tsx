import { useState } from 'react';
import Modal from '../../../components/Modal';
import Button from '../../../components/Button';
import { useRequestApproval } from '../useApprovals';

const DESC_MAX = 500;

interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
  jobId: string;
}

export default function RequestApprovalModal({ open, onClose, projectId, jobId }: Props) {
  const [description, setDescription] = useState('');
  const { mutate: requestApproval, isPending } = useRequestApproval(projectId, jobId);

  const isOverLimit = description.length > DESC_MAX;
  const isEmpty = description.trim().length === 0;

  function handleClose() {
    setDescription('');
    onClose();
  }

  function handleSubmit() {
    if (isEmpty || isOverLimit) return;
    requestApproval(description.trim(), { onSuccess: handleClose });
  }

  return (
    <Modal open={open} onClose={handleClose} title="Request Approval">
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Description <span className="text-red-500">*</span>
          </label>
          <textarea
            rows={4}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:border-transparent resize-none"
            placeholder="What needs approval and why…"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            autoFocus
          />
          <div className="flex justify-end mt-1">
            <span className={`text-xs ${isOverLimit ? 'text-red-600' : 'text-gray-400'}`}>
              {description.length}/{DESC_MAX}
            </span>
          </div>
        </div>

        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={handleClose}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={isEmpty || isOverLimit} loading={isPending}>
            Submit
          </Button>
        </div>
      </div>
    </Modal>
  );
}
