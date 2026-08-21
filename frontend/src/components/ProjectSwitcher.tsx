import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useProject } from '../features/projects/useProjects';
import { useProjectSwitcher } from '../features/projects/useProjectSwitcher';

/**
 * ADR-0047: turns the project breadcrumb into a recency-sorted quick switcher.
 * Preserves the current page type on switch (e.g. Milestones -> Milestones) by
 * substituting the friendlyId segment of the route, but only when the route has
 * no further child segment (a job detail id, etc. — that id belongs to the old
 * project and can't be carried over), falling back to the target project's
 * default page otherwise.
 */
export default function ProjectSwitcher({ projectId }: Readonly<{ projectId: string }>) {
  const { data: project } = useProject(projectId);
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useTranslation('shared1');
  const otherProjects = useProjectSwitcher(projectId);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  if (!project) return null;

  function selectProject(targetFriendlyId: string) {
    setOpen(false);
    const segments = location.pathname.split('/');
    // ['', 'projects', friendlyId, subpage?, ...rest] — a segment past the
    // subpage (e.g. a job id) doesn't carry over to a different project.
    const subpage = segments.length === 4 ? segments[3] : null;
    navigate(subpage ? `/projects/${targetFriendlyId}/${subpage}` : `/projects/${targetFriendlyId}`);
  }

  return (
    <div ref={ref} className="relative min-w-0">
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-1 min-w-0 text-sm font-medium text-white/80 hover:text-white transition-colors cursor-pointer"
        aria-expanded={open}
        aria-haspopup="true"
      >
        <span className="text-white/40 text-lg font-light">/</span>
        <span className="truncate max-w-[140px] sm:max-w-xs">{project.name}</span>
        <svg
          width="12"
          height="12"
          viewBox="0 0 16 16"
          fill="currentColor"
          className={`shrink-0 transition-transform ${open ? 'rotate-180' : ''}`}
        >
          <path d="M4 6l4 4 4-4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
        </svg>
      </button>

      {open && (
        <div className="absolute left-0 mt-2 w-56 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-lg py-1 z-50 max-h-80 overflow-y-auto">
          {otherProjects.length === 0 ? (
            <p className="px-4 py-2 text-sm text-gray-400 dark:text-gray-500">
              {t('projectSwitcherEmpty')}
            </p>
          ) : (
            otherProjects.map((p) =>
              p.isMember ? (
                <button
                  key={p.id}
                  onClick={() => selectProject(p.friendlyId)}
                  className="w-full text-left px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors cursor-pointer truncate"
                >
                  {p.name}
                </button>
              ) : (
                // Directory-only entry (ADR-0045/0026) — visible so an Owner/Admin
                // notices the blind-spot project exists, but not navigable: project
                // pages 403 regardless of org role without actual project membership.
                <div
                  key={p.id}
                  title={t('projectSwitcherNotAMember')}
                  className="w-full text-left px-4 py-2 text-sm text-gray-400 dark:text-gray-500 truncate cursor-not-allowed"
                >
                  {p.name}
                </div>
              ),
            )
          )}
        </div>
      )}
    </div>
  );
}
