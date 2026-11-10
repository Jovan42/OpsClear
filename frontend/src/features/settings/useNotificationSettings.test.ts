import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MutationObserver, QueryClient, QueryObserver } from '@tanstack/react-query';
import type { NotificationSettings, NotificationType } from '../../types';

interface PendingPut {
  muted: NotificationType[];
  resolve: () => void;
  reject: () => void;
}

// A fake server: PUTs wait until the test resolves or rejects them; a resolved PUT stores
// its list, and GET returns what's stored.
const server: { muted: NotificationType[] } = { muted: [] };
const puts: PendingPut[] = [];

vi.mock('../../api/notifications', () => ({
  notificationsApi: {
    getSettings: vi.fn(async (): Promise<NotificationSettings> => ({ muted: [...server.muted] })),
    updateSettings: vi.fn(
      (muted: NotificationType[]) =>
        new Promise<NotificationSettings>((resolve, reject) => {
          puts.push({
            muted,
            resolve: () => {
              server.muted = muted;
              resolve({ muted });
            },
            reject: () => reject(new Error('500')),
          });
        }),
    ),
  },
}));

const { NOTIFICATION_SETTINGS_KEY, notificationSettingsMutationOptions, notificationSettingsQueryOptions } =
  await import('./useNotificationSettings');

/** Lets queued promises and TanStack's scheduling run. */
const flush = () => new Promise((r) => setTimeout(r, 0));

async function setUp(onSaveError = vi.fn()) {
  const queryClient = new QueryClient();
  // An active observer, like the mounted Settings page, so invalidation refetches.
  new QueryObserver(queryClient, notificationSettingsQueryOptions(queryClient)).subscribe(() => undefined);
  await flush();
  const options = notificationSettingsMutationOptions(queryClient, onSaveError);
  // One observer per click, like separate `mutate` calls from the page.
  const toggle = (type: NotificationType, enabled: boolean) =>
    new MutationObserver(queryClient, options).mutate({ type, enabled }).catch(() => undefined);
  const muted = () => queryClient.getQueryData<NotificationSettings>(NOTIFICATION_SETTINGS_KEY)?.muted;
  return { queryClient, toggle, muted, onSaveError };
}

describe('notification settings saves', () => {
  beforeEach(() => {
    puts.length = 0;
    server.muted = [];
  });

  it('runs quick toggles one at a time and keeps both changes', async () => {
    const { toggle, muted } = await setUp();

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
    await flush();
    expect(muted()).toEqual(['JOB_UPDATED', 'NOTE_ADDED']);
    expect(server.muted).toEqual(['JOB_UPDATED', 'NOTE_ADDED']);
  });

  it('reverts a failed toggle and leaves it out of the next queued save', async () => {
    const { toggle, muted, onSaveError } = await setUp();

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
    await flush();
    expect(muted()).toEqual(['NOTE_ADDED']);
    expect(server.muted).toEqual(['NOTE_ADDED']);
    expect(onSaveError).toHaveBeenCalledTimes(1);
  });

  it('a lone failed save reverts its toggle', async () => {
    const { toggle, muted, onSaveError } = await setUp();
    const a = toggle('JOB_UPDATED', false);
    await flush();
    expect(muted()).toEqual(['JOB_UPDATED']);
    puts[0].reject();
    await a;
    await flush();
    expect(muted()).toEqual([]);
    expect(server.muted).toEqual([]);
    expect(onSaveError).toHaveBeenCalledTimes(1);
  });

  it('resyncs with the server when a failed save was already stored by an earlier one', async () => {
    const { toggle, muted, onSaveError } = await setUp();

    // A is in flight; B and C are clicked behind it.
    const a = toggle('JOB_UPDATED', false);
    await flush();
    const b = toggle('NOTE_ADDED', false);
    const c = toggle('JOB_DELETED', false);
    await flush();

    puts[0].resolve();
    await a;
    await flush();
    // B's body carries C's change too, so the server stores C here.
    expect(puts[1].muted).toEqual(['JOB_UPDATED', 'JOB_DELETED', 'NOTE_ADDED']);
    puts[1].resolve();
    await b;
    await flush();

    puts[2].reject();
    await c;
    await flush();
    await flush();

    expect(onSaveError).toHaveBeenCalledTimes(1);
    expect(server.muted).toEqual(['JOB_UPDATED', 'JOB_DELETED', 'NOTE_ADDED']);
    expect(muted()).toEqual(server.muted);
  });

  it("keeps pending toggles when a refetch lands mid-queue, and doesn't save the old list", async () => {
    const { queryClient, toggle, muted } = await setUp();

    const a = toggle('JOB_UPDATED', false);
    await flush();
    const b = toggle('NOTE_ADDED', false);
    await flush();

    // E.g. a window-focus refetch: the server still has nothing muted.
    await queryClient.refetchQueries({ queryKey: NOTIFICATION_SETTINGS_KEY });
    expect(muted()).toEqual(['JOB_UPDATED', 'NOTE_ADDED']);

    puts[0].resolve();
    await a;
    await flush();
    expect(puts[1].muted).toEqual(['JOB_UPDATED', 'NOTE_ADDED']);

    puts[1].resolve();
    await b;
    await flush();
    await flush();
    expect(server.muted).toEqual(['JOB_UPDATED', 'NOTE_ADDED']);
    expect(muted()).toEqual(server.muted);
  });
});
