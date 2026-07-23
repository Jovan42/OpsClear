import DemoRouteWrapper from './DemoRouteWrapper';
import { DEMO_BASE_PROJECT_ID } from './mockData';
import { mockOrgStateNoAddons } from './mockOrgStateNoAddons';
import JobListPage from '../features/jobs/JobListPage';
import JobDetailPage from '../features/jobs/JobDetailPage';
import type { DemoSlide } from './types';

/**
 * The Job tracking card's demo slide (ADR-0040 / JOB-144): the real job list, with no
 * add-ons unlocked — this card represents the base/included experience, so it
 * deliberately shows the plain view rather than the "everything unlocked" treatment
 * every other demo uses. Both the list and job-detail routes are registered in the
 * same MemoryRouter, so clicking a job row navigates live to its (also plain) detail
 * page, exactly like real in-app navigation — not a separate slide reached via arrows.
 *
 * Runs on DEMO_BASE_PROJECT_ID, not the shared DEMO_PROJECT_ID (JOB-146 polish) —
 * milestone grouping in the real job list is driven by whether the project has any
 * milestones at all, not by an addon check, so a "no add-ons" demo needs a project
 * that genuinely has none rather than reusing the shared one (which legitimately
 * has milestones, needed by the Milestones/Templates cards).
 */
export const slides: DemoSlide[] = [
  {
    labelKey: 'featuresPage.demo.slideLabels.jobTracking',
    render: () => (
      <DemoRouteWrapper
        routes={[
          { path: '/projects/:projectFriendlyId/jobs', element: <JobListPage /> },
          { path: '/projects/:projectFriendlyId/jobs/:jobFriendlyId', element: <JobDetailPage /> },
        ]}
        initialEntry={`/projects/${DEMO_BASE_PROJECT_ID}/jobs`}
        orgState={mockOrgStateNoAddons}
      />
    ),
  },
];

export default slides;
