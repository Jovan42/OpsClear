import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuth } from '../../auth/AuthContext';
import Button from '../../components/Button';
import Modal from '../../components/Modal';
import RoleBadge from '../../components/RoleBadge';
import AddMemberForm from './AddMemberForm';
import {
  useProject,
  useProjectMembers,
  useProjectRole,
  useUpdateProject,
  useDeleteProject,
  useUpdateMember,
  useRemoveMember,
} from './useProjects';

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

  const { mutate: updateProject, isPending: saving } = useUpdateProject();
  const { mutate: deleteProject, isPending: deleting } = useDeleteProject();
  const { mutate: updateMember } = useUpdateMember(projectId);
  const { mutate: removeMember } = useRemoveMember(projectId);

  const [deleteModalOpen, setDeleteModalOpen] = useState(false);

  const {
    register,
    handleSubmit,
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

  function onSaveDetails(values: DetailsForm) {
    updateProject(
      { projectId, body: { name: values.name, description: values.description || undefined } },
      { onSuccess: () => reset(values) },
    );
  }

  function handleDeleteConfirm() {
    deleteProject(projectId, {
      onSuccess: () => navigate('/projects'),
    });
  }

  if (projectLoading || membersLoading) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-400 text-sm">
        Loading…
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto px-6 py-8 space-y-10">
      <h1 className="text-xl font-semibold text-gray-900">Project Settings</h1>

      {/* ── Project details ── */}
      <section>
        <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-widest mb-4">
          Details
        </h2>
        <form onSubmit={handleSubmit(onSaveDetails)} className="space-y-4">
          <div>
            <label htmlFor="proj-name" className="block text-sm font-medium text-gray-700 mb-1">
              Name
            </label>
            <input
              id="proj-name"
              {...register('name')}
              disabled={!canEdit}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:border-transparent disabled:bg-gray-50 disabled:text-gray-400"
            />
            {errors.name && (
              <p className="mt-1 text-xs text-red-600">{errors.name.message}</p>
            )}
          </div>
          <div>
            <label htmlFor="proj-desc" className="block text-sm font-medium text-gray-700 mb-1">
              Description
            </label>
            <textarea
              id="proj-desc"
              {...register('description')}
              disabled={!canEdit}
              rows={3}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:border-transparent resize-none disabled:bg-gray-50 disabled:text-gray-400"
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
        <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-widest mb-4">
          Members
        </h2>
        <div className="space-y-4">
          {canEdit && <AddMemberForm projectId={projectId} />}
          <div className="border border-gray-200 rounded-xl overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="text-left px-4 py-2.5 text-xs font-medium text-gray-500">Member</th>
                  <th className="text-left px-4 py-2.5 text-xs font-medium text-gray-500">Role</th>
                  {canEdit && <th className="px-4 py-2.5" />}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {members.map((member) => {
                  const isSelf = member.userId === userId;
                  const isTargetOwner = member.role === 'OWNER';
                  const canModify = canEdit && !isSelf && !(isTargetOwner && !isOwner);
                  return (
                    <tr key={member.id} className="bg-white">
                      <td className="px-4 py-3">
                        <p className="font-medium text-gray-900">{member.userName}</p>
                        <p className="text-xs text-gray-500">{member.userEmail}</p>
                      </td>
                      <td className="px-4 py-3">
                        {canModify ? (
                          <select
                            value={member.role}
                            onChange={(e) =>
                              updateMember({ memberId: member.id, role: e.target.value })
                            }
                            className="rounded-lg border border-gray-200 px-2 py-1 text-xs bg-white focus:outline-none focus:ring-2 focus:border-transparent"
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

      {/* ── Danger zone ── */}
      {isOwner && (
        <section>
          <h2 className="text-sm font-semibold text-red-500 uppercase tracking-widest mb-4">
            Danger zone
          </h2>
          <div className="border border-red-200 rounded-xl p-4 flex items-center justify-between gap-4">
            <div>
              <p className="text-sm font-medium text-gray-900">Delete this project</p>
              <p className="text-xs text-gray-500 mt-0.5">
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
        onClose={() => setDeleteModalOpen(false)}
        title="Delete project?"
      >
        <div className="space-y-4">
          <p className="text-sm text-gray-600">
            This will permanently delete{' '}
            <span className="font-semibold">{project?.name}</span> and all its jobs, notes,
            and approvals. This cannot be undone.
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setDeleteModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="danger" loading={deleting} onClick={handleDeleteConfirm}>
              Delete project
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
