import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { BellOff, CheckCheck } from 'lucide-react';
import Button from '../../components/Button';
import EmptyState from '../../components/EmptyState';
import PageError from '../../components/PageError';
import Skeleton from '../../components/Skeleton';
import { usePageTitle } from '../../hooks/usePageTitle';
import NotificationItem from './NotificationItem';
import { NOTIFICATION_TYPES } from './notificationContent';
import { useMarkAllRead, useNotifications, useUnreadCount } from './useNotifications';
import type { NotificationListFilters, NotificationType } from '../../types';

function TabButton({ active, onClick, children }: Readonly<{ active: boolean; onClick: () => void; children: React.ReactNode }>) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`px-4 py-2 text-sm font-medium transition-colors cursor-pointer ${
        active
          ? 'bg-gray-900 text-white dark:bg-white dark:text-gray-900'
          : 'bg-white text-gray-600 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
      }`}
    >
      {children}
    </button>
  );
}

export default function NotificationsPage() {
  const { t } = useTranslation('notifications');
  usePageTitle(t('title'));

  const [filters, setFilters] = useState<NotificationListFilters>({ unread: false, type: null });
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, isPlaceholderData, refetch } = useNotifications(filters, page);
  const { data: unreadCount = 0 } = useUnreadCount();
  const { mutate: markAllRead, isPending: isMarkingAll } = useMarkAllRead();

  const items = data?.items ?? [];
  const totalPages = data?.totalPages ?? 0;

  // A page can empty under the user (e.g. marking the last unread items read with the
  // Unread filter on page 2+). Step back to the last page that still has items. Set
  // during render (React's "adjust state on change" pattern); it stops once page is 0
  // or the fetched page has items.
  if (data && !isPlaceholderData && items.length === 0 && page > 0) {
    setPage(Math.max(0, Math.min(page - 1, totalPages - 1)));
  }

  function updateFilters(next: Partial<NotificationListFilters>) {
    setFilters((f) => ({ ...f, ...next }));
    setPage(0);
  }

  let emptyMessage = t('empty.all');
  if (filters.type) emptyMessage = t('empty.type');
  else if (filters.unread) emptyMessage = t('empty.unread');

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
        <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">{t('title')}</h1>
        <Button
          variant="secondary"
          size="sm"
          onClick={() => markAllRead()}
          disabled={unreadCount === 0 || isMarkingAll}
        >
          <CheckCheck className="w-4 h-4 mr-1.5 inline" aria-hidden />
          {t('markAllRead')}
        </Button>
      </div>

      <div className="flex flex-wrap items-center gap-3 mb-4">
        <div className="inline-flex rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
          <TabButton active={!filters.unread} onClick={() => updateFilters({ unread: false })}>
            {t('filter.all')}
          </TabButton>
          <TabButton active={filters.unread} onClick={() => updateFilters({ unread: true })}>
            {t('filter.unread')}
          </TabButton>
        </div>
        <label className="sr-only" htmlFor="notification-type-filter">{t('filter.typeLabel')}</label>
        <select
          id="notification-type-filter"
          value={filters.type ?? ''}
          onChange={(e) => updateFilters({ type: (e.target.value || null) as NotificationType | null })}
          className="text-sm rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-200 px-3 py-2 max-w-full"
        >
          <option value="">{t('filter.allTypes')}</option>
          {NOTIFICATION_TYPES.map((type) => (
            <option key={type} value={type}>{t(`type.${type}`)}</option>
          ))}
        </select>
      </div>

      {isError && <PageError message={t('loadError')} onRetry={() => void refetch()} />}

      {isLoading && (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full rounded-xl" />
          ))}
        </div>
      )}

      {!isLoading && !isError && items.length === 0 && (
        <EmptyState icon={BellOff} message={emptyMessage} />
      )}

      {!isError && items.length > 0 && (
        <div
          className={`rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden divide-y divide-gray-100 dark:divide-gray-700 ${isPlaceholderData ? 'opacity-60' : ''}`}
          data-testid="notification-list"
        >
          {items.map((n) => (
            <NotificationItem key={n.id} notification={n} showReadToggle />
          ))}
        </div>
      )}

      {!isError && totalPages > 1 && (
        <div className="flex items-center justify-between mt-4 text-sm text-gray-600 dark:text-gray-300">
          <Button variant="secondary" size="sm" onClick={() => setPage((p) => p - 1)} disabled={page === 0 || isPlaceholderData}>
            {t('pagination.previous')}
          </Button>
          <span>{t('pagination.pageOf', { page: page + 1, total: totalPages })}</span>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setPage((p) => p + 1)}
            disabled={page + 1 >= totalPages || isPlaceholderData}
          >
            {t('pagination.next')}
          </Button>
        </div>
      )}
    </div>
  );
}
