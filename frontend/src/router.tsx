import { createBrowserRouter, Navigate } from 'react-router-dom';
import AppLayout from './components/AppLayout';
import RouteErrorPage from './components/RouteErrorPage';
import ProjectListPage from './features/projects/ProjectListPage';
import ProjectSettingsPage from './features/projects/ProjectSettingsPage';
import JobListPage from './features/jobs/JobListPage';
import JobDetailPage from './features/jobs/JobDetailPage';
import ApprovalQueuePage from './features/approvals/ApprovalQueuePage';
import DashboardPage from './features/dashboard/DashboardPage';
import SettingsPage from './features/settings/SettingsPage';
import DesignPage from './dev/DesignPage';
import ProjectRedirect from './components/ProjectRedirect';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    errorElement: <RouteErrorPage />,
    children: [
      { index: true, element: <Navigate to="/projects" replace /> },
      { path: 'projects', element: <ProjectListPage /> },
      { path: 'projects/:projectId', element: <ProjectRedirect /> },
      { path: 'projects/:projectId/dashboard', element: <DashboardPage /> },
      { path: 'projects/:projectId/jobs', element: <JobListPage /> },
      { path: 'projects/:projectId/jobs/:jobId', element: <JobDetailPage /> },
      { path: 'projects/:projectId/approvals', element: <ApprovalQueuePage /> },
      { path: 'projects/:projectId/settings', element: <ProjectSettingsPage /> },
      { path: 'settings', element: <SettingsPage /> },
      { path: 'design', element: <DesignPage /> },
    ],
  },
]);
