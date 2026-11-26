import { useTranslation } from 'react-i18next';
import { SUPPORT_EMAIL, supportMailto } from '../utils/support';

interface SupportLinkProps {
  /** Classes for the line. Defaults to small grey centred text for a light/dark footer. */
  readonly className?: string;
  /** Classes for the address link itself. */
  readonly linkClassName?: string;
}

/** ADR-0064: "Questions? Write to support@opsclear.app" in the public page footers. */
export default function SupportLink({
  className = 'text-center text-sm text-gray-500 dark:text-gray-400',
  linkClassName = 'text-gray-700 dark:text-gray-200 hover:text-gray-900 dark:hover:text-white underline underline-offset-4',
}: Readonly<SupportLinkProps>) {
  const { t } = useTranslation('shared1');
  return (
    <p className={className}>
      {t('supportFooter')}{' '}
      <a href={supportMailto()} data-testid="support-link" className={`break-all ${linkClassName}`}>
        {SUPPORT_EMAIL}
      </a>
    </p>
  );
}
