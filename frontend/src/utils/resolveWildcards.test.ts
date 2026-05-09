import { describe, it, expect } from 'vitest';
import { resolveWildcards } from './resolveWildcards';

const FIXED = new Date('2025-03-15T10:00:00Z'); // Saturday, week 11, Q1

describe('resolveWildcards', () => {
  // --- base wildcards ---

  it('resolves {{date}} to YYYY-MM-DD', () => {
    expect(resolveWildcards('{{date}}', { now: FIXED })).toBe('2025-03-15');
  });

  it('resolves {{day}}', () => {
    expect(resolveWildcards('{{day}}', { now: FIXED })).toBe('15');
  });

  it('pads {{day}} with leading zero', () => {
    expect(resolveWildcards('{{day}}', { now: new Date('2025-03-05') })).toBe('05');
  });

  it('resolves {{month}}', () => {
    expect(resolveWildcards('{{month}}', { now: FIXED })).toBe('03');
  });

  it('pads {{month}} with leading zero', () => {
    expect(resolveWildcards('{{month}}', { now: new Date('2025-01-15') })).toBe('01');
  });

  it('resolves {{year}}', () => {
    expect(resolveWildcards('{{year}}', { now: FIXED })).toBe('2025');
  });

  it('resolves {{week}} — ISO week number', () => {
    expect(resolveWildcards('{{week}}', { now: FIXED })).toBe('11');
  });

  it('resolves {{quarter}}', () => {
    expect(resolveWildcards('{{quarter}}', { now: FIXED })).toBe('Q1');
  });

  it('resolves {{quarter}} Q2', () => {
    expect(resolveWildcards('{{quarter}}', { now: new Date('2025-04-01') })).toBe('Q2');
  });

  it('resolves {{quarter}} Q3', () => {
    expect(resolveWildcards('{{quarter}}', { now: new Date('2025-07-01') })).toBe('Q3');
  });

  it('resolves {{quarter}} Q4', () => {
    expect(resolveWildcards('{{quarter}}', { now: new Date('2025-10-01') })).toBe('Q4');
  });

  // --- context wildcards ---

  it('resolves {{project}}', () => {
    expect(resolveWildcards('{{project}} report', { now: FIXED, project: 'Alpha' })).toBe('Alpha report');
  });

  it('resolves {{creator}}', () => {
    expect(resolveWildcards('Created by {{creator}}', { now: FIXED, creator: 'Alice' })).toBe('Created by Alice');
  });

  it('resolves {{assignee}}', () => {
    expect(resolveWildcards('Assign {{assignee}}', { now: FIXED, assignee: 'Bob' })).toBe('Assign Bob');
  });

  it('resolves {{occurrence}}', () => {
    expect(resolveWildcards('Run #{{occurrence}}', { now: FIXED, occurrence: 7 })).toBe('Run #7');
  });

  // --- unknown wildcards pass through ---

  it('passes through unknown wildcards unchanged', () => {
    expect(resolveWildcards('Hello {{unknown}}', { now: FIXED })).toBe('Hello {{unknown}}');
  });

  it('passes through {{project}} when not provided in context', () => {
    expect(resolveWildcards('{{project}}', { now: FIXED })).toBe('{{project}}');
  });

  it('passes through {{creator}} when not provided in context', () => {
    expect(resolveWildcards('{{creator}}', { now: FIXED })).toBe('{{creator}}');
  });

  it('passes through {{assignee}} when not provided in context', () => {
    expect(resolveWildcards('{{assignee}}', { now: FIXED })).toBe('{{assignee}}');
  });

  it('passes through {{occurrence}} when not provided in context', () => {
    expect(resolveWildcards('{{occurrence}}', { now: FIXED })).toBe('{{occurrence}}');
  });

  // --- arithmetic: date ---

  it('resolves {{date+3}} as date offset by 3 days', () => {
    expect(resolveWildcards('{{date+3}}', { now: FIXED })).toBe('2025-03-18');
  });

  it('resolves {{date-5}} as date offset by -5 days', () => {
    expect(resolveWildcards('{{date-5}}', { now: FIXED })).toBe('2025-03-10');
  });

  it('{{date+N}} handles month rollover', () => {
    expect(resolveWildcards('{{date+20}}', { now: FIXED })).toBe('2025-04-04');
  });

  it('{{date-N}} handles year rollover backwards', () => {
    expect(resolveWildcards('{{date-80}}', { now: new Date('2025-01-10') })).toBe('2024-10-22');
  });

  // --- arithmetic: week ---

  it('resolves {{week+1}} — next ISO week', () => {
    expect(resolveWildcards('{{week+1}}', { now: FIXED })).toBe('12');
  });

  it('resolves {{week-1}} — previous ISO week', () => {
    expect(resolveWildcards('{{week-1}}', { now: FIXED })).toBe('10');
  });

  // --- arithmetic: month ---

  it('resolves {{month+1}}', () => {
    expect(resolveWildcards('{{month+1}}', { now: FIXED })).toBe('04');
  });

  it('resolves {{month-1}}', () => {
    expect(resolveWildcards('{{month-1}}', { now: FIXED })).toBe('02');
  });

  it('{{month+N}} rolls over to next year month', () => {
    expect(resolveWildcards('{{month+10}}', { now: FIXED })).toBe('01');
  });

  it('{{month-N}} rolls over to previous year month', () => {
    expect(resolveWildcards('{{month-3}}', { now: FIXED })).toBe('12');
  });

  // --- arithmetic: year ---

  it('resolves {{year+1}}', () => {
    expect(resolveWildcards('{{year+1}}', { now: FIXED })).toBe('2026');
  });

  it('resolves {{year-1}}', () => {
    expect(resolveWildcards('{{year-1}}', { now: FIXED })).toBe('2024');
  });

  // --- arithmetic: quarter ---

  it('resolves {{quarter+1}} Q1 → Q2', () => {
    expect(resolveWildcards('{{quarter+1}}', { now: FIXED })).toBe('Q2');
  });

  it('resolves {{quarter+3}} Q1 → Q4', () => {
    expect(resolveWildcards('{{quarter+3}}', { now: FIXED })).toBe('Q4');
  });

  it('{{quarter+N}} rolls over Q4 → Q1', () => {
    const q4 = new Date('2025-10-01');
    expect(resolveWildcards('{{quarter+1}}', { now: q4 })).toBe('Q1');
  });

  it('{{quarter-N}} rolls over Q1 → Q4', () => {
    expect(resolveWildcards('{{quarter-1}}', { now: FIXED })).toBe('Q4');
  });

  // --- arithmetic: invalid offset passes through ---

  it('passes through arithmetic token with non-numeric offset', () => {
    expect(resolveWildcards('{{date+abc}}', { now: FIXED })).toBe('{{date+abc}}');
  });

  it('passes through arithmetic on unknown base token', () => {
    expect(resolveWildcards('{{foo+1}}', { now: FIXED })).toBe('{{foo+1}}');
  });

  // --- multiple wildcards in one string ---

  it('resolves multiple wildcards in one template string', () => {
    const result = resolveWildcards('Report {{year}}-{{month}} week {{week}}', { now: FIXED });
    expect(result).toBe('Report 2025-03 week 11');
  });

  it('resolves mixed wildcards and unknown tokens', () => {
    const result = resolveWildcards('{{project}}: {{date}} ({{unknown}})', {
      now: FIXED,
      project: 'Beta',
    });
    expect(result).toBe('Beta: 2025-03-15 ({{unknown}})');
  });

  // --- uses current date when no context provided ---

  it('resolves without context (uses real now)', () => {
    const result = resolveWildcards('{{year}}');
    expect(result).toMatch(/^\d{4}$/);
  });

  // --- whitespace trimming inside tokens ---

  it('trims whitespace inside {{ token }}', () => {
    expect(resolveWildcards('{{ date }}', { now: FIXED })).toBe('2025-03-15');
  });
});
