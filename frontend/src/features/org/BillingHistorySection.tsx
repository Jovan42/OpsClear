import { useTranslation } from 'react-i18next';
import Skeleton from '../../components/Skeleton';
import { useBillingHistory } from './useBilling';

interface Props {
  orgId: string;
}

function fmt(n: number) {
  return new Intl.NumberFormat('sr-RS').format(n);
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

const SUCCESS_STATUSES = new Set(['completed', 'paid']);
const FAILURE_STATUSES = new Set(['past_due', 'canceled']);

function statusBadgeClass(status: string) {
  if (SUCCESS_STATUSES.has(status)) {
    return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400';
  }
  if (FAILURE_STATUSES.has(status)) {
    return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400';
  }
  return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300';
}

export default function BillingHistorySection({ orgId }: Props) {
  const { t } = useTranslation('org');
  const { data: transactions, isLoading } = useBillingHistory(orgId);

  if (isLoading) {
    return (
      <div className="space-y-2">
        <Skeleton className="h-12 rounded-lg" />
        <Skeleton className="h-12 rounded-lg" />
      </div>
    );
  }

  if (!transactions || transactions.length === 0) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">{t('billingHistoryEmpty')}</p>;
  }

  return (
    <div className="divide-y divide-gray-100 dark:divide-gray-700">
      {transactions.map((txn) => (
        <div key={txn.id} className="py-3 first:pt-0 last:pb-0 flex items-center justify-between gap-4">
          <div className="min-w-0">
            <p className="text-sm text-gray-900 dark:text-gray-100">
              {txn.billedAt ? formatDate(txn.billedAt) : t('billingHistoryNoDateYet')}
            </p>
            <span
              className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium mt-1 ${statusBadgeClass(txn.status)}`}
            >
              {txn.status}
            </span>
          </div>
          <p className="text-sm font-medium text-gray-900 dark:text-gray-100 shrink-0">
            {txn.totalAmount !== null ? `${fmt(txn.totalAmount)} ${txn.currency ?? ''}` : '—'}
          </p>
        </div>
      ))}
    </div>
  );
}
