import { describe, expect, it } from 'vitest';
import { backoffDelay, nextFailureCount, STABLE_STREAM_MS } from './backoff';

const noJitter = () => 0.5; // maps to a jitter factor of 0
const minJitter = () => 0; // -20 %
const maxJitter = () => 0.999999; // just under +20 %

describe('backoffDelay', () => {
  it('starts at 1s and doubles per failure', () => {
    expect([1, 2, 3, 4, 5].map((f) => backoffDelay(f, noJitter))).toEqual([1000, 2000, 4000, 8000, 16000]);
  });

  it('caps at 30s', () => {
    expect(backoffDelay(6, noJitter)).toBe(30000);
    expect(backoffDelay(50, noJitter)).toBe(30000);
  });

  it('treats 0 failures like the first', () => {
    expect(backoffDelay(0, noJitter)).toBe(1000);
  });

  it('applies ±20 % jitter, also around the cap', () => {
    expect(backoffDelay(1, minJitter)).toBe(800);
    expect(backoffDelay(1, maxJitter)).toBe(1200);
    expect(backoffDelay(10, minJitter)).toBe(24000);
    expect(backoffDelay(10, maxJitter)).toBe(36000);
  });

  it('stays within ±20 % with the real random source', () => {
    for (let i = 0; i < 200; i++) {
      const delay = backoffDelay(3);
      expect(delay).toBeGreaterThanOrEqual(3200);
      expect(delay).toBeLessThanOrEqual(4800);
    }
  });
});

describe('nextFailureCount', () => {
  it('counts a connection that never opened as a failure', () => {
    expect(nextFailureCount(0, null)).toBe(1);
    expect(nextFailureCount(3, null)).toBe(4);
  });

  it('resets after a stream stayed open 60s (e.g. the 30-minute server close)', () => {
    expect(nextFailureCount(5, STABLE_STREAM_MS)).toBe(1);
    expect(nextFailureCount(5, 30 * 60_000)).toBe(1);
    expect(backoffDelay(nextFailureCount(5, 30 * 60_000), noJitter)).toBe(1000);
  });

  it('does not reset just because a stream opened', () => {
    expect(nextFailureCount(2, 0)).toBe(3);
    expect(nextFailureCount(2, STABLE_STREAM_MS - 1)).toBe(3);
  });

  it('makes a tab that keeps getting evicted (6+ tabs, 5-stream cap) back off to 30s, not reconnect at once', () => {
    // Each reconnect opens fine, then the server closes it within a second because a
    // newer stream pushed it out of the 5-stream cap.
    let failures = 0;
    const delays: number[] = [];
    for (let i = 0; i < 8; i++) {
      failures = nextFailureCount(failures, 500);
      delays.push(backoffDelay(failures, noJitter));
    }
    expect(delays).toEqual([1000, 2000, 4000, 8000, 16000, 30000, 30000, 30000]);
  });
});
