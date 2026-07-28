import DemoRouteWrapper from './DemoRouteWrapper';
import { DEMO_BASE_PROJECT_ID } from './mockData';
import { mockOrgStateJobTypesOnly } from './mockOrgStateJobTypesOnly';
import JobTypesPage from '../features/jobTypes/JobTypesPage';
import JobListPage from '../features/jobs/JobListPage';
import JobDetailPage from '../features/jobs/JobDetailPage';
import type { DemoSlide } from './types';

/**
 * The Job types card's demo slides (ADR-0040 / JOB-158): the real types management
 * list, plus the job list showing colored type badges and the type filter.
 *
 * Both slides deliberately run on DEMO_BASE_PROJECT_ID — the job-tracking card's
 * milestone-free project (JOB-146 polish) — rather than the shared DEMO_PROJECT_ID,
 * for two reasons at once: they share one dataset (demoStore.trackingJobTypes), so a
 * type created/edited/deleted on slide 1 is immediately visible on slide 2; and that
 * project genuinely has no milestones, so slide 2 stays a plain "just jobs and their
 * types" view rather than picking up the shared project's milestone grouping/filter.
 * Both slides pass mockOrgStateJobTypesOnly (only JOB_TYPES unlocked) so no other
 * add-on's UI shows up either.
 */
export const slides: DemoSlide[] = [
  {
    labelKey: 'featuresPage.demo.slideLabels.jobTypes',
    render: () => (
      <DemoRouteWrapper
        routes={[{ path: '/projects/:projectFriendlyId/types', element: <JobTypesPage /> }]}
        initialEntry={`/projects/${DEMO_BASE_PROJECT_ID}/types`}
        orgState={mockOrgStateJobTypesOnly}
      />
    ),
  },
  {
    labelKey: 'featuresPage.demo.slideLabels.jobTypesInJobList',
    render: () => (
      <DemoRouteWrapper
        routes={[
          { path: '/projects/:projectFriendlyId/jobs', element: <JobListPage /> },
          { path: '/projects/:projectFriendlyId/jobs/:jobFriendlyId', element: <JobDetailPage /> },
        ]}
        initialEntry={`/projects/${DEMO_BASE_PROJECT_ID}/jobs`}
        orgState={mockOrgStateJobTypesOnly}
      />
    ),
  },
];

export default slides;
