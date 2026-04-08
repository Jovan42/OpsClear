import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { isAxiosError } from 'axios';
import Button from '../../components/Button';
import { useAcceptOrgInvite } from './useOrganisation';

export default function AcceptInvitePage() {
  const { token } = useParams<{ token: string }>();
  const navigate = useNavigate();
  const { mutate: acceptInvite, isPending } = useAcceptOrgInvite();

  const [error, setError] = useState<string | null>(token ? null : 'Invalid invite link.');
  const [accepted, setAccepted] = useState(false);

  function handleAccept() {
    if (!token) return;
    setError(null);
    acceptInvite(token, {
      onSuccess: () => {
        setAccepted(true);
        setTimeout(() => navigate('/projects'), 2000);
      },
      onError: (err) => {
        if (isAxiosError(err) && err.response?.data?.message) {
          setError(err.response.data.message as string);
        } else {
          setError('Something went wrong. The invite may have expired or already been used.');
        }
      },
    });
  }

  if (accepted) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
        <div className="max-w-md w-full text-center space-y-3">
          <p className="text-2xl">✓</p>
          <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">
            You've joined the organisation
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            Redirecting you to your projects…
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
      <div className="max-w-md w-full space-y-6">
        <div className="text-center space-y-2">
          <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">
            You've been invited
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            Accept the invite to join the organisation and get access to its projects.
          </p>
        </div>

        {error ? (
          <div className="rounded-xl border border-red-200 dark:border-red-900/50 bg-red-50 dark:bg-red-900/10 px-4 py-3 space-y-3">
            <p className="text-sm text-red-700 dark:text-red-400">{error}</p>
            <Button variant="secondary" size="sm" onClick={() => navigate('/projects')}>
              Go to projects
            </Button>
          </div>
        ) : (
          <div className="flex justify-center">
            <Button onClick={handleAccept} loading={isPending} disabled={!token}>
              Accept invite
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
