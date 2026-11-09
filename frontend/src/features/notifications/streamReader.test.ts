import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { readStream, STREAM_IDLE_TIMEOUT_MS } from './streamReader';

const HEARTBEAT = new TextEncoder().encode(':heartbeat\n\n');

/** A body the test writes to by hand, like a fetch response that stays open. */
function openBody() {
  let controller!: ReadableStreamDefaultController<Uint8Array>;
  let cancelled = false;
  const body = new ReadableStream<Uint8Array>({
    start: (c) => {
      controller = c;
    },
    cancel: () => {
      cancelled = true;
    },
  });
  return { body, controller, isCancelled: () => cancelled };
}

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('readStream', () => {
  it('cancels a stream that goes silent and resolves "idle"', async () => {
    const { body, controller, isCancelled } = openBody();
    const chunks: Uint8Array[] = [];
    const result = readStream(body, (c) => chunks.push(c));

    controller.enqueue(HEARTBEAT); // the heartbeat on open
    await vi.advanceTimersByTimeAsync(STREAM_IDLE_TIMEOUT_MS - 1);
    expect(isCancelled()).toBe(false);

    await vi.advanceTimersByTimeAsync(1);
    await expect(result).resolves.toBe('idle');
    expect(isCancelled()).toBe(true);
    expect(chunks).toHaveLength(1);
  });

  it('keeps a stream with heartbeats every 25s open, and resolves "closed" when the server ends it', async () => {
    const { body, controller, isCancelled } = openBody();
    let settled = false;
    const result = readStream(body, () => undefined).finally(() => {
      settled = true;
    });

    for (let i = 0; i < 12; i++) {
      controller.enqueue(HEARTBEAT);
      await vi.advanceTimersByTimeAsync(25_000);
    }
    expect(settled).toBe(false);
    expect(isCancelled()).toBe(false);

    controller.close();
    await expect(result).resolves.toBe('closed');
    expect(vi.getTimerCount()).toBe(0);
  });

  it('clears the timer when the read fails (e.g. the fetch is aborted)', async () => {
    const { body, controller } = openBody();
    const result = readStream(body, () => undefined);

    controller.error(new DOMException('aborted', 'AbortError'));
    await expect(result).rejects.toThrow('aborted');
    expect(vi.getTimerCount()).toBe(0);
  });
});
