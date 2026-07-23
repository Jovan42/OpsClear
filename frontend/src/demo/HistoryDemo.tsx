import DemoQueryScope from './DemoQueryScope';
import { DEMO_PROJECT_ID, demoStore } from './mockData';
import StatusHistory from '../features/jobs/components/StatusHistory';
import type { DemoSlide } from './types';

const HISTORY_JOB_ID = 'demo-job-07';

/**
 * The Job status history card's demo slide (ADR-0040 / JOB-144): just StatusHistory
 * in isolation on a mock job — strictly scoped to status history itself, nothing else
 * from the job detail page. demo-job-07 walks through every status in one timeline
 * (New → In Progress → Blocked → In Progress → Completed). Only needs its own
 * QueryClient (useJobHistory) — no Router. Starts expanded (defaultExpanded) so the
 * timeline is visible immediately.
 */
export const slides: DemoSlide[] = [
  {
    labelKey: 'featuresPage.demo.slideLabels.history',
    render: () => {
      const job = demoStore.jobs.find((j) => j.id === HISTORY_JOB_ID);
      return (
        <DemoQueryScope>
          <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8 space-y-1.5">
            <p className="text-sm text-gray-500 dark:text-gray-400">{job?.title}</p>
            <StatusHistory projectId={DEMO_PROJECT_ID} jobId={HISTORY_JOB_ID} defaultExpanded />
          </div>
        </DemoQueryScope>
      );
    },
  },
];

export default slides;
