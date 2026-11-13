import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { useAuth } from '../../auth/AuthContext';
import Switch from '../../components/Switch';
import { useCurrentOrg } from '../org/OrgContext';
import { useOrgMembers } from '../org/useOrganisation';
import type { ProjectResponse } from '../../types';
import { useUpdateProjectSettings } from './useProjects';

interface Props {
  readonly projectId: string;
  readonly project: ProjectResponse;
  /** Project Owner or Admin. Everyone else sees the setting read-only. */
  readonly canEdit: boolean;
  /** Completed projects are read-only (the backend returns 409). */
  readonly isCompleted: boolean;
}

const LABEL_ID = 'completion-approval-label';
const LOCKED_ID = 'completion-approval-locked-note';
const READ_ONLY_ID = 'completion-approval-read-only-note';

function LockIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      className="w-3.5 h-3.5 shrink-0 text-gray-400 dark:text-gray-500"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  );
}

/**
 * ADR-0052 "Require approval to complete jobs" toggle, as one self-contained row so it can
 * move into the "Job screen" section later (ADR-0053).
 *
 * Turning the gate on needs the APPROVALS add-on to be usable for writes (see
 * `isAddonUsableForWrites`; the backend returns 403 otherwise). Without it the toggle is
 * locked and the gate isn't enforced, but an Owner/Admin can still turn off a gate that is
 * already on. Errors (403, 409) are shown as toasts by the API client.
 */
export default function CompletionApprovalSetting({ projectId, project, canEdit, isCompleted }: Props) {
  const { t } = useTranslation('projects');
  const { userId } = useAuth();
  const { org, subscription, isAddonUsableForWrites } = useCurrentOrg();
  const { data: orgMembers } = useOrgMembers(org?.id ?? null);
  const { mutate, isPending } = useUpdateProjectSettings(projectId);

  const enabled = project.requireCompletionApproval;
  const canChange = canEdit && !isCompleted;
  const addonLocked = !isAddonUsableForWrites('APPROVALS');
  const locked = !canChange || isPending || (addonLocked && !enabled);
  // Routed pages only render once the org has a subscription (OrgRequiredRoute), so a null
  // subscription here means OrgContext hasn't synced it yet: hold the lock note back.
  const showLockedNote = addonLocked && subscription !== null;
  const isOrgOwner = orgMembers?.find((m) => m.userId === userId)?.role === 'OWNER';
  const showReadOnlyNote = !canEdit && !isCompleted;

  function onChange(next: boolean) {
    mutate(
      { requireCompletionApproval: next },
      {
        onSuccess: () =>
          toast.success(next ? t('completionApproval.enabledToast') : t('completionApproval.disabledToast')),
      },
    );
  }

  let lockedText = t('completionApproval.lockedOff');
  if (enabled) {
    lockedText = canChange ? t('completionApproval.lockedOn') : t('completionApproval.lockedOnReadOnly');
  }

  const describedBy = [showLockedNote && LOCKED_ID, showReadOnlyNote && READ_ONLY_ID].filter(Boolean).join(' ');

  return (
    <div
      className="border border-gray-200 dark:border-gray-700 rounded-xl p-4 flex items-start justify-between gap-4"
      data-testid="completion-approval-setting"
    >
      <div className="min-w-0">
        <p id={LABEL_ID} className="text-sm font-medium text-gray-900 dark:text-gray-100">
          {t('completionApproval.label')}
        </p>
        <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{t('completionApproval.description')}</p>
        {showLockedNote && (
          <p
            id={LOCKED_ID}
            className="text-xs text-gray-500 dark:text-gray-400 mt-2 flex flex-wrap items-center gap-x-2 gap-y-1"
            data-testid="completion-approval-locked"
          >
            <LockIcon />
            <span>{lockedText}</span>
            {isOrgOwner ? (
              <Link to="/org/settings" className="text-brand hover:underline font-medium whitespace-nowrap">
                {t('completionApproval.upgradeLink')}
              </Link>
            ) : (
              <span>{t('completionApproval.askOrgOwner')}</span>
            )}
          </p>
        )}
        {showReadOnlyNote && (
          <p
            id={READ_ONLY_ID}
            className="text-xs text-gray-500 dark:text-gray-400 mt-2"
            data-testid="completion-approval-read-only"
          >
            {t('completionApproval.readOnly')}
          </p>
        )}
      </div>
      <Switch
        checked={enabled}
        locked={locked}
        labelledBy={LABEL_ID}
        describedBy={describedBy || undefined}
        testId="completion-approval-toggle"
        onChange={onChange}
      />
    </div>
  );
}
