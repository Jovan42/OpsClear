import DemoQueryScope from './DemoQueryScope';
import DemoRouteWrapper, { type DemoRoute } from './DemoRouteWrapper';
import { BellScene, MuteSettingsScene } from './NotificationsDemoScenes';
import NotificationsPage from '../features/notifications/NotificationsPage';
import JobDetailPage from '../features/jobs/JobDetailPage';
import SchedulesPage from '../features/schedules/SchedulesPage';
import FeedbackPage from '../features/feedback/FeedbackPage';
import type { DemoSlide } from './types';

/**
 * Where a demo notification can take you. Every item in the sample inbox (mockData.ts)
 * targets one of these, and the bell's "View all" link opens the real Notifications page,
 * so nothing a visitor clicks lands on an empty route.
 */
const TARGET_ROUTES: DemoRoute[] = [
  { path: '/notifications', element: <NotificationsPage /> },
  { path: '/projects/:projectFriendlyId/jobs/:jobFriendlyId', element: <JobDetailPage /> },
  { path: '/projects/:projectFriendlyId/schedules', element: <SchedulesPage /> },
  { path: '/feedback', element: <FeedbackPage /> },
];

/**
 * The Notifications card's demo (ADR-0040 pattern, ADR-0051 feature, JOB-330): the real
 * bell dropdown, Notifications page and mute toggles, unmodified, served the sample inbox
 * and muted list by the `/api/notifications` and `/api/user/notification-settings` mock
 * handlers. Marking items read, "Mark all as read" and the toggles all work against the
 * in-memory store. No live stream: useNotificationStream is only mounted by AppLayout,
 * which the demo never renders, so the components fall back to their REST queries.
 */
export const slides: DemoSlide[] = [
  {
    labelKey: 'featuresPage.demo.slideLabels.notificationBell',
    render: () => (
      <DemoRouteWrapper
        routes={[{ path: '/', element: <BellScene /> }, ...TARGET_ROUTES]}
        initialEntry="/"
      />
    ),
  },
  {
    labelKey: 'featuresPage.demo.slideLabels.notificationsPage',
    render: () => <DemoRouteWrapper routes={TARGET_ROUTES} initialEntry="/notifications" />,
  },
  {
    labelKey: 'featuresPage.demo.slideLabels.notificationSettings',
    render: () => (
      <DemoQueryScope>
        <MuteSettingsScene />
      </DemoQueryScope>
    ),
  },
];

export default slides;
