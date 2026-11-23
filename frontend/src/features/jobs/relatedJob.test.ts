import { describe, expect, it } from 'vitest';
import type { JobRef } from '../../types';
import { isDeletedJob, relatedJobPath } from './relatedJob';

const live: JobRef = { id: 'a1b2', friendlyId: 'JOB-7', title: 'Fix roof', status: 'IN_PROGRESS' };
// What the backend returns for a soft-deleted related job (JobRelationshipIntegrationTest
// getJob_shouldHandleSoftDeletedLinkedJob): the id stays, everything else is null.
const deleted: JobRef = { id: 'c3d4', friendlyId: null, title: null, status: null };

describe('isDeletedJob', () => {
  it('treats a related job with no title as deleted', () => {
    expect(isDeletedJob(deleted)).toBe(true);
  });

  it('treats a related job with a title as live', () => {
    expect(isDeletedJob(live)).toBe(false);
  });
});

describe('relatedJobPath', () => {
  it('links a live job by its friendly id', () => {
    expect(relatedJobPath('p1', live)).toBe('/projects/p1/jobs/JOB-7');
  });

  it('falls back to the job id when there is no friendly id', () => {
    expect(relatedJobPath('p1', { ...live, friendlyId: null })).toBe('/projects/p1/jobs/a1b2');
  });

  it('has no link for a deleted job, even though its id is still set', () => {
    expect(relatedJobPath('p1', deleted)).toBeNull();
  });
});
