import { formatSeconds } from '../../utils/formatDuration';

/** Shown for a metric with no value (no job in the set has it). */
export const EMPTY = '—';

export function formatAvg(seconds: number | null): string {
  return seconds === null ? EMPTY : formatSeconds(seconds);
}

export function formatLateRate(lateRate: number | null): string {
  return lateRate === null ? EMPTY : `${Math.round(lateRate * 100)}%`;
}
