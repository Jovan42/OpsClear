import { describe, expect, it } from 'vitest';
import type { DateFormat, Locale } from '../hooks/usePreferences';
import { appLocale, formatDate, formatDateTime, formatDayMonth, intlLocale } from './formatDate';

// A local date-time (no `Z`), so the expected output doesn't depend on the machine's zone.
const SUNDAY = '2026-11-15T14:05:00';
// Single-digit day and month, to check the fixed formats pad them.
const EARLY = new Date(2026, 2, 7, 9, 3);

const prefs = (dateFormat: DateFormat, locale: Locale) => ({ dateFormat, locale });

describe('formatDate', () => {
  it.each<[DateFormat, Locale, string]>([
    ['AUTO', 'en', '15 Nov 2026'],
    ['AUTO', 'sr', '15. nov 2026.'],
    ['DMY_DOT', 'en', '15.11.2026'],
    ['DMY_DOT', 'sr', '15.11.2026'],
    ['DMY_SLASH', 'en', '15/11/2026'],
    ['DMY_SLASH', 'sr', '15/11/2026'],
    ['MDY_SLASH', 'en', '11/15/2026'],
    ['MDY_SLASH', 'sr', '11/15/2026'],
  ])('%s in %s gives %s', (format, locale, expected) => {
    expect(formatDate(SUNDAY, prefs(format, locale))).toBe(expected);
  });

  it('pads day and month to two digits in the fixed formats', () => {
    expect(formatDate(EARLY, prefs('DMY_DOT', 'en'))).toBe('07.03.2026');
    expect(formatDate(EARLY, prefs('DMY_SLASH', 'en'))).toBe('07/03/2026');
    expect(formatDate(EARLY, prefs('MDY_SLASH', 'en'))).toBe('03/07/2026');
  });

  it('adds the weekday in the app language when asked', () => {
    expect(formatDate(SUNDAY, prefs('AUTO', 'en'), { weekday: true })).toBe('Sun, 15 Nov 2026');
    expect(formatDate(SUNDAY, prefs('DMY_DOT', 'sr'), { weekday: true })).toBe('ned, 15.11.2026');
  });

  it('accepts a Date or a timestamp as well as a string', () => {
    const d = new Date(SUNDAY);
    expect(formatDate(d, prefs('DMY_DOT', 'en'))).toBe('15.11.2026');
    expect(formatDate(d.getTime(), prefs('DMY_DOT', 'en'))).toBe('15.11.2026');
  });

  it('gives an empty string for an invalid date', () => {
    expect(formatDate('not a date', prefs('AUTO', 'en'))).toBe('');
    expect(formatDate('not a date', prefs('DMY_DOT', 'en'))).toBe('');
  });
});

describe('formatDateTime', () => {
  it.each<[DateFormat, Locale, string]>([
    ['AUTO', 'en', '15 Nov 2026, 14:05'],
    ['AUTO', 'sr', '15. nov 2026. 14:05'],
    ['DMY_DOT', 'en', '15.11.2026 14:05'],
    ['DMY_DOT', 'sr', '15.11.2026 14:05'],
    ['DMY_SLASH', 'en', '15/11/2026 14:05'],
    ['DMY_SLASH', 'sr', '15/11/2026 14:05'],
    ['MDY_SLASH', 'en', '11/15/2026 14:05'],
    ['MDY_SLASH', 'sr', '11/15/2026 14:05'],
  ])('%s in %s gives %s', (format, locale, expected) => {
    expect(formatDateTime(SUNDAY, prefs(format, locale))).toBe(expected);
  });

  it('pads the time to two digits', () => {
    expect(formatDateTime(EARLY, prefs('DMY_DOT', 'en'))).toBe('07.03.2026 09:03');
  });

  it('adds the weekday in the app language when asked', () => {
    expect(formatDateTime(SUNDAY, prefs('AUTO', 'sr'), { weekday: true })).toBe('ned, 15. nov 2026. 14:05');
    expect(formatDateTime(SUNDAY, prefs('MDY_SLASH', 'en'), { weekday: true })).toBe('Sun, 11/15/2026 14:05');
  });

  it('gives an empty string for an invalid date', () => {
    expect(formatDateTime('', prefs('AUTO', 'en'))).toBe('');
  });
});

describe('formatDayMonth', () => {
  it.each<[DateFormat, Locale, string]>([
    ['AUTO', 'en', '15 Nov'],
    ['AUTO', 'sr', '15. nov'],
    ['DMY_DOT', 'en', '15.11.'],
    ['DMY_SLASH', 'sr', '15/11'],
    ['MDY_SLASH', 'en', '11/15'],
  ])('%s in %s gives %s', (format, locale, expected) => {
    expect(formatDayMonth(SUNDAY, prefs(format, locale))).toBe(expected);
  });
});

describe('locale helpers', () => {
  it('maps the app language to an Intl locale and back', () => {
    expect(intlLocale('en')).toBe('en-GB');
    expect(intlLocale('sr')).toBe('sr-Latn');
    expect(intlLocale('sr-Latn')).toBe('sr-Latn');
    expect(appLocale('sr-Latn')).toBe('sr');
    expect(appLocale('en-US')).toBe('en');
  });
});
