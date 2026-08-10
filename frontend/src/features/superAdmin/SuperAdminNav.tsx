import { NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
    isActive
      ? 'bg-gray-900 text-white dark:bg-white dark:text-gray-900'
      : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800'
  }`;

export default function SuperAdminNav() {
  const { t } = useTranslation('superAdmin');
  return (
    <nav className="flex gap-1 w-fit">
      <NavLink to="/admin/pricing" className={linkClass}>{t('nav.pricing')}</NavLink>
      <NavLink to="/admin/feedback" className={linkClass}>{t('nav.feedback')}</NavLink>
    </nav>
  );
}
