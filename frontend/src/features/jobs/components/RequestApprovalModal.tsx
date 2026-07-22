import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Modal from '../../../components/Modal';
import Button from '../../../components/Button';
import { useRequestApproval } from '../useApprovals';

const DESC_MAX = 2000;

interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
  jobId: string;
}

export default function RequestApprovalModal({ open, onClose, projectId, jobId }: Props) {
  const { t } = useTranslation(['jobsComponents', 'common']);
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
    <Modal open={open} onClose={handleClose} title={t('jobsComponents:jobStatusBar.requestApproval')}>
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {t('jobsComponents:requestApprovalModal.descriptionLabel')} <span className="text-red-500">*</span>
          </label>
          <textarea
            rows={4}
            className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent resize-none"
            placeholder={t('jobsComponents:requestApprovalModal.placeholder')}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            autoFocus
          />
          <div className="flex justify-end mt-1">
            <span className={`text-xs ${isOverLimit ? 'text-red-600' : 'text-gray-400 dark:text-gray-500'}`}>
              {description.length}/{DESC_MAX}
            </span>
          </div>
        </div>

        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={handleClose}>
            {t('common:cancel')}
          </Button>
          <Button onClick={handleSubmit} disabled={isEmpty || isOverLimit} loading={isPending}>
            {t('common:submit')}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
