import { createBrowserRouter, Navigate } from 'react-router-dom';
import AppLayout from './components/AppLayout';
import OrgRequiredRoute from './components/OrgRequiredRoute';
import RouteErrorPage from './components/RouteErrorPage';
import ProjectListPage from './features/projects/ProjectListPage';
import ProjectSettingsPage from './features/projects/ProjectSettingsPage';
import JobListPage from './features/jobs/JobListPage';
import JobDetailPage from './features/jobs/JobDetailPage';
import ApprovalQueuePage from './features/approvals/ApprovalQueuePage';
import DashboardPage from './features/dashboard/DashboardPage';
import MilestonesPage from './features/milestones/MilestonesPage';
import SettingsPage from './features/settings/SettingsPage';
import DesignPage from './dev/DesignPage';
import ProjectRedirect from './components/ProjectRedirect';
import CreateOrgPage from './features/org/CreateOrgPage';
import OrgInvitesPage from './features/org/OrgInvitesPage';
import OrgMembersPage from './features/org/OrgMembersPage';
import OrgSettingsPage from './features/org/OrgSettingsPage';
import AcceptInvitePage from './features/org/AcceptInvitePage';

export const router = createBrowserRouter([
  { path: '/onboarding', element: <CreateOrgPage />, errorElement: <RouteErrorPage /> },
  { path: '/org/new', element: <CreateOrgPage />, errorElement: <RouteErrorPage /> },
  { path: '/invite/:token', element: <AcceptInvitePage />, errorElement: <RouteErrorPage /> },
  {
    path: '/',
    element: <AppLayout />,
    errorElement: <RouteErrorPage />,
    children: [
      {
        element: <OrgRequiredRoute />,
        children: [
          { index: true, element: <Navigate to="/projects" replace /> },
          { path: 'projects', element: <ProjectListPage /> },
          { path: 'projects/:projectId', element: <ProjectRedirect /> },
          { path: 'projects/:projectId/dashboard', element: <DashboardPage /> },
          { path: 'projects/:projectId/jobs', element: <JobListPage /> },
          { path: 'projects/:projectId/jobs/:jobId', element: <JobDetailPage /> },
          { path: 'projects/:projectId/milestones', element: <MilestonesPage /> },
          { path: 'projects/:projectId/approvals', element: <ApprovalQueuePage /> },
          { path: 'projects/:projectId/settings', element: <ProjectSettingsPage /> },
          { path: 'settings', element: <SettingsPage /> },
          { path: 'org/settings', element: <OrgSettingsPage /> },
          { path: 'org/members', element: <OrgMembersPage /> },
          { path: 'org/invites', element: <OrgInvitesPage /> },
          { path: 'design', element: <DesignPage /> },
        ],
      },
    ],
  },
]);
