import { describe, expect, it } from 'vitest';
import { NOTIFICATION_TYPES, isMutable, toggleMuted, typesInGroup } from './notificationContent';

describe('typesInGroup', () => {
  it('splits the 16 types into ADR-0051 job events and project, organisation and account events', () => {
    const job = typesInGroup('JOB');
    const account = typesInGroup('ACCOUNT');
    expect(job).toHaveLength(11);
    expect(job[0]).toBe('APPROVAL_REQUESTED');
    expect(job.at(-1)).toBe('JOB_OVERDUE');
    expect(account).toEqual([
      'PROJECT_MEMBER_ADDED',
      'SCHEDULE_RUN_MISSED',
      'INVITE_ACCEPTED',
      'FEEDBACK_RESOLVED',
      'SUBSCRIPTION_PAST_DUE',
    ]);
    expect([...job, ...account]).toEqual(NOTIFICATION_TYPES);
  });
});

describe('isMutable', () => {
  it('locks only SUBSCRIPTION_PAST_DUE', () => {
    expect(NOTIFICATION_TYPES.filter((t) => !isMutable(t))).toEqual(['SUBSCRIPTION_PAST_DUE']);
  });
});

describe('toggleMuted', () => {
  it('adds a type when turned off and removes it when turned on, in enum order', () => {
    const muted = toggleMuted(['NOTE_ADDED'], 'APPROVAL_DECIDED', false);
    expect(muted).toEqual(['APPROVAL_DECIDED', 'NOTE_ADDED']);
    expect(toggleMuted(muted, 'NOTE_ADDED', true)).toEqual(['APPROVAL_DECIDED']);
  });

  it('never includes SUBSCRIPTION_PAST_DUE, which the backend rejects', () => {
    expect(toggleMuted([], 'SUBSCRIPTION_PAST_DUE', false)).toEqual([]);
    expect(toggleMuted(['SUBSCRIPTION_PAST_DUE', 'JOB_UPDATED'], 'NOTE_ADDED', false)).toEqual([
      'JOB_UPDATED',
      'NOTE_ADDED',
    ]);
  });

  it('is a no-op for a type already in the requested state', () => {
    expect(toggleMuted(['JOB_UPDATED'], 'JOB_UPDATED', false)).toEqual(['JOB_UPDATED']);
    expect(toggleMuted([], 'JOB_UPDATED', true)).toEqual([]);
  });
});
