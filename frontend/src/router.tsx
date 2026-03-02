import { createBrowserRouter, Navigate } from 'react-router-dom';

// Placeholder pages — replaced as each feature is built
function ProjectListPage() {
  return <div className="p-8 text-gray-700">Project List — coming soon</div>;
}
function ProjectSettingsPage() {
  return <div className="p-8 text-gray-700">Project Settings — coming soon</div>;
}
function JobListPage() {
  return <div className="p-8 text-gray-700">Job List — coming soon</div>;
}
function JobDetailPage() {
  return <div className="p-8 text-gray-700">Job Detail — coming soon</div>;
}
function ApprovalQueuePage() {
  return <div className="p-8 text-gray-700">Approval Queue — coming soon</div>;
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/projects" replace />,
  },
  {
    path: '/projects',
    element: <ProjectListPage />,
  },
  {
    path: '/projects/:projectId/jobs',
    element: <JobListPage />,
  },
  {
    path: '/projects/:projectId/jobs/:jobId',
    element: <JobDetailPage />,
  },
  {
    path: '/projects/:projectId/approvals',
    element: <ApprovalQueuePage />,
  },
  {
    path: '/projects/:projectId/settings',
    element: <ProjectSettingsPage />,
  },
]);
