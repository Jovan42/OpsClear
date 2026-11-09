import { beforeEach, describe, expect, it } from 'vitest';
import { QueryClient } from '@tanstack/react-query';
import type { NotificationListFilters, NotificationPageResponse, NotificationResponse } from '../../types';
import { applyNewNotification, applyReadState, UNREAD_COUNT_KEY } from './notificationCache';

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

beforeEach(() => {
  qc = new QueryClient();
});

describe('applyNewNotification', () => {
  it('prepends to matching first pages, trims to the page size and increments the count', () => {
    qc.setQueryData(UNREAD_COUNT_KEY, 2);
    qc.setQueryData(listKey(ALL, 0, 2), page([n('a'), n('b')], 2, 2));
    qc.setQueryData(listKey(UNREAD), page([n('a')]));

    applyNewNotification(qc, n('new'));

    expect(list(ALL, 0, 2)!.items.map((i) => i.id)).toEqual(['new', 'a']);
    expect(list(ALL, 0, 2)!.totalElements).toBe(3);
    expect(list(ALL, 0, 2)!.totalPages).toBe(2);
    expect(list(UNREAD)!.items.map((i) => i.id)).toEqual(['new', 'a']);
    expect(count()).toBe(3);
  });

  it('skips lists filtered to another type and invalidates later pages', () => {
    qc.setQueryData(listKey(APPROVALS), page([n('x', false, 'APPROVAL_REQUESTED')]));
    qc.setQueryData(listKey(ALL, 1), { ...page([n('b')], 21), page: 1 });

    applyNewNotification(qc, n('new'));

    expect(list(APPROVALS)!.items.map((i) => i.id)).toEqual(['x']);
    expect(isStale(listKey(ALL, 1))).toBe(true);
  });

  it('ignores a notification that is already cached', () => {
    qc.setQueryData(UNREAD_COUNT_KEY, 1);
    qc.setQueryData(listKey(ALL), page([n('a')]));

    applyNewNotification(qc, n('a'));

    expect(list(ALL)!.items).toHaveLength(1);
    expect(count()).toBe(1);
  });
});

describe('applyReadState', () => {
  it('patches read flags in every non-unread list and decrements the count', () => {
    qc.setQueryData(UNREAD_COUNT_KEY, 3);
    qc.setQueryData(listKey(ALL), page([n('a'), n('b'), n('c')]));
    qc.setQueryData(listKey(APPROVALS), page([n('a', false, 'APPROVAL_REQUESTED')]));
    qc.setQueryData(listKey(UNREAD), page([n('a'), n('b'), n('c')]));

    applyReadState(qc, { ids: ['a', 'b'], read: true });

    expect(list(ALL)!.items.map((i) => i.read)).toEqual([true, true, false]);
    expect(list(APPROVALS)!.items[0].read).toBe(true);
    expect(isStale(listKey(UNREAD))).toBe(true);
    expect(count()).toBe(1);
  });

  it('does not change the count again when the cache already has the new state', () => {
    qc.setQueryData(UNREAD_COUNT_KEY, 1);
    qc.setQueryData(listKey(ALL), page([n('a', true), n('b')]));

    applyReadState(qc, { ids: ['a'], read: true });

    expect(count()).toBe(1);
  });

  it('increments the count when marking unread', () => {
    qc.setQueryData(UNREAD_COUNT_KEY, 0);
    qc.setQueryData(listKey(ALL), page([n('a', true)]));

    applyReadState(qc, { ids: ['a'], read: false });

    expect(list(ALL)!.items[0].read).toBe(false);
    expect(count()).toBe(1);
  });

  it('never makes the count negative', () => {
    qc.setQueryData(UNREAD_COUNT_KEY, 0);
    qc.setQueryData(listKey(ALL), page([n('a'), n('b')]));

    applyReadState(qc, { ids: ['a', 'b'], read: true });

    expect(count()).toBe(0);
  });

  it('refetches the count when an ID is not cached', () => {
    qc.setQueryData(UNREAD_COUNT_KEY, 4);
    qc.setQueryData(listKey(ALL), page([n('a')]));

    applyReadState(qc, { ids: ['a', 'unknown'], read: true });

    expect(count()).toBe(4);
    expect(isStale([...UNREAD_COUNT_KEY])).toBe(true);
  });

  it('marks everything read and zeroes the count on {all: true}', () => {
    qc.setQueryData(UNREAD_COUNT_KEY, 5);
    qc.setQueryData(listKey(ALL), page([n('a'), n('b', true)]));
    qc.setQueryData(listKey(UNREAD), page([n('a')]));

    applyReadState(qc, { all: true });

    expect(list(ALL)!.items.every((i) => i.read)).toBe(true);
    expect(isStale(listKey(UNREAD))).toBe(true);
    expect(count()).toBe(0);
  });
});
