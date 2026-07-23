import DemoQueryScope from './DemoQueryScope';
import { DEMO_PROJECT_ID, demoStore } from './mockData';
import NoteThread from '../features/jobs/components/NoteThread';
import type { DemoSlide } from './types';

const NOTES_JOB_ID = 'demo-job-01';

/**
 * The Notes card's demo slide (ADR-0040 / JOB-144): just NoteThread in isolation on
 * a mock job — strictly scoped to notes themselves, nothing else from the job detail
 * page. Only needs its own QueryClient (useNotes/useAddNote) — no Router.
 */
export const slides: DemoSlide[] = [
  {
    labelKey: 'featuresPage.demo.slideLabels.notes',
    render: () => {
      const job = demoStore.jobs.find((j) => j.id === NOTES_JOB_ID);
      return (
        <DemoQueryScope>
          <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8 space-y-1.5">
            <p className="text-sm text-gray-500 dark:text-gray-400">{job?.title}</p>
            <NoteThread
              projectId={DEMO_PROJECT_ID}
              jobId={NOTES_JOB_ID}
              members={demoStore.members}
              projectCompleted={false}
            />
          </div>
        </DemoQueryScope>
      );
    },
  },
];

export default slides;
