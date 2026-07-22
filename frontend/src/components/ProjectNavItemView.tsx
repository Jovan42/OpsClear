import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import ProjectLinksDropdown from './ProjectLinksDropdown';
import type { ProjectNavItem } from '../hooks/useProjectNavItems';
import type { LinkResponse } from '../types';

function LockIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" className="w-3 h-3 opacity-60" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  );
}

function NavBadge({ count }: Readonly<{ count: number }>) {
  return (
    <span className="text-xs font-semibold px-1.5 py-0.5 rounded-full bg-orange-500 text-white leading-none">
      {count}
    </span>
  );
}

interface Props {
  item: ProjectNavItem;
  projectId: string;
  links: LinkResponse[];
  canManageLinks: boolean;
  linkClassName: (opts: { isActive: boolean }) => string;
  /** The Links dropdown, rendered as a static inline panel instead of an absolutely
   *  positioned floating one — used by the mobile NavDrawer (ADR-0038). */
  linksDropdownClassName?: string;
  onNavigate?: () => void;
}

export default function ProjectNavItemView({
  item,
  projectId,
  links,
  canManageLinks,
  linkClassName,
  linksDropdownClassName,
  onNavigate,
}: Readonly<Props>) {
  const { t } = useTranslation('shared2');
  const [linksOpen, setLinksOpen] = useState(false);

  if (item.kind === 'links-dropdown') {
    return (
      <div className="relative">
        <button
          onClick={() => setLinksOpen((v) => !v)}
          className={`${linkClassName({ isActive: linksOpen })} cursor-pointer`}
        >
          <span className="flex items-center gap-1.5">
            {t('projectNav.links')}
            {item.badgeCount > 0 && <NavBadge count={item.badgeCount} />}
          </span>
        </button>
        {linksOpen && (
          <ProjectLinksDropdown
            projectId={projectId}
            links={links}
            canManage={canManageLinks}
            onClose={() => setLinksOpen(false)}
            className={linksDropdownClassName}
          />
        )}
      </div>
    );
  }

  return (
    <NavLink to={item.to} className={linkClassName} onClick={onNavigate}>
      <span className="flex items-center gap-1.5">
        {item.label}
        {item.locked && <LockIcon />}
        {!item.locked && !!item.badgeCount && item.badgeCount > 0 && <NavBadge count={item.badgeCount} />}
      </span>
    </NavLink>
  );
}
