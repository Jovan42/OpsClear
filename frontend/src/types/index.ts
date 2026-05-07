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
  relationships: JobRelationshipView[];
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
  | 'RECURRING_SCHEDULING';


export interface SubscriptionTierResponse {
  id: string;
  maxMembers: number;
  maxProjects: number | null;
  priceMonthly: number;
  priceAnnual: number;
  currency: string;
  displayOrder: number;
}

export interface SubscriptionAddonResponse {
  id: string;
  key: string;
  name: string;
  priceMonthly: number;
  priceAnnual: number;
  available: boolean;
  displayOrder: number;
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

export interface DashboardResponse {
  summary: DashboardSummary;
  blockedJobs: JobSummary[];
  overdueJobs: JobSummary[];
  pendingApprovals: PendingApprovalResponse[];
}
