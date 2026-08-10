import { useState } from 'react';
import { isAxiosError } from 'axios';
import { useTranslation } from 'react-i18next';
import { Navigate } from 'react-router-dom';
import Skeleton from '../../components/Skeleton';
import Button from '../../components/Button';
import FeedbackTypeBadge from '../../components/FeedbackTypeBadge';
import Markdown from '../../components/Markdown';
import SuperAdminNav from './SuperAdminNav';
import GrantCreditModal from './GrantCreditModal';
import {
  useDeclineFeedback,
  useFeedbackSubmissions,
  useOrgCreditLedger,
  useSuperAdminOrganisations,
} from './useSuperAdminFeedback';
import type { FeedbackStatus, FeedbackSubmissionResponse } from '../../types';

function fmt(n: number) {
  return new Intl.NumberFormat('sr-RS').format(n);
}

const STATUS_STYLES: Record<FeedbackStatus, string> = {
  PENDING: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400',
  DECLINED: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  CREDITED: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
};

const STATUS_LABEL_KEYS: Record<FeedbackStatus, string> = {
  PENDING: 'feedback:status.pending',
  DECLINED: 'feedback:status.declined',
  CREDITED: 'feedback:status.creditGranted',
};

interface GrantContext {
  orgId?: string;
  orgName?: string;
  submissionId?: string;
}

export default function SuperAdminFeedbackPage() {
  const { t } = useTranslation(['superAdmin', 'feedback']);
  const { data: submissions, isLoading: submissionsLoading, error: submissionsError } = useFeedbackSubmissions();
  const { data: orgs, isLoading: orgsLoading } = useSuperAdminOrganisations();

  const [ledgerOrgId, setLedgerOrgId] = useState('');
  const { data: ledger, isLoading: ledgerLoading } = useOrgCreditLedger(ledgerOrgId || null);

  const [grantCtx, setGrantCtx] = useState<GrantContext | null>(null);
  const [hideResolved, setHideResolved] = useState(true);
  const { mutate: decline, isPending: declining, variables: decliningId } = useDeclineFeedback();

  const forbidden = isAxiosError(submissionsError) && submissionsError.response?.status === 403;

  // Matches the router's catch-all target exactly — see SuperAdminPricingPage.
  if (forbidden) return <Navigate to="/projects" replace />;

  if (submissionsLoading || orgsLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-8 space-y-4">
        <SuperAdminNav />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-48 rounded-xl" />
        <Skeleton className="h-48 rounded-xl" />
      </div>
    );
  }

  if (!submissions || !orgs) return null;

  const ledgerOrgName = orgs.find((o) => o.id === ledgerOrgId)?.name;

  return (
    <div className="max-w-5xl mx-auto px-4 py-8 space-y-8">
      <SuperAdminNav />

      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">{t('feedbackPageHeading')}</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('feedbackPageSubtitle')}</p>
        </div>
        <Button onClick={() => setGrantCtx({})}>{t('grantModal.openButton')}</Button>
      </div>

      <section className="space-y-3">
        <div className="flex items-center justify-between gap-4">
          <h2 className="text-xs font-semibold text-gray-400 dark:text-gray-500 uppercase tracking-wider">
            {t('submissionsHeading')}
          </h2>
          <label className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-300 cursor-pointer">
            <input
              type="checkbox"
              checked={hideResolved}
              onChange={(e) => setHideResolved(e.target.checked)}
              className="rounded border-gray-300 dark:border-gray-600"
            />
            {t('hideResolvedLabel')}
          </label>
        </div>
        {(() => {
          const visible = hideResolved ? submissions.filter((s) => s.status === 'PENDING') : submissions;
          if (visible.length === 0) {
            return <p className="text-sm text-gray-500 dark:text-gray-400">{t('noSubmissions')}</p>;
          }
          return (
            <div className="divide-y divide-gray-200 dark:divide-gray-700 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
              {visible.map((s: FeedbackSubmissionResponse) => (
                <div key={s.id} className="p-4 space-y-2">
                  <div className="flex items-center justify-between gap-4">
                    <div className="flex items-center gap-2 flex-wrap">
                      <FeedbackTypeBadge type={s.type} />
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_STYLES[s.status]}`}>
                        {t(STATUS_LABEL_KEYS[s.status])}
                      </span>
                      <span className="text-xs text-gray-400 dark:text-gray-500">{s.orgName}</span>
                    </div>
                    {s.status === 'PENDING' && (
                      <div className="flex items-center gap-2 shrink-0">
                        <Button
                          size="sm"
                          variant="ghost"
                          loading={declining && decliningId === s.id}
                          onClick={() => decline(s.id)}
                        >
                          {t('declineButton')}
                        </Button>
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => setGrantCtx({ orgId: s.orgId, orgName: s.orgName, submissionId: s.id })}
                        >
                          {t('grantModal.openButton')}
                        </Button>
                      </div>
                    )}
                  </div>
                  <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{s.title}</p>
                  <Markdown className="text-sm text-gray-600 dark:text-gray-300">{s.description}</Markdown>
                  <p className="text-xs text-gray-400 dark:text-gray-500">
                    {s.submitterName} &middot; {new Date(s.createdAt).toLocaleDateString()}
                  </p>
                </div>
              ))}
            </div>
          );
        })()}
      </section>

      <section className="space-y-3">
        <h2 className="text-xs font-semibold text-gray-400 dark:text-gray-500 uppercase tracking-wider">
          {t('ledgerHeading')}
        </h2>
        <div className="flex items-center gap-3 flex-wrap">
          <select
            value={ledgerOrgId}
            onChange={(e) => setLedgerOrgId(e.target.value)}
            className="rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:border-transparent"
          >
            <option value="">{t('ledgerOrgPlaceholder')}</option>
            {orgs.map((org) => (
              <option key={org.id} value={org.id}>{org.name}</option>
            ))}
          </select>
          {ledgerOrgId && (
            <Button
              size="sm"
              variant="secondary"
              onClick={() => setGrantCtx({ orgId: ledgerOrgId, orgName: ledgerOrgName })}
            >
              {t('grantModal.openButton')}
            </Button>
          )}
        </div>

        {!ledgerOrgId ? null : ledgerLoading ? (
          <Skeleton className="h-32 rounded-xl" />
        ) : !ledger || ledger.length === 0 ? (
          <p className="text-sm text-gray-500 dark:text-gray-400">{t('noLedgerEntries')}</p>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-gray-200 dark:border-gray-700">
            <table className="w-full text-left">
              <thead className="bg-gray-50 dark:bg-gray-800/60 text-xs text-gray-500 dark:text-gray-400 uppercase">
                <tr>
                  <th className="px-4 py-3 font-medium">{t('ledgerColumns.amount')}</th>
                  <th className="px-4 py-3 font-medium">{t('ledgerColumns.reason')}</th>
                  <th className="px-4 py-3 font-medium">{t('ledgerColumns.origin')}</th>
                  <th className="px-4 py-3 font-medium">{t('ledgerColumns.grantedBy')}</th>
                  <th className="px-4 py-3 font-medium">{t('ledgerColumns.date')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-800">
                {ledger.map((entry) => (
                  <tr key={entry.id}>
                    <td className="px-4 py-3 text-sm text-gray-900 dark:text-gray-100">{fmt(entry.amount)}</td>
                    <td className="px-4 py-3 text-sm text-gray-600 dark:text-gray-300">{entry.reason}</td>
                    <td className="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">
                      {entry.submissionId ? t('ledgerColumns.fromSubmission') : t('ledgerColumns.discretionary')}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">{entry.grantedByName}</td>
                    <td className="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">
                      {new Date(entry.createdAt).toLocaleDateString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <GrantCreditModal
        key={grantCtx ? `${grantCtx.orgId ?? 'standalone'}-${grantCtx.submissionId ?? ''}` : 'closed'}
        open={grantCtx !== null}
        onClose={() => setGrantCtx(null)}
        orgId={grantCtx?.orgId}
        orgName={grantCtx?.orgName}
        submissionId={grantCtx?.submissionId}
      />
    </div>
  );
}
