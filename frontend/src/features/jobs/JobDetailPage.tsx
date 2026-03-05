import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import ConfirmModal from '../../components/ConfirmModal';
import Button from '../../components/Button';
import StatusBadge from '../../components/StatusBadge';
import NewJobModal from './NewJobModal';
import BlockedBanner from './components/BlockedBanner';
import JobStatusBar from './components/JobStatusBar';
import BlockModal from './components/BlockModal';
import NoteThread from './components/NoteThread';
import ApprovalList from './components/ApprovalList';
import RequestApprovalModal from './components/RequestApprovalModal';
import { useJob, useUpdateJobStatus, useDeleteJob } from './useJobs';
import { useProjectMembers, useProjectRole } from '../projects/useProjects';
import { useAuth } from '../../auth/AuthContext';
import { useApprovals } from './useApprovals';
import type { JobStatus } from '../../types';

function formatDate(dateStr: string | null) {
  if (!dateStr) return '—';
  return new Date(dateStr).toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

function isOverdue(deadline: string | null, status: JobStatus) {
  if (!deadline || status === 'COMPLETED') return false;
  return new Date(deadline) < new Date();
}

export default function JobDetailPage() {
  const { projectId = '', jobId = '' } = useParams();
  const navigate = useNavigate();
  const { userId } = useAuth();

  const { data: job, isLoading, isError } = useJob(projectId, jobId);
  const { data: members = [] } = useProjectMembers(projectId);
  const { data: approvals = [] } = useApprovals(projectId, jobId);
  const role = useProjectRole(projectId);
  const { mutate: updateStatus, isPending: isStatusPending } = useUpdateJobStatus(projectId);
  const { mutate: deleteJob, isPending: isDeleting } = useDeleteJob(projectId);

  const [editOpen, setEditOpen] = useState(false);
  const [blockOpen, setBlockOpen] = useState(false);
  const [approvalOpen, setApprovalOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [notesExpanded, setNotesExpanded] = useState(true);
  const [approvalsExpanded, setApprovalsExpanded] = useState(false);
  const [kebabOpen, setKebabOpen] = useState(false);

  const isOwnerOrAdmin = role === 'OWNER' || role === 'ADMIN';
  const pendingCount = approvals.filter((a) => a.status === 'PENDING').length;

  // Expand approvals section if there are pending approvals (on first load)
  // We use a derived default; once user collapses it's controlled by state.
  // Actually we track this with state initialized based on pending count.

  function handleStatusChange(status: JobStatus) {
    if (!job) return;
    updateStatus({ jobId: job.id, status });
  }

  function handleBlock(reason: string) {
    if (!job) return;
    updateStatus({ jobId: job.id, status: 'BLOCKED', reason }, { onSuccess: () => setBlockOpen(false) });
  }

  function handleDelete() {
    if (!job) return;
    deleteJob(job.id, {
      onSuccess: () => navigate(`/projects/${projectId}/jobs`),
    });
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-400 text-sm">
        Loading…
      </div>
    );
  }

  if (isError || !job) {
    return (
      <div className="flex items-center justify-center h-64 text-red-500 text-sm">
        Failed to load job. Please refresh.
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto px-6 py-8 space-y-6">
      {/* Header */}
      <div>
        <button
          onClick={() => navigate(`/projects/${projectId}/jobs`)}
          className="text-sm text-gray-500 hover:text-gray-700 mb-3 flex items-center gap-1 cursor-pointer"
        >
          ← Back to jobs
        </button>

        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="text-xl font-semibold text-gray-900">{job.title}</h1>
            <StatusBadge status={job.status} />
          </div>

          <div className="flex items-center gap-2 shrink-0">
            {isOwnerOrAdmin && (
              <Button variant="secondary" size="sm" onClick={() => setEditOpen(true)}>
                Edit
              </Button>
            )}
            {isOwnerOrAdmin && (
              <div className="relative">
                <button
                  onClick={() => setKebabOpen((v) => !v)}
                  className="p-1.5 rounded-lg text-gray-500 hover:bg-gray-100 cursor-pointer"
                >
                  ⋮
                </button>
                {kebabOpen && (
                  <>
                    <div
                      className="fixed inset-0 z-10"
                      onClick={() => setKebabOpen(false)}
                    />
                    <div className="absolute right-0 z-20 mt-1 w-40 bg-white border border-gray-200 rounded-lg shadow-lg overflow-hidden">
                      <button
                        onClick={() => { setKebabOpen(false); setDeleteOpen(true); }}
                        className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 cursor-pointer"
                      >
                        Delete Job
                      </button>
                    </div>
                  </>
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Blocked banner */}
      <BlockedBanner job={job} />

      {/* Info section */}
      <div className="bg-white border border-gray-200 rounded-xl px-6 py-4 space-y-3">
        <div className="grid grid-cols-2 gap-x-8 gap-y-3 text-sm">
          <div>
            <span className="text-gray-500">Client</span>
            <p className="font-medium text-gray-900 mt-0.5">{job.client ?? '—'}</p>
          </div>
          <div>
            <span className="text-gray-500">Assigned to</span>
            <p className="font-medium text-gray-900 mt-0.5">{job.assignedToName ?? '—'}</p>
          </div>
          <div>
            <span className="text-gray-500">Deadline</span>
            <p
              className={`font-medium mt-0.5 ${
                isOverdue(job.deadline, job.status) ? 'text-red-600' : 'text-gray-900'
              }`}
            >
              {formatDate(job.deadline)}
            </p>
          </div>
          <div>
            <span className="text-gray-500">Created</span>
            <p className="font-medium text-gray-900 mt-0.5">{formatDate(job.createdAt)}</p>
          </div>
        </div>

        {job.description && (
          <div className="pt-2 border-t border-gray-100">
            <p className="text-sm text-gray-500 mb-1">Description</p>
            <p className="text-sm text-gray-700 whitespace-pre-wrap">{job.description}</p>
          </div>
        )}
      </div>

      {/* Action bar */}
      <div className="bg-white border border-gray-200 rounded-xl px-6 py-4">
        <JobStatusBar
          job={job}
          role={role}
          userId={userId}
          onStatusChange={handleStatusChange}
          onBlock={() => setBlockOpen(true)}
          onRequestApproval={() => setApprovalOpen(true)}
          isPending={isStatusPending}
        />
      </div>

      {/* Notes accordion */}
      <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
        <button
          onClick={() => setNotesExpanded((v) => !v)}
          className="w-full flex items-center justify-between px-6 py-4 text-sm font-medium text-gray-900 hover:bg-gray-50 cursor-pointer"
        >
          <span>Notes</span>
          <span className="text-gray-400">{notesExpanded ? '▲' : '▼'}</span>
        </button>
        {notesExpanded && (
          <div className="px-6 pt-4 pb-4 border-t border-gray-100">
            <NoteThread projectId={projectId} jobId={jobId} members={members} />
          </div>
        )}
      </div>

      {/* Approvals accordion */}
      <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
        <button
          onClick={() => setApprovalsExpanded((v) => !v)}
          className="w-full flex items-center justify-between px-6 py-4 text-sm font-medium text-gray-900 hover:bg-gray-50 cursor-pointer"
        >
          <div className="flex items-center gap-2">
            <span>Approvals</span>
            {pendingCount > 0 && (
              <span className="bg-orange-100 text-orange-700 text-xs font-medium px-1.5 py-0.5 rounded-full">
                {pendingCount} pending
              </span>
            )}
          </div>
          <span className="text-gray-400">{approvalsExpanded ? '▲' : '▼'}</span>
        </button>
        {approvalsExpanded && (
          <div className="px-6 pt-4 pb-4 border-t border-gray-100">
            <ApprovalList projectId={projectId} jobId={jobId} role={role} members={members} />
          </div>
        )}
      </div>

      {/* Modals */}
      <NewJobModal
        open={editOpen}
        onClose={() => setEditOpen(false)}
        projectId={projectId}
        job={job}
      />

      <BlockModal
        open={blockOpen}
        onClose={() => setBlockOpen(false)}
        projectId={projectId}
        onConfirm={handleBlock}
        isPending={isStatusPending}
      />

      <RequestApprovalModal
        open={approvalOpen}
        onClose={() => setApprovalOpen(false)}
        projectId={projectId}
        jobId={jobId}
      />

      <ConfirmModal
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        onConfirm={handleDelete}
        title="Delete Job"
        message={`Delete "${job.title}"? This cannot be undone.`}
        confirmLabel="Delete"
        variant="danger"
        isPending={isDeleting}
      />
    </div>
  );
}
