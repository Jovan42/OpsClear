import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { usersApi } from '../../api/users';
import Button from '../../components/Button';
import { useAddOrgMember, useOrgMembers } from './useOrganisation';
import type { OrgRole, UserSearchResponse } from '../../types';

const ASSIGNABLE_ROLES: OrgRole[] = ['MEMBER', 'ADMIN'];

interface AddOrgMemberFormProps {
  orgId: string;
}

export default function AddOrgMemberForm({ orgId }: AddOrgMemberFormProps) {
  const { t } = useTranslation(['org', 'common']);
  const [email, setEmail] = useState('');
  const [debouncedEmail, setDebouncedEmail] = useState('');
  const [selected, setSelected] = useState<UserSearchResponse | null>(null);
  const [role, setRole] = useState<OrgRole>('MEMBER');
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const { mutate: addMember, isPending } = useAddOrgMember(orgId);
  const { data: members = [] } = useOrgMembers(orgId);
  const memberIds = new Set(members.map((m) => m.userId));

  useEffect(() => {
    const t = setTimeout(() => setDebouncedEmail(email), 300);
    return () => clearTimeout(t);
  }, [email]);

  const { data: results = [] } = useQuery({
    queryKey: ['users', 'search', debouncedEmail],
    queryFn: () => usersApi.search(debouncedEmail),
    enabled: debouncedEmail.length >= 2 && !selected,
  });

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  function handleSelect(user: UserSearchResponse) {
    setSelected(user);
    setEmail(user.email);
    setOpen(false);
  }

  function handleEmailChange(value: string) {
    setEmail(value);
    setSelected(null);
    setOpen(true);
  }

  function handleSubmit() {
    if (!selected) return;
    addMember(
      { userId: selected.id, role },
      {
        onSuccess: () => {
          setEmail('');
          setSelected(null);
          setRole('MEMBER');
        },
      },
    );
  }

  return (
    <div className="flex gap-2 items-start flex-wrap sm:flex-nowrap">
      <div className="relative flex-1 min-w-48" ref={containerRef}>
        <input
          className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent"
          placeholder={t('org:searchByEmail')}
          value={email}
          onChange={(e) => handleEmailChange(e.target.value)}
          onFocus={() => setOpen(true)}
          autoComplete="off"
        />
        {open && results.length > 0 && !selected && (
          <ul className="absolute z-10 mt-1 w-full bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg shadow-lg overflow-hidden">
            {results.map((user) => {
              const alreadyMember = memberIds.has(user.id);
              return (
                <li key={user.id}>
                  <button
                    type="button"
                    onClick={() => !alreadyMember && handleSelect(user)}
                    disabled={alreadyMember}
                    className={`w-full text-left px-3 py-2 ${alreadyMember ? 'cursor-not-allowed opacity-50' : 'hover:bg-gray-50 dark:hover:bg-gray-600 cursor-pointer'}`}
                  >
                    <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{user.name}</p>
                    <p className="text-xs text-gray-500 dark:text-gray-400">
                      {user.email}
                      {alreadyMember && <span className="ml-2 text-gray-400">{t('org:alreadyMember')}</span>}
                    </p>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
      <select
        value={role}
        onChange={(e) => setRole(e.target.value as OrgRole)}
        className="rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-200 focus:outline-none focus:ring-2 focus:border-transparent"
      >
        {ASSIGNABLE_ROLES.map((r) => (
          <option key={r} value={r}>
            {t(`org:roles.${r}`)}
          </option>
        ))}
      </select>
      <Button onClick={handleSubmit} disabled={!selected} loading={isPending}>
        {t('common:add')}
      </Button>
    </div>
  );
}
