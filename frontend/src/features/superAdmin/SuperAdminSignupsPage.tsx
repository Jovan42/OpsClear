import { useState } from 'react';
import { isAxiosError } from 'axios';
import { useTranslation } from 'react-i18next';
import { Navigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Download, Trash2 } from 'lucide-react';
import Skeleton from '../../components/Skeleton';
import Button from '../../components/Button';
import ConfirmModal from '../../components/ConfirmModal';
import PageError from '../../components/PageError';
import SuperAdminNav from './SuperAdminNav';
import { downloadSignupsCsv, useDeleteSignup, useSignups } from './useSuperAdminSignups';
import { usePreferences } from '../../hooks/usePreferences';
import { formatDateTime } from '../../utils/formatDate';
import type { EmailSignupResponse, SignupList } from '../../types';

/** The lists the backend accepts (`SignupList`). A new campaign list adds its value here. */
const LISTS: readonly SignupList[] = ['early-access'];

const cell = 'px-4 py-3 text-sm text-gray-600 dark:text-gray-300 whitespace-nowrap';

/** ADR-0066: who left their email on a sign-up list, with CSV export and delete (junk, GDPR requests). */
export default function SuperAdminSignupsPage() {
  const { t } = useTranslation('superAdmin');
  const { prefs } = usePreferences();
  const [list, setList] = useState<SignupList>('early-access');
  const { data: signups, isLoading, error, refetch } = useSignups(list);
  const { mutate: deleteSignup, isPending: deleting, reset: resetDelete } = useDeleteSignup();
  const [toDelete, setToDelete] = useState<EmailSignupResponse | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);

  // Matches the router's catch-all target exactly, see SuperAdminPricingPage.
  if (isAxiosError(error) && error.response?.status === 403) return <Navigate to="/projects" replace />;

  async function handleDownload() {
    setDownloading(true);
    try {
      await downloadSignupsCsv(list);
    } catch {
      toast.error(t('signups.downloadError'));
    } finally {
      setDownloading(false);
    }
  }

  function closeDelete() {
    setToDelete(null);
    setDeleteError(null);
    resetDelete();
  }

  function confirmDelete() {
    if (!toDelete) return;
    setDeleteError(null);
    deleteSignup(toDelete.id, {
      onSuccess: closeDelete,
      onError: () => setDeleteError(t('signups.deleteError')),
    });
  }

  return (
    <div className="max-w-6xl mx-auto px-4 py-8 space-y-6">
      <SuperAdminNav />

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">{t('signups.pageHeading')}</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('signups.pageSubtitle')}</p>
        </div>
        <Button
          variant="secondary"
          onClick={() => void handleDownload()}
          loading={downloading}
          disabled={!signups || signups.length === 0}
        >
          <Download size={16} className="mr-1.5" aria-hidden="true" />
          {t('signups.download')}
        </Button>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <label htmlFor="signup-list" className="text-sm font-medium text-gray-700 dark:text-gray-300">
          {t('signups.listLabel')}
        </label>
        <select
          id="signup-list"
          value={list}
          onChange={(e) => setList(e.target.value as SignupList)}
          className="rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:border-transparent"
        >
          {LISTS.map((l) => (
            <option key={l} value={l}>{t(`signups.lists.${l}`)}</option>
          ))}
        </select>
        {signups && (
          <span className="text-sm text-gray-500 dark:text-gray-400" data-testid="signups-count">
            {t('signups.count', { count: signups.length })}
          </span>
        )}
      </div>

      {isLoading ? (
        <Skeleton className="h-48 rounded-xl" />
      ) : error || !signups ? (
        <PageError message={t('signups.loadError')} onRetry={() => void refetch()} />
      ) : signups.length === 0 ? (
        <p className="text-sm text-gray-500 dark:text-gray-400">{t('signups.empty')}</p>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-gray-200 dark:border-gray-700">
          <table className="w-full text-left" data-testid="signups-table">
            <thead className="bg-gray-50 dark:bg-gray-800/60 text-xs text-gray-500 dark:text-gray-400 uppercase">
              <tr>
                <th className="px-4 py-3 font-medium">{t('signups.columns.date')}</th>
                <th className="px-4 py-3 font-medium">{t('signups.columns.email')}</th>
                <th className="px-4 py-3 font-medium">{t('signups.columns.code')}</th>
                <th className="px-4 py-3 font-medium">{t('signups.columns.redeemed')}</th>
                <th className="px-4 py-3 font-medium">{t('signups.columns.language')}</th>
                <th className="px-4 py-3 font-medium">{t('signups.columns.source')}</th>
                <th className="px-4 py-3 font-medium">{t('signups.columns.medium')}</th>
                <th className="px-4 py-3 font-medium">{t('signups.columns.campaign')}</th>
                <th className="px-4 py-3 font-medium sr-only">{t('signups.columns.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-800">
              {signups.map((s) => (
                <tr key={s.id} data-testid="signup-row">
                  <td className={cell}>{formatDateTime(s.createdAt, prefs)}</td>
                  <td className="px-4 py-3 text-sm text-gray-900 dark:text-gray-100 break-all">{s.email}</td>
                  <td className={cell}>{s.code ?? ''}</td>
                  <td className={cell}>{s.redeemedAt ? formatDateTime(s.redeemedAt, prefs) : ''}</td>
                  <td className={cell}>{s.locale ?? ''}</td>
                  <td className={cell}>{s.utmSource ?? ''}</td>
                  <td className={cell}>{s.utmMedium ?? ''}</td>
                  <td className={cell}>{s.utmCampaign ?? ''}</td>
                  <td className="px-2 py-2 text-right">
                    <Button
                      size="sm"
                      variant="ghost"
                      aria-label={t('signups.deleteAria', { email: s.email })}
                      title={t('signups.deleteButton')}
                      onClick={() => setToDelete(s)}
                    >
                      <Trash2 size={16} aria-hidden="true" />
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmModal
        open={toDelete !== null}
        onClose={closeDelete}
        onConfirm={confirmDelete}
        title={t('signups.deleteTitle')}
        message={t('signups.deleteMessage', { email: toDelete?.email ?? '' })}
        confirmLabel={t('signups.deleteButton')}
        variant="danger"
        isPending={deleting}
        errorMessage={deleteError}
      />
    </div>
  );
}
