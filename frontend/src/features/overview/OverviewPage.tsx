import { Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../auth/AuthContext';
import { useCurrentOrg } from '../org/OrgContext';
import { useOrgMembers } from '../org/useOrganisation';
import { usePageTitle } from '../../hooks/usePageTitle';
import PendingApprovalsSection from './PendingApprovalsSection';

export default function OverviewPage() {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const { userId } = useAuth();
  const { org } = useCurrentOrg();
  const { data: members = [], isLoading } = useOrgMembers(org?.id ?? null);
  usePageTitle(t('overview.pageTitle'));

  // Guards against a premature redirect before OrgContext (populated by
  // AppLayout from useMyOrg) or the org members list has loaded.
  if (!org || isLoading) return null;

  const callerRole = members.find((m) => m.userId === userId)?.role ?? null;
  const isOwnerOrAdmin = callerRole === 'OWNER' || callerRole === 'ADMIN';

  // ADR-0045/ADR-0046: both sections on this page deliberately bypass the
  // project-membership boundary, so the page itself is restricted to org-level
  // Owner/Admin rather than relying on each section to hide itself.
  if (!isOwnerOrAdmin) {
    return <Navigate to="/projects" replace />;
  }

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-6">{t('overview.pageTitle')}</h1>
      <div className="space-y-10">
        <PendingApprovalsSection />
        {/* Project Directory section (ADR-0045) is added here by JOB-188. */}
      </div>
    </div>
  );
}
