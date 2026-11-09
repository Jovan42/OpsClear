/**
 * The server writes a heartbeat comment on open and every 25s (ADR-0051), so a stream
 * that's silent this long is dead even if the connection never closed: laptop sleep, a
 * network switch or a proxy dropping it without a FIN leave `reader.read()` waiting
 * forever. Over two missed heartbeats, so one slow one doesn't cut a healthy stream.
 */
export const STREAM_IDLE_TIMEOUT_MS = 60_000;

/** How a read ended: the server closed the stream, or the watchdog cut a silent one. */
export type StreamEnd = 'closed' | 'idle';

/**
 * Reads `body` to the end, passing each chunk to `onChunk`. If no bytes arrive for
 * `idleTimeoutMs`, cancels the reader (which releases the connection) and resolves
 * `'idle'`, so the caller reconnects like after any other drop. Any chunk, heartbeats
 * included, resets the timer. Rejects if the read fails, e.g. when the caller aborts the
 * fetch; the timer is cleared in every case.
 */
export async function readStream(
  body: ReadableStream<Uint8Array>,
  onChunk: (chunk: Uint8Array) => void,
  idleTimeoutMs = STREAM_IDLE_TIMEOUT_MS,
): Promise<StreamEnd> {
  const reader = body.getReader();
  let idle = false;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const arm = () => {
    clearTimeout(timer);
    timer = setTimeout(() => {
      idle = true;
      void reader.cancel().catch(() => undefined);
    }, idleTimeoutMs);
  };

  try {
    arm();
    for (;;) {
      const { done, value } = await reader.read();
      if (done) return idle ? 'idle' : 'closed';
      arm();
      onChunk(value);
    }
  } finally {
    clearTimeout(timer);
  }
}
