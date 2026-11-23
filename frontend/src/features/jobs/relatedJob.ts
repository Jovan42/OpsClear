// Pure rules for a related job on the Relationships section, kept here so they're unit-testable
// without rendering components.

import type { JobRef } from '../../types';

/**
 * Whether the related job was deleted. The backend skips soft-deleted jobs when it loads a
 * job's relationships but keeps the linked id, so a deleted job comes back with a null title
 * (and null friendlyId and status). A live job always has a title.
 */
export function isDeletedJob(job: JobRef): boolean {
  return job.title == null;
}

/** The job page path for a related job, or null when it can't be opened (deleted or no id). */
export function relatedJobPath(projectId: string, job: JobRef): string | null {
  if (!job.id || isDeletedJob(job)) return null;
  return `/projects/${projectId}/jobs/${job.friendlyId ?? job.id}`;
}
