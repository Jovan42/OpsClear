import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation('shared1');
  const role = useProjectRole(projectId);
  const { data: pending = [] } = useApprovalQueue(projectId);
  const { data: project } = useProject(projectId);
  const { hasAddon } = useCurrentOrg();

  const isOwnerOrAdmin = role === 'OWNER' || role === 'ADMIN';
  const links = project?.links ?? [];

  const dashboardLocked = !hasAddon('DASHBOARD');
  const milestonesLocked = !hasAddon('MILESTONES');
  const templatesLocked = !hasAddon('JOB_TEMPLATES');
  const typesLocked = !hasAddon('JOB_TYPES');
  const schedulesLocked = !hasAddon('RECURRING_SCHEDULING');
  const linksLocked = !hasAddon('JOB_LINKS');
  const approvalsLocked = !hasAddon('APPROVALS');

  const items: ProjectNavItem[] = [];

  if (!dashboardLocked) {
    items.push({ kind: 'link', key: 'dashboard', to: `/projects/${projectId}/dashboard`, label: t('nav.dashboard'), locked: false });
  }
  items.push({ kind: 'link', key: 'jobs', to: `/projects/${projectId}/jobs`, label: t('nav.jobs'), locked: false });
  if (!milestonesLocked) {
    items.push({ kind: 'link', key: 'milestones', to: `/projects/${projectId}/milestones`, label: t('nav.milestones'), locked: false });
  }
  if (!templatesLocked) {
    items.push({ kind: 'link', key: 'templates', to: `/projects/${projectId}/templates`, label: t('nav.templates'), locked: false });
  }
  if (!typesLocked) {
    items.push({ kind: 'link', key: 'types', to: `/projects/${projectId}/types`, label: t('nav.types'), locked: false });
  }
  if (!schedulesLocked) {
    items.push({ kind: 'link', key: 'schedules', to: `/projects/${projectId}/schedules`, label: t('nav.schedules'), locked: false });
  }
  if (isOwnerOrAdmin && !approvalsLocked) {
    items.push({
      kind: 'link',
      key: 'approvals',
      to: `/projects/${projectId}/approvals`,
      label: t('nav.approvals'),
      locked: false,
      badgeCount: pending.length,
    });
  }
  items.push({ kind: 'link', key: 'settings', to: `/projects/${projectId}/settings`, label: t('settings'), locked: false });
  if (!linksLocked) {
    items.push({ kind: 'links-dropdown', key: 'links', locked: false, badgeCount: links.length });
  }
  if (dashboardLocked) {
    items.push({ kind: 'link', key: 'dashboard-locked', to: `/projects/${projectId}/dashboard`, label: t('nav.dashboard'), locked: true });
  }
  if (milestonesLocked) {
    items.push({ kind: 'link', key: 'milestones-locked', to: `/projects/${projectId}/milestones`, label: t('nav.milestones'), locked: true });
  }
  if (templatesLocked) {
    items.push({ kind: 'link', key: 'templates-locked', to: `/projects/${projectId}/templates`, label: t('nav.templates'), locked: true });
  }
  if (typesLocked) {
    items.push({ kind: 'link', key: 'types-locked', to: `/projects/${projectId}/types`, label: t('nav.types'), locked: true });
  }
  if (schedulesLocked) {
    items.push({ kind: 'link', key: 'schedules-locked', to: `/projects/${projectId}/schedules`, label: t('nav.schedules'), locked: true });
  }
  if (linksLocked) {
    items.push({ kind: 'link', key: 'links-locked', to: '/org/settings', label: t('nav.links'), locked: true });
  }
  // Approvals stays invisible to plain Members even when unlocked (ApprovalQueuePage
  // itself redirects a Member away regardless of addon state) — so the locked variant
  // is gated by isOwnerOrAdmin too, unlike every other locked item here.
  if (isOwnerOrAdmin && approvalsLocked) {
    items.push({ kind: 'link', key: 'approvals-locked', to: `/projects/${projectId}/approvals`, label: t('nav.approvals'), locked: true });
  }

  return { items, links, canManageLinks: isOwnerOrAdmin, pendingApprovalsCount: pending.length, isOwnerOrAdmin };
}
