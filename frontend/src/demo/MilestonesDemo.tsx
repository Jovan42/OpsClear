import DemoRouteWrapper from './DemoRouteWrapper';
import { DEMO_PROJECT_ID } from './mockData';
import MilestonesPage from '../features/milestones/MilestonesPage';
import type { DemoSlide } from './types';

/**
 * The Milestones card's demo slide (ADR-0040 / JOB-144): just the real Milestones
 * view — strictly scoped to milestones themselves, nothing from other feature cards.
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
];

export default slides;
