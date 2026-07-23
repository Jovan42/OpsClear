import DemoRouteWrapper from './DemoRouteWrapper';
import { DEMO_PROJECT_ID } from './mockData';
import TemplatesPage from '../features/templates/TemplatesPage';
import type { DemoSlide } from './types';

/**
 * The Job templates card's demo slide (ADR-0040 / JOB-145): the real templates list,
 * backed by its own small mock slice (BASE_TEMPLATES in mockData.ts) — templates are
 * a standalone management screen, not part of the shared job-tracking dataset. The
 * "Schedule" button on each row opens ScheduleFormModal, which needs the recurring
 * card's schedule-creation handlers too — both live in the same demoStore/handlers.ts,
 * same as the real backend where these two features are directly related.
 */
export const slides: DemoSlide[] = [
  {
    labelKey: 'featuresPage.demo.slideLabels.templates',
    render: () => (
      <DemoRouteWrapper
        routes={[{ path: '/projects/:projectFriendlyId/templates', element: <TemplatesPage /> }]}
        initialEntry={`/projects/${DEMO_PROJECT_ID}/templates`}
      />
    ),
  },
];

export default slides;
