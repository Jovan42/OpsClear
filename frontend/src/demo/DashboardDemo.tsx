import DemoRouteWrapper from './DemoRouteWrapper';
import { DEMO_PROJECT_ID } from './mockData';
import DashboardPage from '../features/dashboard/DashboardPage';
import JobListPage from '../features/jobs/JobListPage';
import JobDetailPage from '../features/jobs/JobDetailPage';
import ApprovalQueuePage from '../features/approvals/ApprovalQueuePage';
import StatsPage from '../features/stats/StatsPage';
import type { DemoSlide } from './types';

/**
 * The Dashboard card's demo slide (ADR-0040 / JOB-145): the real dashboard, reading
 * off the same shared job-tracking dataset every other card uses — a dashboard is a
 * summary view over that same data, not a standalone entity, so unlike
 * templates/recurring/api-keys it doesn't need its own mock slice.
 *
 * Every "→" on the dashboard (status summary cards, blocked/overdue rows, pending
 * approvals) navigates in-app rather than being a dead link, so the jobs list, job
 * detail, and approvals routes all need registering alongside dashboard itself —
 * same reasoning as ApprovalsDemo's job-link route.
 *
 * The second slide is the real Stats page (ADR-0050, part of the same DASHBOARD add-on),
 * served sample trends by the `/stats` mock handler (mockStats.ts). The demo persona is the
 * project Owner, so the Team / By member toggle and the per-member drill-down work too.
 */
export const slides: DemoSlide[] = [
  {
    labelKey: 'featuresPage.demo.slideLabels.dashboard',
    render: () => (
      <DemoRouteWrapper
        routes={[
          { path: '/projects/:projectFriendlyId/dashboard', element: <DashboardPage /> },
          { path: '/projects/:projectFriendlyId/jobs', element: <JobListPage /> },
          { path: '/projects/:projectFriendlyId/jobs/:jobFriendlyId', element: <JobDetailPage /> },
          { path: '/projects/:projectFriendlyId/approvals', element: <ApprovalQueuePage /> },
        ]}
        initialEntry={`/projects/${DEMO_PROJECT_ID}/dashboard`}
      />
    ),
  },
  {
    labelKey: 'featuresPage.demo.slideLabels.stats',
    render: () => (
      <DemoRouteWrapper
        routes={[{ path: '/projects/:projectFriendlyId/stats', element: <StatsPage /> }]}
        initialEntry={`/projects/${DEMO_PROJECT_ID}/stats`}
      />
    ),
  },
];

export default slides;
