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
  paddleCustomerId: string | null;
  paddleSubscriptionId: string | null;
  subscriptionStatus: 'ACTIVE' | 'PAST_DUE' | 'CANCELED' | null;
  paddleScheduledCancellationAt: string | null;
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

export interface PreviewSubscriptionUpdateResponse {
  upgrade: boolean;
  immediateChargeAmount: number | null;
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
