import { describe, expect, it } from 'vitest';
import { pickActiveHeading } from './headings';

const ids = ['statuses', 'first-steps', 'organised'];
const none = new Set<string>();

describe('pickActiveHeading', () => {
  it('picks the first heading in the top of the viewport', () => {
    expect(pickActiveHeading({ ids, inView: new Set(['first-steps', 'organised']), passed: none, atBottom: false })).toBe('first-steps');
  });

  it('keeps the last heading scrolled past when none is in view', () => {
    expect(pickActiveHeading({ ids, inView: none, passed: new Set(['statuses', 'first-steps']), atBottom: false })).toBe('first-steps');
  });

  it('picks the last heading at the bottom of the page, even if an earlier one is in view', () => {
    expect(pickActiveHeading({ ids, inView: new Set(['first-steps']), passed: new Set(['statuses']), atBottom: true })).toBe('organised');
  });

  it('returns null (keep the current one) with nothing in view or passed', () => {
    expect(pickActiveHeading({ ids, inView: none, passed: none, atBottom: false })).toBeNull();
    expect(pickActiveHeading({ ids: [], inView: none, passed: none, atBottom: true })).toBeNull();
  });
});
