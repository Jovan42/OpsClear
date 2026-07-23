import DemoRouteWrapper from './DemoRouteWrapper';
import { DEMO_PROJECT_ID } from './mockData';
import SchedulesPage from '../features/schedules/SchedulesPage';
import JobDetailPage from '../features/jobs/JobDetailPage';
import type { DemoSlide } from './types';

/**
 * The Recurring scheduling card's demo slide (ADR-0040 / JOB-145): the real schedules
 * list, backed by its own small mock slice (BASE_SCHEDULES/BASE_MISSED_RUNS in
 * mockData.ts) — one active schedule with missed runs pending (demonstrates
 * MissedRunsPanel), one active with none, one paused (demonstrates the pause/resume
 * controls and status badges in one screen).
 *
 * Materializing a missed run surfaces a "View" link to the newly created job — the
 * job detail route needs registering alongside schedules itself for that in-app
 * navigation to land somewhere, same reasoning as ApprovalsDemo/DashboardDemo.
 */
export const slides: DemoSlide[] = [
  {
    labelKey: 'featuresPage.demo.slideLabels.recurring',
    render: () => (
      <DemoRouteWrapper
        routes={[
          { path: '/projects/:projectFriendlyId/schedules', element: <SchedulesPage /> },
          { path: '/projects/:projectFriendlyId/jobs/:jobFriendlyId', element: <JobDetailPage /> },
        ]}
        initialEntry={`/projects/${DEMO_PROJECT_ID}/schedules`}
      />
    ),
  },
];

export default slides;
