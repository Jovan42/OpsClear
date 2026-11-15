// ADR-0052: pure rules for the completion approval flow on the job page, kept here so they're
// unit-testable without rendering components.

type Role = 'OWNER' | 'ADMIN' | 'MEMBER' | null;

/**
 * The description the backend stores when a completion request has no message
 * (`ApprovalService.DEFAULT_COMPLETION_DESCRIPTION`). The API doesn't say whether a message was
 * given, so the UI recognises this text to avoid showing an untranslated placeholder. Keep the
 * two in sync.
 */
export const DEFAULT_COMPLETION_MESSAGE = 'Requesting to complete this job';

/** The request's own message, or null when it's the backend's default text (or empty). */
export function completionRequestMessage(message: string | null | undefined): string | null {
  return message && message !== DEFAULT_COMPLETION_MESSAGE ? message : null;
}

/**
 * Whether this user's Complete becomes a completion request. Mirrors the backend gate: the
 * project has the gate on, the APPROVALS add-on is usable for writes (a PAST_DUE subscription
 * isn't, so the gate isn't enforced), and the user is a Member. Owners/Admins complete directly.
 */
export function requiresCompletionApproval(
  projectGated: boolean,
  approvalsUsableForWrites: boolean,
  role: Role,
): boolean {
  return projectGated && approvalsUsableForWrites && role === 'MEMBER';
}

/**
 * Whether a pending completion request can be acted on (approve, reject, withdraw) at all. These
 * are writes, so they need the APPROVALS add-on usable for writes and a project that isn't
 * completed (read-only). Role checks come on top of this.
 */
export function canActOnCompletionRequest(approvalsUsableForWrites: boolean, projectCompleted: boolean): boolean {
  return approvalsUsableForWrites && !projectCompleted;
}
