import i18n from '../i18n';

/**
 * Formats the span between two instants as a single coarse unit ("3 days", "5 hours",
 * "12 minutes"), in the current language with its plural forms (common:duration.*). Shared by
 * the job status history timeline (ADR-0028) and the Stats page (ADR-0050), so durations read
 * the same everywhere.
 */
export function formatDuration(fromMs: number, toMs: number): string {
  const diffMs = toMs - fromMs;
  const minutes = Math.floor(diffMs / 60_000);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);

  if (days >= 1) return i18n.t('common:duration.day', { count: days });
  if (hours >= 1) return i18n.t('common:duration.hour', { count: hours });
  return i18n.t('common:duration.minute', { count: minutes });
}

/** Same format as {@link formatDuration}, for a length given in seconds. */
export function formatSeconds(seconds: number): string {
  return formatDuration(0, seconds * 1000);
}
