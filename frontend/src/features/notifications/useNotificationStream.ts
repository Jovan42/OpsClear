import { useEffect, useSyncExternalStore } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { notificationsApi, withPayload } from '../../api/notifications';
import { useAuth } from '../../auth/AuthContext';
import type { NotificationResponse } from '../../types';
import { backoffDelay, nextFailureCount } from './backoff';
import { applyNewNotification, applyReadState, invalidateAfterConnect, type ReadStateEvent } from './notificationCache';
import { createSseParser, type SseEvent } from './sseParser';

/** The stream pauses once the tab has been hidden this long, and reconnects when shown. */
export const HIDDEN_PAUSE_MS = 5 * 60_000;

// ---- Connected state ----
// Shared with the REST hooks: their window-focus refetch and the bell's refresh on open
// are only a fallback for while the stream is down.

let connected = false;
const listeners = new Set<() => void>();

function setConnected(value: boolean) {
  if (connected === value) return;
  connected = value;
  listeners.forEach((l) => l());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** True while the notification stream is open, so the caches are kept current by push. */
export function useNotificationStreamConnected(): boolean {
  return useSyncExternalStore(subscribe, () => connected);
}

// ---- Connection loop ----

interface StreamHandlers {
  onOpen: () => void;
  onEvent: (event: SseEvent) => void;
}

/**
 * Keeps one stream open: connects, reads until it ends, then reconnects with backoff
 * (see backoff.ts). Pauses after the tab has been hidden {@link HIDDEN_PAUSE_MS} and
 * reconnects at once when it's shown again. Returns a stop function that aborts the
 * stream and every timer.
 */
function runStream({ onOpen, onEvent }: StreamHandlers): () => void {
  let stopped = false;
  let paused = false;
  let failures = 0;
  let controller: AbortController | null = null;
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
  let hiddenTimer: ReturnType<typeof setTimeout> | undefined;

  async function connect() {
    reconnectTimer = undefined;
    const ac = new AbortController();
    controller = ac;
    let openedAt: number | null = null;

    try {
      const response = await notificationsApi.openStream(ac.signal);
      if (!response.ok || !response.body) throw new Error(`Notification stream failed: ${response.status}`);
      openedAt = Date.now();
      setConnected(true);
      onOpen();

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      const parser = createSseParser(onEvent);
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        parser.push(decoder.decode(value, { stream: true }));
      }
    } catch {
      // Network error, backend down, non-2xx or aborted: all handled below.
    }

    // Aborted by stop() or pause(): they own what happens next.
    if (ac.signal.aborted) return;
    controller = null;
    setConnected(false);
    // Ended by the server (30-minute close, eviction by a newer tab, restart) or failed.
    failures = nextFailureCount(failures, openedAt === null ? null : Date.now() - openedAt);
    reconnectTimer = setTimeout(() => void connect(), backoffDelay(failures));
  }

  function disconnect() {
    clearTimeout(reconnectTimer);
    reconnectTimer = undefined;
    controller?.abort();
    controller = null;
    setConnected(false);
  }

  function pause() {
    if (stopped || paused) return;
    paused = true;
    disconnect();
  }

  function handleVisibility() {
    if (document.visibilityState === 'hidden') {
      clearTimeout(hiddenTimer);
      hiddenTimer = setTimeout(pause, HIDDEN_PAUSE_MS);
      return;
    }
    clearTimeout(hiddenTimer);
    hiddenTimer = undefined;
    if (paused && !stopped) {
      paused = false;
      failures = 0;
      void connect();
    }
  }

  document.addEventListener('visibilitychange', handleVisibility);
  handleVisibility();
  if (!paused) void connect();

  return () => {
    stopped = true;
    document.removeEventListener('visibilitychange', handleVisibility);
    clearTimeout(hiddenTimer);
    disconnect();
  };
}

/**
 * Live notification updates (ADR-0051): one stream per tab, mounted once in AppLayout for
 * authenticated users. `notification` and `read-state` events update the TanStack Query
 * caches in place; every (re)connect refetches the count and lists, because the server
 * doesn't replay what was sent while disconnected. Heartbeat comments are skipped by the
 * parser. The stream is aborted on unmount and logout.
 */
export function useNotificationStream() {
  const { authenticated } = useAuth();
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!authenticated) return;

    function onEvent({ event, data }: SseEvent) {
      let parsed: unknown;
      try {
        parsed = JSON.parse(data);
      } catch {
        return;
      }
      if (event === 'notification') {
        applyNewNotification(queryClient, withPayload(parsed as NotificationResponse));
      } else if (event === 'read-state') {
        applyReadState(queryClient, parsed as ReadStateEvent);
      }
    }

    return runStream({ onOpen: () => invalidateAfterConnect(queryClient), onEvent });
  }, [authenticated, queryClient]);
}
