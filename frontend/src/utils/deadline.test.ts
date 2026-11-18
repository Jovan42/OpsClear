import { describe, expect, it } from 'vitest';
import { deadlineState, deadlineTextClass, isPastDeadline } from './deadline';

// Local noon, so "today" is the same calendar day in the viewer's zone whatever the machine's zone.
const NOW = new Date(2026, 10, 18, 12, 0);

/** A date-only deadline `days` from NOW's local date, stored as UTC midnight like the backend. */
function deadlineIn(days: number): string {
  const d = new Date(NOW.getFullYear(), NOW.getMonth(), NOW.getDate() + days);
  return new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate())).toISOString();
}

describe('deadlineState', () => {
  it('is due soon on the deadline day itself', () => {
    expect(deadlineState(deadlineIn(0), 'IN_PROGRESS', 3, NOW)).toBe('dueSoon');
  });

  it('is due soon exactly N days ahead', () => {
    expect(deadlineState(deadlineIn(3), 'NEW', 3, NOW)).toBe('dueSoon');
    expect(deadlineState(deadlineIn(1), 'NEW', 1, NOW)).toBe('dueSoon');
    expect(deadlineState(deadlineIn(7), 'BLOCKED', 7, NOW)).toBe('dueSoon');
  });

  it('is normal N+1 days ahead', () => {
    expect(deadlineState(deadlineIn(4), 'NEW', 3, NOW)).toBe('normal');
    expect(deadlineState(deadlineIn(2), 'NEW', 1, NOW)).toBe('normal');
    expect(deadlineState(deadlineIn(8), 'NEW', 7, NOW)).toBe('normal');
  });

  it('is overdue once the deadline day has passed, whatever the threshold', () => {
    expect(deadlineState(deadlineIn(-1), 'IN_PROGRESS', 3, NOW)).toBe('overdue');
    expect(deadlineState(deadlineIn(-10), 'BLOCKED', 0, NOW)).toBe('overdue');
  });

  it('never highlights a completed job', () => {
    expect(deadlineState(deadlineIn(-1), 'COMPLETED', 3, NOW)).toBe('normal');
    expect(deadlineState(deadlineIn(0), 'COMPLETED', 3, NOW)).toBe('normal');
    expect(deadlineState(deadlineIn(2), 'COMPLETED', 3, NOW)).toBe('normal');
  });

  it('turns the due-soon highlight off at 0 days', () => {
    expect(deadlineState(deadlineIn(0), 'NEW', 0, NOW)).toBe('normal');
    expect(deadlineState(deadlineIn(1), 'NEW', 0, NOW)).toBe('normal');
  });

  it('is normal without a deadline', () => {
    expect(deadlineState(null, 'NEW', 3, NOW)).toBe('normal');
    expect(deadlineState(undefined, 'NEW', 3, NOW)).toBe('normal');
  });

  it('agrees with isPastDeadline on what counts as overdue', () => {
    expect(isPastDeadline(deadlineIn(0), NOW)).toBe(false);
    expect(isPastDeadline(deadlineIn(-1), NOW)).toBe(true);
  });
});

describe('deadlineTextClass', () => {
  it('is red when overdue, amber when due soon, and the given class otherwise', () => {
    expect(deadlineTextClass('overdue', 'text-gray-500')).toContain('text-red-600');
    expect(deadlineTextClass('dueSoon', 'text-gray-500')).toContain('text-amber-600');
    expect(deadlineTextClass('normal', 'text-gray-500')).toBe('text-gray-500');
  });
});
