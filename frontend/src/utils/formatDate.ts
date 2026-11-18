import type { DateFormat, Locale, Preferences } from '../hooks/usePreferences';

/**
 * The one shared date formatter (ADR-0061). Every date the app shows goes through here, so
 * the user's `dateFormat` preference applies everywhere. Date inputs (pickers) don't: they
 * keep the browser's native format.
 *
 * - `AUTO` follows the **app language** (not the browser's locale): en `15 Nov 2026`,
 *   sr `15. nov 2026.`
 * - `DMY_DOT` `15.11.2026`, `DMY_SLASH` `15/11/2026`, `MDY_SLASH` `11/15/2026`
 *
 * The time part always follows the app language's style. Dates are shown in the viewer's
 * time zone, like the `toLocaleDateString` calls this replaces.
 */

export type DatePrefs = Pick<Preferences, 'dateFormat' | 'locale'>;

export type DateInput = string | number | Date;

export interface DateOptions {
  /** Prefix the short weekday name ("Sun", "ned"), in the app language. */
  weekday?: boolean;
}

/** BCP-47 locale for Intl formatting. The app's Serbian strings are in Latin script. */
export function intlLocale(language: string): string {
  return language.startsWith('sr') ? 'sr-Latn' : 'en-GB';
}

/** The app `Locale` for an i18next language code (`sr`, `sr-Latn`, `en`, ...). */
export function appLocale(language: string): Locale {
  return language.startsWith('sr') ? 'sr' : 'en';
}

const DATE_PARTS: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'short', year: 'numeric' };
const TIME_PARTS: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit' };

function toDate(value: DateInput): Date | null {
  const d = value instanceof Date ? value : new Date(value);
  return Number.isNaN(d.getTime()) ? null : d;
}

const pad = (n: number) => String(n).padStart(2, '0');

function fixedDate(d: Date, format: Exclude<DateFormat, 'AUTO'>): string {
  const day = pad(d.getDate());
  const month = pad(d.getMonth() + 1);
  const year = d.getFullYear();
  switch (format) {
    case 'DMY_DOT':
      return `${day}.${month}.${year}`;
    case 'DMY_SLASH':
      return `${day}/${month}/${year}`;
    case 'MDY_SLASH':
      return `${month}/${day}/${year}`;
  }
}

function fixedDayMonth(d: Date, format: Exclude<DateFormat, 'AUTO'>): string {
  const day = pad(d.getDate());
  const month = pad(d.getMonth() + 1);
  switch (format) {
    case 'DMY_DOT':
      return `${day}.${month}.`;
    case 'DMY_SLASH':
      return `${day}/${month}`;
    case 'MDY_SLASH':
      return `${month}/${day}`;
  }
}

function weekdayPrefix(d: Date, locale: string, options?: DateOptions): string {
  if (!options?.weekday) return '';
  return `${new Intl.DateTimeFormat(locale, { weekday: 'short' }).format(d)}, `;
}

/** A date, e.g. `15 Nov 2026` or `15.11.2026`. Gives `''` for an invalid date. */
export function formatDate(value: DateInput, prefs: DatePrefs, options?: DateOptions): string {
  const d = toDate(value);
  if (!d) return '';
  const locale = intlLocale(prefs.locale);
  if (prefs.dateFormat === 'AUTO') {
    return new Intl.DateTimeFormat(locale, {
      ...DATE_PARTS,
      ...(options?.weekday ? { weekday: 'short' } : {}),
    }).format(d);
  }
  return weekdayPrefix(d, locale, options) + fixedDate(d, prefs.dateFormat);
}

/** A date and time, e.g. `15 Nov 2026, 14:05` or `15.11.2026 14:05`. Gives `''` for an invalid date. */
export function formatDateTime(value: DateInput, prefs: DatePrefs, options?: DateOptions): string {
  const d = toDate(value);
  if (!d) return '';
  const locale = intlLocale(prefs.locale);
  if (prefs.dateFormat === 'AUTO') {
    return new Intl.DateTimeFormat(locale, {
      ...DATE_PARTS,
      ...TIME_PARTS,
      ...(options?.weekday ? { weekday: 'short' } : {}),
    }).format(d);
  }
  const time = new Intl.DateTimeFormat(locale, TIME_PARTS).format(d);
  return `${weekdayPrefix(d, locale, options)}${fixedDate(d, prefs.dateFormat)} ${time}`;
}

/** Day and month only, for compact labels such as chart axes: `15 Nov`, `15.11.`, `11/15`. */
export function formatDayMonth(value: DateInput, prefs: DatePrefs): string {
  const d = toDate(value);
  if (!d) return '';
  if (prefs.dateFormat === 'AUTO') {
    return new Intl.DateTimeFormat(intlLocale(prefs.locale), { day: 'numeric', month: 'short' }).format(d);
  }
  return fixedDayMonth(d, prefs.dateFormat);
}
