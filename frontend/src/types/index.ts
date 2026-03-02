// ---- Users ----

export interface UserModel {
  id: string;
  email: string;
  name: string;
}

// ---- Projects ----

export interface ProjectResponse {
  id: string;
  name: string;
  description: string | null;
  ownerId: string;
  createdAt: string;
}

export interface ProjectMemberResponse {
  userId: string;
  projectId: string;
  role: 'OWNER' | 'ADMIN' | 'MEMBER';
  joinedAt: string;
}

// ---- Jobs ----

export type JobStatus = 'NEW' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED';

export interface JobResponse {
  id: string;
  projectId: string;
  title: string;
  client: string | null;
  responsibleUserId: string | null;
  deadline: string | null;
  status: JobStatus;
  blockedBy: string | null;
  blockedReasonId: string | null;
  blockedReason: string | null;
  blockedAt: string | null;
  createdAt: string;
}

// ---- Block Reasons ----

export interface BlockReasonResponse {
  id: string;
  projectId: string;
  reason: string;
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
