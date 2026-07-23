import { useTranslation } from 'react-i18next';
import { MemoryRouter } from 'react-router-dom';
import DemoAccordion from './DemoAccordion';
import DemoQueryScope from './DemoQueryScope';
import { DEMO_PROJECT_ID, demoStore } from './mockData';
import { useJob } from '../features/jobs/useJobs';
import LinksSection from '../features/jobs/components/LinksSection';
import type { DemoSlide } from './types';

const LINKS_JOB_ID = 'demo-job-01';

// eslint-disable-next-line react-refresh/only-export-components
function LinksDemoContent() {
  const { t } = useTranslation('jobsPages');
  const { data: job } = useJob(DEMO_PROJECT_ID, LINKS_JOB_ID);

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8 space-y-1.5">
      <p className="text-sm text-gray-500 dark:text-gray-400">{job?.title}</p>
      <DemoAccordion title={t('jobsPages:jobDetailPage.linksSection')}>
        <LinksSection
          projectId={DEMO_PROJECT_ID}
          jobId={LINKS_JOB_ID}
          links={job?.links ?? []}
          members={demoStore.members}
          canManage
          projectCompleted={false}
        />
      </DemoAccordion>
    </div>
  );
}

/**
 * The Links card's demo slide (ADR-0040 / JOB-146): LinksSection wrapped in the same
 * accordion chrome JobDetailPage builds around it in the real app — strictly scoped
 * to links themselves, nothing else from the job detail page.
 *
 * Uses the real useJob() hook (not a static demoStore read), same as
 * RelationshipsDemo, so the list reactively updates after add/edit/delete — each
 * mutation invalidates the ['jobs', projectId, jobId] query key. Needs a bare
 * MemoryRouter (no route matching necessary) plus its own QueryClient.
 */
export const slides: DemoSlide[] = [
  {
    labelKey: 'featuresPage.demo.slideLabels.links',
    render: () => (
      <DemoQueryScope>
        <MemoryRouter>
          <LinksDemoContent />
        </MemoryRouter>
      </DemoQueryScope>
    ),
  },
];

export default slides;
