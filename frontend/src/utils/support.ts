/** ADR-0064: the one support address. Components use these, never a copy of the string. */
export const SUPPORT_EMAIL = 'support@opsclear.app';

/** `mailto:` href for the support address. */
export function supportMailto(): string {
  return `mailto:${SUPPORT_EMAIL}`;
}
