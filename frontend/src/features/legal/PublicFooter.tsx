import SupportLink from '../../components/SupportLink';
import LegalLinks from './LegalLinks';

/** JOB-420: the legal links and the support address at the bottom of the public pages without their own footer. */
export default function PublicFooter() {
  return (
    <footer className="shrink-0 border-t border-gray-200 dark:border-gray-800 px-6 py-6">
      <LegalLinks linkClassName="text-sm text-gray-500 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white hover:underline underline-offset-4" />
      <SupportLink className="mt-3 text-center text-sm text-gray-500 dark:text-gray-400" />
    </footer>
  );
}
