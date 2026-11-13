import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from '../../components/Button';
import Skeleton from '../../components/Skeleton';
import Switch from '../../components/Switch';
import type { NotificationType } from '../../types';
import {
  isMutable,
  typesInGroup,
  type NotificationGroup,
} from '../notifications/notificationContent';
import { useNotificationSettings, useUpdateNotificationSettings } from './useNotificationSettings';

const GROUPS: NotificationGroup[] = ['JOB', 'ACCOUNT'];

/** A small tag saying where a setting is stored ("This device only" / "All your devices"). */
export function ScopeTag({ children }: { readonly children: string }) {
  return (
    <span className="inline-block ml-1 align-middle rounded-full bg-gray-100 dark:bg-gray-700 px-2 py-0.5 text-xs font-medium text-gray-600 dark:text-gray-300 whitespace-nowrap">
      {children}
    </span>
  );
}

/**
 * Per-type mute toggles (ADR-0051 §Muting), rendered as rows inside the Settings page's
 * Notifications card. Saved to the account on each toggle, so they apply on every device.
 */
export default function NotificationTypeSettings() {
  const { t } = useTranslation(['notifications']);
  const { data, isPending, isError, refetch } = useNotificationSettings();
  const [saveFailed, setSaveFailed] = useState(false);
  const update = useUpdateNotificationSettings(() => setSaveFailed(true));

  const muted = data?.muted ?? [];

  const onToggle = (type: NotificationType, enabled: boolean) => {
    setSaveFailed(false);
    update.mutate({ type, enabled });
  };

  return (
    <>
      <div className="p-5" data-testid="notification-type-settings">
        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
          {t('settings.heading')} <ScopeTag>{t('settings.scope')}</ScopeTag>
        </p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{t('settings.description')}</p>
        {saveFailed && (
          <p role="alert" className="text-sm text-red-600 dark:text-red-400 mt-2" data-testid="notification-settings-save-error">
            {t('settings.saveError')}
          </p>
        )}
      </div>

      {isPending && (
        <div className="p-5 space-y-3">
          <Skeleton className="h-5 w-full" />
          <Skeleton className="h-5 w-full" />
          <Skeleton className="h-5 w-2/3" />
        </div>
      )}

      {isError && (
        <div className="p-5 flex items-center justify-between gap-4">
          <p className="text-sm text-red-600 dark:text-red-400">{t('settings.loadError')}</p>
          <Button variant="ghost" size="sm" onClick={() => void refetch()}>
            {t('settings.retry')}
          </Button>
        </div>
      )}

      {data &&
        GROUPS.map((group) => (
          <div key={group} role="group" aria-labelledby={`notification-group-${group}`}>
            <h3
              id={`notification-group-${group}`}
              className="px-5 pt-4 pb-2 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide"
            >
              {t(`settings.groups.${group}`)}
            </h3>
            <ul className="divide-y divide-gray-100 dark:divide-gray-700">
              {typesInGroup(group).map((type) => {
                const locked = !isMutable(type);
                const enabled = locked || !muted.includes(type);
                const labelId = `notification-type-${type}`;
                const descriptionId = locked ? `${labelId}-locked` : undefined;
                return (
                  <li key={type} className="flex items-center justify-between gap-6 px-5 py-3">
                    <div className="min-w-0">
                      <p id={labelId} className="text-sm text-gray-900 dark:text-gray-100">
                        {t(`type.${type}`)}
                      </p>
                      {locked && (
                        <p id={descriptionId} className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
                          {t('settings.lockedOn')}
                        </p>
                      )}
                    </div>
                    <Switch
                      checked={enabled}
                      locked={locked}
                      labelledBy={labelId}
                      describedBy={descriptionId}
                      testId={`notification-type-toggle-${type}`}
                      onChange={(next) => onToggle(type, next)}
                    />
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
    </>
  );
}
