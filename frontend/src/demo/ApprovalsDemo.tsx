import { useTranslation } from 'react-i18next';
import DemoAccordion from './DemoAccordion';
import DemoRouteWrapper from './DemoRouteWrapper';
import DemoQueryScope from './DemoQueryScope';
import { DEMO_PROJECT_ID, demoStore } from './mockData';
import type { DemoSlide } from './types';
import ApprovalQueuePage from '../features/approvals/ApprovalQueuePage';
import ApprovalList from '../features/jobs/components/ApprovalList';
import JobDetailPage from '../features/jobs/JobDetailPage';

const HISTORY_JOB_ID = 'demo-job-04';

// Same accordion chrome as JobDetailPage's own Approvals section (JOB-146 polish) —
// defaults expanded since this slide's whole point is showing decided history
// without an extra click.
// eslint-disable-next-line react-refresh/only-export-components
function ApprovalHistorySlide() {
  const { t } = useTranslation(['jobsPages', 'approvalsDashboardSettingsLanding']);
  const job = demoStore.jobs.find((j) => j.id === HISTORY_JOB_ID);
  const approvals = demoStore.approvals.filter((a) => a.jobId === HISTORY_JOB_ID);
  const pendingCount = approvals.filter((a) => a.status === 'PENDING').length;

  return (
    <DemoQueryScope>
      <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8 space-y-1.5">
        <p className="text-sm text-gray-500 dark:text-gray-400">{job?.title}</p>
        <DemoAccordion
          title={t('jobsPages:jobDetailPage.approvalsSection')}
          badge={
            pendingCount > 0 && (
              <span className="bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400 text-xs font-medium px-1.5 py-0.5 rounded-full">
                {t('jobsPages:jobDetailPage.pendingCount', { count: pendingCount })}
              </span>
            )
          }
        >
          <ApprovalList projectId={DEMO_PROJECT_ID} jobId={HISTORY_JOB_ID} role="OWNER" members={demoStore.members} />
        </DemoAccordion>
      </div>
    </DemoQueryScope>
  );
}

/**
 * The Approvals card's demo slides (ADR-0040 / JOB-143): the pending-approvals queue,
 * plus just the ApprovalList component showing decided history (one approved, one
 * rejected) for a completed job — DemoOverlay's prev/next arrows page between them.
 */
export const slides: DemoSlide[] = [
  {
    labelKey: 'featuresPage.demo.slideLabels.pendingApprovals',
    render: () => (
      <DemoRouteWrapper
        routes={[
          // The "→ Job" link on each pending group navigates to the job's detail
          // page — registering that route too (rather than just the approvals one)
          // means that in-app navigation actually goes somewhere, instead of landing
          // on an unmatched route and rendering nothing.
          { path: '/projects/:projectFriendlyId/approvals', element: <ApprovalQueuePage /> },
          { path: '/projects/:projectFriendlyId/jobs/:jobFriendlyId', element: <JobDetailPage /> },
        ]}
        initialEntry={`/projects/${DEMO_PROJECT_ID}/approvals`}
      />
    ),
  },
  {
    labelKey: 'featuresPage.demo.slideLabels.approvalHistory',
    render: () => <ApprovalHistorySlide />,
  },
];

export default slides;
