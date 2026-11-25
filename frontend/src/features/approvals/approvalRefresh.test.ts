import { beforeEach, describe, expect, it } from 'vitest';
import { QueryClient } from '@tanstack/react-query';
import type { NotificationType } from '../../types';
import { refreshApprovalsAfterConflict, refreshApprovalsAfterConnect, refreshApprovalsForNotification } from './approvalRefresh';

const PROJECT_QUEUE = ['approvals', 'PRJ-1', 'pending'];
const CROSS_ORG = ['approvals', 'pending', 'cross-org'];
const JOB_APPROVALS = ['jobs', 'PRJ-1', 'JOB-1', 'approvals'];
const OTHER_PROJECT_JOBS = ['jobs', 'PRJ-2'];
const NOTIFICATIONS = ['notifications', 'unread-count'];

let qc: QueryClient;
const isInvalidated = (key: unknown[]) => qc.getQueryState(key)?.isInvalidated ?? false;

beforeEach(() => {
  qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  for (const key of [PROJECT_QUEUE, CROSS_ORG, JOB_APPROVALS, OTHER_PROJECT_JOBS, NOTIFICATIONS]) {
    qc.setQueryData(key, []);
  }
});

describe('refreshApprovalsForNotification', () => {
  it.each<NotificationType>(['APPROVAL_REQUESTED', 'APPROVAL_DECIDED'])(
    'invalidates the project queue and the cross-org list for %s',
    (type) => {
      refreshApprovalsForNotification(qc, type);

      expect(isInvalidated(PROJECT_QUEUE)).toBe(true);
      expect(isInvalidated(CROSS_ORG)).toBe(true);
      expect(isInvalidated(JOB_APPROVALS)).toBe(false);
      expect(isInvalidated(NOTIFICATIONS)).toBe(false);
    },
  );

  it.each<NotificationType>(['JOB_ASSIGNED', 'NOTE_ADDED', 'JOB_STATUS_CHANGED'])('leaves the queues alone for %s', (type) => {
    refreshApprovalsForNotification(qc, type);

    expect(isInvalidated(PROJECT_QUEUE)).toBe(false);
    expect(isInvalidated(CROSS_ORG)).toBe(false);
  });
});

describe('refreshApprovalsAfterConnect', () => {
  it('invalidates the project queue and the cross-org list, nothing else', () => {
    refreshApprovalsAfterConnect(qc);

    expect(isInvalidated(PROJECT_QUEUE)).toBe(true);
    expect(isInvalidated(CROSS_ORG)).toBe(true);
    expect(isInvalidated(JOB_APPROVALS)).toBe(false);
    expect(isInvalidated(NOTIFICATIONS)).toBe(false);
  });
});

describe('refreshApprovalsAfterConflict', () => {
  it('on a 409 invalidates both queues and the project\'s job data', () => {
    refreshApprovalsAfterConflict(qc, 'PRJ-1', { response: { status: 409 } });

    expect(isInvalidated(PROJECT_QUEUE)).toBe(true);
    expect(isInvalidated(CROSS_ORG)).toBe(true);
    expect(isInvalidated(JOB_APPROVALS)).toBe(true);
    expect(isInvalidated(OTHER_PROJECT_JOBS)).toBe(false);
  });

  it.each([
    ['a 400', { response: { status: 400 } }],
    ['a 403', { response: { status: 403 } }],
    ['a network error', new Error('Network Error')],
    ['no error', null],
  ])('does nothing on %s', (_, error) => {
    refreshApprovalsAfterConflict(qc, 'PRJ-1', error);

    expect(isInvalidated(PROJECT_QUEUE)).toBe(false);
    expect(isInvalidated(CROSS_ORG)).toBe(false);
    expect(isInvalidated(JOB_APPROVALS)).toBe(false);
  });
});
