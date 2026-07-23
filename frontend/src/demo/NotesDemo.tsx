import { useTranslation } from 'react-i18next';
import DemoAccordion from './DemoAccordion';
import DemoQueryScope from './DemoQueryScope';
import { DEMO_PROJECT_ID, demoStore } from './mockData';
import NoteThread from '../features/jobs/components/NoteThread';
import type { DemoSlide } from './types';

const NOTES_JOB_ID = 'demo-job-01';

// eslint-disable-next-line react-refresh/only-export-components
function NotesDemoContent() {
  const { t } = useTranslation('jobsPages');
  const job = demoStore.jobs.find((j) => j.id === NOTES_JOB_ID);

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8 space-y-1.5">
      <p className="text-sm text-gray-500 dark:text-gray-400">{job?.title}</p>
      <DemoAccordion title={t('jobsPages:jobDetailPage.notesSection')}>
        <NoteThread
          projectId={DEMO_PROJECT_ID}
          jobId={NOTES_JOB_ID}
          members={demoStore.members}
          projectCompleted={false}
        />
      </DemoAccordion>
    </div>
  );
}

/**
 * The Notes card's demo slide (ADR-0040 / JOB-144): NoteThread wrapped in the same
 * accordion chrome JobDetailPage builds around it in the real app (JOB-146 polish) —
 * strictly scoped to notes themselves, nothing else from the job detail page. Only
 * needs its own QueryClient (useNotes/useAddNote) — no Router.
 */
export const slides: DemoSlide[] = [
  {
    labelKey: 'featuresPage.demo.slideLabels.notes',
    render: () => (
      <DemoQueryScope>
        <NotesDemoContent />
      </DemoQueryScope>
    ),
  },
];

export default slides;
