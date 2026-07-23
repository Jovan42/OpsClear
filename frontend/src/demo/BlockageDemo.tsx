import { demoStore } from './mockData';
import BlockedBanner from '../features/jobs/components/BlockedBanner';
import type { DemoSlide } from './types';

const BLOCKED_JOB_ID = 'demo-job-02';

/**
 * The Blockage card's demo slide (ADR-0040 / JOB-144): just BlockedBanner in
 * isolation for a blocked job — strictly scoped to blockage visibility itself,
 * nothing else from the job detail page. No QueryClient/Router needed at all —
 * BlockedBanner takes its job data as a plain prop.
 */
export const slides: DemoSlide[] = [
  {
    labelKey: 'featuresPage.demo.slideLabels.blockage',
    render: () => {
      const job = demoStore.jobs.find((j) => j.id === BLOCKED_JOB_ID);
      if (!job) return null;
      return (
        <div className="px-5 py-5 space-y-2">
          <p className="text-sm text-gray-500 dark:text-gray-400">{job.title}</p>
          <BlockedBanner job={job} />
        </div>
      );
    },
  },
];

export default slides;
