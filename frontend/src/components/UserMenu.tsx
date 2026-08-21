import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import keycloak from '../auth/keycloak';
import { useAuth } from '../auth/AuthContext';
import { useCurrentOrg } from '../features/org/OrgContext';
import { useOrgMembers } from '../features/org/useOrganisation';

interface UserMenuProps {
  readonly name: string;
}

export default function UserMenu({ name }: Readonly<UserMenuProps>) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const { org } = useCurrentOrg();
  const { userId } = useAuth();
  const { data: members = [] } = useOrgMembers(org?.id ?? null);
  const { t } = useTranslation('shared1');

  const callerRole = members.find((m) => m.userId === userId)?.role ?? null;
  const isOwnerOrAdmin = callerRole === 'OWNER' || callerRole === 'ADMIN';

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 text-sm text-white/80 hover:text-white transition-colors cursor-pointer"
        aria-expanded={open}
        aria-haspopup="true"
      >
        <span className="hidden sm:inline">{name}</span>
        <svg
          width="16"
          height="16"
          viewBox="0 0 16 16"
          fill="currentColor"
          className={`transition-transform ${open ? 'rotate-180' : ''}`}
        >
          <path d="M4 6l4 4 4-4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
        </svg>
      </button>

      {open && (
        <div className="absolute right-0 mt-2 w-44 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-lg py-1 z-50">
          {org && (
            <button
              onClick={() => { setOpen(false); navigate('/org/settings'); }}
              className="w-full text-left px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors cursor-pointer"
            >
              {t('organisation')}
            </button>
          )}
          <button
            onClick={() => { setOpen(false); navigate('/feedback'); }}
            className="w-full text-left px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors cursor-pointer"
          >
            {t('feedback')}
          </button>
          {isOwnerOrAdmin && (
            <button
              onClick={() => { setOpen(false); navigate('/overview'); }}
              className="w-full text-left px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors cursor-pointer"
            >
              {t('overview')}
            </button>
          )}
          <div className="border-t border-gray-100 dark:border-gray-700 my-1" />
          <button
            onClick={() => { setOpen(false); navigate('/settings'); }}
            className="w-full text-left px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors cursor-pointer"
          >
            {t('accountSettings')}
          </button>
          <button
            onClick={() => keycloak.logout()}
            className="w-full text-left px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors cursor-pointer"
          >
            {t('signOut')}
          </button>
        </div>
      )}
    </div>
  );
}
