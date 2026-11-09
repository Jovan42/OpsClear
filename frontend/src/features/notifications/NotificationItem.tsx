import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Mail, MailOpen } from 'lucide-react';
import type { NotificationResponse } from '../../types';
import {
  NOTIFICATION_ICONS,
  buildNotificationContent,
  formatRelativeTime,
  isUrgent,
  notificationTarget,
} from './notificationContent';
import { useMarkRead, useViewerOrgRole } from './useNotifications';

interface NotificationItemProps {
  notification: NotificationResponse;
  /** Called after a click that navigates (e.g. to close the bell dropdown). */
  onNavigate?: () => void;
  /** Shows the per-item read/unread toggle (Notifications page). */
  showReadToggle?: boolean;
}

/**
 * One notification: icon, text built from type + payload in the viewer's language,
 * source (actor or "OpsClear"), relative time. Clicking marks it read and opens its
 * target; `JOB_DELETED` has none and isn't clickable.
 */
export default function NotificationItem({ notification: n, onNavigate, showReadToggle = false }: Readonly<NotificationItemProps>) {
  const { t, i18n } = useTranslation(['notifications', 'shared2']);
  const { mutate: markRead, isPending } = useMarkRead();

  const viewerOrgRole = useViewerOrgRole();
  const content = buildNotificationContent(n, t, i18n.language, viewerOrgRole);
  const target = notificationTarget(n, viewerOrgRole);
  const urgent = isUrgent(n.type);
  const Icon = NOTIFICATION_ICONS[n.type];

  const rowClass = [
    'flex items-start gap-3 border-l-4',
    urgent ? 'border-red-500' : 'border-transparent',
    n.read ? 'bg-white dark:bg-gray-800' : 'bg-blue-50 dark:bg-blue-900/20',
  ].join(' ');
  const iconClass = urgent
    ? 'text-red-600 dark:text-red-400'
    : 'text-gray-400 dark:text-gray-500';

  const body = (
    <>
      <Icon className={`w-5 h-5 mt-0.5 shrink-0 ${iconClass}`} strokeWidth={1.75} aria-hidden />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-1.5 text-xs text-gray-500 dark:text-gray-400">
          <span className="font-semibold text-gray-700 dark:text-gray-200" data-testid="notification-source">
            {content.source}
          </span>
          {content.context && (
            <>
              <span aria-hidden>·</span>
              <span>{content.context}</span>
            </>
          )}
          <span aria-hidden>·</span>
          <time dateTime={n.createdAt} title={new Date(n.createdAt).toLocaleString()} className="shrink-0">
            {formatRelativeTime(n.createdAt, i18n.language)}
          </time>
        </div>
        <p
          className={`text-sm mt-0.5 break-words ${urgent ? 'text-red-700 dark:text-red-300' : 'text-gray-900 dark:text-gray-100'} ${n.read ? '' : 'font-medium'}`}
        >
          {content.text}
        </p>
        {content.details.map((detail, i) => (
          <p key={i} className="text-xs text-gray-500 dark:text-gray-400 mt-0.5 break-words line-clamp-2">
            {detail}
          </p>
        ))}
      </div>
      {!n.read && (
        <span className="mt-2 shrink-0">
          <span className="block w-2 h-2 rounded-full bg-blue-500" aria-hidden />
          <span className="sr-only">{t('notifications:unreadMarker')}</span>
        </span>
      )}
    </>
  );

  function handleClick() {
    if (!n.read) markRead({ id: n.id, read: true });
    onNavigate?.();
  }

  return (
    <div
      className={rowClass}
      data-testid="notification-item"
      data-type={n.type}
      data-read={n.read ? 'true' : 'false'}
    >
      {target ? (
        <Link
          to={target}
          onClick={handleClick}
          className="flex items-start gap-3 flex-1 min-w-0 px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
        >
          {body}
        </Link>
      ) : (
        <div className="flex items-start gap-3 flex-1 min-w-0 px-4 py-3" title={t('notifications:notClickable')}>
          {body}
        </div>
      )}
      {showReadToggle && (
        <button
          type="button"
          onClick={() => markRead({ id: n.id, read: !n.read })}
          disabled={isPending}
          aria-label={n.read ? t('notifications:markUnread') : t('notifications:markRead')}
          title={n.read ? t('notifications:markUnread') : t('notifications:markRead')}
          className="shrink-0 self-center mr-2 sm:mr-3 p-1.5 sm:px-2 sm:py-1 text-xs font-medium whitespace-nowrap text-gray-500 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-100 rounded hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors cursor-pointer disabled:opacity-50"
        >
          {/* Icon only on narrow screens, so the text keeps the row's width. */}
          {n.read
            ? <Mail className="w-4 h-4 sm:hidden" aria-hidden />
            : <MailOpen className="w-4 h-4 sm:hidden" aria-hidden />}
          <span className="hidden sm:inline">
            {n.read ? t('notifications:markUnread') : t('notifications:markRead')}
          </span>
        </button>
      )}
    </div>
  );
}
