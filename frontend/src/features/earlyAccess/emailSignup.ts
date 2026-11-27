import { isAxiosError } from 'axios';
import type { EmailSignupRequest, SignupList, SignupStatusResponse } from '../../types';

/** Backend limits (`EmailSignupRequest`): longer values get a 400, so the link's values are cut to fit. */
const UTM_MAX = 100;
const CODE_MAX = 30;

/** The backend's message for an unknown code (`ErrorMessages.EmailSignup.UNKNOWN_CODE`). */
const UNKNOWN_CODE_MESSAGE = "This code isn't valid";

/** What the visit's link carries: a promo code and the campaign source (ADR-0066). */
export interface SignupLinkParams {
  code: string;
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
}

function param(search: URLSearchParams, name: string, max: number): string | undefined {
  const value = search.get(name)?.trim();
  return value ? value.slice(0, max) : undefined;
}

/** `?code=` prefills the Promo code field (editable); the UTM values are sent as they are. */
export function readSignupLinkParams(search: URLSearchParams): SignupLinkParams {
  return {
    code: param(search, 'code', CODE_MAX) ?? '',
    utmSource: param(search, 'utm_source', UTM_MAX),
    utmMedium: param(search, 'utm_medium', UTM_MAX),
    utmCampaign: param(search, 'utm_campaign', UTM_MAX),
  };
}

/** The page language as the backend's `locale` (`en|sr`), so the launch mail uses it. */
export function signupLocale(language: string | undefined): 'en' | 'sr' {
  return language?.toLowerCase().startsWith('sr') ? 'sr' : 'en';
}

export interface SignupFormValues {
  email: string;
  consent: boolean;
  code: string;
  /** Honeypot: off-screen, so only bots fill it. Sent as is; the backend stores nothing when it's set. */
  website: string;
}

export function buildSignupPayload(
  list: SignupList,
  values: SignupFormValues,
  link: SignupLinkParams,
  language: string | undefined,
): EmailSignupRequest {
  const code = values.code.trim();
  return {
    list,
    email: values.email.trim(),
    consent: values.consent,
    locale: signupLocale(language),
    ...(link.utmSource && { utmSource: link.utmSource }),
    ...(link.utmMedium && { utmMedium: link.utmMedium }),
    ...(link.utmCampaign && { utmCampaign: link.utmCampaign }),
    ...(code && { code }),
    website: values.website,
  };
}

/**
 * How the form shows a failed sign-up:
 * - `code`: the code was rejected, shown on the Promo code field
 * - `rateLimited`: 429 from the nginx zone
 * - `invalid`: any other 400
 * - `generic`: network error or server failure
 */
export type SignupErrorKind = 'code' | 'rateLimited' | 'invalid' | 'generic';

export function signupErrorKind(error: unknown): SignupErrorKind {
  if (!isAxiosError(error) || !error.response) return 'generic';
  const { status, data } = error.response;
  if (status === 429) return 'rateLimited';
  if (status !== 400) return 'generic';
  const message = (data as { message?: string } | undefined)?.message ?? '';
  // Unknown code (service) or a too-long one (bean validation: "code: …")
  if (message === UNKNOWN_CODE_MESSAGE || /(^|; )code: /.test(message)) return 'code';
  return 'invalid';
}

/**
 * What the form shows after a submit: the thank-you after the 204 (the same for a new and an
 * already listed address, ADR-0066), or the error's kind.
 */
export type SignupOutcome = 'thanks' | SignupErrorKind;

export function signupOutcome(result: { ok: true } | { ok: false; error: unknown }): SignupOutcome {
  return result.ok ? 'thanks' : signupErrorKind(result.error);
}

/**
 * What the Sign up buttons do: `register` opens Keycloak registration, `notify` links to
 * /early-access ("Get notified"). Only a confirmed open status registers, so while the status
 * loads (or fails) the closed state never flashes open.
 */
export type SignupCtaVariant = 'register' | 'notify';

export function signupCtaVariant(status: SignupStatusResponse | undefined): SignupCtaVariant {
  return status?.open === true ? 'register' : 'notify';
}

/**
 * What the create-organisation page shows (ADR-0066): the "not open yet" card when sign-up is
 * closed and the user isn't a super user, or once `POST /api/organisations` answered 403. A status
 * that failed to load shows the form; the backend still refuses a closed sign-up with the 403.
 */
export type CreateOrgView = 'loading' | 'closed' | 'form';

export function createOrgView(input: {
  status: SignupStatusResponse | undefined;
  statusLoading: boolean;
  isSuperUser: boolean | undefined;
  createForbidden: boolean;
}): CreateOrgView {
  if (input.createForbidden) return 'closed';
  if (input.statusLoading) return 'loading';
  if (input.status?.open !== false) return 'form';
  if (input.isSuperUser === undefined) return 'loading';
  return input.isSuperUser ? 'form' : 'closed';
}

/** The download name: the server's when readable, else the same `<list>-YYYY-MM-DD.csv` pattern (UTC). */
export function signupCsvFilename(contentDisposition: string | null, list: SignupList, now = new Date()): string {
  const match = contentDisposition?.match(/filename="?([^";]+)"?/i);
  if (match) return match[1];
  return `${list}-${now.toISOString().slice(0, 10)}.csv`;
}
