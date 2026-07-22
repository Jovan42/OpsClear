import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { useTranslation } from 'react-i18next';
import Button from '../../components/Button';
import { useAcceptOrgInvite } from './useOrganisation';

export default function AcceptInvitePage() {
  const { t } = useTranslation('org');
  const { token } = useParams<{ token: string }>();
  const navigate = useNavigate();
  const { mutate: acceptInvite, isPending } = useAcceptOrgInvite();

  const [error, setError] = useState<string | null>(token ? null : t('acceptInviteInvalidLink'));
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
          setError(t('acceptInviteError'));
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
            {t('acceptInviteJoinedTitle')}
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            {t('acceptInviteRedirecting')}
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
            {t('acceptInviteTitle')}
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            {t('acceptInviteSubtitle')}
          </p>
        </div>

        {error ? (
          <div className="rounded-xl border border-red-200 dark:border-red-900/50 bg-red-50 dark:bg-red-900/10 px-4 py-3 space-y-3">
            <p className="text-sm text-red-700 dark:text-red-400">{error}</p>
            <Button variant="secondary" size="sm" onClick={() => navigate('/projects')}>
              {t('goToProjects')}
            </Button>
          </div>
        ) : (
          <div className="flex justify-center">
            <Button onClick={handleAccept} loading={isPending} disabled={!token}>
              {t('acceptInviteButton')}
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
