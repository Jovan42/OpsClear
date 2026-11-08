import { useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { BarChart3 } from 'lucide-react';
import EmptyState from '../../components/EmptyState';
import PageError from '../../components/PageError';
import RefreshButton from '../../components/RefreshButton';
import Skeleton from '../../components/Skeleton';
import UpgradeCard from '../../components/UpgradeCard';
import { useProject, useProjectMembers, useProjectRole } from '../projects/useProjects';
import { usePageTitle } from '../../hooks/usePageTitle';
import { useCurrentOrg } from '../org/OrgContext';
import { useProjectStats } from './useProjectStats';
import { formatAvg, formatLateRate } from './statsFormat';
import StatsMembersTable from './StatsMembersTable';
import type { StatsBucket, StatsMember, StatsResponse, StatsSummary, StatsWeeks } from '../../types';

type StatsView = 'team' | 'members';

const RANGES: StatsWeeks[] = [4, 12, 26];
const DEFAULT_RANGE: StatsWeeks = 12;
/** Same green the Dashboard's donut uses for Completed. */
const COMPLETED_COLOR = '#22c55e';

/** `weekStart` is a plain ISO date (the week's Monday in the viewer's zone), so parse it as a local date. */
function formatWeek(weekStart: string, locale: string): string {
  const [y, m, d] = weekStart.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(locale, { day: 'numeric', month: 'short' });
}

// ---- Segmented controls (range, Team / By member) ----

function Segmented<T extends string | number>({
  label,
  options,
  value,
  onChange,
}: Readonly<{ label: string; options: { value: T; label: string }[]; value: T; onChange: (v: T) => void }>) {
  return (
    <div
      role="group"
      aria-label={label}
      className="inline-flex rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-0.5"
    >
      {options.map((o) => {
        const active = o.value === value;
        return (
          <button
            key={o.value}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(o.value)}
            className={`px-3 py-1 text-xs font-medium rounded-md cursor-pointer transition-colors ${
              active
                ? 'bg-brand text-white'
                : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700'
            }`}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}

function RangeSelector({ value, onChange }: Readonly<{ value: StatsWeeks; onChange: (w: StatsWeeks) => void }>) {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  return (
    <Segmented
      label={t('stats.rangeLabel')}
      options={RANGES.map((w) => ({ value: w, label: t(`stats.range.${w}`) }))}
      value={value}
      onChange={onChange}
    />
  );
}

function ViewToggle({ value, onChange }: Readonly<{ value: StatsView; onChange: (v: StatsView) => void }>) {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  return (
    <Segmented
      label={t('stats.view.label')}
      options={[
        { value: 'team' as const, label: t('stats.view.team') },
        { value: 'members' as const, label: t('stats.view.members') },
      ]}
      value={value}
      onChange={onChange}
    />
  );
}

/** "Showing: <name> · Back to team", shown while the page is drilled down to one member. */
function MemberFilterIndicator({ name, onBack }: Readonly<{ name: string; onBack: () => void }>) {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  return (
    <div
      data-testid="stats-member-filter"
      className="inline-flex flex-wrap items-center gap-x-2 gap-y-1 rounded-lg border border-brand/30 bg-brand-light/40 dark:bg-brand/10 px-3 py-1.5 text-sm"
    >
      <span className="text-gray-800 dark:text-gray-200">{t('stats.members.showing', { name })}</span>
      <span aria-hidden="true" className="text-gray-400">·</span>
      <button
        type="button"
        onClick={onBack}
        className="font-medium text-brand hover:underline cursor-pointer"
      >
        {t('stats.members.backToTeam')}
      </button>
    </div>
  );
}

// ---- Summary tiles ----

function Tile({ label, value, detail }: Readonly<{ label: string; value: string; detail?: string }>) {
  return (
    <div
      data-testid="stats-tile"
      className="flex flex-col px-4 py-3 rounded-lg border bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700"
    >
      <span className="text-2xl font-bold text-gray-900 dark:text-gray-100">{value}</span>
      <span className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{label}</span>
      {detail && <span className="text-xs text-gray-400 dark:text-gray-500 mt-0.5">{detail}</span>}
    </div>
  );
}

function SummaryTiles({ summary }: Readonly<{ summary: StatsSummary }>) {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const lateRate = formatLateRate(summary.lateRate);
  const lateDetail = summary.completedWithDeadline > 0
    ? t('stats.tiles.lateDetail', { late: summary.completedLate, total: summary.completedWithDeadline })
    : undefined;

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2">
      <Tile label={t('stats.tiles.completed')} value={String(summary.completedCount)} />
      <Tile label={t('stats.tiles.avgLeadTime')} value={formatAvg(summary.avgLeadTimeSeconds)} />
      <Tile label={t('stats.tiles.avgCycleTime')} value={formatAvg(summary.avgCycleTimeSeconds)} />
      <Tile label={t('stats.tiles.avgBlocked')} value={formatAvg(summary.avgBlockedSeconds)} />
      <Tile label={t('stats.tiles.lateRate')} value={lateRate} detail={lateDetail} />
    </div>
  );
}

// ---- Weekly chart ----

function WeeklyChart({ buckets }: Readonly<{ buckets: StatsBucket[] }>) {
  const { t, i18n } = useTranslation('approvalsDashboardSettingsLanding');
  const data = buckets.map((b) => ({ week: formatWeek(b.weekStart, i18n.language), completed: b.completedCount }));

  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5">
      <h2 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">{t('stats.chartHeading')}</h2>
      <div data-testid="stats-chart" data-weeks={buckets.length} style={{ height: 240 }}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: -24 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e5e7eb" strokeOpacity={0.5} />
            <XAxis dataKey="week" tick={{ fontSize: 11, fill: '#9ca3af' }} tickLine={false} axisLine={false} minTickGap={8} />
            <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#9ca3af' }} tickLine={false} axisLine={false} />
            <Tooltip
              cursor={{ fill: 'rgba(156, 163, 175, 0.15)' }}
              formatter={(value) => [value, t('stats.chartCompleted')]}
              labelFormatter={(label) => t('stats.chartWeekOf', { date: String(label) })}
              contentStyle={{ fontSize: 12, borderRadius: 6 }}
            />
            <Bar dataKey="completed" fill={COMPLETED_COLOR} radius={[4, 4, 0, 0]} maxBarSize={40} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

// ---- Page ----

function StatsSkeleton() {
  return (
    <>
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-20 rounded-lg" />
        ))}
      </div>
      <Skeleton className="h-72 rounded-xl" />
    </>
  );
}

export default function StatsPage() {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const { projectFriendlyId: projectId = '' } = useParams();
  const { data: project } = useProject(projectId);
  const { data: projectMembers, isLoading: membersLoading } = useProjectMembers(projectId);
  const role = useProjectRole(projectId);
  const { hasAddon } = useCurrentOrg();
  const queryClient = useQueryClient();
  const [weeks, setWeeks] = useState<StatsWeeks>(DEFAULT_RANGE);

  // View and drill-down live in the URL (?view=members&member=<id>) so refresh and back keep them.
  // Only Owner/Admin get the breakdown (ADR-0050); a Member's stale link just shows their own stats.
  const [searchParams, setSearchParams] = useSearchParams();
  const canBreakdown = role === 'OWNER' || role === 'ADMIN';
  const memberParam = searchParams.get('member');
  const memberId = canBreakdown && memberParam ? memberParam : undefined;
  const view: StatsView = canBreakdown && (memberId || searchParams.get('view') === 'members') ? 'members' : 'team';

  // Hold a drill-down link until the role is known, so it doesn't load the team totals first.
  const { data, isPending, isError, isFetching, dataUpdatedAt, refetch } = useProjectStats(
    projectId,
    weeks,
    memberId,
    !(memberParam && membersLoading),
  );
  usePageTitle(t('stats.pageTitle'), project?.name);

  const selectMember = (m: StatsMember) => setSearchParams({ view: 'members', member: m.userId });
  const backToTeam = () => setSearchParams({});
  const changeView = (v: StatsView) => setSearchParams(v === 'members' ? { view: 'members' } : {});

  /**
   * The filtered response has no `members`, so the name comes from the project members, or, for a
   * former member who still shows up as an assignee, from any cached team response of this project.
   */
  function memberName(id: string): string {
    const current = projectMembers?.find((m) => m.userId === id)?.userName;
    if (current) return current;
    for (const [, cached] of queryClient.getQueriesData<StatsResponse>({ queryKey: ['projects', projectId, 'stats'] })) {
      const name = cached?.members?.find((m) => m.userId === id)?.name;
      if (name) return name;
    }
    return t('stats.members.formerMember');
  }

  if (!hasAddon('DASHBOARD')) return <UpgradeCard featureName={t('stats.featureName')} />;

  // A Member's stats are already scoped to their own jobs by the backend (ADR-0050).
  // No title until the role is known, so a Member never briefly sees "Team stats".
  let title: string | null = null;
  if (role === 'MEMBER') title = t('stats.yourTitle');
  else if (role) title = t('stats.teamTitle');

  let content;
  // isPending, not isLoading: a held drill-down request is pending without fetching.
  if (isPending) {
    content = <StatsSkeleton />;
  } else if (isError || !data) {
    content = <PageError message={t('stats.loadError')} onRetry={() => void refetch()} />;
  } else {
    content = (
      <>
        <SummaryTiles summary={data.summary} />
        {view === 'members' && !memberId && (
          // `members` is null on a filtered response kept as placeholder while the team one loads.
          data.members
            ? <StatsMembersTable members={data.members} onSelect={selectMember} />
            : <Skeleton className="h-40 rounded-xl" />
        )}
        <WeeklyChart buckets={data.buckets} />
        {data.summary.completedCount === 0 && (
          <EmptyState icon={BarChart3} message={t('stats.empty')} description={t('stats.emptyDescription')} className="py-8" />
        )}
      </>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8 space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-lg font-semibold text-gray-900 dark:text-gray-100 min-h-7">{title}</h1>
        <div className="flex items-center gap-3">
          <RangeSelector value={weeks} onChange={setWeeks} />
          {data && <RefreshButton lastUpdated={dataUpdatedAt} isFetching={isFetching} onRefresh={() => void refetch()} />}
        </div>
      </div>
      {canBreakdown && (
        memberId
          ? <MemberFilterIndicator name={memberName(memberId)} onBack={backToTeam} />
          : <ViewToggle value={view} onChange={changeView} />
      )}
      {content}
    </div>
  );
}
