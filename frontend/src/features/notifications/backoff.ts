// Reconnect policy of the notification stream (ADR-0051 §SSE stream): exponential backoff
// from 1s, doubling to a 30s cap, ±20% jitter, reset once a stream has stayed open 60s.

export const BACKOFF_BASE_MS = 1_000;
export const BACKOFF_CAP_MS = 30_000;
export const BACKOFF_JITTER = 0.2;
/** A stream that stayed open at least this long counts as healthy and resets the backoff. */
export const STABLE_STREAM_MS = 60_000;

/**
 * Delay before reconnect number `failures` (1 = the first reconnect after a drop):
 * 1s, 2s, 4s, … capped at 30s, then ±20% jitter so tabs and clients don't reconnect in
 * lockstep after a backend restart. `random` is injectable for tests (0 ≤ r < 1).
 */
export function backoffDelay(failures: number, random: () => number = Math.random): number {
  const exponent = Math.max(0, failures - 1);
  const base = Math.min(BACKOFF_BASE_MS * 2 ** exponent, BACKOFF_CAP_MS);
  const jitter = (random() * 2 - 1) * BACKOFF_JITTER;
  return Math.round(base * (1 + jitter));
}

/**
 * The failure count after a stream ends. `openForMs` is how long it was open, or null if
 * it never opened (network error, backend down, non-2xx).
 *
 * Only a stream that stayed open {@link STABLE_STREAM_MS} resets the count, not merely
 * opening one. That matters for the server's 5-streams-per-user cap: with a 6th tab, the
 * server closes the oldest stream right after another opens. If opening reset the count,
 * the evicted tab would reconnect after 1s, evict another tab, and so on forever. Counting
 * the short-lived stream as a failure makes evicted tabs back off to 30s instead.
 *
 * A stream ended by the server's 30-minute close was open long enough, so it reconnects
 * after the first (≈1s) delay.
 */
export function nextFailureCount(failures: number, openForMs: number | null): number {
  if (openForMs !== null && openForMs >= STABLE_STREAM_MS) return 1;
  return failures + 1;
}
