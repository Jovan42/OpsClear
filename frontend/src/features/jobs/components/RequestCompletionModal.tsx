import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import Modal from '../../../components/Modal';
import Button from '../../../components/Button';
import { isCompletionRequested, useUpdateJobStatus } from '../useJobs';

const MESSAGE_MAX = 2000;

interface Props {
  open: boolean;
  onClose: () => void;
  /** Ids the job's queries are keyed by (route params on the job page). */
  projectId: string;
  jobId: string;
}

/**
 * ADR-0052: confirm dialog for a Member completing a job on a gated project. Sends the
 * COMPLETED status change with an optional message; the backend answers 202 and creates a
 * completion approval, so the job stays In Progress and the request banner appears.
 */
export default function RequestCompletionModal({ open, onClose, projectId, jobId }: Props) {
  const { t } = useTranslation(['jobsComponents', 'common']);
  const queryClient = useQueryClient();
  const [message, setMessage] = useState('');
  const { mutate: updateStatus, isPending } = useUpdateJobStatus(projectId);

  const isOverLimit = message.length > MESSAGE_MAX;

  function handleClose() {
    setMessage('');
    onClose();
  }

  function handleSubmit() {
    if (isOverLimit) return;
    updateStatus(
      { jobId, status: 'COMPLETED', message: message.trim() || undefined },
      {
        onSuccess: (job) => {
          // 200 means the gate no longer applied (e.g. it was turned off meanwhile) and the
          // job was completed directly; the page simply shows it as Completed.
          if (isCompletionRequested(job)) {
            toast.success(t('jobsComponents:requestCompletionModal.requestedToast'));
          }
          handleClose();
        },
        onError: (error) => {
          // 409: a completion request is already pending (double click, or someone else asked
          // first). The API client shows the error; refresh the job so its banner shows the
          // existing request.
          if ((error as { response?: { status?: number } }).response?.status === 409) {
            void queryClient.invalidateQueries({ queryKey: ['jobs', projectId, jobId] });
            handleClose();
          }
        },
      },
    );
  }

  return (
    <Modal open={open} onClose={handleClose} title={t('jobsComponents:requestCompletionModal.title')}>
      <div className="space-y-4">
        <p className="text-sm text-gray-600 dark:text-gray-400">
          {t('jobsComponents:requestCompletionModal.explanation')}
        </p>
        <div>
          <label
            htmlFor="completion-request-message"
            className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1"
          >
            {t('jobsComponents:requestCompletionModal.messageLabel')}{' '}
            <span className="text-gray-400 font-normal">({t('common:optional')})</span>
          </label>
          <textarea
            id="completion-request-message"
            rows={3}
            className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent resize-none"
            placeholder={t('jobsComponents:requestCompletionModal.messagePlaceholder')}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            autoFocus
          />
          <div className="flex justify-end mt-1">
            <span className={`text-xs ${isOverLimit ? 'text-red-600' : 'text-gray-400 dark:text-gray-500'}`}>
              {message.length}/{MESSAGE_MAX}
            </span>
          </div>
        </div>

        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={handleClose}>
            {t('common:cancel')}
          </Button>
          <Button onClick={handleSubmit} disabled={isOverLimit} loading={isPending}>
            {t('jobsComponents:requestCompletionModal.submit')}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
