import { useProject, useProjectRole } from '../features/projects/useProjects';
import { useApprovalQueue } from '../features/approvals/useApprovalQueue';
import { useCurrentOrg } from '../features/org/OrgContext';
import type { LinkResponse } from '../types';

export interface ProjectNavLinkItem {
  kind: 'link';
  key: string;
  to: string;
  label: string;
  locked: boolean;
  badgeCount?: number;
}

export interface ProjectNavLinksItem {
  kind: 'links-dropdown';
  key: 'links';
  locked: boolean;
  badgeCount: number;
}

export type ProjectNavItem = ProjectNavLinkItem | ProjectNavLinksItem;

export interface ProjectNavData {
  items: ProjectNavItem[];
  links: LinkResponse[];
  canManageLinks: boolean;
  pendingApprovalsCount: number;
  isOwnerOrAdmin: boolean;
}

/**
 * Single source of truth for the project nav's item list, order, and addon gating —
 * consumed by both the desktop horizontal ProjectNav and the mobile NavDrawer (ADR-0038)
 * so the two can never drift apart as addons are added or gated differently.
 */
export function useProjectNavItems(projectId: string): ProjectNavData {
  const role = useProjectRole(projectId);
  const { data: pending = [] } = useApprovalQueue(projectId);
  const { data: project } = useProject(projectId);
  const { hasAddon } = useCurrentOrg();

  const isOwnerOrAdmin = role === 'OWNER' || role === 'ADMIN';
  const links = project?.links ?? [];

  const dashboardLocked = !hasAddon('DASHBOARD');
  const milestonesLocked = !hasAddon('MILESTONES');
  const templatesLocked = !hasAddon('JOB_TEMPLATES');
  const schedulesLocked = !hasAddon('RECURRING_SCHEDULING');
  const linksLocked = !hasAddon('JOB_LINKS');

  const items: ProjectNavItem[] = [];

  if (!dashboardLocked) {
    items.push({ kind: 'link', key: 'dashboard', to: `/projects/${projectId}/dashboard`, label: 'Dashboard', locked: false });
  }
  items.push({ kind: 'link', key: 'jobs', to: `/projects/${projectId}/jobs`, label: 'Jobs', locked: false });
  if (!milestonesLocked) {
    items.push({ kind: 'link', key: 'milestones', to: `/projects/${projectId}/milestones`, label: 'Milestones', locked: false });
  }
  if (!templatesLocked) {
    items.push({ kind: 'link', key: 'templates', to: `/projects/${projectId}/templates`, label: 'Templates', locked: false });
  }
  if (!schedulesLocked) {
    items.push({ kind: 'link', key: 'schedules', to: `/projects/${projectId}/schedules`, label: 'Schedules', locked: false });
  }
  if (isOwnerOrAdmin) {
    items.push({
      kind: 'link',
      key: 'approvals',
      to: `/projects/${projectId}/approvals`,
      label: 'Approvals',
      locked: false,
      badgeCount: pending.length,
    });
  }
  items.push({ kind: 'link', key: 'settings', to: `/projects/${projectId}/settings`, label: 'Settings', locked: false });
  if (!linksLocked) {
    items.push({ kind: 'links-dropdown', key: 'links', locked: false, badgeCount: links.length });
  }
  if (dashboardLocked) {
    items.push({ kind: 'link', key: 'dashboard-locked', to: `/projects/${projectId}/dashboard`, label: 'Dashboard', locked: true });
  }
  if (milestonesLocked) {
    items.push({ kind: 'link', key: 'milestones-locked', to: `/projects/${projectId}/milestones`, label: 'Milestones', locked: true });
  }
  if (templatesLocked) {
    items.push({ kind: 'link', key: 'templates-locked', to: `/projects/${projectId}/templates`, label: 'Templates', locked: true });
  }
  if (schedulesLocked) {
    items.push({ kind: 'link', key: 'schedules-locked', to: `/projects/${projectId}/schedules`, label: 'Schedules', locked: true });
  }
  if (linksLocked) {
    items.push({ kind: 'link', key: 'links-locked', to: '/org/settings', label: 'Links', locked: true });
  }

  return { items, links, canManageLinks: isOwnerOrAdmin, pendingApprovalsCount: pending.length, isOwnerOrAdmin };
}
