import type { TFunction } from 'i18next';
import type { LucideIcon } from 'lucide-react';
import {
  AlarmClock,
  AlertOctagon,
  AlertTriangle,
  CalendarX,
  CheckCircle2,
  ClipboardCheck,
  CreditCard,
  FolderPlus,
  MessageSquare,
  Pencil,
  RefreshCw,
  Trash2,
  Unlock,
  UserCheck,
  UserPlus,
  Gift,
} from 'lucide-react';
import type {
  JobNotificationPayload,
  JobPriority,
  JobStatus,
  NotificationResponse,
  NotificationType,
} from '../../types';

// A Record keyed by the union makes TypeScript fail the build if a type is added to
// NotificationType without being listed here.
const TYPE_ORDER: Record<NotificationType, number> = {
  APPROVAL_REQUESTED: 0,
  APPROVAL_DECIDED: 1,
  JOB_ASSIGNED: 2,
  JOB_STATUS_CHANGED: 3,
  JOB_BLOCKED: 4,
  BLOCKER_CLEARED: 5,
  JOB_UPDATED: 6,
  JOB_DELETED: 7,
  NOTE_ADDED: 8,
  JOB_DEADLINE_SOON: 9,
  JOB_OVERDUE: 10,
  PROJECT_MEMBER_ADDED: 11,
  SCHEDULE_RUN_MISSED: 12,
  INVITE_ACCEPTED: 13,
  FEEDBACK_RESOLVED: 14,
  SUBSCRIPTION_PAST_DUE: 15,
};

/** All 16 types, in the backend enum's order (for the type filter). */
export const NOTIFICATION_TYPES = (Object.keys(TYPE_ORDER) as NotificationType[]).sort(
  (a, b) => TYPE_ORDER[a] - TYPE_ORDER[b],
);

const URGENT_TYPES: ReadonlySet<NotificationType> = new Set([
  'JOB_BLOCKED',
  'JOB_OVERDUE',
  'SUBSCRIPTION_PAST_DUE',
]);

/** Unread badge text: `99+` above 99. */
export function formatBadge(count: number): string {
  return count > 99 ? '99+' : String(count);
}

export function isUrgent(type: NotificationType): boolean {
  return URGENT_TYPES.has(type);
}

export const NOTIFICATION_ICONS: Record<NotificationType, LucideIcon> = {
  APPROVAL_REQUESTED: ClipboardCheck,
  APPROVAL_DECIDED: CheckCircle2,
  JOB_ASSIGNED: UserCheck,
  JOB_STATUS_CHANGED: RefreshCw,
  JOB_BLOCKED: AlertOctagon,
  BLOCKER_CLEARED: Unlock,
  JOB_UPDATED: Pencil,
  JOB_DELETED: Trash2,
  NOTE_ADDED: MessageSquare,
  JOB_DEADLINE_SOON: AlarmClock,
  JOB_OVERDUE: AlertTriangle,
  PROJECT_MEMBER_ADDED: FolderPlus,
  SCHEDULE_RUN_MISSED: CalendarX,
  INVITE_ACCEPTED: UserPlus,
  FEEDBACK_RESOLVED: Gift,
  SUBSCRIPTION_PAST_DUE: CreditCard,
};

/**
 * Where clicking the notification goes, or null when it isn't clickable (`JOB_DELETED`,
 * whose job is gone, or a notification missing the IDs its target needs).
 */
export function notificationTarget(n: NotificationResponse): string | null {
  // Org-level types have neither key, so both stay null for them.
  const snapshot = n.payload as JobNotificationPayload;
  const projectId = n.projectFriendlyId ?? snapshot.projectFriendlyId ?? null;
  const jobId = n.jobFriendlyId ?? snapshot.jobFriendlyId ?? null;
  const jobPath = projectId && jobId ? `/projects/${projectId}/jobs/${jobId}` : null;

  switch (n.type) {
    case 'JOB_DELETED':
      return null;
    case 'APPROVAL_REQUESTED':
    case 'APPROVAL_DECIDED':
      return jobPath ? `${jobPath}#approvals` : null;
    case 'JOB_ASSIGNED':
    case 'JOB_STATUS_CHANGED':
    case 'JOB_BLOCKED':
    case 'BLOCKER_CLEARED':
    case 'JOB_UPDATED':
    case 'NOTE_ADDED':
    case 'JOB_DEADLINE_SOON':
    case 'JOB_OVERDUE':
      return jobPath;
    case 'PROJECT_MEMBER_ADDED':
      return projectId ? `/projects/${projectId}` : null;
    case 'SCHEDULE_RUN_MISSED':
      return projectId ? `/projects/${projectId}/schedules` : null;
    case 'INVITE_ACCEPTED':
      return '/org/members';
    case 'FEEDBACK_RESOLVED':
      return '/feedback';
    case 'SUBSCRIPTION_PAST_DUE':
      return '/org/settings#billing';
  }
}

export interface NotificationContent {
  /** Who it's from: the actor's name, or "OpsClear" for system events. */
  source: string;
  /** The main line. */
  text: string;
  /** Optional secondary lines (reason, comment, new deadline, ...). */
  details: string[];
  /** Project or organisation name, when the snapshot has one. */
  context: string | null;
}

/** BCP-47 locale for Intl formatting. The app's Serbian strings are in Latin script. */
export function intlLocale(language: string): string {
  return language.startsWith('sr') ? 'sr-Latn' : 'en-GB';
}

function formatDate(iso: string | undefined, locale: string): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  return d.toLocaleDateString(locale, { day: 'numeric', month: 'short', year: 'numeric' });
}

function formatDateTime(iso: string | undefined, locale: string): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  return d.toLocaleString(locale, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

/** "5 min ago" style time, in the viewer's language. */
export function formatRelativeTime(iso: string, language: string, now = Date.now()): string {
  const diffSec = Math.round((new Date(iso).getTime() - now) / 1000);
  const rtf = new Intl.RelativeTimeFormat(intlLocale(language), { numeric: 'auto', style: 'short' });
  const abs = Math.abs(diffSec);
  if (abs < 60) return rtf.format(0, 'second');
  if (abs < 3600) return rtf.format(Math.round(diffSec / 60), 'minute');
  if (abs < 86_400) return rtf.format(Math.round(diffSec / 3600), 'hour');
  if (abs < 7 * 86_400) return rtf.format(Math.round(diffSec / 86_400), 'day');
  return formatDate(iso, intlLocale(language)) ?? '';
}

const STATUS_KEYS: Record<JobStatus, string> = {
  NEW: 'shared2:status.new',
  IN_PROGRESS: 'shared2:status.inProgress',
  BLOCKED: 'shared2:status.blocked',
  COMPLETED: 'shared2:status.completed',
};

const PRIORITY_KEYS: Record<JobPriority, string> = {
  LOW: 'shared2:priority.low',
  MEDIUM: 'shared2:priority.medium',
  HIGH: 'shared2:priority.high',
  CRITICAL: 'shared2:priority.critical',
};

const ROLE_KEYS = {
  OWNER: 'shared2:role.owner',
  ADMIN: 'shared2:role.admin',
  MEMBER: 'shared2:role.member',
} as const;

function label(id: string | undefined, title: string | undefined): string | null {
  if (id && title) return `${id} ${title}`;
  return id ?? title ?? null;
}

/**
 * Builds the text for one notification from its type and payload snapshot, in the
 * viewer's language. Every payload key may be missing, so each falls back to a generic
 * wording rather than rendering "undefined".
 */
export function buildNotificationContent(
  n: NotificationResponse,
  t: TFunction<['notifications', 'shared2']>,
  language: string,
): NotificationContent {
  const locale = intlLocale(language);
  const p = n.payload;
  const source = n.actor
    ? n.actor.name ?? p.actorName ?? t('notifications:fallback.someone')
    : t('notifications:system');

  const jobP = p as JobNotificationPayload;
  const job = label(jobP.jobFriendlyId ?? n.jobFriendlyId ?? undefined, jobP.jobTitle) ?? t('notifications:fallback.job');
  const project = jobP.projectName ?? null;
  const org = (p as { orgName?: string }).orgName ?? null;
  const context = project ?? org;
  const priority = (value: JobPriority | undefined) =>
    value && PRIORITY_KEYS[value] ? t('notifications:detail.priority', { priority: t(PRIORITY_KEYS[value]) }) : null;

  const details: (string | null | undefined)[] = [];
  let text: string;

  switch (n.type) {
    case 'APPROVAL_REQUESTED':
      text = t('notifications:text.APPROVAL_REQUESTED', { job });
      details.push(n.payload.description);
      break;
    case 'APPROVAL_DECIDED': {
      const decision = n.payload.decision;
      const key = decision === 'APPROVED' ? 'approved' : decision === 'REJECTED' ? 'rejected' : 'decided';
      text = t(`notifications:text.APPROVAL_DECIDED.${key}`, { job });
      details.push(n.payload.comment);
      break;
    }
    case 'JOB_ASSIGNED': {
      text = t('notifications:text.JOB_ASSIGNED', { job });
      const due = formatDate(n.payload.deadline, locale);
      if (due) details.push(t('notifications:detail.due', { date: due }));
      details.push(priority(n.payload.priority));
      break;
    }
    case 'JOB_STATUS_CHANGED': {
      const from = n.payload.fromStatus && STATUS_KEYS[n.payload.fromStatus];
      const to = n.payload.toStatus && STATUS_KEYS[n.payload.toStatus];
      if (from && to) {
        text = t('notifications:text.JOB_STATUS_CHANGED.fromTo', { job, from: t(from), to: t(to) });
      } else if (to) {
        text = t('notifications:text.JOB_STATUS_CHANGED.to', { job, to: t(to) });
      } else {
        text = t('notifications:text.JOB_STATUS_CHANGED.generic', { job });
      }
      break;
    }
    case 'JOB_BLOCKED':
      text = t('notifications:text.JOB_BLOCKED', { job });
      if (n.payload.blockReason) details.push(t('notifications:detail.reason', { reason: n.payload.blockReason }));
      break;
    case 'BLOCKER_CLEARED': {
      const blocker = label(n.payload.blockerJobFriendlyId, n.payload.blockerJobTitle);
      text = blocker
        ? t('notifications:text.BLOCKER_CLEARED.named', { job, blocker })
        : t('notifications:text.BLOCKER_CLEARED.generic', { job });
      break;
    }
    case 'JOB_UPDATED': {
      text = t('notifications:text.JOB_UPDATED', { job });
      const changed = n.payload.changedFields ?? [];
      if (changed.includes('deadline')) {
        // A changed deadline with no value means it was cleared.
        const due = formatDate(n.payload.deadline, locale);
        details.push(due ? t('notifications:detail.newDeadline', { date: due }) : t('notifications:detail.deadlineRemoved'));
      }
      if (changed.includes('priority')) details.push(priority(n.payload.priority));
      break;
    }
    case 'JOB_DELETED':
      text = t('notifications:text.JOB_DELETED', { job });
      break;
    case 'NOTE_ADDED':
      text = t('notifications:text.NOTE_ADDED', { job });
      break;
    case 'JOB_DEADLINE_SOON': {
      text = t('notifications:text.JOB_DEADLINE_SOON', { job });
      const due = formatDate(n.payload.deadline, locale);
      if (due) details.push(t('notifications:detail.due', { date: due }));
      break;
    }
    case 'JOB_OVERDUE': {
      text = t('notifications:text.JOB_OVERDUE', { job });
      const due = formatDate(n.payload.deadline, locale);
      if (due) details.push(t('notifications:detail.wasDue', { date: due }));
      break;
    }
    case 'PROJECT_MEMBER_ADDED': {
      const name = project ?? t('notifications:fallback.project');
      const role = n.payload.role && ROLE_KEYS[n.payload.role];
      text = role
        ? t('notifications:text.PROJECT_MEMBER_ADDED.withRole', { project: name, role: t(role) })
        : t('notifications:text.PROJECT_MEMBER_ADDED.generic', { project: name });
      break;
    }
    case 'SCHEDULE_RUN_MISSED': {
      const name = project ?? t('notifications:fallback.project');
      text = n.payload.scheduleName
        ? t('notifications:text.SCHEDULE_RUN_MISSED.named', { schedule: n.payload.scheduleName, project: name })
        : t('notifications:text.SCHEDULE_RUN_MISSED.generic', { project: name });
      const missed = formatDateTime(n.payload.missedAt, locale);
      if (missed) details.push(t('notifications:detail.missedAt', { date: missed }));
      break;
    }
    case 'INVITE_ACCEPTED':
      text = t('notifications:text.INVITE_ACCEPTED', { org: org ?? t('notifications:fallback.org') });
      if (n.payload.memberEmail) details.push(n.payload.memberEmail);
      break;
    case 'FEEDBACK_RESOLVED': {
      const title = n.payload.feedbackTitle ?? t('notifications:fallback.feedback');
      if (n.payload.outcome === 'CREDITED') {
        text = n.payload.creditAmount != null
          ? t('notifications:text.FEEDBACK_RESOLVED.creditedAmount', {
            title,
            amount: new Intl.NumberFormat('sr-RS').format(n.payload.creditAmount),
          })
          : t('notifications:text.FEEDBACK_RESOLVED.credited', { title });
      } else if (n.payload.outcome === 'DECLINED') {
        text = t('notifications:text.FEEDBACK_RESOLVED.declined', { title });
      } else {
        text = t('notifications:text.FEEDBACK_RESOLVED.generic', { title });
      }
      break;
    }
    case 'SUBSCRIPTION_PAST_DUE':
      text = t('notifications:text.SUBSCRIPTION_PAST_DUE', { org: org ?? t('notifications:fallback.org') });
      details.push(t('notifications:detail.updatePayment'));
      break;
  }

  // INVITE_ACCEPTED's actor is the new member; prefer their snapshot name if the actor has none.
  const resolvedSource = n.type === 'INVITE_ACCEPTED' && n.actor && !n.actor.name && n.payload.memberName
    ? n.payload.memberName
    : source;

  return {
    source: resolvedSource,
    text,
    details: details.filter((d): d is string => Boolean(d)),
    context,
  };
}
