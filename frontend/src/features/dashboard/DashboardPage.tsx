import { type ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { usePreferences } from '../../hooks/usePreferences';
import { useFormatDeadline } from '../../hooks/useFormatDeadline';
import { PieChart, Pie, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { CheckCircle2, ListTodo } from 'lucide-react';
import EmptyState from '../../components/EmptyState';
import Markdown from '../../components/Markdown';
import PageError from '../../components/PageError';
import Skeleton from '../../components/Skeleton';
import UpgradeCard from '../../components/UpgradeCard';
import { useDashboard } from './useDashboard';
import { useProject, useProjectRole } from '../projects/useProjects';
import { usePageTitle } from '../../hooks/usePageTitle';
import { useCurrentOrg } from '../org/OrgContext';
import { JOB_TYPE_HEX } from '../../utils/jobTypeColors';
import i18n from '../../i18n';
import type { DashboardSummary, JobSummary, JobTypeBreakdown, PendingApprovalResponse } from '../../types';

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
  if (days === 0) return i18n.t('approvalsDashboardSettingsLanding:dashboard.today');
  if (days === 1) return i18n.t('approvalsDashboardSettingsLanding:dashboard.oneDayAgo');
  return i18n.t('approvalsDashboardSettingsLanding:dashboard.daysAgo', { count: days });
}

// ---- Donut chart ----

const CHART_COLORS = {
  NEW: '#f59e0b',
  IN_PROGRESS: '#3b82f6',
  BLOCKED: '#ef4444',
  COMPLETED: '#22c55e',
};

function StatusDonut({ summary }: Readonly<{ summary: DashboardSummary }>) {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  if (summary.total === 0) return null;

  const data = [
    { name: t('dashboard.statusLabels.new'), value: summary.newCount, fill: CHART_COLORS.NEW },
    { name: t('dashboard.statusLabels.inProgress'), value: summary.inProgressCount, fill: CHART_COLORS.IN_PROGRESS },
    { name: t('dashboard.statusLabels.blocked'), value: summary.blockedCount, fill: CHART_COLORS.BLOCKED },
    { name: t('dashboard.statusLabels.completed'), value: summary.completedCount, fill: CHART_COLORS.COMPLETED },
  ].filter((d) => d.value > 0);

  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5 flex flex-col">
      <h2 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">{t('dashboard.statusDistribution')}</h2>
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
        onClick ? 'cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700' : 'cursor-default'
      } bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700`}
    >
      <span className={`text-2xl font-bold ${colorClass}`}>{value}</span>
      <span className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{label}</span>
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
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const navigate = useNavigate();

  const goToJobs = (status?: string) => {
    const query = status ? '?status=' + status : '';
    navigate('/projects/' + projectId + '/jobs' + query);
  };

  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5">
      <h2 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">{t('dashboard.summaryHeading')}</h2>
      <div className="grid grid-cols-2 gap-2">
        <SummaryCard
          label={t('dashboard.statusLabels.new')}
          value={summary.newCount}
          colorClass="text-amber-600"
          onClick={() => goToJobs('NEW')}
        />
        <SummaryCard
          label={t('dashboard.statusLabels.inProgress')}
          value={summary.inProgressCount}
          colorClass="text-blue-600"
          onClick={() => goToJobs('IN_PROGRESS')}
        />
        <SummaryCard
          label={t('dashboard.statusLabels.blocked')}
          value={summary.blockedCount}
          colorClass="text-red-600"
          onClick={() => goToJobs('BLOCKED')}
        />
        <SummaryCard
          label={t('dashboard.statusLabels.completed')}
          value={summary.completedCount}
          colorClass="text-green-600"
          onClick={() => goToJobs('COMPLETED')}
        />
        <SummaryCard
          label={t('dashboard.statusLabels.overdue')}
          value={summary.overdueCount}
          colorClass="text-orange-600"
        />
        {isOwnerOrAdmin && (
          <SummaryCard
            label={t('dashboard.statusLabels.pendingApprovals')}
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
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const navigate = useNavigate();
  return (
    <div className="flex items-start justify-between gap-3 border border-gray-200 dark:border-gray-700 rounded-lg px-4 py-3 bg-white dark:bg-gray-800">
      <div className="min-w-0">
        <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">{job.title}</p>
        <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
          {job.assignedToName ?? t('dashboard.unassigned')}
          {job.blockedAt && <> {t('dashboard.blockedSuffix', { time: blockedFor(job.blockedAt) })}</>}
        </p>
        {job.blockedReason && (
          <p className="text-xs text-red-600 dark:text-red-400 mt-1 italic">"{job.blockedReason}"</p>
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
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const navigate = useNavigate();
  const formatDeadline = useFormatDeadline();
  return (
    <div className="flex items-start justify-between gap-3 border border-gray-200 dark:border-gray-700 rounded-lg px-4 py-3 bg-white dark:bg-gray-800">
      <div className="min-w-0">
        <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">{job.title}</p>
        <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
          {job.assignedToName ?? t('dashboard.unassigned')}
          {job.deadline && <> {t('dashboard.dueSuffix', { time: formatDeadline(job.deadline, job.status).text })}</>}
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
    <div className="flex items-start justify-between gap-3 border border-gray-200 dark:border-gray-700 rounded-lg px-4 py-3 bg-white dark:bg-gray-800">
      <div className="min-w-0">
        <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">{approval.description}</p>
        <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
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

// ---- Type breakdown ----

function TypeBreakdownWidget({ breakdown }: Readonly<{ breakdown: JobTypeBreakdown[] }>) {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  if (breakdown.length === 0) return null;
  const maxCount = Math.max(...breakdown.map((b) => b.count));

  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5">
      <h2 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">{t('dashboard.typeBreakdownHeading')}</h2>
      <div className="space-y-2.5">
        {breakdown.map((b) => (
          <div key={b.typeId} className="flex items-center gap-3">
            <span className="text-xs text-gray-600 dark:text-gray-300 w-24 truncate shrink-0">{b.typeName}</span>
            <div className="flex-1 h-2 rounded-full bg-gray-100 dark:bg-gray-700 overflow-hidden">
              <div
                className="h-full rounded-full"
                style={{ width: `${(b.count / maxCount) * 100}%`, backgroundColor: JOB_TYPE_HEX[b.typeColor] }}
              />
            </div>
            <span className="text-xs font-medium text-gray-500 dark:text-gray-400 w-6 text-right shrink-0">{b.count}</span>
          </div>
        ))}
      </div>
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
        <h2 className="text-sm font-semibold text-gray-800 dark:text-gray-200">
          {title}
          <span className="ml-2 text-xs font-medium px-1.5 py-0.5 rounded-full bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300">
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
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const { projectFriendlyId: projectId = '' } = useParams();
  const navigate = useNavigate();
  const { data: project } = useProject(projectId);
  const role = useProjectRole(projectId);
  const { data, isLoading, isError, refetch } = useDashboard(projectId);
  usePageTitle(t('dashboard.pageTitle'), project?.name);
  const { prefs } = usePreferences();
  const { hasAddon } = useCurrentOrg();

  const isOwnerOrAdmin = role === 'OWNER' || role === 'ADMIN';

  if (!hasAddon('DASHBOARD')) return <UpgradeCard featureName={t('dashboard.featureName')} />;

  if (isLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8 space-y-8">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Skeleton className="h-56 rounded-xl" />
          <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5 space-y-3">
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
    return <PageError message={t('dashboard.loadError')} onRetry={() => void refetch()} />;
  }

  const { summary, blockedJobs, overdueJobs, pendingApprovals, typeBreakdown } = data;
  const visibleApprovals = isOwnerOrAdmin ? pendingApprovals.slice(0, 5) : [];

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8 space-y-8">
      {/* Completed banner */}
      {project?.status === 'COMPLETED' && (
        <div className="flex items-center gap-3 px-4 py-3 rounded-xl bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 text-sm text-green-800 dark:text-green-300">
          <svg xmlns="http://www.w3.org/2000/svg" className="w-4 h-4 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
            <polyline points="22 4 12 14.01 9 11.01" />
          </svg>
          {t('dashboard.completedBanner')}
        </div>
      )}

      {/* Project description */}
      {project?.description && (
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-6 py-4">
          <Markdown className="text-sm text-gray-700 dark:text-gray-300">{project.description}</Markdown>
        </div>
      )}

      {/* Top row: chart + summary */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <StatusDonut summary={summary} />
        <SummaryCards summary={summary} projectId={projectId} isOwnerOrAdmin={isOwnerOrAdmin} />
      </div>

      {/* Type breakdown — hidden entirely when the project has no types defined */}
      <TypeBreakdownWidget breakdown={typeBreakdown} />

      {/* Blocked jobs */}
      {prefs.showBlockedSection && blockedJobs.length > 0 && (
        <Section title={t('dashboard.sections.blocked')} count={blockedJobs.length}>
          {blockedJobs.map((job) => (
            <BlockedJobRow key={job.id} job={job} projectId={projectId} />
          ))}
        </Section>
      )}

      {/* Overdue jobs */}
      {prefs.showOverdueSection && overdueJobs.length > 0 && (
        <Section title={t('dashboard.sections.overdue')} count={overdueJobs.length}>
          {overdueJobs.map((job) => (
            <OverdueJobRow key={job.id} job={job} projectId={projectId} />
          ))}
        </Section>
      )}

      {/* Pending approvals (owner/admin only) */}
      {prefs.showPendingApprovalsSection && isOwnerOrAdmin && pendingApprovals.length > 0 && (
        <Section
          title={t('dashboard.sections.pendingApprovals')}
          count={pendingApprovals.length}
          action={
            pendingApprovals.length > 5 ? (
              <button
                onClick={() => navigate(`/projects/${projectId}/approvals`)}
                className="text-xs text-brand hover:underline cursor-pointer"
              >
                {t('dashboard.viewAll')}
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
      {(!prefs.showBlockedSection || blockedJobs.length === 0) &&
        (!prefs.showOverdueSection || overdueJobs.length === 0) &&
        (!prefs.showPendingApprovalsSection || !isOwnerOrAdmin || pendingApprovals.length === 0) &&
        summary.total > 0 && (
        <EmptyState icon={CheckCircle2} message={t('dashboard.allClear')} />
      )}

      {summary.total === 0 && (
        <EmptyState
          icon={ListTodo}
          message={t('dashboard.noJobs')}
          action={{ label: t('dashboard.createFirstJob'), onClick: () => navigate(`/projects/${projectId}/jobs`) }}
        />
      )}
    </div>
  );
}
