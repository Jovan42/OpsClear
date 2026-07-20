import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { useTemplates, useDeleteTemplate } from './useTemplates';
import { useProject, useProjectRole } from '../projects/useProjects';
import { usePageTitle } from '../../hooks/usePageTitle';
import { useCurrentOrg } from '../org/OrgContext';
import TemplateFormModal from './TemplateFormModal';
import ScheduleFormModal from '../schedules/ScheduleFormModal';
import ConfirmModal from '../../components/ConfirmModal';
import Button from '../../components/Button';
import PageError from '../../components/PageError';
import UpgradeCard from '../../components/UpgradeCard';
import PriorityBadge from '../../components/PriorityBadge';
import type { JobTemplateResponse } from '../../types';

function LoadingSkeleton() {
  return (
    <div className="space-y-3">
      {[1, 2, 3].map((i) => (
        <div
          key={i}
          className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5 animate-pulse"
        >
          <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded w-1/3 mb-2" />
          <div className="h-3 bg-gray-100 dark:bg-gray-700 rounded w-2/3" />
        </div>
      ))}
    </div>
  );
}

interface TemplateRowProps {
  template: JobTemplateResponse;
  onEdit: (t: JobTemplateResponse) => void;
  onDelete: (t: JobTemplateResponse) => void;
  onSchedule?: (t: JobTemplateResponse) => void;
}

function TemplateRow({ template, onEdit, onDelete, onSchedule }: Readonly<TemplateRowProps>) {
  return (
    <div className="flex items-start justify-between gap-4 p-5">
      <div className="min-w-0 space-y-1 flex-1">
        <div className="flex items-center gap-2">
          <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{template.name}</p>
          {template.friendlyId && (
            <span className="text-xs text-gray-400 dark:text-gray-500 font-mono">{template.friendlyId}</span>
          )}
          {template.priority && <PriorityBadge priority={template.priority} />}
        </div>
        {template.title && (
          <p className="text-sm text-gray-500 dark:text-gray-400 truncate">{template.title}</p>
        )}
        <div className="flex items-center gap-3 text-xs text-gray-400 dark:text-gray-500">
          {template.milestoneName && <span>📍 {template.milestoneName}</span>}
          {template.deadlineOffsetDays != null && <span>+{template.deadlineOffsetDays}d deadline</span>}
          {template.occurrenceCount > 0 && (
            <span>Used {template.occurrenceCount}×</span>
          )}
        </div>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        {onSchedule && (
          <Button variant="ghost" size="sm" onClick={() => onSchedule(template)}>
            Schedule
          </Button>
        )}
        <Button variant="ghost" size="sm" onClick={() => onEdit(template)}>Edit</Button>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => onDelete(template)}
          className="text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20"
        >
          Delete
        </Button>
      </div>
    </div>
  );
}

export default function TemplatesPage() {
  const { projectFriendlyId: projectId = '' } = useParams();
  const { data: project } = useProject(projectId);
  const { data: templates = [], isLoading, isError, refetch } = useTemplates(projectId);
  const deleteTemplate = useDeleteTemplate(projectId);
  const { hasAddon } = useCurrentOrg();
  const role = useProjectRole(projectId);
  const isOwnerOrAdmin = role === 'OWNER' || role === 'ADMIN';
  const canSchedule = isOwnerOrAdmin && hasAddon('RECURRING_SCHEDULING');

  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing]       = useState<JobTemplateResponse | null>(null);
  const [deleting, setDeleting]     = useState<JobTemplateResponse | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [scheduling, setScheduling] = useState<JobTemplateResponse | null>(null);

  usePageTitle('Templates', project?.name);

  if (!hasAddon('JOB_TEMPLATES')) return <UpgradeCard featureName="Job Templates" />;

  if (isLoading) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-10">
        <LoadingSkeleton />
      </div>
    );
  }

  if (isError) {
    return <PageError message="Failed to load templates." onRetry={() => void refetch()} />;
  }

  return (
    <>
      <div className="max-w-2xl mx-auto px-4 py-10 space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">Job Templates</h1>
          <Button variant="primary" size="sm" onClick={() => setShowCreate(true)}>
            + New Template
          </Button>
        </div>

        {templates.length === 0 ? (
          <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-10 text-center">
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
              No templates yet. Create reusable job presets with wildcards like{' '}
              <code className="font-mono text-blue-600 dark:text-blue-400">{'{{date}}'}</code> and{' '}
              <code className="font-mono text-blue-600 dark:text-blue-400">{'{{project}}'}</code>.
            </p>
            <Button onClick={() => setShowCreate(true)}>Create first template</Button>
          </div>
        ) : (
          <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
            {templates.map((t) => (
              <TemplateRow
                key={t.id}
                template={t}
                onEdit={setEditing}
                onDelete={(tmpl) => { setDeleting(tmpl); setDeleteError(null); }}
                onSchedule={canSchedule ? setScheduling : undefined}
              />
            ))}
          </div>
        )}
      </div>

      <TemplateFormModal
        key={showCreate ? 'create-open' : 'create-closed'}
        open={showCreate}
        onClose={() => setShowCreate(false)}
        projectId={projectId}
      />

      <TemplateFormModal
        key={editing?.id ?? 'edit-closed'}
        open={editing !== null}
        onClose={() => setEditing(null)}
        projectId={projectId}
        template={editing ?? undefined}
      />

      <ScheduleFormModal
        key={scheduling?.id ?? 'schedule-closed'}
        open={scheduling !== null}
        onClose={() => setScheduling(null)}
        projectId={projectId}
        initialTemplateId={scheduling?.id}
      />

      <ConfirmModal
        open={deleting !== null}
        onClose={() => { setDeleting(null); setDeleteError(null); }}
        onConfirm={() => {
          if (!deleting) return;
          deleteTemplate.mutate(deleting.id, {
            onSuccess: () => { setDeleting(null); setDeleteError(null); },
            onError: (err) => {
              if (isAxiosError(err) && err.response?.status === 409) {
                setDeleteError(err.response.data.message as string);
              }
            },
          });
        }}
        title="Delete Template"
        message={`Delete "${deleting?.name}"? This cannot be undone.`}
        confirmLabel="Delete"
        variant="danger"
        isPending={deleteTemplate.isPending}
        errorMessage={deleteError}
      />
    </>
  );
}
