import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import Button from '../../../components/Button';
import ConfirmModal from '../../../components/ConfirmModal';
import ApprovalDecisionModal from './ApprovalDecisionModal';
import { useCancelApproval } from '../useApprovals';
import { completionRequestMessage } from '../completionApproval';
import type { JobResponse } from '../../../types';
import { usePreferences } from '../../../hooks/usePreferences';
import { formatDateTime } from '../../../utils/formatDate';

type Role = 'OWNER' | 'ADMIN' | 'MEMBER' | null;

interface Props {
  job: JobResponse;
  /** Ids the job's queries are keyed by (route params on the job page). */
  projectId: string;
  jobId: string;
  role: Role;
  userId: string | null;
  /** False when approvals can't be acted on (no APPROVALS add-on, or a read-only project). */
  canAct: boolean;
}

/**
 * ADR-0052: shows a job's pending completion approval, mirroring `BlockedBanner`. Owners/Admins
 * approve or reject it; the requester and Owners/Admins can withdraw it.
 */
export default function CompletionRequestBanner({ job, projectId, jobId, role, userId, canAct }: Props) {
  const { t } = useTranslation(['jobsComponents', 'common']);
  const { prefs } = usePreferences();
  const queryClient = useQueryClient();
  const [decision, setDecision] = useState<'APPROVED' | 'REJECTED' | null>(null);
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const { mutate: cancel, isPending: isCancelling } = useCancelApproval(projectId, jobId);

  const pending = job.pendingCompletionApproval;
  if (!pending) return null;

  const isOwnerOrAdmin = role === 'OWNER' || role === 'ADMIN';
  const isRequester = userId !== null && pending.requesterId === userId;
  const canDecide = canAct && isOwnerOrAdmin;
  const canWithdraw = canAct && (isOwnerOrAdmin || isRequester);
  const message = completionRequestMessage(pending.message);

  function handleWithdraw() {
    if (!pending) return;
    cancel(pending.id, {
      onSuccess: () => setWithdrawOpen(false),
      // e.g. 409 when it was decided meanwhile (the API client shows the error): close and refresh
      // so the banner reflects the real state.
      onError: () => {
        setWithdrawOpen(false);
        void queryClient.invalidateQueries({ queryKey: ['jobs', projectId, jobId] });
      },
    });
  }

  return (
    <>
      <div
        data-testid="completion-request-banner"
        className="bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-900 rounded-lg px-4 py-3"
      >
        <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
          <div className="flex items-start gap-2 min-w-0">
            <span className="text-amber-500 dark:text-amber-400 text-base mt-0.5" aria-hidden="true">⏳</span>
            <div className="min-w-0">
              <p className="text-sm font-medium text-amber-800 dark:text-amber-300">
                {t('jobsComponents:completionRequestBanner.requestedBy', {
                  name: pending.requesterName ?? t('jobsComponents:unknownUser'),
                  time: formatDateTime(pending.requestedAt, prefs),
                })}
              </p>
              {message && (
                <p className="mt-0.5 text-sm text-amber-700 dark:text-amber-400 break-words">"{message}"</p>
              )}
            </div>
          </div>

          {(canDecide || canWithdraw) && (
            <div className="flex flex-wrap gap-2 shrink-0">
              {canWithdraw && (
                <Button variant="ghost" size="sm" onClick={() => setWithdrawOpen(true)}>
                  {t('jobsComponents:completionRequestBanner.withdraw')}
                </Button>
              )}
              {canDecide && (
                <>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => setDecision('REJECTED')}
                    style={{ color: '#dc2626' }}
                  >
                    {t('jobsComponents:approvalDecisionModal.reject')}
                  </Button>
                  <Button size="sm" onClick={() => setDecision('APPROVED')}>
                    {t('jobsComponents:approvalDecisionModal.approve')}
                  </Button>
                </>
              )}
            </div>
          )}
        </div>
      </div>

      {decision && (
        <ApprovalDecisionModal
          open
          onClose={() => setDecision(null)}
          projectId={projectId}
          jobId={jobId}
          approvalId={pending.id}
          decision={decision}
        />
      )}

      <ConfirmModal
        open={withdrawOpen}
        onClose={() => setWithdrawOpen(false)}
        onConfirm={handleWithdraw}
        title={t('jobsComponents:completionRequestBanner.withdrawModal.title')}
        message={t('jobsComponents:completionRequestBanner.withdrawModal.message')}
        confirmLabel={t('jobsComponents:completionRequestBanner.withdraw')}
        variant="danger"
        isPending={isCancelling}
      />
    </>
  );
}
