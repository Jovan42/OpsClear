import { createBrowserRouter, Navigate } from 'react-router-dom';
import AppLayout from './components/AppLayout';
import OrgRequiredRoute from './components/OrgRequiredRoute';
import RequireAuth from './components/RequireAuth';
import RouteErrorPage from './components/RouteErrorPage';
import LandingPage from './features/landing/LandingPage';
import FeaturesPage from './features/landing/FeaturesPage';
import ProjectListPage from './features/projects/ProjectListPage';
import ProjectSettingsPage from './features/projects/ProjectSettingsPage';
import JobListPage from './features/jobs/JobListPage';
import JobDetailPage from './features/jobs/JobDetailPage';
import ApprovalQueuePage from './features/approvals/ApprovalQueuePage';
import DashboardPage from './features/dashboard/DashboardPage';
import MilestonesPage from './features/milestones/MilestonesPage';
import TemplatesPage from './features/templates/TemplatesPage';
import JobTypesPage from './features/jobTypes/JobTypesPage';
import SchedulesPage from './features/schedules/SchedulesPage';
import SettingsPage from './features/settings/SettingsPage';
import DesignPage from './dev/DesignPage';
import ProjectRedirect from './components/ProjectRedirect';
import CreateOrgPage from './features/org/CreateOrgPage';
import OrgInvitesPage from './features/org/OrgInvitesPage';
import OrgMembersPage from './features/org/OrgMembersPage';
import OrgSettingsPage from './features/org/OrgSettingsPage';
import AcceptInvitePage from './features/org/AcceptInvitePage';
import SuperAdminPricingPage from './features/superAdmin/SuperAdminPricingPage';
import SuperAdminFeedbackPage from './features/superAdmin/SuperAdminFeedbackPage';
import FeedbackPage from './features/feedback/FeedbackPage';
import OverviewPage from './features/overview/OverviewPage';

export const router = createBrowserRouter([
  { path: '/', element: <LandingPage />, errorElement: <RouteErrorPage /> },
  { path: '/features', element: <FeaturesPage />, errorElement: <RouteErrorPage /> },
  { path: '/onboarding', element: <CreateOrgPage />, errorElement: <RouteErrorPage /> },
  { path: '/org/new', element: <CreateOrgPage />, errorElement: <RouteErrorPage /> },
  { path: '/invite/:token', element: <AcceptInvitePage />, errorElement: <RouteErrorPage /> },
  {
    element: <RequireAuth />,
    errorElement: <RouteErrorPage />,
    children: [
      {
        element: <AppLayout />,
        children: [
          {
            element: <OrgRequiredRoute />,
            children: [
              { path: 'projects', element: <ProjectListPage /> },
              { path: 'projects/:projectFriendlyId', element: <ProjectRedirect /> },
              { path: 'projects/:projectFriendlyId/dashboard', element: <DashboardPage /> },
              { path: 'projects/:projectFriendlyId/jobs', element: <JobListPage /> },
              { path: 'projects/:projectFriendlyId/jobs/:jobFriendlyId', element: <JobDetailPage /> },
              { path: 'projects/:projectFriendlyId/milestones', element: <MilestonesPage /> },
              { path: 'projects/:projectFriendlyId/templates', element: <TemplatesPage /> },
              { path: 'projects/:projectFriendlyId/types', element: <JobTypesPage /> },
              { path: 'projects/:projectFriendlyId/schedules', element: <SchedulesPage /> },
              { path: 'projects/:projectFriendlyId/approvals', element: <ApprovalQueuePage /> },
              { path: 'projects/:projectFriendlyId/settings', element: <ProjectSettingsPage /> },
              { path: 'settings', element: <SettingsPage /> },
              { path: 'org/settings', element: <OrgSettingsPage /> },
              { path: 'org/members', element: <OrgMembersPage /> },
              { path: 'org/invites', element: <OrgInvitesPage /> },
              { path: 'feedback', element: <FeedbackPage /> },
              { path: 'overview', element: <OverviewPage /> },
              { path: 'design', element: <DesignPage /> },
              { path: '*', element: <Navigate to="/projects" replace /> },
            ],
          },
          // Not under OrgRequiredRoute — super_user access crosses org boundaries and
          // must not depend on the caller having an org/subscription at all.
          { path: 'admin', element: <Navigate to="/admin/pricing" replace /> },
          { path: 'admin/pricing', element: <SuperAdminPricingPage /> },
          { path: 'admin/feedback', element: <SuperAdminFeedbackPage /> },
        ],
      },
    ],
  },
]);
