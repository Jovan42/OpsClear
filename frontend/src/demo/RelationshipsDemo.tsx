import { useState } from 'react';
import { MemoryRouter } from 'react-router-dom';
import DemoQueryScope from './DemoQueryScope';
import { DEMO_PROJECT_ID } from './mockData';
import { useJob } from '../features/jobs/useJobs';
import RelationshipsSection from '../features/jobs/components/RelationshipsSection';
import AddRelationshipModal from '../features/jobs/components/AddRelationshipModal';
import type { DemoSlide } from './types';

const RELATIONSHIPS_JOB_ID = 'demo-job-06';

// eslint-disable-next-line react-refresh/only-export-components
function RelationshipsDemoContent() {
  const { data: job } = useJob(DEMO_PROJECT_ID, RELATIONSHIPS_JOB_ID);
  const [addOpen, setAddOpen] = useState(false);

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8 space-y-1.5">
      <p className="text-sm text-gray-500 dark:text-gray-400">{job?.title}</p>
      <RelationshipsSection
        projectId={DEMO_PROJECT_ID}
        jobId={RELATIONSHIPS_JOB_ID}
        relationships={job?.relationships ?? []}
        canManage
        onAdd={() => setAddOpen(true)}
        projectCompleted={false}
      />
      <AddRelationshipModal
        open={addOpen}
        onClose={() => setAddOpen(false)}
        projectId={DEMO_PROJECT_ID}
        jobId={RELATIONSHIPS_JOB_ID}
      />
    </div>
  );
}

/**
 * The Job relationships card's demo slide (ADR-0040 / JOB-144): RelationshipsSection
 * plus AddRelationshipModal, both real and interactive — strictly scoped to
 * relationships themselves, nothing else from the job detail page. demo-job-06 was
 * chosen because it carries every direction/type/status combination at once: blocked
 * by a job that's already completed, blocking a not-yet-started job, related to an
 * in-progress job, and a simple blocked-by pair.
 *
 * Uses the real useJob() hook (not a static demoStore read) so the relationships list
 * reactively updates after adding/deleting one — useCreateRelationship/
 * useDeleteRelationship both invalidate the ['jobs', projectId, jobId] query key.
 * Needs a bare MemoryRouter (no route matching — relationships' <Link> just needs *a*
 * router context to exist, projectId/jobId are passed as plain props) plus its own
 * QueryClient.
 */
export const slides: DemoSlide[] = [
  {
    labelKey: 'featuresPage.demo.slideLabels.relationships',
    render: () => (
      <DemoQueryScope>
        <MemoryRouter>
          <RelationshipsDemoContent />
        </MemoryRouter>
      </DemoQueryScope>
    ),
  },
];

export default slides;
