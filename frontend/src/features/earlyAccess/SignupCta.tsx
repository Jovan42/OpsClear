import type { CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import keycloak from '../../auth/keycloak';
import { useSignupStatus } from './useSignups';
import { signupCtaVariant } from './emailSignup';

interface SignupCtaProps {
  /** The label while sign-up is open (the page's own "Get started" or "Start now"). */
  readonly registerLabel: string;
  readonly className: string;
  readonly style?: CSSProperties;
}

/**
 * ADR-0066: a public page's Sign up button. Open: Keycloak registration as before. Closed, or
 * while the status loads: a "Get notified" link to /early-access.
 */
export default function SignupCta({ registerLabel, className, style }: SignupCtaProps) {
  const { t } = useTranslation('earlyAccess');
  const { data: status } = useSignupStatus();

  if (signupCtaVariant(status) === 'register') {
    return (
      <button type="button" onClick={() => void keycloak.register()} className={className} style={style}>
        {registerLabel}
      </button>
    );
  }
  return (
    <Link to="/early-access" className={`inline-block ${className}`} style={style} data-testid="get-notified-link">
      {t('cta.getNotified')}
    </Link>
  );
}
