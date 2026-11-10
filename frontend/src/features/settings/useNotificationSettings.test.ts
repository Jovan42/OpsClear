import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MutationObserver, QueryClient } from '@tanstack/react-query';
import type { NotificationSettings, NotificationType } from '../../types';

interface PendingPut {
  muted: NotificationType[];
  resolve: () => void;
  reject: () => void;
}

const puts: PendingPut[] = [];

vi.mock('../../api/notifications', () => ({
  notificationsApi: {
    getSettings: vi.fn(),
    updateSettings: vi.fn(
      (muted: NotificationType[]) =>
        new Promise<NotificationSettings>((resolve, reject) => {
          puts.push({ muted, resolve: () => resolve({ muted }), reject: () => reject(new Error('500')) });
        }),
    ),
  },
}));

const { NOTIFICATION_SETTINGS_KEY, notificationSettingsMutationOptions } = await import('./useNotificationSettings');

/** Lets queued promises and TanStack's scheduling run. */
const flush = () => new Promise((r) => setTimeout(r, 0));

function setUp(onSaveError = vi.fn()) {
  const queryClient = new QueryClient();
  queryClient.setQueryData<NotificationSettings>(NOTIFICATION_SETTINGS_KEY, { muted: [] });
  const options = notificationSettingsMutationOptions(queryClient, onSaveError);
  // One observer per click, like separate `mutate` calls from the page.
  const toggle = (type: NotificationType, enabled: boolean) =>
    new MutationObserver(queryClient, options).mutate({ type, enabled }).catch(() => undefined);
  const muted = () => queryClient.getQueryData<NotificationSettings>(NOTIFICATION_SETTINGS_KEY)?.muted;
  return { toggle, muted, onSaveError };
}

describe('notification settings saves', () => {
  beforeEach(() => {
    puts.length = 0;
  });

  it('runs quick toggles one at a time and keeps both changes', async () => {
    const { toggle, muted } = setUp();

    const a = toggle('JOB_UPDATED', false);
    await flush();
    const b = toggle('NOTE_ADDED', false);
    await flush();

    // Both flip at once, but B's PUT waits for A's response.
    expect(muted()).toEqual(['JOB_UPDATED', 'NOTE_ADDED']);
    expect(puts).toHaveLength(1);
    expect(puts[0].muted).toEqual(['JOB_UPDATED']);

    puts[0].resolve();
    await a;
    await flush();
    // A's older answer doesn't hide B's pending change.
    expect(muted()).toEqual(['JOB_UPDATED', 'NOTE_ADDED']);
    expect(puts).toHaveLength(2);
    expect(puts[1].muted).toEqual(['JOB_UPDATED', 'NOTE_ADDED']);

    puts[1].resolve();
    await b;
    expect(muted()).toEqual(['JOB_UPDATED', 'NOTE_ADDED']);
  });

  it("reverts a failed toggle and leaves it out of the next queued save", async () => {
    const { toggle, muted, onSaveError } = setUp();

    const a = toggle('JOB_UPDATED', false);
    await flush();
    const b = toggle('NOTE_ADDED', false);
    await flush();
    expect(puts).toHaveLength(1);

    puts[0].reject();
    await a;
    await flush();
    expect(onSaveError).toHaveBeenCalledTimes(1);
    // Only A is reverted; B is still on screen and its PUT doesn't re-send A.
    expect(muted()).toEqual(['NOTE_ADDED']);
    expect(puts).toHaveLength(2);
    expect(puts[1].muted).toEqual(['NOTE_ADDED']);

    puts[1].resolve();
    await b;
    expect(muted()).toEqual(['NOTE_ADDED']);
    expect(onSaveError).toHaveBeenCalledTimes(1);
  });

  it('a lone failed save reverts its toggle', async () => {
    const { toggle, muted, onSaveError } = setUp();
    const a = toggle('JOB_UPDATED', false);
    await flush();
    expect(muted()).toEqual(['JOB_UPDATED']);
    puts[0].reject();
    await a;
    expect(muted()).toEqual([]);
    expect(onSaveError).toHaveBeenCalledTimes(1);
  });
});
