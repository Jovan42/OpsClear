import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import Button from '../../components/Button';
import NewProjectModal from './NewProjectModal';
import { useProjectList } from './useProjects';

export default function ProjectListPage() {
  const [modalOpen, setModalOpen] = useState(false);
  const { userId } = useAuth();
  const navigate = useNavigate();
  const { data: projects, isLoading, isError } = useProjectList();

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-400 text-sm">
        Loading projects…
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex items-center justify-center h-64 text-red-500 text-sm">
        Failed to load projects. Please refresh.
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-semibold text-gray-900">Projects</h1>
        <Button onClick={() => setModalOpen(true)}>+ New Project</Button>
      </div>

      {projects?.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 text-center">
          <p className="text-gray-500 text-sm mb-4">No projects yet.</p>
          <Button onClick={() => setModalOpen(true)}>Create your first project</Button>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {projects?.map((project) => {
            const isOwner = project.ownerId === userId;
            return (
              <div
                key={project.id}
                className="relative text-left bg-white border border-gray-200 rounded-xl p-5 hover:border-gray-300 hover:shadow-sm transition-all"
              >
                <button
                  onClick={() => navigate(`/projects/${project.id}/jobs`)}
                  className="w-full text-left cursor-pointer"
                >
                  <div className="flex items-start justify-between gap-2 mb-2">
                    <h2 className="font-semibold text-gray-900 text-sm leading-snug">
                      {project.name}
                    </h2>
                    {isOwner && (
                      <span className="shrink-0 text-xs font-medium px-2 py-0.5 rounded-full bg-brand-light text-brand">
                        Owner
                      </span>
                    )}
                  </div>
                  {project.description && (
                    <p className="text-xs text-gray-500 line-clamp-2 leading-relaxed">
                      {project.description}
                    </p>
                  )}
                </button>
                <button
                  onClick={() => navigate(`/projects/${project.id}/settings`)}
                  className="absolute bottom-3 right-3 p-1 text-gray-300 hover:text-gray-500 transition-colors cursor-pointer"
                  title="Project settings"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="12" cy="12" r="3" />
                    <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
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
