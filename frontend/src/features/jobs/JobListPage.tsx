import { useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import Button from '../../components/Button';
import StatusBadge from '../../components/StatusBadge';
import NewJobModal from './NewJobModal';
import { useJobList } from './useJobs';
import type { JobStatus } from '../../types';

type Filter = 'ALL' | JobStatus;
type SortKey = 'title' | 'client' | 'assignedToName' | 'deadline' | 'status';
type SortDir = 'asc' | 'desc';

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
  NEW:         { badge: 'bg-amber-100 text-amber-700', text: 'text-amber-700' },
  IN_PROGRESS: { badge: 'bg-blue-100 text-blue-700',  text: 'text-blue-700'  },
  BLOCKED:     { badge: 'bg-red-100 text-red-700',    text: 'text-red-700'   },
  COMPLETED:   { badge: 'bg-green-100 text-green-700',text: 'text-green-700' },
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

  function toggleSort(key: SortKey) {
    if (sortKey === key) setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    else { setSortKey(key); setSortDir('asc'); }
  }

  const { data: jobs = [], isLoading, isError } = useJobList(projectId);

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
      } else {
        const va = (a[sortKey] ?? '').toLowerCase();
        const vb = (b[sortKey] ?? '').toLowerCase();
        cmp = va < vb ? -1 : va > vb ? 1 : 0;
      }
      return sortDir === 'asc' ? cmp : -cmp;
    });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-400 text-sm">
        Loading…
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex items-center justify-center h-64 text-red-500 text-sm">
        Failed to load jobs. Please refresh.
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-semibold text-gray-900">Jobs</h1>
        <Button onClick={() => setModalOpen(true)}>+ New Job</Button>
      </div>

      {/* Status filter tabs */}
      <div className="flex gap-1 mb-4 border-b border-gray-200">
        {FILTERS.map(({ key, label }) => {
          const count = key === 'ALL' ? jobs.length : counts[key as JobStatus];
          const isActive = filter === key;
          const hasJobs = count > 0;
          const colors = key !== 'ALL' ? STATUS_COLORS[key as JobStatus] : null;
          const labelColor = !hasJobs && key !== 'ALL'
            ? 'text-gray-400'
            : key === 'ALL'
              ? isActive ? 'text-brand' : 'text-gray-500'
              : colors!.text;
          const badgeColor = !hasJobs && key !== 'ALL'
            ? 'bg-gray-100 text-gray-400'
            : key === 'ALL'
              ? 'bg-gray-100 text-gray-600'
              : colors!.badge;

          return (
            <button
              key={key}
              onClick={() => setFilter(key)}
              className={`flex items-center gap-1.5 px-3 py-2 text-sm font-medium border-b-2 -mb-px transition-colors cursor-pointer ${
                isActive ? 'border-brand' : 'border-transparent hover:border-gray-200'
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

      {/* Jobs table */}
      {filtered.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 text-center">
          <p className="text-gray-500 text-sm mb-4">
            {jobs.length === 0 ? 'No jobs yet.' : 'No jobs match this filter.'}
          </p>
          {jobs.length === 0 && (
            <Button onClick={() => setModalOpen(true)}>Create first job</Button>
          )}
        </div>
      ) : (
        <div className="border border-gray-200 rounded-xl overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                {(
                  [
                    { key: 'title',          label: 'Title' },
                    { key: 'client',         label: 'Client' },
                    { key: 'assignedToName', label: 'Assigned to' },
                    { key: 'deadline',       label: 'Deadline' },
                    { key: 'status',         label: 'Status' },
                  ] as { key: SortKey; label: string }[]
                ).map(({ key, label }) => (
                  <th
                    key={key}
                    onClick={() => toggleSort(key)}
                    className="text-left px-4 py-2.5 text-xs font-medium text-gray-500 select-none cursor-pointer hover:text-gray-700 whitespace-nowrap"
                  >
                    {label}
                    <span className="ml-1 inline-block w-3 text-center">
                      {sortKey === key ? (sortDir === 'asc' ? '↑' : '↓') : <span className="text-gray-300">↕</span>}
                    </span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filtered.map((job) => (
                <tr
                  key={job.id}
                  onClick={() => navigate(`/projects/${projectId}/jobs/${job.id}`)}
                  className={`bg-white hover:bg-gray-50 cursor-pointer transition-colors ${
                    job.status === 'BLOCKED' ? 'border-l-2 border-l-red-400' : ''
                  }`}
                >
                  <td className="px-4 py-3 font-medium text-gray-900">{job.title}</td>
                  <td className="px-4 py-3 text-gray-500">{job.client ?? '—'}</td>
                  <td className="px-4 py-3 text-gray-500">{job.assignedToName ?? '—'}</td>
                  <td
                    className={`px-4 py-3 ${
                      isOverdue(job.deadline, job.status)
                        ? 'text-red-600 font-medium'
                        : 'text-gray-500'
                    }`}
                  >
                    {formatDeadline(job.deadline)}
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge status={job.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <NewJobModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        projectId={projectId}
      />
    </div>
  );
}
