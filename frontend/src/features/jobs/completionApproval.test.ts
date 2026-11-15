import { describe, expect, it } from 'vitest';
import {
  DEFAULT_COMPLETION_MESSAGE,
  autoCancelReason,
  canActOnCompletionRequest,
  isApprovedNotApplied,
  completionRequestMessage,
  requiresCompletionApproval,
} from './completionApproval';

describe('requiresCompletionApproval', () => {
  it('gates a Member on a gated project with APPROVALS usable for writes', () => {
    expect(requiresCompletionApproval(true, true, 'MEMBER')).toBe(true);
  });

  it('never gates Owners or Admins', () => {
    expect(requiresCompletionApproval(true, true, 'OWNER')).toBe(false);
    expect(requiresCompletionApproval(true, true, 'ADMIN')).toBe(false);
  });

  it('does not gate on an ungated project', () => {
    expect(requiresCompletionApproval(false, true, 'MEMBER')).toBe(false);
  });

  it('does not gate when APPROVALS is not usable for writes (missing or PAST_DUE)', () => {
    expect(requiresCompletionApproval(true, false, 'MEMBER')).toBe(false);
  });

  it('does not gate without a project role', () => {
    expect(requiresCompletionApproval(true, true, null)).toBe(false);
  });
});

describe('canActOnCompletionRequest', () => {
  it('allows acting with APPROVALS usable for writes on an active project', () => {
    expect(canActOnCompletionRequest(true, false)).toBe(true);
  });

  it('blocks acting when APPROVALS is not usable for writes (missing or PAST_DUE)', () => {
    expect(canActOnCompletionRequest(false, false)).toBe(false);
  });

  it('blocks acting on a completed project', () => {
    expect(canActOnCompletionRequest(true, true)).toBe(false);
  });
});

describe('completionRequestMessage', () => {
  it('returns the message the requester wrote', () => {
    expect(completionRequestMessage('All checks done')).toBe('All checks done');
  });

  it('hides the backend default text and empty messages', () => {
    expect(completionRequestMessage(DEFAULT_COMPLETION_MESSAGE)).toBeNull();
    expect(completionRequestMessage('')).toBeNull();
    expect(completionRequestMessage(null)).toBeNull();
  });
});

describe('autoCancelReason', () => {
  it('recognises the comments the backend stores on automatic cancellation', () => {
    expect(autoCancelReason('Completed directly by Alice Smith')).toBe('COMPLETED_DIRECTLY');
    expect(autoCancelReason('Job deleted by Bob Jones')).toBe('JOB_DELETED');
  });

  it('returns null for a withdrawal (no comment) and for other comments', () => {
    expect(autoCancelReason(null)).toBeNull();
    expect(autoCancelReason(undefined)).toBeNull();
    expect(autoCancelReason('')).toBeNull();
    expect(autoCancelReason('Not ready yet')).toBeNull();
  });
});

describe('isApprovedNotApplied', () => {
  it('is true only for an approved completion approval that did not complete the job', () => {
    expect(isApprovedNotApplied({ status: 'APPROVED', kind: 'COMPLETION', completionApplied: false })).toBe(true);
  });

  it('is false when the completion was applied or not decided yet', () => {
    expect(isApprovedNotApplied({ status: 'APPROVED', kind: 'COMPLETION', completionApplied: true })).toBe(false);
    expect(isApprovedNotApplied({ status: 'PENDING', kind: 'COMPLETION', completionApplied: null })).toBe(false);
  });

  it('is false for manual approvals', () => {
    expect(isApprovedNotApplied({ status: 'APPROVED', kind: 'MANUAL', completionApplied: false })).toBe(false);
  });
});
