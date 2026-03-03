import { createBrowserRouter, Navigate } from 'react-router-dom';
import AppLayout from './components/AppLayout';
import ProjectListPage from './features/projects/ProjectListPage';
import ProjectSettingsPage from './features/projects/ProjectSettingsPage';
import JobListPage from './features/jobs/JobListPage';
import JobDetailPage from './features/jobs/JobDetailPage';
import ApprovalQueuePage from './features/approvals/ApprovalQueuePage';
import DesignPage from './dev/DesignPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/projects" replace /> },
      { path: 'projects', element: <ProjectListPage /> },
      { path: 'projects/:projectId/jobs', element: <JobListPage /> },
      { path: 'projects/:projectId/jobs/:jobId', element: <JobDetailPage /> },
      { path: 'projects/:projectId/approvals', element: <ApprovalQueuePage /> },
      { path: 'projects/:projectId/settings', element: <ProjectSettingsPage /> },
      { path: 'design', element: <DesignPage /> },
    ],
  },
]);
