import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { isAxiosError } from 'axios';
import { useAuth } from '../../auth/AuthContext';
import Button from '../../components/Button';
import Modal from '../../components/Modal';
import Skeleton from '../../components/Skeleton';
import { useCurrentOrg } from './OrgContext';
import { useOrganisation, useUpdateOrganisation, useDeleteOrganisation, useOrgMembers } from './useOrganisation';
import { usePageTitle } from '../../hooks/usePageTitle';
import SubscriptionSection from './SubscriptionSection';
import { useOrgTemplates, useDeleteOrgTemplate } from '../templates/useTemplates';
import TemplateFormModal from '../templates/TemplateFormModal';
import ConfirmModal from '../../components/ConfirmModal';
import PriorityBadge from '../../components/PriorityBadge';
import type { JobTemplateResponse } from '../../types';

const schema = z.object({
  name: z.string().min(1, 'Name is required').max(100, 'Max 100 characters'),
  slug: z
    .string()
    .min(2, 'Slug must be 2–3 letters')
    .max(3, 'Slug must be 2–3 letters')
    .regex(/^[A-Za-z]+$/, 'Letters only'),
});
type FormValues = z.infer<typeof schema>;

export default function OrgSettingsPage() {
  const navigate = useNavigate();
  const { userId } = useAuth();
  const { org: ctxOrg, setOrg, clearOrg, hasAddon } = useCurrentOrg();

  const { data: org, isLoading } = useOrganisation(ctxOrg?.id ?? null);
  const { data: members = [] } = useOrgMembers(ctxOrg?.id ?? null);
  usePageTitle('Organisation settings');

  const callerRole = members.find((m) => m.userId === userId)?.role ?? null;
  const isOwner = callerRole === 'OWNER';
  const isOwnerOrAdmin = callerRole === 'OWNER' || callerRole === 'ADMIN';

  const { mutate: updateOrg, isPending: saving } = useUpdateOrganisation(org?.id ?? '');
  const { mutate: deleteOrg, isPending: deleting } = useDeleteOrganisation();

  const [slugApiError, setSlugApiError] = useState<string | null>(null);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);

  const { data: orgTemplates = [], isLoading: templatesLoading } = useOrgTemplates(ctxOrg?.id ?? null);
  const deleteOrgTemplate = useDeleteOrgTemplate(ctxOrg?.id ?? '');
  const [showCreateTemplate, setShowCreateTemplate] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<JobTemplateResponse | null>(null);
  const [deletingTemplate, setDeletingTemplate] = useState<JobTemplateResponse | null>(null);
  const [deleteTemplateError, setDeleteTemplateError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isDirty },
    reset,
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    values: org ? { name: org.name, slug: org.slug } : undefined,
  });

  function onSave(values: FormValues) {
    setSlugApiError(null);
    updateOrg(
      { name: values.name, slug: values.slug.toUpperCase() },
      {
        onSuccess: (data) => {
          setOrg(data);
          reset({ name: data.name, slug: data.slug });
        },
        onError: (err) => {
          if (isAxiosError(err) && err.response?.data?.message) {
            setSlugApiError(err.response.data.message as string);
          }
        },
      },
    );
  }

  function handleDelete() {
    if (!org) return;
    deleteOrg(org.id, {
      onSuccess: () => {
        clearOrg();
        navigate('/projects');
      },
    });
  }

  if (!ctxOrg) {
    return (
      <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8">
        <p className="text-sm text-gray-500 dark:text-gray-400">
          No organisation found.{' '}
          <button
            onClick={() => navigate('/org/new')}
            className="text-blue-600 dark:text-blue-400 hover:underline"
          >
            Create one
          </button>
        </p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8 space-y-6">
        <Skeleton className="h-7 w-48" />
        <div className="space-y-3">
          <Skeleton className="h-3 w-12" />
          <Skeleton className="h-10 rounded-lg" />
          <Skeleton className="h-3 w-10" />
          <Skeleton className="h-10 rounded-lg w-36" />
        </div>
      </div>
    );
  }

  if (!org) return null;

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8 space-y-10">
      <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">
        Organisation Settings
      </h1>

      {/* ── Members ── */}
      <section>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-widest mb-4">
          Members
        </h2>
        <div className="border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
          <div className="p-4 flex items-center justify-between">
            <p className="text-sm text-gray-600 dark:text-gray-300">
              Manage who has access to this organisation.
            </p>
            <Button variant="secondary" size="sm" onClick={() => navigate('/org/members')}>
              Manage members
            </Button>
          </div>
          {isOwnerOrAdmin && (
            <div className="p-4 flex items-center justify-between">
              <p className="text-sm text-gray-600 dark:text-gray-300">
                Invite people by email and manage pending invites.
              </p>
              <Button variant="secondary" size="sm" onClick={() => navigate('/org/invites')}>
                Invites
              </Button>
            </div>
          )}
        </div>
      </section>

      {/* ── Details ── */}
      <section>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-widest mb-4">
          Details
        </h2>
        <form onSubmit={handleSubmit(onSave)} className="space-y-4">
          <div>
            <label
              htmlFor="org-name"
              className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1"
            >
              Name
            </label>
            <input
              id="org-name"
              {...register('name')}
              disabled={!isOwner}
              className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:border-transparent disabled:bg-gray-50 dark:disabled:bg-gray-800 disabled:text-gray-400 dark:disabled:text-gray-500"
            />
            {errors.name && (
              <p className="mt-1 text-xs text-red-600">{errors.name.message}</p>
            )}
          </div>

          <div>
            <label
              htmlFor="org-slug"
              className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1"
            >
              Slug{' '}
              <span className="text-xs font-normal text-gray-400 dark:text-gray-500">
                2–3 letters, URL prefix
              </span>
            </label>
            <input
              id="org-slug"
              {...register('slug')}
              onChange={(e) => {
                const upper = e.target.value.toUpperCase().replace(/[^A-Z]/g, '');
                setValue('slug', upper, { shouldValidate: true });
              }}
              disabled={!isOwner}
              maxLength={3}
              className="w-28 rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm font-mono bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:border-transparent disabled:bg-gray-50 dark:disabled:bg-gray-800 disabled:text-gray-400 dark:disabled:text-gray-500 uppercase"
            />
            {errors.slug && (
              <p className="mt-1 text-xs text-red-600">{errors.slug.message}</p>
            )}
            {slugApiError && (
              <p className="mt-1 text-xs text-red-600">{slugApiError}</p>
            )}
          </div>

          <div className="text-xs text-gray-400 dark:text-gray-500 space-y-0.5">
            <p>Created by <span className="text-gray-600 dark:text-gray-300">{org.createdByName ?? org.createdBy}</span></p>
          </div>

          {isOwner && (
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

      {/* ── Org Templates ── */}
      {hasAddon('JOB_TEMPLATES') && (
        <section>
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-widest">
              Org Templates
            </h2>
            {isOwnerOrAdmin && (
              <Button variant="secondary" size="sm" onClick={() => setShowCreateTemplate(true)}>
                + New Template
              </Button>
            )}
          </div>

          {templatesLoading ? (
            <div className="space-y-2">
              <Skeleton className="h-14 rounded-xl" />
              <Skeleton className="h-14 rounded-xl" />
            </div>
          ) : orgTemplates.length === 0 ? (
            <div className="border border-gray-200 dark:border-gray-700 rounded-xl p-6 text-center">
              <p className="text-sm text-gray-500 dark:text-gray-400">
                No org-level templates yet.{' '}
                {isOwnerOrAdmin && (
                  <button
                    onClick={() => setShowCreateTemplate(true)}
                    className="text-blue-600 dark:text-blue-400 hover:underline"
                  >
                    Create one
                  </button>
                )}
              </p>
            </div>
          ) : (
            <div className="border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
              {orgTemplates.map((t) => (
                <div key={t.id} className="flex items-start justify-between gap-4 p-4">
                  <div className="min-w-0 space-y-1 flex-1">
                    <div className="flex items-center gap-2">
                      <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{t.name}</p>
                      {t.friendlyId && (
                        <span className="text-xs text-gray-400 dark:text-gray-500 font-mono">{t.friendlyId}</span>
                      )}
                      {t.priority && <PriorityBadge priority={t.priority} />}
                    </div>
                    {t.title && (
                      <p className="text-sm text-gray-500 dark:text-gray-400 truncate">{t.title}</p>
                    )}
                    <div className="flex items-center gap-3 text-xs text-gray-400 dark:text-gray-500">
                      {t.deadlineOffsetDays != null && <span>+{t.deadlineOffsetDays}d deadline</span>}
                      {t.occurrenceCount > 0 && <span>Used {t.occurrenceCount}×</span>}
                    </div>
                  </div>
                  {isOwnerOrAdmin && (
                    <div className="flex items-center gap-2 shrink-0">
                      <Button variant="ghost" size="sm" onClick={() => setEditingTemplate(t)}>Edit</Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => { setDeletingTemplate(t); setDeleteTemplateError(null); }}
                        className="text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20"
                      >
                        Delete
                      </Button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {/* ── Subscription ── */}
      {isOwner && (
        <section>
          <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-widest mb-4">
            Subscription
          </h2>
          <SubscriptionSection orgId={org.id} />
        </section>
      )}

      {/* ── Danger zone ── */}
      {isOwner && (
        <section>
          <h2 className="text-sm font-semibold text-red-500 uppercase tracking-widest mb-4">
            Danger zone
          </h2>
          <div className="border border-red-200 dark:border-red-900/50 rounded-xl p-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
                Delete this organisation
              </p>
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                Permanently removes the organisation. This cannot be undone.
              </p>
            </div>
            <Button variant="danger" size="sm" onClick={() => setDeleteModalOpen(true)}>
              Delete organisation
            </Button>
          </div>
        </section>
      )}

      {/* ── Org template modals ── */}
      <TemplateFormModal
        key={showCreateTemplate ? 'org-create-open' : 'org-create-closed'}
        open={showCreateTemplate}
        onClose={() => setShowCreateTemplate(false)}
        orgId={org.id}
      />

      <TemplateFormModal
        key={editingTemplate?.id ?? 'org-edit-closed'}
        open={editingTemplate !== null}
        onClose={() => setEditingTemplate(null)}
        orgId={org.id}
        template={editingTemplate ?? undefined}
      />

      <ConfirmModal
        open={deletingTemplate !== null}
        onClose={() => { setDeletingTemplate(null); setDeleteTemplateError(null); }}
        onConfirm={() => {
          if (!deletingTemplate) return;
          deleteOrgTemplate.mutate(deletingTemplate.id, {
            onSuccess: () => { setDeletingTemplate(null); setDeleteTemplateError(null); },
            onError: (err) => {
              if (isAxiosError(err) && err.response?.status === 409) {
                setDeleteTemplateError(err.response.data.message as string);
              }
            },
          });
        }}
        title="Delete Template"
        message={`Delete "${deletingTemplate?.name}"? This cannot be undone.`}
        confirmLabel="Delete"
        variant="danger"
        isPending={deleteOrgTemplate.isPending}
        errorMessage={deleteTemplateError}
      />

      {/* ── Delete confirmation modal ── */}
      <Modal
        open={deleteModalOpen}
        onClose={() => setDeleteModalOpen(false)}
        title="Delete organisation?"
      >
        <div className="space-y-4">
          <p className="text-sm text-gray-600 dark:text-gray-300">
            This will permanently delete{' '}
            <span className="font-semibold">{org.name}</span>.{' '}
            <span className="font-medium text-red-600 dark:text-red-400">
              This cannot be undone.
            </span>
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setDeleteModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="danger" loading={deleting} onClick={handleDelete}>
              Delete organisation
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
