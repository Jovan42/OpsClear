import { describe, expect, it } from 'vitest';
import {
  DEFAULT_COMPLETION_MESSAGE,
  canActOnCompletionRequest,
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
