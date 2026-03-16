import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import Button from '../../components/Button';
import PageError from '../../components/PageError';
import Skeleton from '../../components/Skeleton';
import NewProjectModal from './NewProjectModal';
import { useProjectList } from './useProjects';
import { usePageTitle } from '../../hooks/usePageTitle';
import type { ProjectStatus } from '../../types';

type FilterTab = ProjectStatus | 'ALL';

const TAB_COLORS: Record<FilterTab, { text: string; badge: string }> = {
  ACTIVE:    { text: 'text-blue-700 dark:text-blue-400',  badge: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'  },
  COMPLETED: { text: 'text-green-700 dark:text-green-400', badge: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' },
  ALL:       { text: 'text-gray-600 dark:text-gray-300',  badge: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'     },
};

const TABS: { value: FilterTab; label: string }[] = [
  { value: 'ACTIVE',    label: 'Active'     },
  { value: 'COMPLETED', label: 'Completed'  },
  { value: 'ALL',       label: 'All'        },
];

function ProjectListSkeleton() {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5 space-y-2">
          <Skeleton className="h-4 w-3/4" />
          <Skeleton className="h-3 w-full" />
          <Skeleton className="h-3 w-2/3" />
        </div>
      ))}
    </div>
  );
}

export default function ProjectListPage() {
  const [modalOpen, setModalOpen] = useState(false);
  const [filter, setFilter] = useState<FilterTab>('ACTIVE');
  const { userId } = useAuth();
  const navigate = useNavigate();
  usePageTitle('Projects');

  // Always load all projects and filter client-side so counts stay accurate across tabs
  const { data: allProjects = [], isLoading, isError, refetch } = useProjectList('ALL');

  const counts: Record<FilterTab, number> = {
    ACTIVE:    allProjects.filter((p) => p.status === 'ACTIVE').length,
    COMPLETED: allProjects.filter((p) => p.status === 'COMPLETED').length,
    ALL:       allProjects.length,
  };

  const projects = filter === 'ALL'
    ? allProjects
    : allProjects.filter((p) => p.status === filter);

  if (isLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
        <div className="flex items-center justify-between mb-6">
          <Skeleton className="h-7 w-24" />
          <Skeleton className="h-9 w-32 rounded-lg" />
        </div>
        <ProjectListSkeleton />
      </div>
    );
  }

  if (isError) {
    return <PageError message="Failed to load projects." onRetry={() => void refetch()} />;
  }

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">Projects</h1>
        <Button onClick={() => setModalOpen(true)}>+ New Project</Button>
      </div>

      {/* Status filter tabs */}
      <div className="flex gap-1 mb-6 border-b border-gray-200 dark:border-gray-700">
        {TABS.map(({ value, label }) => {
          const count = counts[value];
          const isActive = filter === value;
          const colors = TAB_COLORS[value];
          return (
            <button
              key={value}
              onClick={() => setFilter(value)}
              className={`flex items-center gap-1.5 px-3 py-2 text-sm font-medium border-b-2 -mb-px transition-colors cursor-pointer ${
                isActive ? 'border-brand' : 'border-transparent hover:border-gray-200 dark:hover:border-gray-700'
              }`}
            >
              <span className={colors.text}>{label}</span>
              <span className={`text-xs font-medium px-1.5 py-0.5 rounded-full ${colors.badge}`}>
                {count}
              </span>
            </button>
          );
        })}
      </div>

      {projects.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 text-center">
          <p className="text-gray-500 dark:text-gray-400 text-sm mb-4">
            {allProjects.length === 0 ? 'No projects yet.' : 'No projects match this filter.'}
          </p>
          {allProjects.length === 0 && (
            <Button onClick={() => setModalOpen(true)}>Create your first project</Button>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {projects.map((project) => {
            const isOwner = project.ownerId === userId;
            const isCompleted = project.status === 'COMPLETED';
            return (
              <div
                key={project.id}
                className={`relative text-left border rounded-xl p-5 hover:shadow-sm transition-all ${
                  isCompleted
                    ? 'bg-gray-50 dark:bg-gray-800/50 border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
                    : 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
                }`}
              >
                <button
                  onClick={() => navigate(`/projects/${project.id}/dashboard`)}
                  className="w-full text-left cursor-pointer"
                >
                  <div className="flex items-start justify-between gap-2 mb-2">
                    <h2 className={`font-semibold text-sm leading-snug ${isCompleted ? 'text-gray-500 dark:text-gray-400' : 'text-gray-900 dark:text-gray-100'}`}>
                      {project.name}
                    </h2>
                    <div className="flex items-center gap-1.5 shrink-0">
                      {isCompleted && (
                        <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400">
                          Completed
                        </span>
                      )}
                      {isOwner && !isCompleted && (
                        <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-brand-light text-brand dark:bg-green-900/40 dark:text-green-300">
                          Owner
                        </span>
                      )}
                    </div>
                  </div>
                </button>
                <button
                  onClick={() => navigate(`/projects/${project.id}/settings`)}
                  className="absolute bottom-3 right-3 p-1 text-gray-300 dark:text-gray-600 hover:text-gray-500 dark:hover:text-gray-400 transition-colors cursor-pointer"
                  title="Project settings"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="12" cy="12" r="3" />
                    <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
                  </svg>
                </button>
              </div>
            );
          })}
        </div>
      )}

      <NewProjectModal open={modalOpen} onClose={() => setModalOpen(false)} />
    </div>
  );
}
