import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

export const LEGAL_PAGES = [
  { key: 'terms', path: '/terms' },
  { key: 'privacy', path: '/privacy' },
  { key: 'refunds', path: '/refunds' },
] as const;

interface LegalLinksProps {
  /** Classes for each link, so the list fits the footer it sits in. */
  readonly linkClassName: string;
  readonly className?: string;
}

/** JOB-420: links to the Terms, Privacy Policy and Refund policy pages. */
export default function LegalLinks({ linkClassName, className = '' }: LegalLinksProps) {
  const { t } = useTranslation('legal');
  return (
    <nav aria-label={t('common.footerLabel')} data-testid="legal-links" className={className}>
      <ul className="flex flex-wrap justify-center gap-x-5 gap-y-2">
        {LEGAL_PAGES.map(({ key, path }) => (
          <li key={key}>
            <Link to={path} className={linkClassName}>
              {t(`links.${key}`)}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
