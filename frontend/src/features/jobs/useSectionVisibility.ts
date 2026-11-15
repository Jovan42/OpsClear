// ADR-0053: per-project job-detail section visibility. The single place that decides whether an
// optional job-detail section is shown, so the job page, its locked row and the data hooks can't
// drift apart.

import { useCurrentOrg } from '../org/OrgContext';
import type { AddonCode, JobSection, ProjectResponse } from '../../types';

/** The add-on each section needs. */
export const SECTION_ADDON: Record<JobSection, AddonCode> = {
  NOTES: 'NOTES',
  APPROVALS: 'APPROVALS',
  RELATIONSHIPS: 'JOB_RELATIONSHIPS',
  LINKS: 'JOB_LINKS',
  STATUS_HISTORY: 'JOB_STATUS_HISTORY',
};

export interface SectionVisibility {
  /** Shown: the org has the section's add-on and the project doesn't hide it. */
  isVisible: (section: JobSection) => boolean;
  /** In the "upgrade" row: the org lacks the add-on and the project doesn't hide it. */
  isLocked: (section: JobSection) => boolean;
}

/**
 * The pure rule behind `useSectionVisibility`. While the project isn't loaded yet nothing is
 * visible or locked, so a section the project hides never flashes in or fetches its data.
 */
export function sectionVisibility(
  hasAddon: (key: AddonCode) => boolean,
  project: Pick<ProjectResponse, 'hiddenJobSections'> | undefined,
): SectionVisibility {
  const known = project !== undefined;
  // `?.` on the list too: a backend from before ADR-0053 doesn't send it (treat as nothing hidden)
  const hidden = (section: JobSection) => !!project?.hiddenJobSections?.includes(section);
  return {
    isVisible: (section) => known && hasAddon(SECTION_ADDON[section]) && !hidden(section),
    isLocked: (section) => known && !hasAddon(SECTION_ADDON[section]) && !hidden(section),
  };
}

/** `isVisible` / `isLocked` for the optional job-detail sections of `project`. */
export function useSectionVisibility(
  project: Pick<ProjectResponse, 'hiddenJobSections'> | undefined,
): SectionVisibility {
  const { hasAddon } = useCurrentOrg();
  return sectionVisibility(hasAddon, project);
}
