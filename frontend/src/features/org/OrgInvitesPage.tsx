import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { isAxiosError } from 'axios';
import Button from '../../components/Button';
import Modal from '../../components/Modal';
import Skeleton from '../../components/Skeleton';
import { usePageTitle } from '../../hooks/usePageTitle';
import { useCurrentOrg } from './OrgContext';
import { useOrgInvites, useSendOrgInvite, useRevokeOrgInvite } from './useOrganisation';
import type { OrgInviteResponse } from '../../types';

function formatExpiry(expiresAt: string): string {
  const diff = new Date(expiresAt).getTime() - Date.now();
  const days = Math.ceil(diff / (1000 * 60 * 60 * 24));
  if (days <= 0) return 'Expired';
  if (days === 1) return 'Expires tomorrow';
  return `Expires in ${days} days`;
}

export default function OrgInvitesPage() {
  const navigate = useNavigate();
  const { org } = useCurrentOrg();
  usePageTitle('Pending invites');

  const { data: invites = [], isLoading } = useOrgInvites(org?.id ?? null);
  const { mutate: sendInvite, isPending: sending } = useSendOrgInvite(org?.id ?? '');
  const { mutate: revokeInvite, isPending: revoking } = useRevokeOrgInvite(org?.id ?? '');

  const [email, setEmail] = useState('');
  const [emailError, setEmailError] = useState<string | null>(null);
  const [confirmTarget, setConfirmTarget] = useState<OrgInviteResponse | null>(null);

  function handleSend() {
    setEmailError(null);
    if (!email.trim()) {
      setEmailError('Email is required');
      return;
    }
    sendInvite(email.trim(), {
      onSuccess: () => setEmail(''),
      onError: (err) => {
        if (isAxiosError(err) && err.response?.data?.message) {
          setEmailError(err.response.data.message as string);
        } else {
          setEmailError('Failed to send invite');
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
          No organisation found.{' '}
          <button
            onClick={() => navigate('/org/new')}
            className="text-blue-600 dark:text-blue-400 hover:underline"
          >
            Create one
          </button>
        </p>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8 space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">
          Pending invites
        </h1>
        <button
          onClick={() => navigate('/org/members')}
          className="text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 transition-colors"
        >
          ← Members
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
            placeholder="name@company.com"
            className="flex-1 rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent"
          />
          <Button onClick={handleSend} loading={sending} disabled={!email.trim()}>
            Send invite
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
        <p className="text-sm text-gray-500 dark:text-gray-400">No pending invites.</p>
      ) : (
        <div className="border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
              <tr>
                <th className="text-left px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                  Email
                </th>
                <th className="text-left px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                  Invited by
                </th>
                <th className="text-left px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                  Expiry
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
                    {formatExpiry(invite.expiresAt)}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => setConfirmTarget(invite)}
                      className="text-xs text-red-500 hover:text-red-700 transition-colors cursor-pointer"
                    >
                      Revoke
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
        title="Revoke invite?"
      >
        <div className="space-y-4">
          <p className="text-sm text-gray-600 dark:text-gray-300">
            Revoke the invite for{' '}
            <span className="font-semibold">{confirmTarget?.email}</span>?
            They will no longer be able to join using this link.
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setConfirmTarget(null)}>
              Cancel
            </Button>
            <Button variant="danger" loading={revoking} onClick={handleRevoke}>
              Revoke
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
