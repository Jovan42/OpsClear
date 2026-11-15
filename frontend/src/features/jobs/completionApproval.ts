// ADR-0052: pure rules for the completion approval flow on the job page, kept here so they're
// unit-testable without rendering components.

import type { TFunction } from 'i18next';

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

/**
 * The comment prefixes the backend stores when it cancels a completion request automatically
 * (`CompletionApprovalCanceller.COMPLETED_DIRECTLY_COMMENT` / `JOB_DELETED_COMMENT`, followed by
 * the person's name). The API has no separate field for the reason, so the UI recognises them to
 * show a translated reason instead of untranslated English. Keep them in sync with the backend.
 */
const AUTO_CANCEL_PREFIXES = {
  COMPLETED_DIRECTLY: 'Completed directly by ',
  JOB_DELETED: 'Job deleted by ',
} as const;

export type AutoCancelReason = keyof typeof AUTO_CANCEL_PREFIXES;

/** Why a completion request was cancelled automatically, or null (a withdrawal, or any other comment). */
export function autoCancelReason(comment: string | null | undefined): AutoCancelReason | null {
  if (!comment) return null;
  const match = (Object.keys(AUTO_CANCEL_PREFIXES) as AutoCancelReason[]).find((reason) =>
    comment.startsWith(AUTO_CANCEL_PREFIXES[reason]),
  );
  return match ?? null;
}

/**
 * The text to show for an approval in a list. A completion request without its own message
 * stores the backend's untranslated default, so that shows a translated equivalent instead.
 */
export function approvalDescription(approval: { kind?: string; description: string }, t: TFunction): string {
  if (approval.kind !== 'COMPLETION') return approval.description;
  return completionRequestMessage(approval.description)
    ?? t('jobsComponents:approvalState.defaultCompletionDescription');
}

/** True for an approved completion approval that didn't complete the job ("approved, not applied"). */
export function isApprovedNotApplied(approval: {
  status: string;
  kind?: string;
  completionApplied?: boolean | null;
}): boolean {
  return approval.status === 'APPROVED' && approval.kind === 'COMPLETION' && approval.completionApplied === false;
}
