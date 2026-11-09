import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Bell, BellOff } from 'lucide-react';
import EmptyState from '../../components/EmptyState';
import Skeleton from '../../components/Skeleton';
import NotificationItem from './NotificationItem';
import type { NotificationListFilters } from '../../types';
import { DROPDOWN_SIZE, useMarkAllRead, useNotifications, useUnreadCount } from './useNotifications';
import { formatBadge } from './notificationContent';
import { useNotificationStreamConnected } from './useNotificationStream';

const ALL: NotificationListFilters = { unread: false, type: null };

/** Header bell (ADR-0051): unread badge, dropdown with the 10 most recent. */
export default function NotificationBell() {
  const { t } = useTranslation(['notifications', 'shared2']);
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();
  const streamConnected = useNotificationStreamConnected();

  const { data: unreadCount = 0 } = useUnreadCount();
  // Fetched only while open; the badge comes from the cheaper count endpoint.
  const { data, isLoading, isError, refetch } = useNotifications(ALL, 0, DROPDOWN_SIZE, open);
  const { mutate: markAllRead, isPending: isMarkingAll } = useMarkAllRead();
  const items = data?.items ?? [];

  useEffect(() => {
    if (!open) return;
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    function handleEscape(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false);
    }
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleEscape);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleEscape);
    };
  }, [open]);

  const close = () => setOpen(false);
  function toggle() {
    // The stream keeps the cache current. While it's down, opening fetches fresh data
    // instead, since the cached count or list may be out of date.
    if (!open && !streamConnected) void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    setOpen((v) => !v);
  }

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={toggle}
        className="relative p-1.5 text-white/80 hover:text-white transition-colors cursor-pointer"
        aria-label={unreadCount > 0 ? t('bell.labelUnread', { count: unreadCount }) : t('bell.label')}
        aria-expanded={open}
        aria-haspopup="dialog"
        data-testid="notification-bell"
        data-live={streamConnected}
      >
        <Bell className="w-5 h-5" strokeWidth={2} aria-hidden />
        {unreadCount > 0 && (
          <span
            className="absolute -top-0.5 -right-1 min-w-[1.125rem] h-[1.125rem] px-1 rounded-full bg-red-500 text-white text-[10px] font-semibold leading-[1.125rem] text-center"
            data-testid="notification-badge"
          >
            {formatBadge(unreadCount)}
          </span>
        )}
      </button>

      {open && (
        <div
          className="fixed inset-x-2 top-14 sm:absolute sm:inset-x-auto sm:top-auto sm:right-0 sm:mt-2 sm:w-96 max-h-[75vh] flex flex-col rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-lg z-50 overflow-hidden"
          role="dialog"
          aria-label={t('title')}
          data-testid="notification-dropdown"
        >
          <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100 dark:border-gray-700">
            <span className="text-sm font-semibold text-gray-900 dark:text-gray-100">{t('title')}</span>
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={() => markAllRead()}
                disabled={isMarkingAll}
                className="text-xs font-medium text-[var(--brand)] hover:underline cursor-pointer disabled:opacity-50"
              >
                {t('markAllRead')}
              </button>
            )}
          </div>

          <div className="overflow-y-auto divide-y divide-gray-100 dark:divide-gray-700">
            {isLoading && (
              <div className="p-4 space-y-3">
                {Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={i} className="h-10 w-full" />
                ))}
              </div>
            )}
            {isError && (
              <div className="p-4 text-center text-sm">
                <p className="text-red-500">{t('loadError')}</p>
                <button type="button" onClick={() => void refetch()} className="mt-1 text-[var(--brand)] hover:underline cursor-pointer">
                  {t('shared2:pageError.tryAgain')}
                </button>
              </div>
            )}
            {!isLoading && !isError && items.length === 0 && (
              <EmptyState icon={BellOff} message={t('empty.all')} className="py-10" />
            )}
            {items.map((n) => (
              <NotificationItem key={n.id} notification={n} onNavigate={close} />
            ))}
          </div>

          <Link
            to="/notifications"
            onClick={close}
            className="block px-4 py-2.5 text-center text-sm font-medium text-[var(--brand)] border-t border-gray-100 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50"
          >
            {t('viewAll')}
          </Link>
        </div>
      )}
    </div>
  );
}
