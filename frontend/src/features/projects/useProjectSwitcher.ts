import { useMemo } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { useCurrentOrg } from '../org/OrgContext';
import { useOrgMembers, useProjectDirectory } from '../org/useOrganisation';
import { getLastVisitedProjects } from '../../hooks/useRecentProjects';
import { useProjectList } from './useProjects';

export interface SwitcherProject {
  id: string;
  friendlyId: string;
  name: string;
  // False for a directory-only entry (Owner/Admin sees it org-wide, but isn't
  // a project member) — project pages 403 regardless of org role (ADR-0026),
  // so these are listed for awareness but can't actually be navigated into.
  isMember: boolean;
}

/**
 * ADR-0047: the switcher's project list. Members see only their own active
 * projects; Owner/Admin additionally see every active project in the org via
 * ADR-0045's directory endpoint (reused as-is, not duplicated), so an Owner/Admin
 * can jump straight to a project they weren't even aware they had.
 */
export function useProjectSwitcher(currentProjectFriendlyId: string): SwitcherProject[] {
  const { userId } = useAuth();
  const { org } = useCurrentOrg();
  const { data: members = [] } = useOrgMembers(org?.id ?? null);
  const callerRole = members.find((m) => m.userId === userId)?.role ?? null;
  const isOwnerOrAdmin = callerRole === 'OWNER' || callerRole === 'ADMIN';

  const { data: myProjects = [] } = useProjectList('ACTIVE');
  const { data: directory = [] } = useProjectDirectory(isOwnerOrAdmin ? (org?.id ?? null) : null);

  return useMemo(() => {
    const byId = new Map<string, SwitcherProject>();
    for (const p of myProjects) {
      byId.set(p.id, { id: p.id, friendlyId: p.friendlyId, name: p.name, isMember: true });
    }
    for (const entry of directory) {
      if (entry.status !== 'ACTIVE' || byId.has(entry.id)) continue;
      byId.set(entry.id, { id: entry.id, friendlyId: entry.friendlyId, name: entry.name, isMember: false });
    }

    const recents = getLastVisitedProjects();
    return [...byId.values()]
      .filter((p) => p.friendlyId !== currentProjectFriendlyId)
      .sort((a, b) => {
        const recencyA = recents[a.friendlyId] ?? -1;
        const recencyB = recents[b.friendlyId] ?? -1;
        if (recencyA !== recencyB) return recencyB - recencyA;
        return a.name.localeCompare(b.name);
      });
  }, [myProjects, directory, currentProjectFriendlyId]);
}
