import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CheckCircle2 } from 'lucide-react';
import EmptyState from '../../components/EmptyState';
import Skeleton from '../../components/Skeleton';
import Button from '../../components/Button';
import PageError from '../../components/PageError';
import { usePendingApprovalsAcrossOrgs } from '../approvals/useApprovalQueue';

const PAGE_SIZE = 10;

function formatDateTime(dateStr: string) {
  return new Date(dateStr).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function PendingApprovalsSection() {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const navigate = useNavigate();
  const { data: approvals = [], isLoading, isError, refetch } = usePendingApprovalsAcrossOrgs();
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);

  const visible = approvals.slice(0, visibleCount);
  const remaining = approvals.length - visible.length;

  return (
    <section>
      <h2 className="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-3">
        {t('overview.pendingApprovalsHeading')}
        {approvals.length > 0 && (
          <span className="ml-2 text-xs font-medium px-2 py-0.5 rounded-full bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400">
            {t('approvals.pendingBadge', { count: approvals.length })}
          </span>
        )}
      </h2>

      {isLoading && (
        <div className="space-y-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="border border-gray-200 dark:border-gray-700 rounded-lg px-4 py-3 bg-white dark:bg-gray-800 space-y-2">
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-3 w-1/2" />
            </div>
          ))}
        </div>
      )}

      {isError && (
        <PageError message={t('overview.pendingApprovalsLoadError')} onRetry={() => void refetch()} />
      )}

      {!isLoading && !isError && approvals.length === 0 && (
        <EmptyState
          icon={CheckCircle2}
          message={t('overview.pendingApprovalsEmptyState')}
          className="py-10"
        />
      )}

      {!isLoading && !isError && approvals.length > 0 && (
        <>
          <div className="space-y-3">
            {visible.map((approval) => (
              <button
                key={approval.id}
                onClick={() => navigate(`/projects/${approval.projectFriendlyId}/jobs/${approval.jobFriendlyId}`)}
                className="w-full text-left border border-gray-200 dark:border-gray-700 rounded-lg px-4 py-3 bg-white dark:bg-gray-800 hover:border-brand transition-colors cursor-pointer"
              >
                <div className="flex items-center justify-between gap-2 mb-1">
                  <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">{approval.jobTitle}</p>
                  <span className="shrink-0 text-xs text-gray-400 dark:text-gray-500">{approval.projectName}</span>
                </div>
                <p className="text-sm text-gray-600 dark:text-gray-300 mb-1 truncate">{approval.description}</p>
                <p className="text-xs text-gray-500 dark:text-gray-400">
                  {t('overview.requestedAt', { date: formatDateTime(approval.requestedAt) })}
                </p>
              </button>
            ))}
          </div>
          {remaining > 0 && (
            <div className="mt-4">
              <Button variant="secondary" size="sm" onClick={() => setVisibleCount((c) => c + PAGE_SIZE)}>
                {t('overview.showMore', { count: remaining })}
              </Button>
            </div>
          )}
        </>
      )}
    </section>
  );
}
