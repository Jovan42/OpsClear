import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { useTranslation } from 'react-i18next';
import Button from '../../components/Button';
import Modal from '../../components/Modal';
import Skeleton from '../../components/Skeleton';
import { usePageTitle } from '../../hooks/usePageTitle';
import { useCurrentOrg } from './OrgContext';
import { useOrgInvites, useSendOrgInvite, useRevokeOrgInvite } from './useOrganisation';
import type { OrgInviteResponse } from '../../types';
import type { TFunction } from 'i18next';

function formatExpiry(expiresAt: string, t: TFunction): string {
  const diff = new Date(expiresAt).getTime() - Date.now();
  const days = Math.ceil(diff / (1000 * 60 * 60 * 24));
  if (days <= 0) return t('expired');
  if (days === 1) return t('expiresTomorrow');
  return t('expiresInDays', { count: days });
}

export default function OrgInvitesPage() {
  const { t } = useTranslation(['org', 'common']);
  const navigate = useNavigate();
  const { org } = useCurrentOrg();
  usePageTitle(t('org:invitesPageTitle'));

  const { data: invites = [], isLoading } = useOrgInvites(org?.id ?? null);
  const { mutate: sendInvite, isPending: sending } = useSendOrgInvite(org?.id ?? '');
  const { mutate: revokeInvite, isPending: revoking } = useRevokeOrgInvite(org?.id ?? '');

  const [email, setEmail] = useState('');
  const [emailError, setEmailError] = useState<string | null>(null);
  const [confirmTarget, setConfirmTarget] = useState<OrgInviteResponse | null>(null);

  function handleSend() {
    setEmailError(null);
    if (!email.trim()) {
      setEmailError(t('org:emailRequired'));
      return;
    }
    sendInvite(email.trim(), {
      onSuccess: () => setEmail(''),
      onError: (err) => {
        if (isAxiosError(err) && err.response?.data?.message) {
          setEmailError(err.response.data.message as string);
        } else {
          setEmailError(t('org:failedToSendInvite'));
        }
      },
    });
  }

  function handleRevoke() {
    if (!confirmTarget) return;
    revokeInvite(confirmTarget.id, {
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

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8 space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">
          {t('org:invitesPageTitle')}
        </h1>
        <button
          onClick={() => navigate('/org/members')}
          className="text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 transition-colors"
        >
          {t('org:backToMembers')}
        </button>
      </div>

      {/* Send invite form */}
      <div className="space-y-2">
        <div className="flex gap-2">
          <input
            type="email"
            value={email}
            onChange={(e) => {
              setEmail(e.target.value);
              setEmailError(null);
            }}
            onKeyDown={(e) => e.key === 'Enter' && handleSend()}
            placeholder={t('org:emailPlaceholder')}
            className="flex-1 rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent"
          />
          <Button onClick={handleSend} loading={sending} disabled={!email.trim()}>
            {t('org:sendInvite')}
          </Button>
        </div>
        {emailError && <p className="text-xs text-red-600 dark:text-red-400">{emailError}</p>}
      </div>

      {/* Invite list */}
      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="flex items-center justify-between py-2">
              <div className="space-y-1">
                <Skeleton className="h-4 w-40" />
                <Skeleton className="h-3 w-28" />
              </div>
              <Skeleton className="h-7 w-16 rounded-lg" />
            </div>
          ))}
        </div>
      ) : invites.length === 0 ? (
        <p className="text-sm text-gray-500 dark:text-gray-400">{t('org:noPendingInvites')}</p>
      ) : (
        <div className="border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
              <tr>
                <th className="text-left px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                  {t('org:colEmail')}
                </th>
                <th className="text-left px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                  {t('org:colInvitedBy')}
                </th>
                <th className="text-left px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                  {t('org:colExpiry')}
                </th>
                <th className="px-4 py-2.5" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
              {invites.map((invite) => (
                <tr key={invite.id} className="bg-white dark:bg-gray-800">
                  <td className="px-4 py-3 font-medium text-gray-900 dark:text-gray-100">
                    {invite.email}
                  </td>
                  <td className="px-4 py-3 text-gray-500 dark:text-gray-400">
                    {invite.invitedByName ?? '—'}
                  </td>
                  <td className="px-4 py-3 text-gray-500 dark:text-gray-400 text-xs">
                    {formatExpiry(invite.expiresAt, t)}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => setConfirmTarget(invite)}
                      className="text-xs text-red-500 hover:text-red-700 transition-colors cursor-pointer"
                    >
                      {t('org:revoke')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal
        open={!!confirmTarget}
        onClose={() => setConfirmTarget(null)}
        title={t('org:revokeInviteTitle')}
      >
        <div className="space-y-4">
          <p className="text-sm text-gray-600 dark:text-gray-300">
            {t('org:revokeInviteMessagePrefix')}{' '}
            <span className="font-semibold">{confirmTarget?.email}</span>
            {t('org:revokeInviteMessageSuffix')}
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setConfirmTarget(null)}>
              {t('common:cancel')}
            </Button>
            <Button variant="danger" loading={revoking} onClick={handleRevoke}>
              {t('org:revoke')}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
