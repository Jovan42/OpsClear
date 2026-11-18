import { useTranslation } from 'react-i18next';
import AppRoleBadge from '../../components/RoleBadge';

type GuideRole = 'Owner' | 'Admin' | 'Member';

/** "Who can do this", with the app's own role badges. */
export function RoleBadge({ role }: Readonly<{ role: GuideRole | GuideRole[] }>) {
  const { t } = useTranslation('guide');
  const roles = Array.isArray(role) ? role : [role];
  return (
    <p className="not-prose my-4 flex flex-wrap items-center gap-2 text-sm text-gray-500 dark:text-gray-400" data-testid="guide-role-badge">
      <span>{t('whoCanDoThis')}:</span>
      {roles.map((r) => (
        <AppRoleBadge key={r} role={r.toUpperCase() as 'OWNER' | 'ADMIN' | 'MEMBER'} />
      ))}
    </p>
  );
}
