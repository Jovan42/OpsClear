import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../auth/AuthContext';
import Button from '../../components/Button';
import Modal from '../../components/Modal';
import RoleBadge from '../../components/RoleBadge';
import Skeleton from '../../components/Skeleton';
import { usePageTitle } from '../../hooks/usePageTitle';
import { useCurrentOrg } from './OrgContext';
import AddOrgMemberForm from './AddOrgMemberForm';
import {
  useOrgMembers,
  useUpdateOrgMemberRole,
  useRemoveOrgMember,
} from './useOrganisation';
import type { OrgMemberResponse, OrgRole } from '../../types';

const ASSIGNABLE_ROLES: OrgRole[] = ['MEMBER', 'ADMIN'];

export default function OrgMembersPage() {
  const { t } = useTranslation(['org', 'common']);
  const navigate = useNavigate();
  const { userId } = useAuth();
  const { org } = useCurrentOrg();
  usePageTitle(t('org:membersPageTitle'));

  const { data: members = [], isLoading } = useOrgMembers(org?.id ?? null);
  const { mutate: updateRole } = useUpdateOrgMemberRole(org?.id ?? '');
  const { mutate: removeMember, isPending: removing } = useRemoveOrgMember(org?.id ?? '');

  const [confirmTarget, setConfirmTarget] = useState<OrgMemberResponse | null>(null);

  const callerRole = members.find((m) => m.userId === userId)?.role ?? null;
  const isOwner = callerRole === 'OWNER';
  const isOwnerOrAdmin = callerRole === 'OWNER' || callerRole === 'ADMIN';

  function handleRemoveConfirm() {
    if (!confirmTarget) return;
    removeMember(confirmTarget.userId, {
      onSuccess: () => setConfirmTarget(null),
    });
  }

  if (!org) {
    return (
      <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8">
        <p className="text-sm text-gray-500 dark:text-gray-400">
          {t('org:noOrgFound')}{' '}
          <button
            onClick={() => navigate('/org/new')}
            className="text-blue-600 dark:text-blue-400 hover:underline"
          >
            {t('org:createOne')}
          </button>
        </p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8 space-y-4">
        <Skeleton className="h-7 w-48" />
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="flex items-center justify-between py-2">
            <div className="space-y-1">
              <Skeleton className="h-4 w-32" />
              <Skeleton className="h-3 w-48" />
            </div>
            <Skeleton className="h-5 w-16 rounded-full" />
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8 space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">
          {t('org:membersHeading')}
        </h1>
        <button
          onClick={() => navigate('/org/settings')}
          className="text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 transition-colors"
        >
          {t('org:backToOrgSettings')}
        </button>
      </div>

      {isOwnerOrAdmin && (
        <AddOrgMemberForm orgId={org.id} />
      )}

      <div className="border border-gray-200 dark:border-gray-700 rounded-xl overflow-x-auto">
        <table className="w-full text-sm min-w-[28rem]">
          <thead className="bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
            <tr>
              <th className="text-left px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                {t('org:colMember')}
              </th>
              <th className="text-left px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                {t('org:colRole')}
              </th>
              {isOwnerOrAdmin && <th className="px-4 py-2.5" />}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
            {members.map((member) => {
              const isTargetOwner = member.role === 'OWNER';
              const canChangeRole = isOwner && !isTargetOwner;
              const canRemove = isOwnerOrAdmin && !isTargetOwner;

              return (
                <tr key={member.userId} className="bg-white dark:bg-gray-800">
                  <td className="px-4 py-3">
                    <p className="font-medium text-gray-900 dark:text-gray-100">
                      {member.userName}
                    </p>
                    <p className="text-xs text-gray-500 dark:text-gray-400">
                      {member.userEmail}
                    </p>
                  </td>
                  <td className="px-4 py-3">
                    {canChangeRole ? (
                      <select
                        value={member.role}
                        onChange={(e) =>
                          updateRole({ userId: member.userId, role: e.target.value as OrgRole })
                        }
                        className="rounded-lg border border-gray-200 dark:border-gray-600 px-2 py-1 text-xs bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-200 focus:outline-none focus:ring-2 focus:border-transparent"
                      >
                        {ASSIGNABLE_ROLES.map((r) => (
                          <option key={r} value={r}>
                            {t(`org:roles.${r}`)}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <RoleBadge role={member.role} />
                    )}
                  </td>
                  {isOwnerOrAdmin && (
                    <td className="px-4 py-3 text-right">
                      {canRemove && (
                        <button
                          onClick={() => setConfirmTarget(member)}
                          className="text-xs text-red-500 hover:text-red-700 transition-colors cursor-pointer"
                        >
                          {t('common:remove')}
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <Modal
        open={!!confirmTarget}
        onClose={() => setConfirmTarget(null)}
        title={t('org:removeMemberTitle')}
      >
        <div className="space-y-4">
          <p className="text-sm text-gray-600 dark:text-gray-300">
            {t('org:removeMemberMessagePrefix')}{' '}
            <span className="font-semibold">{confirmTarget?.userName}</span>{' '}
            {t('org:removeMemberMessageMiddle')}{' '}
            <span className="font-semibold">{org.name}</span>
            {t('org:removeMemberMessageSuffix')}
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setConfirmTarget(null)}>
              {t('common:cancel')}
            </Button>
            <Button variant="danger" loading={removing} onClick={handleRemoveConfirm}>
              {t('common:remove')}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
