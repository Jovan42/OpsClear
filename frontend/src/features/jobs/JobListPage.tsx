import { useState, useEffect, useMemo } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import Button from '../../components/Button';
import PageError from '../../components/PageError';
import ProgressBar from '../../components/ProgressBar';
import PriorityBadge from '../../components/PriorityBadge';
import Skeleton from '../../components/Skeleton';
import StatusBadge from '../../components/StatusBadge';
import NewJobModal from './NewJobModal';
import { useJobList } from './useJobs';
import { useMilestones } from './useMilestones';
import { useProject } from '../projects/useProjects';
import { useDebounce } from '../../hooks/useDebounce';
import { usePageTitle } from '../../hooks/usePageTitle';
import { usePreferences, type SortOrder, type ProgressFormat } from '../../hooks/usePreferences';
import { useFormatDeadline } from '../../hooks/useFormatDeadline';
import type { JobPriority, JobResponse, JobStatus, MilestoneResponse } from '../../types';

function JobListSkeleton() {
  return (
    <div className="border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
      <div className="bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 px-4 py-2.5 flex gap-8">
        {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-3 w-16" />)}
      </div>
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="flex gap-8 px-4 py-3 border-b border-gray-100 dark:border-gray-700 bg-white dark:bg-gray-800">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="h-4 w-24" />
          <Skeleton className="h-4 w-24" />
          <Skeleton className="h-4 w-20" />
          <Skeleton className="h-5 w-20 rounded-full" />
        </div>
      ))}
    </div>
  );
}

type Filter = 'ALL' | JobStatus;
type SortKey = 'title' | 'client' | 'assignedToName' | 'deadline' | 'status' | 'priority' | 'createdAt';
type SortDir = 'asc' | 'desc';
type ViewMode = 'flat' | 'grouped';

const PRIORITY_ORDER: Record<JobPriority, number> = {
  CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3,
};

const PRIORITY_FILTERS: { key: JobPriority | 'ALL'; label: string }[] = [
  { key: 'ALL',      label: 'All priorities' },
  { key: 'CRITICAL', label: 'Critical' },
  { key: 'HIGH',     label: 'High' },
  { key: 'MEDIUM',   label: 'Medium' },
  { key: 'LOW',      label: 'Low' },
];

const STATUS_ORDER: Record<JobStatus, number> = {
  BLOCKED: 0, IN_PROGRESS: 1, NEW: 2, COMPLETED: 3,
};

const FILTERS: { key: Filter; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'NEW', label: 'New' },
  { key: 'IN_PROGRESS', label: 'In Progress' },
  { key: 'BLOCKED', label: 'Blocked' },
  { key: 'COMPLETED', label: 'Completed' },
];

const STATUS_COLORS: Record<JobStatus, { badge: string; text: string }> = {
  NEW:         { badge: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400', text: 'text-amber-700 dark:text-amber-400' },
  IN_PROGRESS: { badge: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',   text: 'text-blue-700 dark:text-blue-400'   },
  BLOCKED:     { badge: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',       text: 'text-red-700 dark:text-red-400'     },
  COMPLETED:   { badge: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400', text: 'text-green-700 dark:text-green-400' },
};


function sortJobs(jobs: JobResponse[], sortKey: SortKey, sortDir: SortDir): JobResponse[] {
  return jobs.slice().sort((a, b) => {
    let cmp: number;
    if (sortKey === 'deadline') {
      const da = a.deadline ? new Date(a.deadline).getTime() : Infinity;
      const db = b.deadline ? new Date(b.deadline).getTime() : Infinity;
      cmp = da - db;
    } else if (sortKey === 'createdAt') {
      cmp = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
    } else if (sortKey === 'status') {
      cmp = STATUS_ORDER[a.status] - STATUS_ORDER[b.status];
    } else if (sortKey === 'priority') {
      cmp = PRIORITY_ORDER[a.priority] - PRIORITY_ORDER[b.priority];
    } else {
      const va = (a[sortKey] ?? '').toLowerCase();
      const vb = (b[sortKey] ?? '').toLowerCase();
      cmp = va < vb ? -1 : va > vb ? 1 : 0;
    }
    return sortDir === 'asc' ? cmp : -cmp;
  });
}

function sortOrderToKeyDir(order: SortOrder): { key: SortKey; dir: SortDir } {
  switch (order) {
    case 'DEADLINE_ASC':  return { key: 'deadline',   dir: 'asc'  };
    case 'DEADLINE_DESC': return { key: 'deadline',   dir: 'desc' };
    case 'PRIORITY_DESC': return { key: 'priority',   dir: 'asc'  };
    case 'CREATED_DESC':  return { key: 'createdAt',  dir: 'desc' };
  }
}

interface JobCardProps {
  job: JobResponse;
  projectId: string;
  showMilestoneChip?: boolean;
}

function JobCard({ job, projectId, showMilestoneChip = false }: JobCardProps) {
  const navigate = useNavigate();
  const formatDeadline = useFormatDeadline();
  const deadline = formatDeadline(job.deadline, job.status);
  return (
    <button
      onClick={() => navigate(`/projects/${projectId}/jobs/${job.id}`)}
      className={`w-full text-left bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors cursor-pointer ${
        job.status === 'BLOCKED' ? 'border-l-4 border-l-red-400' : ''
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm font-medium text-gray-900 dark:text-gray-100 flex-1">{job.title}</p>
        <div className="flex items-center gap-1.5 shrink-0">
          <PriorityBadge priority={job.priority} />
          <StatusBadge status={job.status} />
        </div>
      </div>
      <div className="flex flex-wrap gap-x-4 gap-y-0.5 mt-1">
        {job.client && (
          <span className="text-xs text-gray-500 dark:text-gray-400">{job.client}</span>
        )}
        {job.assignedToName && (
          <span className="text-xs text-gray-500 dark:text-gray-400">{job.assignedToName}</span>
        )}
        {job.deadline && (
          <span className={`text-xs ${deadline.overdue ? 'text-red-600 font-medium' : 'text-gray-500 dark:text-gray-400'}`}>
            {deadline.text}
          </span>
        )}
        {showMilestoneChip && job.milestoneName && (
          <span className="text-xs px-1.5 py-0.5 rounded bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
            {job.milestoneName}
          </span>
        )}
      </div>
    </button>
  );
}

interface JobRowProps {
  job: JobResponse;
  projectId: string;
  showMilestoneChip?: boolean;
}

function JobRow({ job, projectId, showMilestoneChip = false }: JobRowProps) {
  const navigate = useNavigate();
  const formatDeadline = useFormatDeadline();
  const deadline = formatDeadline(job.deadline, job.status);
  return (
    <tr
      onClick={() => navigate(`/projects/${projectId}/jobs/${job.id}`)}
      className={`bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer transition-colors ${
        job.status === 'BLOCKED' ? 'border-l-2 border-l-red-400' : ''
      }`}
    >
      <td className="px-4 py-3 font-medium text-gray-900 dark:text-gray-100">
        <div className="flex items-center gap-2 flex-wrap">
          {job.title}
          {showMilestoneChip && job.milestoneName && (
            <span className="text-xs px-1.5 py-0.5 rounded bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
              {job.milestoneName}
            </span>
          )}
        </div>
      </td>
      <td className="px-4 py-3 text-gray-500 dark:text-gray-400">{job.client ?? '—'}</td>
      <td className="px-4 py-3 text-gray-500 dark:text-gray-400">{job.assignedToName ?? '—'}</td>
      <td className={`px-4 py-3 ${deadline.overdue ? 'text-red-600 font-medium' : 'text-gray-500 dark:text-gray-400'}`}>
        {deadline.text}
      </td>
      <td className="px-4 py-3">
        <PriorityBadge priority={job.priority} />
      </td>
      <td className="px-4 py-3 whitespace-nowrap">
        <StatusBadge status={job.status} />
      </td>
    </tr>
  );
}

const TABLE_HEADERS: { key: SortKey; label: string }[] = [
  { key: 'title',          label: 'Title' },
  { key: 'client',         label: 'Client' },
  { key: 'assignedToName', label: 'Assigned to' },
  { key: 'deadline',       label: 'Deadline' },
  { key: 'priority',       label: 'Priority' },
  { key: 'status',         label: 'Status' },
];

interface GroupSectionProps {
  groupKey: string;
  title: string;
  deadline: string | null;
  jobs: JobResponse[];
  projectId: string;
  sortKey: SortKey;
  sortDir: SortDir;
  toggleSort: (key: SortKey) => void;
  collapsedGroups: Set<string>;
  toggleGroup: (key: string) => void;
  isFirst: boolean;
  completed: number;
  total: number;
  progressFormat: ProgressFormat;
}

function GroupSection({
  groupKey, title, deadline, jobs, projectId,
  sortKey, sortDir, toggleSort,
  collapsedGroups, toggleGroup,
  completed, total, progressFormat,
}: GroupSectionProps) {
  const isCollapsed = collapsedGroups.has(groupKey);
  const formatDeadline = useFormatDeadline();
  const formattedDeadline = formatDeadline(deadline);
  const showProgress = groupKey !== '__ungrouped__';

  return (
    <div className="border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
      <div
        onClick={() => toggleGroup(groupKey)}
        className="flex items-center justify-between px-4 py-2.5 bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 cursor-pointer select-none"
      >
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-medium text-sm text-gray-800 dark:text-gray-200">{title}</span>
            {deadline && (
              <span className={`text-xs ${formattedDeadline.overdue ? 'text-red-600 font-medium' : 'text-gray-500 dark:text-gray-400'}`}>
                {formattedDeadline.text}
              </span>
            )}
            <span className="text-xs font-medium px-1.5 py-0.5 rounded-full bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
              {jobs.length}
            </span>
          </div>
          {showProgress && <ProgressBar completed={completed} total={total} format={progressFormat} />}
        </div>
        <span className="text-gray-400 dark:text-gray-500 text-sm ml-2">{isCollapsed ? '▸' : '▾'}</span>
      </div>

      {!isCollapsed && jobs.length > 0 && (
        <>
          {/* Mobile cards */}
          <div className="flex flex-col gap-2 p-3 md:hidden bg-white dark:bg-gray-800">
            {jobs.map((job) => (
              <JobCard key={job.id} job={job} projectId={projectId} showMilestoneChip={false} />
            ))}
          </div>

          {/* Desktop table */}
          <div className="hidden md:block overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
                <tr>
                  {TABLE_HEADERS.map(({ key, label }) => (
                    <th
                      key={key}
                      onClick={(e) => { e.stopPropagation(); toggleSort(key); }}
                      className="text-left px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400 select-none cursor-pointer hover:text-gray-700 dark:hover:text-gray-300 whitespace-nowrap"
                    >
                      {label}
                      <span className="ml-1 inline-block w-3 text-center">
                        {sortKey === key ? (sortDir === 'asc' ? '↑' : '↓') : <span className="text-gray-300 dark:text-gray-600">↕</span>}
                      </span>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                {jobs.map((job) => (
                  <JobRow key={job.id} job={job} projectId={projectId} showMilestoneChip={false} />
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {!isCollapsed && jobs.length === 0 && (
        <p className="px-4 py-3 text-sm text-gray-400 dark:text-gray-500 italic bg-white dark:bg-gray-800">
          No jobs in this group.
        </p>
      )}
    </div>
  );
}

export default function JobListPage() {
  const { projectId = '' } = useParams();
  const { data: project } = useProject(projectId);
  usePageTitle('Jobs', project?.name);
  const { prefs } = usePreferences();
  const [searchParams, setSearchParams] = useSearchParams();
  const statusParam = searchParams.get('status');
  const filter: Filter = statusParam && FILTERS.some((f) => f.key === statusParam)
    ? (statusParam as Filter)
    : 'ALL';
  const setFilter = (key: Filter) => {
    setSearchParams({ status: key }, { replace: true });
  };

  useEffect(() => {
    if (!statusParam) {
      setSearchParams({ status: prefs.defaultStatusTab }, { replace: true });
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const initSort = sortOrderToKeyDir(prefs.defaultSortOrder);
  const [sortKey, setSortKey] = useState<SortKey>(initSort.key);
  const [sortDir, setSortDir] = useState<SortDir>(initSort.dir);
  const [modalOpen, setModalOpen] = useState(false);
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounce(search, 300);
  const [priorityFilter, setPriorityFilter] = useState<JobPriority | 'ALL'>('ALL');
  const [milestoneFilter, setMilestoneFilter] = useState<string | 'ALL'>(
    searchParams.get('milestone') ?? 'ALL',
  );
  const { data: milestones = [] } = useMilestones(projectId);

  const hasMilestones = milestones.length > 0;
  const milestoneFilterActive = milestoneFilter !== 'ALL';

  // viewMode: preference drives the default; user toggle sets an explicit override
  const [viewModeOverride, setViewModeOverride] = useState<ViewMode | null>(null);
  const viewMode: ViewMode = viewModeOverride ?? (prefs.defaultViewMode === 'GROUPED' && hasMilestones ? 'grouped' : 'flat');

  // collapsedGroups: derive from preference + user toggles (XOR: toggling flips the preference default)
  const [userToggles, setUserToggles] = useState<Set<string>>(new Set());
  const collapsedGroups = useMemo(() => {
    const defaultCollapsed = prefs.milestoneAccordionState === 'COLLAPSED';
    const allKeys = [...milestones.map((ms) => ms.id), '__ungrouped__'];
    const result = new Set<string>();
    for (const key of allKeys) {
      const isCollapsed = userToggles.has(key) ? !defaultCollapsed : defaultCollapsed;
      if (isCollapsed) result.add(key);
    }
    return result;
  }, [milestones, prefs.milestoneAccordionState, userToggles]);

  const effectiveViewMode: ViewMode = milestoneFilterActive ? 'flat' : viewMode;

  function toggleSort(key: SortKey) {
    if (sortKey === key) setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    else { setSortKey(key); setSortDir('asc'); }
  }

  function toggleGroup(key: string) {
    setUserToggles((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  const { data: jobs = [], isLoading, isError, refetch } = useJobList(
    projectId,
    debouncedSearch || undefined,
    priorityFilter !== 'ALL' ? priorityFilter : undefined,
    milestoneFilterActive ? milestoneFilter : undefined,
  );
  const { data: allJobs = [] } = useJobList(projectId);

  const counts: Record<JobStatus, number> = {
    NEW: jobs.filter((j) => j.status === 'NEW').length,
    IN_PROGRESS: jobs.filter((j) => j.status === 'IN_PROGRESS').length,
    BLOCKED: jobs.filter((j) => j.status === 'BLOCKED').length,
    COMPLETED: jobs.filter((j) => j.status === 'COMPLETED').length,
  };

  const activeJobs = prefs.hideCompletedFromAll ? jobs.filter((j) => j.status !== 'COMPLETED') : jobs;
  const filtered = filter === 'ALL' ? activeJobs : jobs.filter((j) => j.status === filter);
  const sorted = sortJobs(filtered, sortKey, sortDir);
  const allCount = activeJobs.length;

  if (isLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
        <div className="flex items-center justify-between mb-6">
          <Skeleton className="h-7 w-16" />
          <Skeleton className="h-9 w-28 rounded-lg" />
        </div>
        <JobListSkeleton />
      </div>
    );
  }

  if (isError) {
    return <PageError message="Failed to load jobs." onRetry={() => void refetch()} />;
  }

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">Jobs</h1>
        <Button onClick={() => setModalOpen(true)} disabled={project?.status === 'COMPLETED'}>+ New Job</Button>
      </div>

      {/* Search + Priority filter + Milestone filter + View toggle */}
      <div className="flex flex-wrap gap-2 mb-4">
        <input
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by title, client, or assignee…"
          className="flex-1 min-w-0 sm:max-w-sm rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent"
        />
        <select
          value={priorityFilter}
          onChange={(e) => setPriorityFilter(e.target.value as JobPriority | 'ALL')}
          className="rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:border-transparent"
        >
          {PRIORITY_FILTERS.map(({ key, label }) => (
            <option key={key} value={key}>{label}</option>
          ))}
        </select>
        {hasMilestones && effectiveViewMode === 'flat' && (
          <select
            value={milestoneFilter}
            onChange={(e) => setMilestoneFilter(e.target.value)}
            className="rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:border-transparent"
          >
            <option value="ALL">All milestones</option>
            {milestones.map((ms: MilestoneResponse) => (
              <option key={ms.id} value={ms.id}>{ms.name}</option>
            ))}
          </select>
        )}
        {hasMilestones && !milestoneFilterActive && (
          <button
            onClick={() => setViewModeOverride(viewMode === 'grouped' ? 'flat' : 'grouped')}
            className={`rounded-lg border px-3 py-2 text-sm transition-colors cursor-pointer ${
              effectiveViewMode === 'grouped'
                ? 'bg-brand text-white border-brand'
                : 'border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'
            }`}
          >
            {effectiveViewMode === 'grouped' ? 'Grouped' : 'Flat'}
          </button>
        )}
      </div>

      {/* Status filter tabs */}
      <div className="overflow-x-auto -mx-4 sm:mx-0 px-4 sm:px-0">
      <div className="flex gap-1 mb-4 border-b border-gray-200 dark:border-gray-700 min-w-max sm:min-w-0">
        {FILTERS.map(({ key, label }) => {
          const count = key === 'ALL' ? allCount : counts[key as JobStatus];
          const isActive = filter === key;
          const hasJobs = count > 0;
          const colors = key !== 'ALL' ? STATUS_COLORS[key as JobStatus] : null;
          const labelColor = !hasJobs && key !== 'ALL'
            ? 'text-gray-400 dark:text-gray-500'
            : key === 'ALL'
              ? isActive ? 'text-brand' : 'text-gray-500 dark:text-gray-400'
              : colors!.text;
          const badgeColor = !hasJobs && key !== 'ALL'
            ? 'bg-gray-100 text-gray-400 dark:bg-gray-700 dark:text-gray-500'
            : key === 'ALL'
              ? 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
              : colors!.badge;

          return (
            <button
              key={key}
              onClick={() => setFilter(key)}
              className={`flex items-center gap-1.5 px-3 py-2 text-sm font-medium border-b-2 -mb-px transition-colors cursor-pointer ${
                isActive ? 'border-brand' : 'border-transparent hover:border-gray-200 dark:hover:border-gray-700'
              } ${!hasJobs && key !== 'ALL' ? 'opacity-50' : ''}`}
            >
              <span className={labelColor}>{label}</span>
              <span className={`text-xs font-medium px-1.5 py-0.5 rounded-full ${badgeColor}`}>
                {count}
              </span>
            </button>
          );
        })}
      </div>
      </div>

      {/* Jobs table / card list */}
      {sorted.length === 0 && effectiveViewMode === 'flat' ? (
        <div className="flex flex-col items-center justify-center py-24 text-center">
          <p className="text-gray-500 dark:text-gray-400 text-sm mb-4">
            {debouncedSearch
              ? `No results for "${debouncedSearch}".`
              : jobs.length === 0
                ? 'No jobs yet.'
                : 'No jobs match this filter.'}
          </p>
          {!debouncedSearch && jobs.length === 0 && (
            <Button onClick={() => setModalOpen(true)}>Create first job</Button>
          )}
        </div>
      ) : effectiveViewMode === 'grouped' ? (
        <div className="space-y-3">
          {milestones.map((ms: MilestoneResponse) => {
            const groupJobs = sortJobs(
              filtered.filter((j) => j.milestoneId === ms.id),
              sortKey,
              sortDir,
            );
            const msAllJobs = allJobs.filter((j) => j.milestoneId === ms.id);
            return (
              <GroupSection
                key={ms.id}
                groupKey={ms.id}
                title={ms.name}
                deadline={ms.deadline}
                jobs={groupJobs}
                projectId={projectId}
                sortKey={sortKey}
                sortDir={sortDir}
                toggleSort={toggleSort}
                collapsedGroups={collapsedGroups}
                toggleGroup={toggleGroup}
                isFirst={false}
                completed={msAllJobs.filter((j) => j.status === 'COMPLETED').length}
                total={msAllJobs.length}
                progressFormat={prefs.milestoneProgressFormat}
              />
            );
          })}
          {(() => {
            const ungrouped = sortJobs(
              filtered.filter((j) => j.milestoneId === null),
              sortKey,
              sortDir,
            );
            if (ungrouped.length === 0) return null;
            return (
              <GroupSection
                key="__ungrouped__"
                groupKey="__ungrouped__"
                title="Ungrouped"
                deadline={null}
                jobs={ungrouped}
                projectId={projectId}
                sortKey={sortKey}
                sortDir={sortDir}
                toggleSort={toggleSort}
                collapsedGroups={collapsedGroups}
                toggleGroup={toggleGroup}
                isFirst={false}
                completed={0}
                total={0}
                progressFormat={prefs.milestoneProgressFormat}
              />
            );
          })()}
          {filtered.length === 0 && (
            <div className="flex flex-col items-center justify-center py-24 text-center">
              <p className="text-gray-500 dark:text-gray-400 text-sm mb-4">
                {debouncedSearch
                  ? `No results for "${debouncedSearch}".`
                  : jobs.length === 0
                    ? 'No jobs yet.'
                    : 'No jobs match this filter.'}
              </p>
              {!debouncedSearch && jobs.length === 0 && (
                <Button onClick={() => setModalOpen(true)}>Create first job</Button>
              )}
            </div>
          )}
        </div>
      ) : (
        <>
          {/* Mobile card view */}
          <div className="flex flex-col gap-2 md:hidden">
            {sorted.map((job) => (
              <JobCard key={job.id} job={job} projectId={projectId} showMilestoneChip={hasMilestones} />
            ))}
          </div>

          {/* Desktop table view */}
          <div className="hidden md:block border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
                <tr>
                  {TABLE_HEADERS.map(({ key, label }) => (
                    <th
                      key={key}
                      onClick={() => toggleSort(key)}
                      className="text-left px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400 select-none cursor-pointer hover:text-gray-700 dark:hover:text-gray-300 whitespace-nowrap"
                    >
                      {label}
                      <span className="ml-1 inline-block w-3 text-center">
                        {sortKey === key ? (sortDir === 'asc' ? '↑' : '↓') : <span className="text-gray-300 dark:text-gray-600">↕</span>}
                      </span>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                {sorted.map((job) => (
                  <JobRow key={job.id} job={job} projectId={projectId} showMilestoneChip={hasMilestones} />
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      <NewJobModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        projectId={projectId}
        milestones={milestones}
      />
    </div>
  );
}
