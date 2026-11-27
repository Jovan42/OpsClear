import { useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Trans, useTranslation } from 'react-i18next';
import { CheckCircle2 } from 'lucide-react';
import Button from '../../components/Button';
import { useEmailSignUp } from './useSignups';
import {
  buildSignupPayload,
  readSignupLinkParams,
  signupOutcome,
  type SignupErrorKind,
  type SignupFormValues,
  type SignupOutcome,
} from './emailSignup';
import type { SignupList } from '../../types';

interface EmailSignupFormProps {
  /** The sign-up list (ADR-0066), so a later campaign page reuses this form. */
  readonly list: SignupList;
  /** Signs up this address instead of asking for one (a signed-in user's account email).
   *  The Promo code field is left out then. */
  readonly fixedEmail?: string;
  readonly submitLabel?: string;
}

const inputClass =
  'w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent';

/**
 * ADR-0066: email, required consent and an optional promo code (`?code=` prefills it), with the
 * link's UTM values and the page language sent along. Shows a thank-you after the 204, which is
 * the same for a new and an already listed address.
 */
export default function EmailSignupForm({ list, fixedEmail, submitLabel }: EmailSignupFormProps) {
  const { t, i18n } = useTranslation('earlyAccess');
  const { search } = useLocation();
  // Read once: the form keeps what the visit's link carried even if the URL changes later
  const [link] = useState(() => readSignupLinkParams(new URLSearchParams(search)));
  const { mutate: signUp, isPending } = useEmailSignUp();
  const [done, setDone] = useState(false);
  const [formError, setFormError] = useState<Exclude<SignupErrorKind, 'code'> | null>(null);

  const schema = useMemo(
    () =>
      z.object({
        email: z.string().trim().min(1, t('form.emailRequired')).email(t('form.emailInvalid')).max(254, t('form.emailInvalid')),
        consent: z.boolean().refine((v) => v, t('form.consentRequired')),
        code: z.string().trim().max(30, t('form.codeInvalid')),
        website: z.string(),
      }),
    [t],
  );

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<SignupFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: fixedEmail ?? '', consent: false, code: fixedEmail ? '' : link.code, website: '' },
  });

  function show(outcome: SignupOutcome) {
    if (outcome === 'thanks') setDone(true);
    else if (outcome === 'code') setError('code', { message: t('form.codeInvalid') });
    else setFormError(outcome);
  }

  function onSubmit(values: SignupFormValues) {
    setFormError(null);
    signUp(buildSignupPayload(list, values, link, i18n.language), {
      onSuccess: () => show(signupOutcome({ ok: true })),
      onError: (error) => show(signupOutcome({ ok: false, error })),
    });
  }

  if (done) {
    return (
      <div
        data-testid="signup-thanks"
        role="status"
        className="flex items-start gap-3 rounded-xl border border-green-200 dark:border-green-800 bg-green-50 dark:bg-green-900/20 p-4 text-sm text-green-800 dark:text-green-300"
      >
        <CheckCircle2 size={20} className="shrink-0" aria-hidden="true" />
        <p>{t('form.thanks')}</p>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4" data-testid="email-signup-form">
      {fixedEmail ? (
        <p className="text-sm font-medium text-gray-900 dark:text-gray-100 break-all" data-testid="signup-fixed-email">
          {fixedEmail}
        </p>
      ) : (
        <div>
          <label htmlFor="signup-email" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {t('form.emailLabel')}
          </label>
          <input
            id="signup-email"
            type="email"
            autoComplete="email"
            inputMode="email"
            placeholder={t('form.emailPlaceholder')}
            {...register('email')}
            className={inputClass}
          />
          {errors.email && <p className="mt-1 text-xs text-red-600">{errors.email.message}</p>}
        </div>
      )}

      {!fixedEmail && (
        <div>
          <label htmlFor="signup-code" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {t('form.codeLabel')}{' '}
            <span className="text-xs font-normal text-gray-400 dark:text-gray-500">{t('form.codeOptional')}</span>
          </label>
          <input
            id="signup-code"
            autoComplete="off"
            autoCapitalize="characters"
            {...register('code')}
            className={`${inputClass} uppercase`}
          />
          {errors.code && <p className="mt-1 text-xs text-red-600" data-testid="signup-code-error">{errors.code.message}</p>}
        </div>
      )}

      {/* Honeypot: off-screen and out of the tab order, so only bots fill it */}
      <div aria-hidden="true" className="absolute -left-[10000px] top-auto w-px h-px overflow-hidden">
        <label htmlFor="signup-website">{t('form.honeypotLabel')}</label>
        <input id="signup-website" type="text" tabIndex={-1} autoComplete="off" {...register('website')} />
      </div>

      <div>
        <label className="flex items-start gap-2 text-sm text-gray-700 dark:text-gray-300 cursor-pointer">
          <input
            type="checkbox"
            {...register('consent')}
            className="mt-0.5 h-4 w-4 shrink-0 rounded border-gray-300 dark:border-gray-600"
          />
          <span>{t('form.consentLabel')}</span>
        </label>
        {errors.consent && <p className="mt-1 text-xs text-red-600">{errors.consent.message}</p>}
        <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
          <Trans
            t={t}
            i18nKey="form.privacy"
            components={{
              privacy: (
                <Link
                  to="/privacy"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="underline underline-offset-2 hover:text-gray-700 dark:hover:text-gray-200"
                />
              ),
            }}
          />
        </p>
      </div>

      {formError && (
        <p role="alert" className="text-sm text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20 rounded-lg px-3 py-2">
          {t(`form.${formError}`)}
        </p>
      )}

      <Button type="submit" loading={isPending} style={{ width: '100%', justifyContent: 'center' }}>
        {submitLabel ?? t('form.submit')}
      </Button>
    </form>
  );
}
