import { describe, expect, it } from 'vitest';
import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios';
import {
  buildSignupPayload,
  createOrgView,
  readSignupLinkParams,
  signupCsvFilename,
  signupCtaVariant,
  signupErrorKind,
  signupLocale,
  signupOutcome,
  type SignupFormValues,
} from './emailSignup';

function httpError(status: number, message?: string): AxiosError {
  const response = {
    status,
    data: message === undefined ? {} : { error: 'Bad Request', message },
    statusText: '',
    headers: {},
    config: { headers: new AxiosHeaders() },
  } as AxiosResponse;
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, response);
}

const values = (overrides: Partial<SignupFormValues> = {}): SignupFormValues => ({
  email: 'ana@example.com',
  consent: true,
  code: '',
  website: '',
  ...overrides,
});

describe('readSignupLinkParams', () => {
  it('prefills the code from ?code= and reads the UTM values', () => {
    const link = readSignupLinkParams(
      new URLSearchParams('?code=TRIAL&utm_source=linkedin&utm_medium=social&utm_campaign=launch'),
    );
    expect(link).toEqual({ code: 'TRIAL', utmSource: 'linkedin', utmMedium: 'social', utmCampaign: 'launch' });
  });

  it('leaves the code empty and the UTM values out when the link has none', () => {
    expect(readSignupLinkParams(new URLSearchParams(''))).toEqual({
      code: '',
      utmSource: undefined,
      utmMedium: undefined,
      utmCampaign: undefined,
    });
  });

  it('trims the values and cuts them to the backend limits, so a long link never gets a 400', () => {
    const link = readSignupLinkParams(
      new URLSearchParams({ code: ` ${'C'.repeat(40)} `, utm_source: 'x'.repeat(150), utm_medium: '  ' }),
    );
    expect(link.code).toBe('C'.repeat(30));
    expect(link.utmSource).toBe('x'.repeat(100));
    expect(link.utmMedium).toBeUndefined();
  });
});

describe('buildSignupPayload', () => {
  const link = { code: 'TRIAL', utmSource: 'linkedin', utmMedium: 'social', utmCampaign: 'launch' };

  it('sends the list, email, consent, the UTM values and the page language', () => {
    expect(buildSignupPayload('early-access', values({ code: 'trial' }), link, 'sr')).toEqual({
      list: 'early-access',
      email: 'ana@example.com',
      consent: true,
      locale: 'sr',
      utmSource: 'linkedin',
      utmMedium: 'social',
      utmCampaign: 'launch',
      code: 'trial',
      website: '',
    });
  });

  it('sends the code as edited in the field, not the one from the link', () => {
    expect(buildSignupPayload('early-access', values({ code: ' OTHER ' }), link, 'en').code).toBe('OTHER');
  });

  it('leaves out an empty code and missing UTM values', () => {
    const payload = buildSignupPayload('early-access', values(), { code: 'TRIAL' }, 'en');
    expect(payload).not.toHaveProperty('code');
    expect(payload).not.toHaveProperty('utmSource');
    expect(payload).not.toHaveProperty('utmMedium');
    expect(payload).not.toHaveProperty('utmCampaign');
  });

  it('passes the honeypot through as filled, so the backend can drop a bot sign-up', () => {
    expect(buildSignupPayload('early-access', values({ website: 'spam.example' }), link, 'en').website).toBe(
      'spam.example',
    );
    expect(buildSignupPayload('early-access', values(), link, 'en').website).toBe('');
  });

  it('trims the email', () => {
    expect(buildSignupPayload('early-access', values({ email: '  ana@example.com ' }), link, 'en').email).toBe(
      'ana@example.com',
    );
  });
});

describe('signupLocale', () => {
  it.each([
    ['sr', 'sr'],
    ['sr-Latn', 'sr'],
    ['en', 'en'],
    ['de', 'en'],
    [undefined, 'en'],
  ])('maps %s to %s', (language, locale) => {
    expect(signupLocale(language)).toBe(locale);
  });
});

describe('signupErrorKind', () => {
  it('puts an unknown code on the code field', () => {
    expect(signupErrorKind(httpError(400, "This code isn't valid"))).toBe('code');
  });

  it('puts a too-long code from bean validation on the code field', () => {
    expect(signupErrorKind(httpError(400, 'code: Code must not exceed 30 characters'))).toBe('code');
    expect(signupErrorKind(httpError(400, 'email: bad; code: Code must not exceed 30 characters'))).toBe('code');
  });

  it('shows other 400s as an invalid sign-up', () => {
    expect(signupErrorKind(httpError(400, 'email: Email must be a valid email address'))).toBe('invalid');
    expect(signupErrorKind(httpError(400))).toBe('invalid');
  });

  it('shows the rate limit on 429', () => {
    expect(signupErrorKind(httpError(429))).toBe('rateLimited');
  });

  it('shows a generic error for a server or network failure', () => {
    expect(signupErrorKind(httpError(500))).toBe('generic');
    expect(signupErrorKind(new AxiosError('Network Error', 'ERR_NETWORK'))).toBe('generic');
    expect(signupErrorKind(new Error('boom'))).toBe('generic');
  });
});

describe('signupOutcome', () => {
  it('shows the thank-you after the 204', () => {
    expect(signupOutcome({ ok: true })).toBe('thanks');
  });

  it('shows the error kind after a failure', () => {
    expect(signupOutcome({ ok: false, error: httpError(400, "This code isn't valid") })).toBe('code');
    expect(signupOutcome({ ok: false, error: httpError(429) })).toBe('rateLimited');
  });
});

describe('signupCtaVariant', () => {
  it('opens Keycloak registration while sign-up is open', () => {
    expect(signupCtaVariant({ open: true })).toBe('register');
  });

  it('links to /early-access while sign-up is closed', () => {
    expect(signupCtaVariant({ open: false })).toBe('notify');
  });

  it('links to /early-access while the status loads or failed, so closed never flashes open', () => {
    expect(signupCtaVariant(undefined)).toBe('notify');
  });
});

describe('createOrgView', () => {
  const base = { status: { open: true }, statusLoading: false, isSuperUser: undefined, createForbidden: false };

  it('shows the form while sign-up is open', () => {
    expect(createOrgView(base)).toBe('form');
  });

  it('waits for the status before choosing', () => {
    expect(createOrgView({ ...base, status: undefined, statusLoading: true })).toBe('loading');
  });

  it('shows the form when the status failed to load (the backend still answers 403 if closed)', () => {
    expect(createOrgView({ ...base, status: undefined })).toBe('form');
  });

  it('when closed, waits for the super-user check, then shows the card to a normal user', () => {
    expect(createOrgView({ ...base, status: { open: false } })).toBe('loading');
    expect(createOrgView({ ...base, status: { open: false }, isSuperUser: false })).toBe('closed');
  });

  it('when closed, shows the form to a super user', () => {
    expect(createOrgView({ ...base, status: { open: false }, isSuperUser: true })).toBe('form');
  });

  it('shows the card after a 403 from creating the organisation', () => {
    expect(createOrgView({ ...base, createForbidden: true })).toBe('closed');
  });
});

describe('signupCsvFilename', () => {
  it("uses the server's filename when the header is readable", () => {
    expect(signupCsvFilename('attachment; filename="early-access-2026-11-27.csv"', 'early-access')).toBe(
      'early-access-2026-11-27.csv',
    );
  });

  it('falls back to <list>-YYYY-MM-DD.csv in UTC', () => {
    expect(signupCsvFilename(null, 'early-access', new Date('2026-11-27T23:30:00Z'))).toBe(
      'early-access-2026-11-27.csv',
    );
  });
});
