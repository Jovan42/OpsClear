import { useTranslation } from 'react-i18next';

type Role = 'OWNER' | 'ADMIN' | 'MEMBER';

const styles: Record<Role, string> = {
  OWNER: 'bg-brand-light text-brand dark:bg-green-900/40 dark:text-green-300',
  ADMIN: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
  MEMBER: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300',
};

export default function RoleBadge({ role }: { role: Role }) {
  const { t } = useTranslation('shared2');
  const labels: Record<Role, string> = {
    OWNER: t('role.owner'),
    ADMIN: t('role.admin'),
    MEMBER: t('role.member'),
  };
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${styles[role]}`}>
      {labels[role]}
    </span>
  );
}
