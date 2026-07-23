import DemoRouteWrapper from './DemoRouteWrapper';
import { DEMO_PROJECT_ID } from './mockData';
import MilestonesPage from '../features/milestones/MilestonesPage';
import JobListPage from '../features/jobs/JobListPage';
import JobDetailPage from '../features/jobs/JobDetailPage';
import type { DemoSlide } from './types';

/**
 * The Milestones card's demo slides (ADR-0040 / JOB-144, second slide added in
 * JOB-146 polish): the Milestones management view itself, plus a second slide (same
 * two-slide pattern as Approvals) showing the job list actually grouped by
 * milestone — the visible effect this add-on has elsewhere in the app, not just its
 * own dedicated page. Runs on the shared DEMO_PROJECT_ID with the default
 * (all-addons) org state, since these jobs already have real milestone assignments.
 */
export const slides: DemoSlide[] = [
  {
    labelKey: 'featuresPage.demo.slideLabels.milestones',
    render: () => (
      <DemoRouteWrapper
        routes={[{ path: '/projects/:projectFriendlyId/milestones', element: <MilestonesPage /> }]}
        initialEntry={`/projects/${DEMO_PROJECT_ID}/milestones`}
      />
    ),
  },
  {
    labelKey: 'featuresPage.demo.slideLabels.milestoneGrouping',
    render: () => (
      <DemoRouteWrapper
        routes={[
          { path: '/projects/:projectFriendlyId/jobs', element: <JobListPage /> },
          { path: '/projects/:projectFriendlyId/jobs/:jobFriendlyId', element: <JobDetailPage /> },
        ]}
        initialEntry={`/projects/${DEMO_PROJECT_ID}/jobs`}
      />
    ),
  },
];

export default slides;
