type Role = 'OWNER' | 'ADMIN' | 'MEMBER';

const styles: Record<Role, string> = {
  OWNER: 'bg-brand-light text-brand dark:bg-green-900/40 dark:text-green-300',
  ADMIN: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
  MEMBER: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300',
};

export default function RoleBadge({ role }: { role: Role }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${styles[role]}`}>
      {role.charAt(0) + role.slice(1).toLowerCase()}
    </span>
  );
}
