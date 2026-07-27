import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
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
import RelationshipsSection from './components/RelationshipsSection';
import AddRelationshipModal from './components/AddRelationshipModal';
import StatusHistory from './components/StatusHistory';
import LinksSection from './components/LinksSection';
import { useJob, useUpdateJobStatus, useDeleteJob, useJobHistory } from './useJobs';
import { useNotes } from './useNotes';
import { useSchedule } from '../schedules/useSchedules';
import { useMilestones } from './useMilestones';
import { useProject, useProjectMembers, useProjectRole } from '../projects/useProjects';
import { useAuth } from '../../auth/AuthContext';
import { useApprovals } from './useApprovals';
import { usePageTitle } from '../../hooks/usePageTitle';
import { useFormatDeadline } from '../../hooks/useFormatDeadline';
import Markdown from '../../components/Markdown';
import LockedSectionRow from '../../components/LockedSectionRow';
import { useCurrentOrg } from '../org/OrgContext';
import type { JobStatus } from '../../types';

function formatDate(dateStr: string | null) {
  if (!dateStr) return '—';
  return new Date(dateStr).toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

export default function JobDetailPage() {
  const { t } = useTranslation(['jobsPages', 'common']);
  const { projectFriendlyId: projectId = '', jobFriendlyId: jobId = '' } = useParams();
  const navigate = useNavigate();
  const { userId } = useAuth();

  const { data: job, isLoading, isError, refetch } = useJob(projectId, jobId);
  const { data: project } = useProject(projectId);
  const { data: members = [] } = useProjectMembers(projectId);
  const { data: approvals = [], isLoading: approvalsLoading } = useApprovals(projectId, jobId);
  const { data: notes = [], isLoading: notesLoading } = useNotes(projectId, jobId);
  const { data: history = [], isLoading: historyLoading } = useJobHistory(projectId, jobId);
  const { data: milestones = [] } = useMilestones(projectId);
  const role = useProjectRole(projectId);
  usePageTitle(job?.title, project?.name);
  const formatDeadline = useFormatDeadline();
  const { mutate: updateStatus, isPending: isStatusPending } = useUpdateJobStatus(projectId);
  const { mutate: deleteJob, isPending: isDeleting } = useDeleteJob(projectId);

  const [editOpen, setEditOpen] = useState(false);
  const [blockOpen, setBlockOpen] = useState(false);
  const [approvalOpen, setApprovalOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [notesExpanded, setNotesExpanded] = useState(false);
  const [approvalsExpanded, setApprovalsExpanded] = useState(false);
  const [linksExpanded, setLinksExpanded] = useState(false);
  const [kebabOpen, setKebabOpen] = useState(false);
  const [addRelOpen, setAddRelOpen] = useState(false);

  const { hasAddon } = useCurrentOrg();
  const isOwnerOrAdmin = role === 'OWNER' || role === 'ADMIN';

  const { data: sourceSchedule } = useSchedule(projectId, job?.sourceScheduleId ?? null);
  const isProjectCompleted = project?.status === 'COMPLETED';
  const pendingCount = approvals.filter((a) => a.status === 'PENDING').length;

  // Sections open by default once they already have content, stay collapsed when
  // empty — can't be a useState initializer since notes/approvals/history each load
  // independently of the job itself and of each other; this fires once each finishes
  // loading rather than relying on data that may not exist yet at first render.
  useEffect(() => {
    if (!notesLoading) setNotesExpanded(notes.length > 0);
    // Deliberately only depends on notesLoading, not notes.length — this should
    // fire once when notes first finish loading, not every time the count changes
    // (e.g. adding a note), which would override a manual collapse.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [notesLoading]);
  useEffect(() => {
    if (!approvalsLoading) setApprovalsExpanded(approvals.length > 0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [approvalsLoading]);
  useEffect(() => {
    // Depends on the job's own isLoading flag (stable after the first successful
    // load — TanStack Query v5 doesn't flip it back to true on background
    // refetches), not the `job` object itself, which gets a new reference on every
    // refetch — using `job` directly would silently re-collapse/re-expand this on
    // every unrelated mutation, overriding a manual toggle.
    if (!isLoading && job) setLinksExpanded(job.links.length > 0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoading]);

  const lockedSections = [
    !hasAddon('NOTES') && t('jobDetailPage.notesSection'),
    !hasAddon('APPROVALS') && t('jobDetailPage.approvalsSection'),
    !hasAddon('JOB_STATUS_HISTORY') && t('jobDetailPage.jobHistorySection'),
    !hasAddon('JOB_RELATIONSHIPS') && t('jobDetailPage.relationshipsSection'),
    !hasAddon('JOB_LINKS') && t('jobDetailPage.linksSection'),
  ].filter((s): s is string => Boolean(s));

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

  // historyLoading is included here (not just isLoading/useJob) so StatusHistory
  // mounts for the first time only once its data is ready — its defaultExpanded
  // prop is only read on mount, so mounting it early with a not-yet-loaded history
  // would permanently lock it collapsed even once entries arrive a moment later.
  if (isLoading || historyLoading) {
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
    return <PageError message={t('jobDetailPage.failedToLoad')} onRetry={() => void refetch()} />;
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
            {t('jobDetailPage.jobsBreadcrumb')}
          </Link>
          <span>/</span>
          <span className="text-gray-700 dark:text-gray-200 truncate">{job.title}</span>
        </nav>

        <div className="flex items-start justify-between gap-4">
          <div className="flex flex-col gap-1.5">
            <div className="flex items-center gap-3 flex-wrap">
              <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">{job.title}</h1>
              <PriorityBadge priority={job.priority} />
              <StatusBadge status={job.status} />
            </div>
            {sourceSchedule && (
              <div className="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
                <svg className="w-3.5 h-3.5 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                {t('jobDetailPage.createdBySchedule')}{' '}
                <Link
                  to={`/projects/${projectId}/schedules`}
                  className="font-medium text-[var(--brand)] hover:underline"
                >
                  {sourceSchedule.name}
                </Link>
              </div>
            )}
          </div>

          <div className="flex items-center gap-2 shrink-0">
            {isOwnerOrAdmin && (
              <Button variant="secondary" size="sm" onClick={() => setEditOpen(true)} disabled={isProjectCompleted}>
                {t('jobDetailPage.editButton')}
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
                        {t('jobDetailPage.deleteJobButton')}
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
            <span className="text-gray-500 dark:text-gray-400">{t('clientLabel')}</span>
            <p className="font-medium text-gray-900 dark:text-gray-100 mt-0.5">{job.client ?? '—'}</p>
          </div>
          <div>
            <span className="text-gray-500 dark:text-gray-400">{t('assignedToLabel')}</span>
            <p className="font-medium text-gray-900 dark:text-gray-100 mt-0.5">{job.assignedToName ?? '—'}</p>
          </div>
          <div>
            <span className="text-gray-500 dark:text-gray-400">{t('deadlineLabel')}</span>
            {(() => {
              const d = formatDeadline(job.deadline, job.status);
              return (
                <p className={`font-medium mt-0.5 ${d.overdue ? 'text-red-600' : 'text-gray-900 dark:text-gray-100'}`}>
                  {d.text}
                </p>
              );
            })()}
          </div>
          <div>
            <span className="text-gray-500 dark:text-gray-400">{t('jobDetailPage.createdLabel')}</span>
            <p className="font-medium text-gray-900 dark:text-gray-100 mt-0.5">{formatDate(job.createdAt)}</p>
          </div>
        </div>

        {job.description && (
          <div className="pt-2 border-t border-gray-100 dark:border-gray-700">
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-1">{t('descriptionLabel')}</p>
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

      {/* Relationships */}
      {hasAddon('JOB_RELATIONSHIPS') && (
        <RelationshipsSection
          projectId={projectId}
          jobId={jobId}
          relationships={job.relationships}
          canManage={isOwnerOrAdmin}
          onAdd={() => setAddRelOpen(true)}
          projectCompleted={isProjectCompleted}
          defaultExpanded={job.relationships.length > 0}
        />
      )}

      {/* Notes accordion */}
      {hasAddon('NOTES') && (
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
          <button
            onClick={() => setNotesExpanded((v) => !v)}
            className="w-full flex items-center justify-between px-6 py-4 text-sm font-medium text-gray-900 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
          >
            <span>{t('jobDetailPage.notesSection')}</span>
            <span className="text-gray-400 dark:text-gray-500">{notesExpanded ? '▲' : '▼'}</span>
          </button>
          {notesExpanded && (
            <div className="px-6 pt-4 pb-4 border-t border-gray-100 dark:border-gray-700">
              <NoteThread projectId={projectId} jobId={jobId} members={members} projectCompleted={isProjectCompleted} />
            </div>
          )}
        </div>
      )}

      {/* Approvals accordion */}
      {hasAddon('APPROVALS') && (
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
          <button
            onClick={() => setApprovalsExpanded((v) => !v)}
            className="w-full flex items-center justify-between px-6 py-4 text-sm font-medium text-gray-900 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
          >
            <div className="flex items-center gap-2">
              <span>{t('jobDetailPage.approvalsSection')}</span>
              {pendingCount > 0 && (
                <span className="bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400 text-xs font-medium px-1.5 py-0.5 rounded-full">
                  {t('jobDetailPage.pendingCount', { count: pendingCount })}
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
      )}

      {/* Links accordion */}
      {hasAddon('JOB_LINKS') && (
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
          <button
            onClick={() => setLinksExpanded((v) => !v)}
            className="w-full flex items-center justify-between px-6 py-4 text-sm font-medium text-gray-900 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
          >
            <span>{t('jobDetailPage.linksSection')}</span>
            <span className="text-gray-400 dark:text-gray-500">{linksExpanded ? '▲' : '▼'}</span>
          </button>
          {linksExpanded && (
            <div className="px-6 pt-4 pb-4 border-t border-gray-100 dark:border-gray-700">
              <LinksSection
                projectId={projectId}
                jobId={jobId}
                links={job.links}
                members={members}
                canManage={isOwnerOrAdmin}
                projectCompleted={isProjectCompleted}
              />
            </div>
          )}
        </div>
      )}

      {/* Status history — kept last: the least frequently acted-on section */}
      {hasAddon('JOB_STATUS_HISTORY') && (
        <StatusHistory projectId={projectId} jobId={jobId} defaultExpanded={history.length > 0} />
      )}

      {/* Collapsed locked sections row */}
      <LockedSectionRow sections={lockedSections} />

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
        title={t('jobDetailPage.deleteJobButton')}
        message={t('jobDetailPage.deleteJobConfirmMessage', { title: job.title })}
        confirmLabel={t('common:delete')}
        variant="danger"
        isPending={isDeleting}
      />

      <AddRelationshipModal
        open={addRelOpen}
        onClose={() => setAddRelOpen(false)}
        projectId={projectId}
        jobId={jobId}
      />
    </div>
  );
}
