type Role = 'OWNER' | 'ADMIN' | 'MEMBER';

const styles: Record<Role, string> = {
  OWNER: 'bg-brand-light text-brand',
  ADMIN: 'bg-purple-100 text-purple-700',
  MEMBER: 'bg-gray-100 text-gray-600',
};

export default function RoleBadge({ role }: { role: Role }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${styles[role]}`}>
      {role.charAt(0) + role.slice(1).toLowerCase()}
    </span>
  );
}
