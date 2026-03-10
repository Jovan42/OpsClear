import { type ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { PieChart, Pie, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import PageError from '../../components/PageError';
import Skeleton from '../../components/Skeleton';
import { useDashboard } from './useDashboard';
import { useProjectRole } from '../projects/useProjects';
import type { DashboardSummary, JobSummary, PendingApprovalResponse } from '../../types';

// ---- helpers ----

function formatDate(dateStr: string | null) {
  if (!dateStr) return null;
  return new Date(dateStr).toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

function blockedFor(blockedAt: string | null) {
  if (!blockedAt) return null;
  const days = Math.floor((Date.now() - new Date(blockedAt).getTime()) / 86_400_000);
  if (days === 0) return 'today';
  if (days === 1) return '1 day ago';
  return `${days} days ago`;
}

// ---- Donut chart ----

const CHART_COLORS = {
  NEW: '#f59e0b',
  IN_PROGRESS: '#3b82f6',
  BLOCKED: '#ef4444',
  COMPLETED: '#22c55e',
};

function StatusDonut({ summary }: Readonly<{ summary: DashboardSummary }>) {
  if (summary.total === 0) return null;

  const data = [
    { name: 'New', value: summary.newCount, fill: CHART_COLORS.NEW },
    { name: 'In Progress', value: summary.inProgressCount, fill: CHART_COLORS.IN_PROGRESS },
    { name: 'Blocked', value: summary.blockedCount, fill: CHART_COLORS.BLOCKED },
    { name: 'Completed', value: summary.completedCount, fill: CHART_COLORS.COMPLETED },
  ].filter((d) => d.value > 0);

  return (
    <div className="bg-white border border-gray-200 rounded-xl p-5 flex flex-col">
      <h2 className="text-sm font-semibold text-gray-700 mb-3">Status distribution</h2>
      <div className="flex-1" style={{ minHeight: 180 }}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={data}
              cx="50%"
              cy="50%"
              innerRadius="55%"
              outerRadius="80%"
              dataKey="value"
              strokeWidth={0}
            />
            <Tooltip
              formatter={(value) => [value, '']}
              contentStyle={{ fontSize: 12, borderRadius: 6 }}
            />
            <Legend
              iconType="circle"
              iconSize={8}
              wrapperStyle={{ fontSize: 12 }}
            />
          </PieChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

// ---- Summary cards ----

interface SummaryCardProps {
  readonly label: string;
  readonly value: number;
  readonly colorClass: string;
  readonly onClick?: () => void;
}

function SummaryCard({ label, value, colorClass, onClick }: SummaryCardProps) {
  return (
    <button
      onClick={onClick}
      className={`flex flex-col items-start px-4 py-3 rounded-lg border text-left transition-colors ${
        onClick ? 'cursor-pointer hover:bg-gray-50' : 'cursor-default'
      } bg-white border-gray-200`}
    >
      <span className={`text-2xl font-bold ${colorClass}`}>{value}</span>
      <span className="text-xs text-gray-500 mt-0.5">{label}</span>
    </button>
  );
}

function SummaryCards({
  summary,
  projectId,
  isOwnerOrAdmin,
}: Readonly<{
  summary: DashboardSummary;
  projectId: string;
  isOwnerOrAdmin: boolean;
}>) {
  const navigate = useNavigate();

  const goToJobs = (status?: string) => {
    const query = status ? '?status=' + status : '';
    navigate('/projects/' + projectId + '/jobs' + query);
  };

  return (
    <div className="bg-white border border-gray-200 rounded-xl p-5">
      <h2 className="text-sm font-semibold text-gray-700 mb-3">Summary</h2>
      <div className="grid grid-cols-2 gap-2">
        <SummaryCard
          label="New"
          value={summary.newCount}
          colorClass="text-amber-600"
          onClick={() => goToJobs('NEW')}
        />
        <SummaryCard
          label="In Progress"
          value={summary.inProgressCount}
          colorClass="text-blue-600"
          onClick={() => goToJobs('IN_PROGRESS')}
        />
        <SummaryCard
          label="Blocked"
          value={summary.blockedCount}
          colorClass="text-red-600"
          onClick={() => goToJobs('BLOCKED')}
        />
        <SummaryCard
          label="Completed"
          value={summary.completedCount}
          colorClass="text-green-600"
          onClick={() => goToJobs('COMPLETED')}
        />
        <SummaryCard
          label="Overdue"
          value={summary.overdueCount}
          colorClass="text-orange-600"
        />
        {isOwnerOrAdmin && (
          <SummaryCard
            label="Pending approvals"
            value={summary.pendingApprovalsCount}
            colorClass="text-purple-600"
            onClick={() => navigate(`/projects/${projectId}/approvals`)}
          />
        )}
      </div>
    </div>
  );
}

// ---- Blocked jobs ----

function BlockedJobRow({ job, projectId }: Readonly<{ job: JobSummary; projectId: string }>) {
  const navigate = useNavigate();
  return (
    <div className="flex items-start justify-between gap-3 border border-gray-200 rounded-lg px-4 py-3 bg-white">
      <div className="min-w-0">
        <p className="text-sm font-medium text-gray-900 truncate">{job.title}</p>
        <p className="text-xs text-gray-500 mt-0.5">
          {job.assignedToName ?? 'Unassigned'}
          {job.blockedAt && <> · blocked {blockedFor(job.blockedAt)}</>}
        </p>
        {job.blockedReason && (
          <p className="text-xs text-red-600 mt-1 italic">"{job.blockedReason}"</p>
        )}
      </div>
      <button
        onClick={() => navigate(`/projects/${projectId}/jobs/${job.id}`)}
        className="shrink-0 text-xs text-brand hover:underline cursor-pointer mt-0.5"
      >
        →
      </button>
    </div>
  );
}

// ---- Overdue jobs ----

function OverdueJobRow({ job, projectId }: Readonly<{ job: JobSummary; projectId: string }>) {
  const navigate = useNavigate();
  return (
    <div className="flex items-start justify-between gap-3 border border-gray-200 rounded-lg px-4 py-3 bg-white">
      <div className="min-w-0">
        <p className="text-sm font-medium text-gray-900 truncate">{job.title}</p>
        <p className="text-xs text-gray-500 mt-0.5">
          {job.assignedToName ?? 'Unassigned'}
          {job.deadline && <> · due {formatDate(job.deadline)}</>}
        </p>
      </div>
      <button
        onClick={() => navigate(`/projects/${projectId}/jobs/${job.id}`)}
        className="shrink-0 text-xs text-brand hover:underline cursor-pointer mt-0.5"
      >
        →
      </button>
    </div>
  );
}

// ---- Pending approval row ----

function PendingApprovalRow({
  approval,
  projectId,
}: Readonly<{
  approval: PendingApprovalResponse;
  projectId: string;
}>) {
  const navigate = useNavigate();
  return (
    <div className="flex items-start justify-between gap-3 border border-gray-200 rounded-lg px-4 py-3 bg-white">
      <div className="min-w-0">
        <p className="text-sm font-medium text-gray-900 truncate">{approval.description}</p>
        <p className="text-xs text-gray-500 mt-0.5">
          {approval.jobTitle} · {formatDate(approval.requestedAt)}
        </p>
      </div>
      <button
        onClick={() => navigate(`/projects/${projectId}/jobs/${approval.jobId}`)}
        className="shrink-0 text-xs text-brand hover:underline cursor-pointer mt-0.5"
      >
        →
      </button>
    </div>
  );
}

// ---- Section wrapper ----

function Section({
  title,
  count,
  children,
  action,
}: Readonly<{
  title: string;
  count: number;
  children: ReactNode;
  action?: ReactNode;
}>) {
  return (
    <div>
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-sm font-semibold text-gray-800">
          {title}
          <span className="ml-2 text-xs font-medium px-1.5 py-0.5 rounded-full bg-gray-100 text-gray-600">
            {count}
          </span>
        </h2>
        {action}
      </div>
      <div className="space-y-2">{children}</div>
    </div>
  );
}

// ---- Page ----

export default function DashboardPage() {
  const { projectId = '' } = useParams();
  const navigate = useNavigate();
  const role = useProjectRole(projectId);
  const { data, isLoading, isError, refetch } = useDashboard(projectId);

  const isOwnerOrAdmin = role === 'OWNER' || role === 'ADMIN';

  if (isLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8 space-y-8">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Skeleton className="h-56 rounded-xl" />
          <div className="bg-white border border-gray-200 rounded-xl p-5 space-y-3">
            <Skeleton className="h-4 w-20" />
            <div className="grid grid-cols-2 gap-2">
              {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="h-16 rounded-lg" />
              ))}
            </div>
          </div>
        </div>
        <div className="space-y-2">
          <Skeleton className="h-5 w-24" />
          {Array.from({ length: 2 }).map((_, i) => <Skeleton key={i} className="h-16 rounded-lg" />)}
        </div>
      </div>
    );
  }

  if (isError || !data) {
    return <PageError message="Failed to load dashboard." onRetry={() => void refetch()} />;
  }

  const { summary, blockedJobs, overdueJobs, pendingApprovals } = data;
  const visibleApprovals = isOwnerOrAdmin ? pendingApprovals.slice(0, 5) : [];

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8 space-y-8">
      {/* Top row: chart + summary */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <StatusDonut summary={summary} />
        <SummaryCards summary={summary} projectId={projectId} isOwnerOrAdmin={isOwnerOrAdmin} />
      </div>

      {/* Blocked jobs */}
      {blockedJobs.length > 0 && (
        <Section title="Blocked" count={blockedJobs.length}>
          {blockedJobs.map((job) => (
            <BlockedJobRow key={job.id} job={job} projectId={projectId} />
          ))}
        </Section>
      )}

      {/* Overdue jobs */}
      {overdueJobs.length > 0 && (
        <Section title="Overdue" count={overdueJobs.length}>
          {overdueJobs.map((job) => (
            <OverdueJobRow key={job.id} job={job} projectId={projectId} />
          ))}
        </Section>
      )}

      {/* Pending approvals (owner/admin only) */}
      {isOwnerOrAdmin && pendingApprovals.length > 0 && (
        <Section
          title="Pending Approvals"
          count={pendingApprovals.length}
          action={
            pendingApprovals.length > 5 ? (
              <button
                onClick={() => navigate(`/projects/${projectId}/approvals`)}
                className="text-xs text-brand hover:underline cursor-pointer"
              >
                → View all
              </button>
            ) : undefined
          }
        >
          {visibleApprovals.map((approval) => (
            <PendingApprovalRow key={approval.id} approval={approval} projectId={projectId} />
          ))}
        </Section>
      )}

      {/* Empty state */}
      {blockedJobs.length === 0 && overdueJobs.length === 0 && (!isOwnerOrAdmin || pendingApprovals.length === 0) && summary.total > 0 && (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <p className="text-gray-500 text-sm">All clear — no blocked, overdue, or pending items.</p>
        </div>
      )}

      {summary.total === 0 && (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <p className="text-gray-500 text-sm">No jobs yet.</p>
          <button
            onClick={() => navigate(`/projects/${projectId}/jobs`)}
            className="mt-2 text-sm text-brand hover:underline cursor-pointer"
          >
            Create the first job →
          </button>
        </div>
      )}
    </div>
  );
}
