import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { demoStore } from './mockData';
import NotificationBell from '../features/notifications/NotificationBell';
import NotificationTypeSettings from '../features/settings/NotificationTypeSettings';

/**
 * The real header bell in a stand-in for the app's header bar (same brand colour and
 * height as AppLayout's), opened on mount so the card's preview shows the dropdown with
 * unread items. After that it behaves as in the app: clicking outside closes it, the bell
 * reopens it. `translateZ(0)` makes this box the containing block for the dropdown's
 * `position: fixed` mobile layout, so on a narrow screen it sits under this header rather
 * than at the top of the viewport.
 */
export function BellScene() {
  const ref = useRef<HTMLDivElement>(null);
  // Guards against StrictMode's double-run of mount effects, which would click twice
  // and close the dropdown again.
  const opened = useRef(false);

  useEffect(() => {
    if (opened.current) return;
    opened.current = true;
    ref.current?.querySelector<HTMLButtonElement>('[data-testid="notification-bell"]')?.click();
  }, []);

  return (
    <div ref={ref} className="min-h-[520px] bg-gray-50 dark:bg-gray-900" style={{ transform: 'translateZ(0)' }}>
      <div className="px-4 text-white" style={{ backgroundColor: 'var(--brand)' }}>
        <div className="flex items-center justify-between h-14">
          <div className="flex items-center gap-2 min-w-0">
            <span className="font-semibold text-lg tracking-tight">OpsClear</span>
            <span className="text-white/70 text-sm truncate">{demoStore.project.name}</span>
          </div>
          <NotificationBell />
        </div>
      </div>
    </div>
  );
}

/**
 * The Settings page's Notifications section with the mute toggles only (the sound switch
 * next to them is a per-device preference, not part of the notification centre's data).
 */
export function MuteSettingsScene() {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8">
      <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3">
        {t('settings.notificationsSection.heading')}
      </h2>
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
        <NotificationTypeSettings />
      </div>
    </div>
  );
}
