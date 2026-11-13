// ---- Projects ----

export type ProjectStatus = 'ACTIVE' | 'COMPLETED';

export interface ProjectResponse {
  id: string;
  friendlyId: string;
  name: string;
  description: string | null;
  ownerId: string;
  ownerName: string | null;
  status: ProjectStatus;
  createdAt: string;
  links: LinkResponse[];
  /** ADR-0052: Members' completions need Owner/Admin approval. Enforced only while the
   *  org's APPROVALS add-on is usable (not past due). */
  requireCompletionApproval: boolean;
}

/** ADR-0052: body of PATCH /api/projects/{id}/settings. A missing field is left unchanged. */
export interface UpdateProjectSettingsRequest {
  requireCompletionApproval?: boolean;
}

// ADR-0045: org-wide project directory (Owner/Admin only), regardless of the
// viewer's own membership — deliberately excludes job/content data.
export interface ProjectDirectoryEntryResponse {
  id: string;
  friendlyId: string;
  name: string;
  ownerId: string;
  ownerName: string | null;
  status: ProjectStatus;
  memberCount: number;
}

export interface ProjectMemberResponse {
  id: string;
  userId: string;
  projectId: string;
  userName: string;
  userEmail: string;
  role: 'OWNER' | 'ADMIN' | 'MEMBER';
  joinedAt: string;
}

export interface UserSearchResponse {
  id: string;
  name: string;
  email: string;
}

// ---- Jobs ----

export type JobStatus = 'NEW' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED';

export type JobPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type JobRelationshipType = 'BLOCKED_BY' | 'RELATED_TO' | 'DUPLICATES';

export type JobRelationshipDirection = 'OUTGOING' | 'INCOMING';

export interface JobRef {
  id: string;
  friendlyId: string | null;
  title: string | null;
  status: JobStatus | null;
}

export interface JobRelationshipView {
  id: string;
  type: JobRelationshipType;
  direction: JobRelationshipDirection;
  job: JobRef;
}

export interface JobResponse {
  id: string;
  friendlyId: string;
  projectId: string;
  title: string;
  description: string | null;
  client: string | null;
  assignedTo: string | null;
  assignedToName: string | null;
  deadline: string | null;
  status: JobStatus;
  priority: JobPriority;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  blockedBy: string | null;
  blockedReason: string | null;
  blockedAt: string | null;
  milestoneId: string | null;
  milestoneName: string | null;
  typeId: string | null;
  typeName: string | null;
  typeColor: JobTypeColor | null;
  relationships: JobRelationshipView[];
  links: LinkResponse[];
  sourceScheduleId: string | null;
}

// ---- Job Types ----

export type JobTypeColor =
  | 'RED' | 'ORANGE' | 'AMBER' | 'GREEN' | 'TEAL'
  | 'BLUE' | 'INDIGO' | 'PURPLE' | 'PINK' | 'GRAY';

export interface JobTypeResponse {
  id: string;
  projectId: string;
  name: string;
  color: JobTypeColor;
  displayOrder: number;
  createdAt: string;
}

// ---- Links ----

export interface LinkResponse {
  id: string;
  url: string;
  label: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

// ---- Job History ----

export interface JobHistoryEntry {
  id: string;
  jobId: string;
  changedFrom: string | null;
  changedTo: string;
  changedBy: string | null;
  changedByName: string | null;
  changedAt: string;
  blockReason: string | null;
}

// ---- Milestones ----

export interface MilestoneResponse {
  id: string;
  friendlyId: string;
  projectId: string;
  name: string;
  description: string | null;
  deadline: string | null;
  createdAt: string;
}

// ---- Notes ----

export interface NoteResponse {
  id: string;
  jobId: string;
  authorId: string;
  content: string;
  createdAt: string;
}

// ---- Approvals ----

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ApprovalResponse {
  id: string;
  jobId: string;
  requesterId: string;
  approverId: string | null;
  description: string;
  status: ApprovalStatus;
  comment: string | null;
  requestedAt: string;
  decidedAt: string | null;
}

export interface PendingApprovalResponse {
  id: string;
  jobId: string;
  jobTitle: string;
  requesterId: string;
  description: string;
  requestedAt: string;
  // Only populated by the cross-project GET /api/approvals/pending endpoint
  // (JOB-190/ADR-0046) — undefined on the per-project pending list, whose
  // caller already knows which project/job it's looking at.
  jobFriendlyId?: string;
  projectId?: string;
  projectFriendlyId?: string;
  projectName?: string;
}

// ---- Organisations ----

export type OrgRole = 'OWNER' | 'ADMIN' | 'MEMBER';

export interface OrganisationResponse {
  id: string;
  name: string;
  slug: string;
  createdBy: string;
  createdByName: string | null;
  createdAt: string;
}

export interface OrgMemberResponse {
  organisationId: string;
  userId: string;
  userName: string;
  userEmail: string;
  role: OrgRole;
  joinedAt: string;
}

export interface OrgInviteResponse {
  id: string;
  organisationId: string;
  email: string;
  invitedBy: string;
  invitedByName: string | null;
  expiresAt: string;
  createdAt: string;
}

// ---- Subscriptions ----

export type AddonCode =
  | 'DASHBOARD'
  | 'APPROVALS'
  | 'NOTES'
  | 'JOB_STATUS_HISTORY'
  | 'MILESTONES'
  | 'JOB_RELATIONSHIPS'
  | 'API_KEYS'
  | 'JOB_TEMPLATES'
  | 'RECURRING_SCHEDULING'
  | 'JOB_LINKS'
  | 'JOB_TYPES';


export interface SubscriptionTierResponse {
  id: string;
  maxMembers: number;
  maxProjects: number | null;
  priceMonthly: number;
  priceAnnual: number;
  currency: string;
  displayOrder: number;
  paddlePriceIdMonthly: string | null;
  paddlePriceIdAnnual: string | null;
}

export interface SubscriptionAddonResponse {
  id: string;
  key: string;
  name: string;
  priceMonthly: number;
  priceAnnual: number;
  available: boolean;
  displayOrder: number;
  paddlePriceIdMonthly: string | null;
  paddlePriceIdAnnual: string | null;
}

export interface CatalogResponse {
  tiers: SubscriptionTierResponse[];
  addons: SubscriptionAddonResponse[];
}

export interface OrgSubscriptionResponse {
  id: string;
  billingCycle: 'MONTHLY' | 'ANNUAL';
  internal: boolean;
  updatedAt: string;
  tier: SubscriptionTierResponse;
  addons: SubscriptionAddonResponse[];
  totalMonthly: number;
  paddleSubscriptionId: string | null;
  subscriptionStatus: 'ACTIVE' | 'PAST_DUE' | 'CANCELED' | null;
  paddleScheduledCancellationAt: string | null;
  pendingTierId: string | null;
  pendingAddonIds: string[];
  paddlePendingDowngradeEffectiveAt: string | null;
}

// ---- Paddle billing ----

export interface InitiateSubscriptionResponse {
  orgId: string;
  paddleCustomerId: string;
}

export interface PaddleSubscriptionResponse {
  orgId: string;
  paddleSubscriptionId: string;
  status: string;
}

export interface UpdatePaymentMethodTransactionResponse {
  transactionId: string;
}

export interface PaddleBillingTransactionResponse {
  id: string;
  status: string;
  billedAt: string | null;
  currency: string | null;
  totalAmount: number | null;
}

export interface PreviewSubscriptionUpdateResponse {
  upgrade: boolean;
  immediateChargeAmount: number | null;
  /** Only set when Paddle's preview actually applied a discount (e.g. an unconsumed
   *  credit) to this charge — already netted into immediateChargeAmount, this is just
   *  how much of that reduction came from the credit specifically. */
  creditApplied: number | null;
  currency: string | null;
  effectiveAt: string | null;
}

// ---- API Keys ----

export interface ApiKeyResponse {
  id: string;
  name: string;
  keyPrefix: string;
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
  revokedAt: string | null;
}

export interface CreateApiKeyResponse {
  id: string;
  name: string;
  key: string;
  keyPrefix: string;
  createdAt: string;
  expiresAt: string | null;
}

// ---- Feedback + credits ----

export type FeedbackType = 'BUG' | 'FEATURE' | 'OTHER';
export type FeedbackStatus = 'PENDING' | 'DECLINED' | 'CREDITED';

export interface FeedbackSubmissionResponse {
  id: string;
  orgId: string;
  orgName: string;
  submittedBy: string;
  submitterName: string;
  submitterEmail: string;
  type: FeedbackType;
  title: string;
  description: string;
  status: FeedbackStatus;
  createdAt: string;
}

export interface CreditBalanceResponse {
  orgId: string;
  balance: number;
}

export interface CreditLedgerEntryResponse {
  id: string;
  orgId: string;
  amount: number;
  reason: string;
  submissionId: string | null;
  grantedBy: string;
  grantedByName: string | null;
  createdAt: string;
  /** Only ever set on the response to POST /credits/grant, when the Paddle sync was
   *  skipped for that grant — currently only NO_PADDLE_SUBSCRIPTION (the org has no
   *  real, webhook-confirmed Paddle subscription yet to attach a discount to). A
   *  genuine Paddle failure rolls the grant back instead (502) rather than reaching
   *  this field. */
  paddleSyncSkippedReason: string | null;
}

// ---- Job Templates ----

export type AssigneeMode = 'NONE' | 'FIXED' | 'ASK';

export interface JobTemplateResponse {
  id: string;
  friendlyId: string | null;
  projectId: string | null;
  orgId: string | null;
  scope: 'PROJECT' | 'ORG';
  name: string;
  title: string | null;
  description: string | null;
  client: string | null;
  priority: JobPriority | null;
  assigneeMode: AssigneeMode;
  assigneeId: string | null;
  assigneeName: string | null;
  milestoneId: string | null;
  milestoneName: string | null;
  defaultTypeId: string | null;
  defaultTypeName: string | null;
  deadlineOffsetDays: number | null;
  occurrenceCount: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

// ---- Recurring Schedules ----

export interface ScheduleAssigneeResponse {
  userId: string;
  userName: string;
  order: number;
}

export type ScheduleStatus = 'ACTIVE' | 'PAUSED' | 'PAUSED_NO_ASSIGNEES' | 'EXPIRED';

export interface RecurringScheduleResponse {
  id: string;
  projectId: string;
  templateId: string;
  templateName: string | null;
  name: string;
  cronExpression: string;
  timezone: string;
  pausedUntil: string | null;
  expiresAt: string | null;
  currentRotationIndex: number;
  nextRunAt: string;
  lastRunAt: string | null;
  assignees: ScheduleAssigneeResponse[];
  status: ScheduleStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CronPreviewResponse {
  nextRuns: string[];
}

export interface ScheduleMissedRunResponse {
  id: string;
  scheduleId: string;
  expectedAt: string;
  recordedAt: string;
}

// ---- Dashboard ----

export interface JobSummary {
  id: string;
  title: string;
  client: string | null;
  assignedTo: string | null;
  assignedToName: string | null;
  deadline: string | null;
  status: JobStatus;
  blockedReason: string | null;
  blockedAt: string | null;
  blockedBy: string | null;
}

export interface DashboardSummary {
  total: number;
  newCount: number;
  inProgressCount: number;
  blockedCount: number;
  completedCount: number;
  overdueCount: number;
  pendingApprovalsCount: number;
}

export interface JobTypeBreakdown {
  typeId: string;
  typeName: string;
  typeColor: JobTypeColor;
  count: number;
}

export interface DashboardResponse {
  summary: DashboardSummary;
  blockedJobs: JobSummary[];
  overdueJobs: JobSummary[];
  pendingApprovals: PendingApprovalResponse[];
  typeBreakdown: JobTypeBreakdown[];
}

// ---- Stats (ADR-0050) ----

export type StatsWeeks = 4 | 12 | 26;

/** Averages are null when no job in the set has the value; lateRate is null when no job has a deadline. */
export interface StatsSummary {
  completedCount: number;
  avgLeadTimeSeconds: number | null;
  avgCycleTimeSeconds: number | null;
  avgBlockedSeconds: number | null;
  completedWithDeadline: number;
  completedLate: number;
  lateRate: number | null;
}

export interface StatsBucket {
  /** Monday of the ISO week, as an ISO date (YYYY-MM-DD). */
  weekStart: string;
  completedCount: number;
  avgLeadTimeSeconds: number | null;
  avgCycleTimeSeconds: number | null;
  avgBlockedSeconds: number | null;
  completedWithDeadline: number;
  completedLate: number;
}

export interface StatsMember {
  userId: string;
  name: string;
  completedCount: number;
  avgLeadTimeSeconds: number | null;
  avgCycleTimeSeconds: number | null;
  avgBlockedSeconds: number | null;
  completedWithDeadline: number;
  completedLate: number;
  lateRate: number | null;
}

export interface StatsResponse {
  weeks: StatsWeeks;
  timezone: string;
  memberId: string | null;
  summary: StatsSummary;
  /** Exactly `weeks` entries, oldest first; the last one is the current (partial) week. */
  buckets: StatsBucket[];
  /** Only for Owner/Admin without a memberId filter; null otherwise. */
  members: StatsMember[] | null;
}

// ---- Notifications (ADR-0051) ----

/** The 16 notification types; mirrors the backend `NotificationType` enum. */
export type NotificationType =
  | 'APPROVAL_REQUESTED'
  | 'APPROVAL_DECIDED'
  | 'JOB_ASSIGNED'
  | 'JOB_STATUS_CHANGED'
  | 'JOB_BLOCKED'
  | 'BLOCKER_CLEARED'
  | 'JOB_UPDATED'
  | 'JOB_DELETED'
  | 'NOTE_ADDED'
  | 'JOB_DEADLINE_SOON'
  | 'JOB_OVERDUE'
  | 'PROJECT_MEMBER_ADDED'
  | 'SCHEDULE_RUN_MISSED'
  | 'INVITE_ACCEPTED'
  | 'FEEDBACK_RESOLVED'
  | 'SUBSCRIPTION_PAST_DUE';

// Payload snapshots, keys from the backend `NotificationPayload`. The backend leaves out
// null values, so every key is optional and the renderer must handle any of them missing.

/** Shared by every job-related type. */
export interface JobNotificationPayload {
  jobFriendlyId?: string;
  jobTitle?: string;
  projectFriendlyId?: string;
  projectName?: string;
  /** Absent for system events (shown as "OpsClear"). */
  actorName?: string;
}

/** Project-level types without a job. */
export interface ProjectNotificationPayload {
  projectFriendlyId?: string;
  projectName?: string;
  actorName?: string;
}

/** Organisation-level types (no project). */
export interface OrgNotificationPayload {
  orgName?: string;
  actorName?: string;
}

export interface ApprovalRequestedPayload extends JobNotificationPayload {
  description?: string;
}

export interface ApprovalDecidedPayload extends JobNotificationPayload {
  decision?: 'APPROVED' | 'REJECTED';
  comment?: string;
}

export interface JobAssignedPayload extends JobNotificationPayload {
  /** ISO-8601 instant; also set when the same edit changed the deadline. */
  deadline?: string;
  priority?: JobPriority;
}

export interface JobStatusChangedPayload extends JobNotificationPayload {
  fromStatus?: JobStatus;
  toStatus?: JobStatus;
}

export interface JobBlockedPayload extends JobNotificationPayload {
  blockReason?: string;
}

export interface BlockerClearedPayload extends JobNotificationPayload {
  blockerJobFriendlyId?: string;
  blockerJobTitle?: string;
}

export type JobUpdatedField = 'deadline' | 'priority';

export interface JobUpdatedPayload extends JobNotificationPayload {
  changedFields?: JobUpdatedField[];
  /** New deadline. Missing while `changedFields` includes `deadline` means it was cleared. */
  deadline?: string;
  priority?: JobPriority;
}

export interface JobDeadlinePayload extends JobNotificationPayload {
  deadline?: string;
}

export interface ProjectMemberAddedPayload extends ProjectNotificationPayload {
  role?: 'OWNER' | 'ADMIN' | 'MEMBER';
}

export interface ScheduleRunMissedPayload extends ProjectNotificationPayload {
  scheduleName?: string;
  /** ISO-8601 instant of the missed run. */
  missedAt?: string;
}

export interface InviteAcceptedPayload extends OrgNotificationPayload {
  memberName?: string;
  memberEmail?: string;
}

export interface FeedbackResolvedPayload extends OrgNotificationPayload {
  feedbackTitle?: string;
  outcome?: 'CREDITED' | 'DECLINED';
  /** Only when credited. */
  creditAmount?: number;
}

export interface NotificationPayloadMap {
  APPROVAL_REQUESTED: ApprovalRequestedPayload;
  APPROVAL_DECIDED: ApprovalDecidedPayload;
  JOB_ASSIGNED: JobAssignedPayload;
  JOB_STATUS_CHANGED: JobStatusChangedPayload;
  JOB_BLOCKED: JobBlockedPayload;
  BLOCKER_CLEARED: BlockerClearedPayload;
  JOB_UPDATED: JobUpdatedPayload;
  JOB_DELETED: JobNotificationPayload;
  NOTE_ADDED: JobNotificationPayload;
  JOB_DEADLINE_SOON: JobDeadlinePayload;
  JOB_OVERDUE: JobDeadlinePayload;
  PROJECT_MEMBER_ADDED: ProjectMemberAddedPayload;
  SCHEDULE_RUN_MISSED: ScheduleRunMissedPayload;
  INVITE_ACCEPTED: InviteAcceptedPayload;
  FEEDBACK_RESOLVED: FeedbackResolvedPayload;
  SUBSCRIPTION_PAST_DUE: OrgNotificationPayload;
}

interface NotificationBase {
  id: string;
  read: boolean;
  createdAt: string;
  /** Null for system events. */
  actor: { id: string; name: string | null } | null;
  /** Null for organisation-level types; never assume a project. */
  projectFriendlyId: string | null;
  jobFriendlyId: string | null;
  approvalId: string | null;
}

/** Discriminated on `type`, so narrowing it types the payload. */
export type NotificationResponse = {
  [K in NotificationType]: NotificationBase & { type: K; payload: NotificationPayloadMap[K] };
}[NotificationType];

export interface NotificationPageResponse {
  items: NotificationResponse[];
  /** Zero-based. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** The caller's muted notification types (`/api/user/notification-settings`). Server-stored,
 *  so it applies on every device. Never contains `SUBSCRIPTION_PAST_DUE` (the backend 400s). */
export interface NotificationSettings {
  muted: NotificationType[];
}

export interface NotificationListFilters {
  unread: boolean;
  type: NotificationType | null;
}
