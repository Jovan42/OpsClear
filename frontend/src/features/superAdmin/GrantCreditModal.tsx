import { useState } from 'react';
import type { FormEvent } from 'react';
import { isAxiosError } from 'axios';
import { useTranslation } from 'react-i18next';
import Modal from '../../components/Modal';
import Button from '../../components/Button';
import { useGrantCredit, useSuperAdminOrganisations } from './useSuperAdminFeedback';

interface Props {
  readonly open: boolean;
  readonly onClose: () => void;
  /** Pre-set and locked when granting from a submission or from the ledger view
   *  for an already-selected org. Left undefined only for the fully standalone
   *  "Grant credit" action, where the admin picks any org from the list. */
  readonly orgId?: string;
  readonly orgName?: string;
  readonly submissionId?: string;
}

export default function GrantCreditModal({ open, onClose, orgId: fixedOrgId, orgName, submissionId }: Props) {
  const { t } = useTranslation(['superAdmin', 'common']);
  const { data: orgs } = useSuperAdminOrganisations();
  const { mutate: grant, isPending, error, reset } = useGrantCredit();

  const [selectedOrgId, setSelectedOrgId] = useState(fixedOrgId ?? '');
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const [syncWarning, setSyncWarning] = useState<string | null>(null);

  // Reset the form whenever the modal transitions closed -> open, per React's
  // documented "adjusting state when a prop changes" pattern (setState during
  // render, not in an effect) — avoids the cascading-render lint rule while still
  // clearing stale input from a previous open/close cycle on the same instance.
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) {
      setSelectedOrgId(fixedOrgId ?? '');
      setAmount('');
      setReason('');
      setSyncWarning(null);
      reset();
    }
  }

  const orgId = fixedOrgId ?? selectedOrgId;
  const parsedAmount = Number(amount);
  const canSubmit = !!orgId && Number.isInteger(parsedAmount) && parsedAmount > 0 && reason.trim() !== '';

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!canSubmit) return;
    grant(
      { orgId, amount: parsedAmount, reason: reason.trim(), submissionId },
      {
        onSuccess: (data) => {
          // The grant itself always succeeds (org_credits is the source of truth) —
          // but if Paddle sync was skipped/failed, keep the modal open with a warning
          // instead of silently closing, so the admin doesn't assume it went through.
          if (data.paddleSyncSkippedReason) {
            setSyncWarning(data.paddleSyncSkippedReason);
          } else {
            onClose();
          }
        },
      },
    );
  }

  return (
    <Modal open={open} onClose={onClose} title={t('superAdmin:grantModal.title')}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {t('superAdmin:grantModal.orgLabel')}
          </label>
          {fixedOrgId ? (
            <p className="text-sm text-gray-900 dark:text-gray-100 py-2">{orgName}</p>
          ) : (
            <select
              value={selectedOrgId}
              onChange={(e) => setSelectedOrgId(e.target.value)}
              required
              className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:border-transparent"
            >
              <option value="" disabled>{t('superAdmin:grantModal.orgPlaceholder')}</option>
              {(orgs ?? []).map((org) => (
                <option key={org.id} value={org.id}>{org.name}</option>
              ))}
            </select>
          )}
        </div>

        <div>
          <label htmlFor="grant-amount" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {t('superAdmin:grantModal.amountLabel')}
          </label>
          <input
            id="grant-amount"
            type="number"
            min={1}
            step={1}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            required
            className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:border-transparent"
          />
        </div>

        <div>
          <label htmlFor="grant-reason" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {t('superAdmin:grantModal.reasonLabel')}
          </label>
          <textarea
            id="grant-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={3}
            required
            className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:border-transparent"
          />
        </div>

        {syncWarning && (
          <div className="rounded-lg border border-amber-300 dark:border-amber-700 bg-amber-50 dark:bg-amber-900/20 px-3 py-2">
            <p className="text-sm font-medium text-amber-800 dark:text-amber-300">
              {t('superAdmin:grantModal.syncWarningTitle')}
            </p>
            <p className="text-sm text-amber-700 dark:text-amber-400 mt-1">
              {t(`superAdmin:grantModal.syncWarning.${syncWarning}`)}
            </p>
          </div>
        )}

        {error && (
          <p className="text-sm text-red-600 dark:text-red-400">
            {isAxiosError(error) && error.response?.data?.message
              ? (error.response.data.message as string)
              : t('superAdmin:grantModal.errorGeneric')}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {syncWarning ? t('superAdmin:grantModal.closeButton') : t('common:cancel')}
          </Button>
          {!syncWarning && (
            <Button type="submit" loading={isPending} disabled={!canSubmit}>
              {t('superAdmin:grantModal.submitButton')}
            </Button>
          )}
        </div>
      </form>
    </Modal>
  );
}
