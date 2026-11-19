import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { Trans, useTranslation } from 'react-i18next';
import Button from '../../components/Button';
import PageError from '../../components/PageError';
import Skeleton from '../../components/Skeleton';
import keycloak from '../../auth/keycloak';
import { useAuth } from '../../auth/AuthContext';
import { usePageTitle } from '../../hooks/usePageTitle';
import OnboardingHeader from './OnboardingHeader';
import { useAcceptOrgInvite, usePublicInvite } from './useOrganisation';
import { clearPendingInvite, inviteView, savePendingInvite } from './inviteLink';
import { JOINED_ORGANISATION_STATE } from './joinedOrganisationToast';
import type { PublicInviteResponse } from '../../types';

// Tokens this page has already sent an accept for, in this page load. A module-level set, not
// a ref: StrictMode's second effect run and a remount after the invite reloads must not send
// a second accept. A page refresh starts over, but by then the invite reads as accepted.
const acceptsSent = new Set<string>();

const strong = <span className="font-semibold text-gray-900 dark:text-gray-100" />;

function inviteUrl(token: string) {
  return `${window.location.origin}/invite/${token}`;
}

function Card({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <div className="bg-white dark:bg-gray-800 rounded-2xl border border-gray-200 dark:border-gray-700 p-6 shadow-sm space-y-5 text-center">
      {children}
    </div>
  );
}

function Message({ title, children }: Readonly<{ title: string; children?: ReactNode }>) {
  return (
    <div className="space-y-2">
      <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">{title}</h1>
      {children && <p className="text-sm text-gray-500 dark:text-gray-400">{children}</p>}
    </div>
  );
}

function GoToProjects() {
  const { t } = useTranslation('org');
  const navigate = useNavigate();
  return (
    <Button variant="secondary" onClick={() => navigate('/projects')}>
      {t('goToProjects')}
    </Button>
  );
}

/** A closed invite (expired, revoked, already used) or a link that matches no invite. */
function ClosedInvite({ title, body }: Readonly<{ title: string; body: string }>) {
  const { t } = useTranslation('org');
  const { authenticated } = useAuth();
  return (
    <Card>
      <Message title={title}>{body}</Message>
      {authenticated ? (
        <GoToProjects />
      ) : (
        <Button variant="secondary" onClick={() => void keycloak.login({ redirectUri: `${window.location.origin}/` })}>
          {t('invite.logIn')}
        </Button>
      )}
    </Card>
  );
}

function InviteHeading({ invite }: Readonly<{ invite: PublicInviteResponse }>) {
  const { t } = useTranslation('org');
  return (
    <h1 className="text-xl text-gray-700 dark:text-gray-300">
      {invite.inviterName ? (
        <Trans
          t={t}
          i18nKey="invite.heading"
          values={{ inviter: invite.inviterName, organisation: invite.organisationName }}
          components={{ b: strong }}
        />
      ) : (
        <Trans
          t={t}
          i18nKey="invite.headingNoInviter"
          values={{ organisation: invite.organisationName }}
          components={{ b: strong }}
        />
      )}
    </h1>
  );
}

/** Signed out: sign up or log in with the invited address, then come back here and join. */
function SignInToJoin({ token, invite }: Readonly<{ token: string; invite: PublicInviteResponse }>) {
  const { t } = useTranslation('org');
  const options = { loginHint: invite.email, redirectUri: inviteUrl(token) };
  return (
    <Card>
      <InviteHeading invite={invite} />
      <p className="text-sm text-gray-500 dark:text-gray-400">
        <Trans t={t} i18nKey="invite.signInBody" values={{ email: invite.email }} components={{ b: strong }} />
      </p>
      <div className="flex flex-col gap-3">
        <Button
          onClick={() => {
            savePendingInvite(token);
            void keycloak.register(options);
          }}
        >
          {t('invite.createAccount')}
        </Button>
        <Button
          variant="secondary"
          onClick={() => {
            savePendingInvite(token);
            void keycloak.login(options);
          }}
        >
          {t('invite.haveAccount')}
        </Button>
      </div>
    </Card>
  );
}

function WrongAccount({ token, invite }: Readonly<{ token: string; invite: PublicInviteResponse }>) {
  const { t } = useTranslation('org');
  const { email } = useAuth();
  return (
    <Card>
      <Message title={t('invite.wrongAccountTitle')} />
      <p className="text-sm text-gray-500 dark:text-gray-400">
        <Trans
          t={t}
          i18nKey="invite.wrongAccountBody"
          values={{ inviteEmail: invite.email, email: email ?? '' }}
          components={{ b: strong }}
        />
      </p>
      <Button onClick={() => void keycloak.logout({ redirectUri: inviteUrl(token) })}>
        {t('invite.signOut')}
      </Button>
    </Card>
  );
}

/** Signed in with the invited address: accept straight away, then open the projects list. */
function JoinOrganisation({ token, invite }: Readonly<{ token: string; invite: PublicInviteResponse }>) {
  const { t } = useTranslation('org');
  const navigate = useNavigate();
  const { mutate: accept, isSuccess, isError, error } = useAcceptOrgInvite();

  useEffect(() => {
    if (acceptsSent.has(token)) return;
    acceptsSent.add(token);
    clearPendingInvite();
    accept(token);
  }, [accept, token]);

  useEffect(() => {
    if (isSuccess) {
      navigate('/projects', { replace: true, state: { [JOINED_ORGANISATION_STATE]: invite.organisationName } });
    }
  }, [isSuccess, navigate, invite.organisationName]);

  if (isError) {
    const message =
      isAxiosError(error) && typeof error.response?.data?.message === 'string'
        ? (error.response.data.message as string)
        : t('acceptInviteError');
    return (
      <Card>
        <Message title={t('invite.joinFailedTitle', { organisation: invite.organisationName })} />
        <p className="text-sm text-red-700 dark:text-red-400">{message}</p>
        <div className="flex justify-center gap-3">
          <Button onClick={() => accept(token)}>{t('invite.tryAgain')}</Button>
          <GoToProjects />
        </div>
      </Card>
    );
  }

  return (
    <Card>
      <Message title={t('invite.joining', { organisation: invite.organisationName })}>
        {t('invite.joiningBody')}
      </Message>
    </Card>
  );
}

function InviteContent({ token }: Readonly<{ token: string | undefined }>) {
  const { t } = useTranslation('org');
  const { authenticated, email } = useAuth();
  const { data: invite, isLoading, error, refetch } = usePublicInvite(token);

  if (!token || (isAxiosError(error) && error.response?.status === 404)) {
    return <ClosedInvite title={t('invite.notFoundTitle')} body={t('invite.notFoundBody')} />;
  }
  if (isLoading) {
    return (
      <Card>
        <Skeleton className="h-6 w-3/4 mx-auto" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-10 w-full" />
      </Card>
    );
  }
  if (!invite) {
    return <PageError message={t('invite.loadError')} onRetry={() => void refetch()} />;
  }

  const inviter = invite.inviterName;
  switch (inviteView(invite, authenticated, email)) {
    case 'expired':
      return (
        <ClosedInvite
          title={t('invite.expiredTitle')}
          body={inviter ? t('invite.askInviter', { inviter }) : t('invite.askForNew')}
        />
      );
    case 'revoked':
      return (
        <ClosedInvite
          title={t('invite.revokedTitle')}
          body={inviter ? t('invite.askInviter', { inviter }) : t('invite.askForNew')}
        />
      );
    case 'accepted':
      return (
        <ClosedInvite
          title={t('invite.acceptedTitle')}
          body={t('invite.acceptedBody', { organisation: invite.organisationName })}
        />
      );
    case 'signIn':
      return <SignInToJoin token={token} invite={invite} />;
    case 'wrongAccount':
      return <WrongAccount token={token} invite={invite} />;
    case 'join':
      return <JoinOrganisation token={token} invite={invite} />;
  }
}

/**
 * JOB-399: the page an invite link opens. Public, so a new invitee sees who invited them before
 * having an account; signing up or logging in from here comes back here and joins automatically.
 */
export default function AcceptInvitePage() {
  const { t } = useTranslation('org');
  usePageTitle(t('invite.pageTitle'));
  const { token } = useParams<{ token: string }>();

  return (
    <div className="min-h-screen flex flex-col bg-gray-50 dark:bg-gray-900">
      <OnboardingHeader />
      <main className="flex-1 flex items-center justify-center px-4 py-8">
        <div className="w-full max-w-md" data-testid="invite-page">
          <InviteContent token={token} />
        </div>
      </main>
    </div>
  );
}
