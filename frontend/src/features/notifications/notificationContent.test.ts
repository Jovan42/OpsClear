import { describe, expect, it } from 'vitest';
import type { TFunction } from 'i18next';
import i18n from '../../i18n';
import { DEFAULT_COMPLETION_MESSAGE } from '../jobs/completionApproval';
import type { NotificationPayloadMap, NotificationResponse } from '../../types';
import {
  NOTIFICATION_TYPES,
  buildNotificationContent,
  isMutable,
  toggleMuted,
  typesInGroup,
} from './notificationContent';

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

// ADR-0052 (JOB-290): completion approval texts, rendered with the real en resources.
describe('buildNotificationContent for approvals', () => {
  const t = i18n.getFixedT('en', ['notifications', 'shared2']) as TFunction<['notifications', 'shared2']>;
  const job = { jobFriendlyId: 'JOB-7', jobTitle: 'Fix roof', projectName: 'Harbor' };

  function approvalNotification<K extends 'APPROVAL_REQUESTED' | 'APPROVAL_DECIDED'>(
    type: K,
    payload: NotificationPayloadMap[K],
  ): NotificationResponse {
    return {
      id: 'n1',
      read: false,
      createdAt: '2026-11-15T10:00:00Z',
      actor: { id: 'u1', name: 'Alice' },
      projectFriendlyId: 'PRJ-1',
      jobFriendlyId: 'JOB-7',
      approvalId: 'a1',
      type,
      payload,
    } as NotificationResponse;
  }

  const build = (n: NotificationResponse) => buildNotificationContent(n, t, 'en');

  it('a completion request reads "Requested to complete" and hides the default message', () => {
    const withDefault = build(approvalNotification('APPROVAL_REQUESTED', {
      ...job, kind: 'COMPLETION', description: DEFAULT_COMPLETION_MESSAGE,
    }));
    expect(withDefault.text).toBe('Requested to complete JOB-7 Fix roof');
    expect(withDefault.details).toEqual([]);

    const withMessage = build(approvalNotification('APPROVAL_REQUESTED', {
      ...job, kind: 'COMPLETION', description: 'All checks done',
    }));
    expect(withMessage.details).toEqual(['All checks done']);
  });

  it('an applied completion approval reads "Approved completion of"', () => {
    const content = build(approvalNotification('APPROVAL_DECIDED', {
      ...job, kind: 'COMPLETION', decision: 'APPROVED', completionApplied: true,
    }));
    expect(content.text).toBe('Approved completion of JOB-7 Fix roof');
  });

  it('an approved-not-applied completion approval names the job status that stopped it', () => {
    const content = build(approvalNotification('APPROVAL_DECIDED', {
      ...job, kind: 'COMPLETION', decision: 'APPROVED', completionApplied: false, completionNotAppliedReason: 'BLOCKED',
    }));
    expect(content.text).toBe("Approved completion of JOB-7 Fix roof, but it wasn't completed because it is Blocked");
  });

  it('a cancelled completion request reads "Cancelled", with the automatic reason translated', () => {
    const withdrawn = build(approvalNotification('APPROVAL_DECIDED', {
      ...job, kind: 'COMPLETION', decision: 'CANCELLED',
    }));
    expect(withdrawn.text).toBe('Cancelled your completion request on JOB-7 Fix roof');
    expect(withdrawn.details).toEqual([]);

    const completedDirectly = build(approvalNotification('APPROVAL_DECIDED', {
      ...job, kind: 'COMPLETION', decision: 'CANCELLED', comment: 'Completed directly by Alice',
    }));
    expect(completedDirectly.text).toBe('Cancelled your completion request on JOB-7 Fix roof');
    expect(completedDirectly.details).toEqual(['The job was completed directly']);
  });

  it('keeps the manual approval texts unchanged', () => {
    expect(build(approvalNotification('APPROVAL_REQUESTED', {
      ...job, kind: 'MANUAL', description: 'Buy tiles',
    }))).toMatchObject({ text: 'Requested approval on JOB-7 Fix roof', details: ['Buy tiles'] });
    // Payloads from before ADR-0052 have no kind
    expect(build(approvalNotification('APPROVAL_REQUESTED', { ...job, description: 'Buy tiles' })).text)
      .toBe('Requested approval on JOB-7 Fix roof');
    expect(build(approvalNotification('APPROVAL_DECIDED', {
      ...job, kind: 'MANUAL', decision: 'APPROVED', comment: 'Go ahead',
    }))).toMatchObject({ text: 'Approved your request on JOB-7 Fix roof', details: ['Go ahead'] });
    expect(build(approvalNotification('APPROVAL_DECIDED', { ...job, kind: 'MANUAL', decision: 'REJECTED' })).text)
      .toBe('Rejected your request on JOB-7 Fix roof');
    // A rejected completion approval reads like a manual rejection
    expect(build(approvalNotification('APPROVAL_DECIDED', { ...job, kind: 'COMPLETION', decision: 'REJECTED' })).text)
      .toBe('Rejected your request on JOB-7 Fix roof');
  });
});
