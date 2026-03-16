import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuth } from '../../auth/AuthContext';
import Button from '../../components/Button';
import MarkdownEditor from '../../components/MarkdownEditor';
import Modal from '../../components/Modal';
import Skeleton from '../../components/Skeleton';
import RoleBadge from '../../components/RoleBadge';
import AddMemberForm from './AddMemberForm';
import {
  useProject,
  useProjectMembers,
  useProjectRole,
  useUpdateProject,
  useUpdateProjectStatus,
  useDeleteProject,
  useUpdateMember,
  useRemoveMember,
} from './useProjects';
import {
  useBlockReasons,
  useDeleteBlockReason,
} from '../jobs/useBlockReasons';
import {
  useMilestones,
  useCreateMilestone,
  useUpdateMilestone,
  useDeleteMilestone,
} from '../jobs/useMilestones';
import { usePageTitle } from '../../hooks/usePageTitle';

const detailsSchema = z.object({
  name: z.string().min(1, 'Name is required').max(80, 'Max 80 characters'),
  description: z.string().max(255, 'Max 255 characters').optional(),
});
type DetailsForm = z.infer<typeof detailsSchema>;

const ROLES = ['MEMBER', 'ADMIN', 'OWNER'] as const;

export default function ProjectSettingsPage() {
  const { projectId = '' } = useParams();
  const navigate = useNavigate();
  const { userId } = useAuth();

  const { data: project, isLoading: projectLoading } = useProject(projectId);
  const { data: members = [], isLoading: membersLoading } = useProjectMembers(projectId);
  const role = useProjectRole(projectId);
  usePageTitle('Settings', project?.name);

  const { mutate: updateProject, isPending: saving } = useUpdateProject();
  const { mutate: updateProjectStatus, isPending: updatingStatus } = useUpdateProjectStatus(projectId);
  const { mutate: deleteProject, isPending: deleting } = useDeleteProject();
  const { mutate: updateMember } = useUpdateMember(projectId);
  const { mutate: removeMember } = useRemoveMember(projectId);

  const { data: blockReasons = [] } = useBlockReasons(projectId, !!projectId);
  const { mutate: deleteBlockReason } = useDeleteBlockReason(projectId);

  const { data: milestones = [] } = useMilestones(projectId);
  const { mutate: createMilestone, isPending: creatingMilestone } = useCreateMilestone(projectId);
  const { mutate: updateMilestone } = useUpdateMilestone(projectId);
  const { mutate: deleteMilestone } = useDeleteMilestone(projectId);

  const [newMilestoneName, setNewMilestoneName] = useState('');
  const [newMilestoneDeadline, setNewMilestoneDeadline] = useState('');
  const [editingMilestoneId, setEditingMilestoneId] = useState<string | null>(null);
  const [editMilestoneName, setEditMilestoneName] = useState('');
  const [editMilestoneDeadline, setEditMilestoneDeadline] = useState('');

  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [deleteConfirmInput, setDeleteConfirmInput] = useState('');

  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isDirty },
    reset,
  } = useForm<DetailsForm>({
    resolver: zodResolver(detailsSchema),
    values: project
      ? { name: project.name, description: project.description ?? '' }
      : undefined,
  });

  const canEdit = role === 'OWNER' || role === 'ADMIN';
  const isOwner = role === 'OWNER';
  const isCompleted = project?.status === 'COMPLETED';

  function onSaveDetails(values: DetailsForm) {
    updateProject(
      { projectId, body: { name: values.name, description: values.description || undefined } },
      { onSuccess: () => reset(values) },
    );
  }

  function handleAddMilestone() {
    if (!newMilestoneName.trim()) return;
    createMilestone(
      {
        name: newMilestoneName.trim(),
        deadline: newMilestoneDeadline ? new Date(newMilestoneDeadline).toISOString() : undefined,
      },
      {
        onSuccess: () => {
          setNewMilestoneName('');
          setNewMilestoneDeadline('');
        },
      },
    );
  }

  function handleStartEditMilestone(id: string, name: string, deadline: string | null) {
    setEditingMilestoneId(id);
    setEditMilestoneName(name);
    setEditMilestoneDeadline(deadline ? new Date(deadline).toISOString().split('T')[0] : '');
  }

  function handleSaveMilestone(milestoneId: string) {
    if (!editMilestoneName.trim()) return;
    updateMilestone(
      {
        milestoneId,
        body: {
          name: editMilestoneName.trim(),
          deadline: editMilestoneDeadline ? new Date(editMilestoneDeadline).toISOString() : undefined,
        },
      },
      { onSuccess: () => setEditingMilestoneId(null) },
    );
  }

  function handleDeleteConfirm() {
    deleteProject(projectId, {
      onSuccess: () => navigate('/projects'),
    });
  }

  if (projectLoading || membersLoading) {
    return (
      <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8 space-y-8">
        <Skeleton className="h-7 w-40" />
        <div className="space-y-3">
          <Skeleton className="h-3 w-12" />
          <Skeleton className="h-10 rounded-lg" />
          <Skeleton className="h-3 w-16" />
          <Skeleton className="h-20 rounded-lg" />
          <Skeleton className="h-9 w-24 rounded-lg" />
        </div>
        <div className="space-y-3">
          <Skeleton className="h-5 w-20" />
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="flex items-center justify-between py-2">
              <div className="space-y-1">
                <Skeleton className="h-4 w-32" />
                <Skeleton className="h-3 w-48" />
              </div>
              <Skeleton className="h-5 w-16 rounded-full" />
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8 space-y-10">
      <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">Project Settings</h1>

      {/* ── Completed banner ── */}
      {isCompleted && (
        <div className="flex items-center gap-3 px-4 py-3 rounded-xl bg-gray-100 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 text-sm text-gray-600 dark:text-gray-400">
          <svg xmlns="http://www.w3.org/2000/svg" className="w-4 h-4 shrink-0 text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
            <polyline points="22 4 12 14.01 9 11.01" />
          </svg>
          This project is completed and no longer accepts changes.
        </div>
      )}

      {/* ── Project details ── */}
      <section>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-widest mb-4">
          Details
        </h2>
        <form onSubmit={handleSubmit(onSaveDetails)} className="space-y-4">
          <div>
            <label htmlFor="proj-name" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Name
            </label>
            <input
              id="proj-name"
              {...register('name')}
              disabled={!canEdit}
              className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:border-transparent disabled:bg-gray-50 dark:disabled:bg-gray-800 disabled:text-gray-400 dark:disabled:text-gray-500"
            />
            {errors.name && (
              <p className="mt-1 text-xs text-red-600">{errors.name.message}</p>
            )}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Description
            </label>
            <Controller
              name="description"
              control={control}
              render={({ field }) => (
                <MarkdownEditor
                  value={field.value ?? ''}
                  onChange={field.onChange}
                  rows={3}
                  disabled={!canEdit}
                />
              )}
            />
            {errors.description && (
              <p className="mt-1 text-xs text-red-600">{errors.description.message}</p>
            )}
          </div>
          {canEdit && (
            <div className="flex gap-2">
              <Button type="submit" loading={saving} disabled={!isDirty}>
                Save changes
              </Button>
              {isDirty && (
                <Button variant="secondary" type="button" onClick={() => reset()}>
                  Cancel
                </Button>
              )}
            </div>
          )}
        </form>
      </section>

      {/* ── Members ── */}
      <section>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-widest mb-4">
          Members
        </h2>
        <div className="space-y-4">
          {canEdit && <AddMemberForm projectId={projectId} />}
          <div className="border border-gray-200 dark:border-gray-700 rounded-xl overflow-x-auto">
            <table className="w-full text-sm min-w-[28rem]">
              <thead className="bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
                <tr>
                  <th className="text-left px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400">Member</th>
                  <th className="text-left px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400">Role</th>
                  {canEdit && <th className="px-4 py-2.5" />}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                {members.map((member) => {
                  const isSelf = member.userId === userId;
                  const isTargetOwner = member.role === 'OWNER';
                  const canModify = canEdit && !isSelf && !(isTargetOwner && !isOwner);
                  return (
                    <tr key={member.id} className="bg-white dark:bg-gray-800">
                      <td className="px-4 py-3">
                        <p className="font-medium text-gray-900 dark:text-gray-100">{member.userName}</p>
                        <p className="text-xs text-gray-500 dark:text-gray-400">{member.userEmail}</p>
                      </td>
                      <td className="px-4 py-3">
                        {canModify ? (
                          <select
                            value={member.role}
                            onChange={(e) =>
                              updateMember({ memberId: member.id, role: e.target.value })
                            }
                            className="rounded-lg border border-gray-200 dark:border-gray-600 px-2 py-1 text-xs bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-200 focus:outline-none focus:ring-2 focus:border-transparent"
                          >
                            {ROLES.map((r) => (
                              <option key={r} value={r}>
                                {r.charAt(0) + r.slice(1).toLowerCase()}
                              </option>
                            ))}
                          </select>
                        ) : (
                          <RoleBadge role={member.role} />
                        )}
                      </td>
                      {canEdit && (
                        <td className="px-4 py-3 text-right">
                          {canModify && (
                            <button
                              onClick={() => removeMember(member.id)}
                              className="text-xs text-red-500 hover:text-red-700 transition-colors cursor-pointer"
                            >
                              Remove
                            </button>
                          )}
                        </td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      </section>

      {/* ── Block reasons ── */}
      {canEdit && (
        <section>
          <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-widest mb-4">
            Block Reasons
          </h2>
          <div className="space-y-3">
            <p className="text-xs text-gray-500 dark:text-gray-400">
              Pre-configured reasons team members can select when blocking a job.
            </p>
            {blockReasons.length === 0 ? (
              <p className="text-xs text-gray-400 dark:text-gray-500 italic">
                No block reasons configured. Set them when creating the project.
              </p>
            ) : (
              <ul className="border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
                {blockReasons.map((br) => (
                  <li key={br.id} className="flex items-center justify-between px-4 py-2.5 bg-white dark:bg-gray-800 first:rounded-t-xl last:rounded-b-xl">
                    <span className="text-sm text-gray-800 dark:text-gray-200">{br.reason}</span>
                    <button
                      onClick={() => deleteBlockReason(br.id)}
                      className="text-xs text-red-500 hover:text-red-700 transition-colors cursor-pointer ml-4 shrink-0"
                    >
                      Remove
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>
      )}

      {/* ── Milestones ── */}
      {canEdit && (
        <section>
          <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-widest mb-4">
            Milestones
          </h2>
          <div className="space-y-3">
            {milestones.length === 0 ? (
              <p className="text-xs text-gray-400 dark:text-gray-500 italic">No milestones yet.</p>
            ) : (
              <ul className="border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
                {milestones.map((ms) => (
                  <li key={ms.id} className="flex items-center justify-between px-4 py-2.5 bg-white dark:bg-gray-800 first:rounded-t-xl last:rounded-b-xl gap-3">
                    {editingMilestoneId === ms.id ? (
                      <>
                        <div className="flex flex-1 gap-2 min-w-0">
                          <input
                            value={editMilestoneName}
                            onChange={(e) => setEditMilestoneName(e.target.value)}
                            className="flex-1 min-w-0 rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-1.5 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:border-transparent"
                          />
                          <input
                            type="date"
                            value={editMilestoneDeadline}
                            onChange={(e) => setEditMilestoneDeadline(e.target.value)}
                            className="rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-1.5 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:border-transparent"
                          />
                        </div>
                        <div className="flex gap-2 shrink-0">
                          <button
                            onClick={() => handleSaveMilestone(ms.id)}
                            disabled={!editMilestoneName.trim()}
                            className="text-xs text-brand hover:opacity-80 transition-opacity cursor-pointer disabled:opacity-40"
                          >
                            Save
                          </button>
                          <button
                            onClick={() => setEditingMilestoneId(null)}
                            className="text-xs text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 transition-colors cursor-pointer"
                          >
                            Cancel
                          </button>
                        </div>
                      </>
                    ) : (
                      <>
                        <div className="flex-1 min-w-0">
                          <span className="text-sm text-gray-800 dark:text-gray-200">{ms.name}</span>
                          {ms.deadline && (
                            <span className="ml-2 text-xs text-gray-500 dark:text-gray-400">
                              {new Date(ms.deadline).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })}
                            </span>
                          )}
                        </div>
                        <div className="flex gap-3 shrink-0">
                          <button
                            onClick={() => handleStartEditMilestone(ms.id, ms.name, ms.deadline)}
                            className="text-xs text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 transition-colors cursor-pointer"
                          >
                            Edit
                          </button>
                          <button
                            onClick={() => deleteMilestone(ms.id)}
                            className="text-xs text-red-500 hover:text-red-700 transition-colors cursor-pointer"
                          >
                            Remove
                          </button>
                        </div>
                      </>
                    )}
                  </li>
                ))}
              </ul>
            )}
            <div className="flex gap-2">
              <input
                value={newMilestoneName}
                onChange={(e) => setNewMilestoneName(e.target.value)}
                placeholder="Milestone name"
                maxLength={100}
                className="flex-1 min-w-0 rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent"
                onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); handleAddMilestone(); } }}
              />
              <input
                type="date"
                value={newMilestoneDeadline}
                onChange={(e) => setNewMilestoneDeadline(e.target.value)}
                className="rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:border-transparent"
              />
              <Button
                size="sm"
                onClick={handleAddMilestone}
                disabled={!newMilestoneName.trim()}
                loading={creatingMilestone}
              >
                Add milestone
              </Button>
            </div>
          </div>
        </section>
      )}

      {/* ── Danger zone ── */}
      {isOwner && (
        <section>
          <h2 className="text-sm font-semibold text-red-500 uppercase tracking-widest mb-4">
            Danger zone
          </h2>
          <div className="border border-gray-200 dark:border-gray-700 rounded-xl p-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
                {isCompleted ? 'Reactivate this project' : 'Complete this project'}
              </p>
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                {isCompleted
                  ? 'Mark the project as active again to allow new changes.'
                  : 'Mark the project as completed. All jobs must be closed first.'}
              </p>
            </div>
            <Button
              variant="secondary"
              size="sm"
              loading={updatingStatus}
              onClick={() => updateProjectStatus(isCompleted ? 'ACTIVE' : 'COMPLETED')}
            >
              {isCompleted ? 'Reactivate' : 'Complete project'}
            </Button>
          </div>
          <div className="mt-6 border border-red-200 dark:border-red-900/50 rounded-xl p-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-sm font-medium text-gray-900 dark:text-gray-100">Delete this project</p>
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                Permanently removes the project and all its jobs. This cannot be undone.
              </p>
            </div>
            <Button variant="danger" size="sm" onClick={() => setDeleteModalOpen(true)}>
              Delete project
            </Button>
          </div>
        </section>
      )}

      {/* ── Delete confirmation modal ── */}
      <Modal
        open={deleteModalOpen}
        onClose={() => { setDeleteModalOpen(false); setDeleteConfirmInput(''); }}
        title="Delete project?"
      >
        <div className="space-y-4">
          <p className="text-sm text-gray-600 dark:text-gray-300">
            This will permanently delete{' '}
            <span className="font-semibold">{project?.name}</span> and all its jobs, notes,
            and approvals. <span className="font-medium text-red-600 dark:text-red-400">This cannot be undone.</span>
          </p>
          <div>
            <label className="block text-sm text-gray-600 dark:text-gray-300 mb-1.5">
              To confirm, type the project name:{' '}
              <span className="font-semibold text-gray-900 dark:text-gray-100">{project?.name}</span>
            </label>
            <input
              value={deleteConfirmInput}
              onChange={(e) => setDeleteConfirmInput(e.target.value)}
              className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:ring-red-400 focus:border-transparent"
              placeholder={project?.name}
              autoComplete="off"
            />
          </div>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => { setDeleteModalOpen(false); setDeleteConfirmInput(''); }}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={deleting}
              disabled={deleteConfirmInput !== project?.name}
              onClick={handleDeleteConfirm}
            >
              Delete project
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
