import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import ConfirmModal from '../../components/ConfirmModal';
import Button from '../../components/Button';
import PageError from '../../components/PageError';
import Skeleton from '../../components/Skeleton';
import PriorityBadge from '../../components/PriorityBadge';
import StatusBadge from '../../components/StatusBadge';
import NewJobModal from './NewJobModal';
import BlockedBanner from './components/BlockedBanner';
import JobStatusBar from './components/JobStatusBar';
import BlockModal from './components/BlockModal';
import NoteThread from './components/NoteThread';
import ApprovalList from './components/ApprovalList';
import RequestApprovalModal from './components/RequestApprovalModal';
import { useJob, useUpdateJobStatus, useDeleteJob } from './useJobs';
import { useMilestones } from './useMilestones';
import { useProject, useProjectMembers, useProjectRole } from '../projects/useProjects';
import { useAuth } from '../../auth/AuthContext';
import { useApprovals } from './useApprovals';
import { usePageTitle } from '../../hooks/usePageTitle';
import Markdown from '../../components/Markdown';
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

  const { data: job, isLoading, isError, refetch } = useJob(projectId, jobId);
  const { data: project } = useProject(projectId);
  const { data: members = [] } = useProjectMembers(projectId);
  const { data: approvals = [] } = useApprovals(projectId, jobId);
  const { data: milestones = [] } = useMilestones(projectId);
  const role = useProjectRole(projectId);
  usePageTitle(job?.title, project?.name);
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
  const isProjectCompleted = project?.status === 'COMPLETED';
  const pendingCount = approvals.filter((a) => a.status === 'PENDING').length;

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
      <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8 space-y-6">
        <div className="flex items-center gap-3">
          <Skeleton className="h-7 w-48" />
          <Skeleton className="h-5 w-20 rounded-full" />
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="space-y-1">
              <Skeleton className="h-3 w-16" />
              <Skeleton className="h-4 w-32" />
            </div>
          ))}
        </div>
        <Skeleton className="h-24 rounded-xl" />
        <Skeleton className="h-24 rounded-xl" />
      </div>
    );
  }

  if (isError || !job) {
    return <PageError message="Failed to load job." onRetry={() => void refetch()} />;
  }

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8 space-y-6">
      {/* Header */}
      <div>
        <nav className="flex items-center gap-1.5 text-sm text-gray-500 dark:text-gray-400 mb-3">
          <Link
            to={`/projects/${projectId}/jobs`}
            className="hover:text-gray-700 dark:hover:text-gray-200 transition-colors"
          >
            Jobs
          </Link>
          <span>/</span>
          <span className="text-gray-700 dark:text-gray-200 truncate">{job.title}</span>
        </nav>

        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">{job.title}</h1>
            <PriorityBadge priority={job.priority} />
            <StatusBadge status={job.status} />
          </div>

          <div className="flex items-center gap-2 shrink-0">
            {isOwnerOrAdmin && (
              <Button variant="secondary" size="sm" onClick={() => setEditOpen(true)} disabled={isProjectCompleted}>
                Edit
              </Button>
            )}
            {isOwnerOrAdmin && (
              <div className="relative">
                <button
                  onClick={() => setKebabOpen((v) => !v)}
                  className="p-1.5 rounded-lg text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 cursor-pointer"
                >
                  ⋮
                </button>
                {kebabOpen && (
                  <>
                    <div
                      className="fixed inset-0 z-10"
                      onClick={() => setKebabOpen(false)}
                    />
                    <div className="absolute right-0 z-20 mt-1 w-40 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg overflow-hidden">
                      <button
                        onClick={() => { setKebabOpen(false); setDeleteOpen(true); }}
                        className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40 cursor-pointer"
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
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-6 py-4 space-y-3">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-3 text-sm">
          <div>
            <span className="text-gray-500 dark:text-gray-400">Client</span>
            <p className="font-medium text-gray-900 dark:text-gray-100 mt-0.5">{job.client ?? '—'}</p>
          </div>
          <div>
            <span className="text-gray-500 dark:text-gray-400">Assigned to</span>
            <p className="font-medium text-gray-900 dark:text-gray-100 mt-0.5">{job.assignedToName ?? '—'}</p>
          </div>
          <div>
            <span className="text-gray-500 dark:text-gray-400">Deadline</span>
            <p
              className={`font-medium mt-0.5 ${
                isOverdue(job.deadline, job.status) ? 'text-red-600' : 'text-gray-900 dark:text-gray-100'
              }`}
            >
              {formatDate(job.deadline)}
            </p>
          </div>
          <div>
            <span className="text-gray-500 dark:text-gray-400">Created</span>
            <p className="font-medium text-gray-900 dark:text-gray-100 mt-0.5">{formatDate(job.createdAt)}</p>
          </div>
        </div>

        {job.description && (
          <div className="pt-2 border-t border-gray-100 dark:border-gray-700">
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-1">Description</p>
            <Markdown className="text-sm text-gray-700 dark:text-gray-300">{job.description}</Markdown>
          </div>
        )}
      </div>

      {/* Action bar */}
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-6 py-4">
        <JobStatusBar
          job={job}
          role={role}
          userId={userId}
          onStatusChange={handleStatusChange}
          onBlock={() => setBlockOpen(true)}
          onRequestApproval={() => setApprovalOpen(true)}
          isPending={isStatusPending}
          projectCompleted={isProjectCompleted}
        />
      </div>

      {/* Notes accordion */}
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
        <button
          onClick={() => setNotesExpanded((v) => !v)}
          className="w-full flex items-center justify-between px-6 py-4 text-sm font-medium text-gray-900 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
        >
          <span>Notes</span>
          <span className="text-gray-400 dark:text-gray-500">{notesExpanded ? '▲' : '▼'}</span>
        </button>
        {notesExpanded && (
          <div className="px-6 pt-4 pb-4 border-t border-gray-100 dark:border-gray-700">
            <NoteThread projectId={projectId} jobId={jobId} members={members} projectCompleted={isProjectCompleted} />
          </div>
        )}
      </div>

      {/* Approvals accordion */}
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
        <button
          onClick={() => setApprovalsExpanded((v) => !v)}
          className="w-full flex items-center justify-between px-6 py-4 text-sm font-medium text-gray-900 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
        >
          <div className="flex items-center gap-2">
            <span>Approvals</span>
            {pendingCount > 0 && (
              <span className="bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400 text-xs font-medium px-1.5 py-0.5 rounded-full">
                {pendingCount} pending
              </span>
            )}
          </div>
          <span className="text-gray-400 dark:text-gray-500">{approvalsExpanded ? '▲' : '▼'}</span>
        </button>
        {approvalsExpanded && (
          <div className="px-6 pt-4 pb-4 border-t border-gray-100 dark:border-gray-700">
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
        milestones={milestones}
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
