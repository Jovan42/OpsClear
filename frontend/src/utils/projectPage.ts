import type { ProjectPage } from '../hooks/usePreferences';

/** URL segment of each "default project page" choice. */
export const PROJECT_PAGE_PATHS: Record<ProjectPage, string> = {
  DASHBOARD: 'dashboard',
  JOBS: 'jobs',
  APPROVALS: 'approvals',
};

/** `/projects/{friendlyId}/{page}`: where opening a project lands. */
export function projectPagePath(projectFriendlyId: string, page: ProjectPage): string {
  return `/projects/${projectFriendlyId}/${PROJECT_PAGE_PATHS[page]}`;
}
