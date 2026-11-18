import type { LandingProject, ProjectPage } from '../../hooks/usePreferences';
import { projectPagePath } from '../../utils/projectPage';

export const PROJECT_LIST_PATH = '/projects';

/** The project the landing preference points at, as an id from a URL or a UUID; null for the list. */
export function landingProjectRef(
  landingProject: LandingProject,
  landingProjectId: string | null,
  mostRecentProject: string | null,
): string | null {
  if (landingProject === 'LAST') return mostRecentProject;
  if (landingProject === 'SPECIFIC') return landingProjectId;
  return null;
}

/**
 * ADR-0061: where the app lands after login when there's no saved deep link. `ref` is from
 * {@link landingProjectRef}; `projects` are the user's active projects. A project that isn't
 * among them (deleted, archived, no longer a member) falls back to the project list.
 */
export function landingPath(
  ref: string | null,
  projects: readonly { id: string; friendlyId: string }[],
  defaultProjectPage: ProjectPage,
): string {
  if (!ref) return PROJECT_LIST_PATH;
  const wanted = ref.toLowerCase();
  const project = projects.find((p) => p.id.toLowerCase() === wanted || p.friendlyId.toLowerCase() === wanted);
  return project ? projectPagePath(project.friendlyId, defaultProjectPage) : PROJECT_LIST_PATH;
}
