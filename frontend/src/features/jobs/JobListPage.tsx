import { useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import Button from '../../components/Button';
import PageError from '../../components/PageError';
import PriorityBadge from '../../components/PriorityBadge';
import Skeleton from '../../components/Skeleton';
import StatusBadge from '../../components/StatusBadge';
import NewJobModal from './NewJobModal';
import { useJobList } from './useJobs';
import { useProject } from '../projects/useProjects';
import { useDebounce } from '../../hooks/useDebounce';
import { usePageTitle } from '../../hooks/usePageTitle';
import type { JobPriority, JobStatus } from '../../types';

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
type SortKey = 'title' | 'client' | 'assignedToName' | 'deadline' | 'status' | 'priority';
type SortDir = 'asc' | 'desc';

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

function formatDeadline(deadline: string | null): string {
  if (!deadline) return '—';
  return new Date(deadline).toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

function isOverdue(deadline: string | null, status: JobStatus): boolean {
  if (!deadline || status === 'COMPLETED') return false;
  return new Date(deadline) < new Date();
}

export default function JobListPage() {
  const { projectId = '' } = useParams();
  const navigate = useNavigate();
  const { data: project } = useProject(projectId);
  usePageTitle('Jobs', project?.name);
  const [searchParams, setSearchParams] = useSearchParams();
  const statusParam = searchParams.get('status');
  const filter: Filter = statusParam && FILTERS.some((f) => f.key === statusParam)
    ? (statusParam as Filter)
    : 'ALL';
  const setFilter = (key: Filter) => {
    if (key === 'ALL') setSearchParams({}, { replace: true });
    else setSearchParams({ status: key }, { replace: true });
  };
  const [sortKey, setSortKey] = useState<SortKey>('status');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [modalOpen, setModalOpen] = useState(false);
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounce(search, 300);
  const [priorityFilter, setPriorityFilter] = useState<JobPriority | 'ALL'>('ALL');

  function toggleSort(key: SortKey) {
    if (sortKey === key) setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    else { setSortKey(key); setSortDir('asc'); }
  }

  const { data: jobs = [], isLoading, isError, refetch } = useJobList(
    projectId,
    debouncedSearch || undefined,
    priorityFilter !== 'ALL' ? priorityFilter : undefined,
  );

  const counts: Record<JobStatus, number> = {
    NEW: jobs.filter((j) => j.status === 'NEW').length,
    IN_PROGRESS: jobs.filter((j) => j.status === 'IN_PROGRESS').length,
    BLOCKED: jobs.filter((j) => j.status === 'BLOCKED').length,
    COMPLETED: jobs.filter((j) => j.status === 'COMPLETED').length,
  };

  const filtered = (filter === 'ALL' ? jobs : jobs.filter((j) => j.status === filter))
    .slice()
    .sort((a, b) => {
      let cmp: number;
      if (sortKey === 'deadline') {
        const da = a.deadline ? new Date(a.deadline).getTime() : Infinity;
        const db = b.deadline ? new Date(b.deadline).getTime() : Infinity;
        cmp = da - db;
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
        <Button onClick={() => setModalOpen(true)}>+ New Job</Button>
      </div>

      {/* Search + Priority filter */}
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
      </div>

      {/* Status filter tabs */}
      <div className="overflow-x-auto -mx-4 sm:mx-0 px-4 sm:px-0">
      <div className="flex gap-1 mb-4 border-b border-gray-200 dark:border-gray-700 min-w-max sm:min-w-0">
        {FILTERS.map(({ key, label }) => {
          const count = key === 'ALL' ? jobs.length : counts[key as JobStatus];
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
      {filtered.length === 0 ? (
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
      ) : (
        <>
          {/* Mobile card view */}
          <div className="flex flex-col gap-2 md:hidden">
            {filtered.map((job) => (
              <button
                key={job.id}
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
                    <span className={`text-xs ${isOverdue(job.deadline, job.status) ? 'text-red-600 font-medium' : 'text-gray-500 dark:text-gray-400'}`}>
                      {formatDeadline(job.deadline)}
                    </span>
                  )}
                </div>
              </button>
            ))}
          </div>

          {/* Desktop table view */}
          <div className="hidden md:block border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
                <tr>
                  {(
                    [
                      { key: 'title',          label: 'Title' },
                      { key: 'client',         label: 'Client' },
                      { key: 'assignedToName', label: 'Assigned to' },
                      { key: 'deadline',       label: 'Deadline' },
                      { key: 'priority',       label: 'Priority' },
                      { key: 'status',         label: 'Status' },
                    ] as { key: SortKey; label: string }[]
                  ).map(({ key, label }) => (
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
                {filtered.map((job) => (
                  <tr
                    key={job.id}
                    onClick={() => navigate(`/projects/${projectId}/jobs/${job.id}`)}
                    className={`bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer transition-colors ${
                      job.status === 'BLOCKED' ? 'border-l-2 border-l-red-400' : ''
                    }`}
                  >
                    <td className="px-4 py-3 font-medium text-gray-900 dark:text-gray-100">{job.title}</td>
                    <td className="px-4 py-3 text-gray-500 dark:text-gray-400">{job.client ?? '—'}</td>
                    <td className="px-4 py-3 text-gray-500 dark:text-gray-400">{job.assignedToName ?? '—'}</td>
                    <td
                      className={`px-4 py-3 ${
                        isOverdue(job.deadline, job.status)
                          ? 'text-red-600 font-medium'
                          : 'text-gray-500 dark:text-gray-400'
                      }`}
                    >
                      {formatDeadline(job.deadline)}
                    </td>
                    <td className="px-4 py-3">
                      <PriorityBadge priority={job.priority} />
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={job.status} />
                    </td>
                  </tr>
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
      />
    </div>
  );
}
