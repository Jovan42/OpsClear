import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import UserMenu from './UserMenu';
import NotificationBell from '../features/notifications/NotificationBell';

interface AppHeaderProps {
  /** Before the logo, e.g. the mobile menu button. */
  start?: ReactNode;
  /** Right after the logo, e.g. the project switcher. */
  afterLogo?: ReactNode;
  /** Before the notification bell, e.g. the project nav. */
  end?: ReactNode;
}

/** The signed-in app's top bar: logo, notifications and the user menu. */
export default function AppHeader({ start, afterLogo, end }: Readonly<AppHeaderProps>) {
  const { name } = useAuth();
  return (
    <nav className="shrink-0 px-4 text-white" style={{ backgroundColor: 'var(--brand)' }}>
      <div className="flex items-center justify-between h-14">
        <div className="flex items-center gap-2 min-w-0">
          {start}
          <Link
            to="/projects"
            className="font-semibold text-lg tracking-tight text-white hover:text-white/80 transition-colors shrink-0"
          >
            OpsClear
          </Link>
          {afterLogo}
        </div>

        <div className="flex items-center gap-4 sm:gap-6">
          {end}
          <NotificationBell />
          <UserMenu name={name ?? ''} />
        </div>
      </div>
    </nav>
  );
}
