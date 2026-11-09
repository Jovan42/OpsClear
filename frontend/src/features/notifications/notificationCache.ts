import type { QueryClient, QueryKey } from '@tanstack/react-query';
import type { NotificationListFilters, NotificationPageResponse, NotificationResponse } from '../../types';

// TanStack Query keys of the notification hooks (useNotifications.ts). Lists are
// ['notifications', 'list', filters, page, size].
export const NOTIFICATIONS_KEY = ['notifications'] as const;
export const UNREAD_COUNT_KEY = ['notifications', 'unread-count'] as const;
export const LIST_KEY = ['notifications', 'list'] as const;

/** Data of the stream's `read-state` event: `{ids, read}` or `{all: true}`. */
export type ReadStateEvent = { ids: string[]; read: boolean } | { all: true };

interface CachedList {
  key: QueryKey;
  data: NotificationPageResponse;
  filters: NotificationListFilters;
  page: number;
  size: number;
}

/** Every list variant (filters, pages, sizes) that has data in the cache. */
function cachedLists(queryClient: QueryClient): CachedList[] {
  return queryClient
    .getQueriesData<NotificationPageResponse>({ queryKey: LIST_KEY })
    .flatMap(([key, data]) => {
      if (!data) return [];
      const [, , filters, page, size] = key as [string, string, NotificationListFilters, number, number];
      return [{ key, data, filters, page, size }];
    });
}

function invalidateExact(queryClient: QueryClient, key: QueryKey) {
  void queryClient.invalidateQueries({ queryKey: key, exact: true });
}

/**
 * A `notification` event: prepend it to every cached first page whose filters it matches
 * and add one to the unread count. Later pages shift by one, so they're invalidated
 * rather than patched. An item already in any cached list (e.g. the list was refetched
 * after it was created) is ignored, so it's never shown or counted twice.
 */
export function applyNewNotification(queryClient: QueryClient, notification: NotificationResponse) {
  const lists = cachedLists(queryClient);
  if (lists.some((l) => l.data.items.some((item) => item.id === notification.id))) return;

  for (const { key, data, filters, page, size } of lists) {
    const matches =
      (filters.type == null || filters.type === notification.type) && (!filters.unread || !notification.read);
    if (!matches) continue;
    if (page !== 0) {
      invalidateExact(queryClient, key);
      continue;
    }
    const totalElements = data.totalElements + 1;
    queryClient.setQueryData<NotificationPageResponse>(key, {
      ...data,
      items: [notification, ...data.items].slice(0, size),
      totalElements,
      totalPages: Math.ceil(totalElements / size),
    });
  }

  if (!notification.read) {
    queryClient.setQueryData<number>(UNREAD_COUNT_KEY, (count) => (count === undefined ? count : count + 1));
  }
}

function withRead(item: NotificationResponse, read: boolean): NotificationResponse {
  return item.read === read ? item : ({ ...item, read } as NotificationResponse);
}

/**
 * A `read-state` event (from this tab's own mark-read, or another tab's).
 *
 * - "All"/type-filtered lists: the items' `read` flags are patched in place on every page.
 * - "Unread" lists: invalidated. Marking read removes items (shifting later pages) and
 *   marking unread adds items we may not have, so patching them isn't safe.
 * - Count: `{all}` sets it to 0. For `{ids}` it moves by the number of items whose cached
 *   state actually changes, so the event arriving after this tab already refetched (its
 *   own mutation invalidates too) changes nothing. If an ID isn't in any cached list its
 *   previous state is unknown, so the count is refetched instead. Never below 0.
 */
export function applyReadState(queryClient: QueryClient, event: ReadStateEvent) {
  const lists = cachedLists(queryClient);

  if ('all' in event) {
    for (const { key, data, filters } of lists) {
      if (filters.unread) invalidateExact(queryClient, key);
      else queryClient.setQueryData<NotificationPageResponse>(key, { ...data, items: data.items.map((i) => withRead(i, true)) });
    }
    queryClient.setQueryData<number>(UNREAD_COUNT_KEY, (count) => (count === undefined ? count : 0));
    return;
  }

  const ids = new Set(event.ids);
  // Previous read state per ID, from whichever cached list has it.
  const previous = new Map<string, boolean>();
  for (const { data } of lists) {
    for (const item of data.items) {
      if (ids.has(item.id) && !previous.has(item.id)) previous.set(item.id, item.read);
    }
  }

  for (const { key, data, filters } of lists) {
    if (filters.unread) {
      invalidateExact(queryClient, key);
      continue;
    }
    if (!data.items.some((i) => ids.has(i.id))) continue;
    queryClient.setQueryData<NotificationPageResponse>(key, {
      ...data,
      items: data.items.map((i) => (ids.has(i.id) ? withRead(i, event.read) : i)),
    });
  }

  if ([...ids].some((id) => !previous.has(id))) {
    void queryClient.invalidateQueries({ queryKey: UNREAD_COUNT_KEY });
    return;
  }
  const changed = [...ids].filter((id) => previous.get(id) !== event.read).length;
  if (changed === 0) return;
  const delta = event.read ? -changed : changed;
  queryClient.setQueryData<number>(UNREAD_COUNT_KEY, (count) =>
    count === undefined ? count : Math.max(0, count + delta),
  );
}

/** After every (re)connect: the server doesn't replay, so refetch what may have been missed. */
export function invalidateAfterConnect(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: UNREAD_COUNT_KEY });
  void queryClient.invalidateQueries({ queryKey: LIST_KEY });
}
