import { beforeEach, describe, expect, it, vi } from 'vitest';
import { QueryClient } from '@tanstack/react-query';
import type { NotificationListFilters, NotificationPageResponse, NotificationResponse } from '../../types';
import { applyNewNotification, applyReadState, SETTLE_MS, UNREAD_COUNT_KEY } from './notificationCache';

const ALL: NotificationListFilters = { unread: false, type: null };
const UNREAD: NotificationListFilters = { unread: true, type: null };
const APPROVALS: NotificationListFilters = { unread: false, type: 'APPROVAL_REQUESTED' };

function n(id: string, read = false, type: NotificationResponse['type'] = 'JOB_STATUS_CHANGED'): NotificationResponse {
  return {
    id,
    type,
    read,
    createdAt: '2026-11-09T10:00:00Z',
    actor: null,
    projectFriendlyId: 'PRJ-1',
    jobFriendlyId: 'JOB-1',
    approvalId: null,
    payload: {},
  } as NotificationResponse;
}

function page(items: NotificationResponse[], totalElements = items.length, size = 20): NotificationPageResponse {
  return { items, page: 0, size, totalElements, totalPages: Math.ceil(totalElements / size) };
}

const listKey = (filters: NotificationListFilters, p = 0, size = 20) => ['notifications', 'list', filters, p, size];

let qc: QueryClient;
const count = () => qc.getQueryData<number>(UNREAD_COUNT_KEY);
const list = (filters: NotificationListFilters, p = 0, size = 20) =>
  qc.getQueryData<NotificationPageResponse>(listKey(filters, p, size));
const isStale = (key: unknown[]) => qc.getQueryState(key)?.isInvalidated;

/** Settled cache data: fetched well before the event, nothing in flight. */
function seed(key: readonly unknown[], data: unknown) {
  qc.setQueryData(key, data, { updatedAt: Date.now() - 60_000 });
}

// What a count refetch returns. Unset: a fetch that never resolves (stays in flight).
let serverCount: number | undefined;

beforeEach(() => {
  qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  serverCount = undefined;
  qc.setQueryDefaults(UNREAD_COUNT_KEY, {
    queryFn: () => (serverCount === undefined ? new Promise<number>(() => {}) : Promise.resolve(serverCount)),
  });
  qc.setQueryDefaults(['notifications', 'list'], { queryFn: () => new Promise(() => {}) });
});

describe('applyNewNotification', () => {
  it('prepends to matching first pages, trims to the page size and increments the count', () => {
    seed(UNREAD_COUNT_KEY, 2);
    seed(listKey(ALL, 0, 2), page([n('a'), n('b')], 2, 2));
    seed(listKey(UNREAD), page([n('a')]));

    applyNewNotification(qc, n('new'));

    expect(list(ALL, 0, 2)!.items.map((i) => i.id)).toEqual(['new', 'a']);
    expect(list(ALL, 0, 2)!.totalElements).toBe(3);
    expect(list(ALL, 0, 2)!.totalPages).toBe(2);
    expect(list(UNREAD)!.items.map((i) => i.id)).toEqual(['new', 'a']);
    expect(count()).toBe(3);
  });

  it('skips lists filtered to another type and invalidates later pages', () => {
    seed(listKey(APPROVALS), page([n('x', false, 'APPROVAL_REQUESTED')]));
    seed(listKey(ALL, 1), { ...page([n('b')], 21), page: 1 });

    applyNewNotification(qc, n('new'));

    expect(list(APPROVALS)!.items.map((i) => i.id)).toEqual(['x']);
    expect(isStale(listKey(ALL, 1))).toBe(true);
  });

  it('ignores a notification that is already cached', () => {
    seed(UNREAD_COUNT_KEY, 1);
    seed(listKey(ALL), page([n('a')]));

    applyNewNotification(qc, n('a'));

    expect(list(ALL)!.items).toHaveLength(1);
    expect(count()).toBe(1);
  });
});

describe('applyReadState', () => {
  it('patches read flags in every non-unread list and decrements the count', () => {
    seed(UNREAD_COUNT_KEY, 3);
    seed(listKey(ALL), page([n('a'), n('b'), n('c')]));
    seed(listKey(APPROVALS), page([n('a', false, 'APPROVAL_REQUESTED')]));
    seed(listKey(UNREAD), page([n('a'), n('b'), n('c')]));

    applyReadState(qc, { ids: ['a', 'b'], read: true });

    expect(list(ALL)!.items.map((i) => i.read)).toEqual([true, true, false]);
    expect(list(APPROVALS)!.items[0].read).toBe(true);
    expect(isStale(listKey(UNREAD))).toBe(true);
    expect(count()).toBe(1);
  });

  it('does not change the count again when the cache already has the new state', () => {
    seed(UNREAD_COUNT_KEY, 1);
    seed(listKey(ALL), page([n('a', true), n('b')]));

    applyReadState(qc, { ids: ['a'], read: true });

    expect(count()).toBe(1);
  });

  it('increments the count when marking unread', () => {
    seed(UNREAD_COUNT_KEY, 0);
    seed(listKey(ALL), page([n('a', true)]));

    applyReadState(qc, { ids: ['a'], read: false });

    expect(list(ALL)!.items[0].read).toBe(false);
    expect(count()).toBe(1);
  });

  it('never makes the count negative', () => {
    seed(UNREAD_COUNT_KEY, 0);
    seed(listKey(ALL), page([n('a'), n('b')]));

    applyReadState(qc, { ids: ['a', 'b'], read: true });

    expect(count()).toBe(0);
  });

  it('refetches the count when an ID is not cached', () => {
    seed(UNREAD_COUNT_KEY, 4);
    seed(listKey(ALL), page([n('a')]));

    applyReadState(qc, { ids: ['a', 'unknown'], read: true });

    expect(count()).toBe(4);
    expect(isStale([...UNREAD_COUNT_KEY])).toBe(true);
  });

  it('marks everything read and zeroes the count on {all: true}', () => {
    seed(UNREAD_COUNT_KEY, 5);
    seed(listKey(ALL), page([n('a'), n('b', true)]));
    seed(listKey(UNREAD), page([n('a')]));

    applyReadState(qc, { all: true });

    expect(list(ALL)!.items.every((i) => i.read)).toBe(true);
    expect(isStale(listKey(UNREAD))).toBe(true);
    expect(count()).toBe(0);
  });
});

describe('races between events and fetches', () => {
  it('refetches instead of incrementing a count fetched moments ago (it may already include the event)', async () => {
    // Only the count is cached (dropdown closed) and was just refetched after a reconnect:
    // the notification was committed before that fetch, but its event arrives after it.
    qc.setQueryData(UNREAD_COUNT_KEY, 3, { updatedAt: Date.now() - (SETTLE_MS - 1000) });
    serverCount = 3;

    applyNewNotification(qc, n('new'));

    expect(count()).toBe(3);
    await vi.waitFor(() => expect(qc.getQueryState(UNREAD_COUNT_KEY)?.fetchStatus).toBe('idle'));
    expect(count()).toBe(3);
  });

  it('replaces a count fetch already in flight instead of patching under it', async () => {
    seed(UNREAD_COUNT_KEY, 3);
    // A fetch that started before the notification was committed (never resolves here).
    void qc.fetchQuery({ queryKey: UNREAD_COUNT_KEY }).catch(() => undefined);
    expect(qc.getQueryState(UNREAD_COUNT_KEY)?.fetchStatus).toBe('fetching');
    serverCount = 4;

    applyNewNotification(qc, n('new'));

    // Not patched to 4 locally: the stale fetch is cancelled and a new one brings the server value.
    expect(count()).toBe(3);
    await vi.waitFor(() => expect(count()).toBe(4));
  });

  it('refetches a first page with a fetch in flight instead of prepending to it', () => {
    seed(listKey(ALL), page([n('a')]));
    void qc.fetchQuery({ queryKey: listKey(ALL) }).catch(() => undefined);

    applyNewNotification(qc, n('new'));

    expect(list(ALL)!.items.map((i) => i.id)).toEqual(['a']);
    expect(qc.getQueryState(listKey(ALL))?.fetchStatus).toBe('fetching');
  });

  it('refetches instead of patching read-state onto a just-fetched count', () => {
    seed(listKey(ALL), page([n('a')]));
    qc.setQueryData(UNREAD_COUNT_KEY, 1, { updatedAt: Date.now() });

    applyReadState(qc, { ids: ['a'], read: true });

    expect(count()).toBe(1);
    expect(qc.getQueryState(UNREAD_COUNT_KEY)?.fetchStatus).toBe('fetching');
  });
});
