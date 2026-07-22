import { Trans, useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { useCurrentOrg } from '../features/org/OrgContext';
import { useOrgMembers } from '../features/org/useOrganisation';
import SubscriptionSection from '../features/org/SubscriptionSection';

export default function SubscriptionWall() {
  const { t } = useTranslation('shared2');
  const { userId } = useAuth();
  const { org } = useCurrentOrg();
  const { data: members = [] } = useOrgMembers(org?.id ?? null);

  if (!org) return null;

  const myRole = members.find((m) => m.userId === userId)?.role ?? null;
  const isOwner = myRole === 'OWNER';
  const ownerMember = members.find((m) => m.role === 'OWNER');

  if (isOwner) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-10">
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {t('subscriptionWall.setupTitle')}
          </h1>
          <p className="mt-2 text-gray-500 dark:text-gray-400">
            {t('subscriptionWall.setupDescription')}
          </p>
        </div>
        <SubscriptionSection orgId={org.id} />
      </div>
    );
  }

  return (
    <div className="flex items-center justify-center min-h-[60vh]">
      <div className="text-center max-w-sm px-4">
        <div className="text-4xl mb-4">🔒</div>
        <h2 className="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-2">
          {t('subscriptionWall.requiredTitle')}
        </h2>
        <p className="text-gray-500 dark:text-gray-400">
          {ownerMember
            ? (
              <Trans
                t={t}
                i18nKey="subscriptionWall.contactOwner"
                values={{ name: ownerMember.userName, org: org.name }}
                components={{ b: <span className="font-medium text-gray-700 dark:text-gray-300" /> }}
              />
            )
            : (
              <Trans
                t={t}
                i18nKey="subscriptionWall.askOwner"
                values={{ org: org.name }}
                components={{ b: <span className="font-medium text-gray-700 dark:text-gray-300" /> }}
              />
            )
          }
        </p>
      </div>
    </div>
  );
}
