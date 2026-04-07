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
import { useOrganisation, useUpdateOrganisation, useDeleteOrganisation } from './useOrganisation';
import { usePageTitle } from '../../hooks/usePageTitle';

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
  const { org: ctxOrg, setOrg, clearOrg } = useCurrentOrg();

  const { data: org, isLoading } = useOrganisation(ctxOrg?.id ?? null);
  usePageTitle('Organisation settings');

  const isOwner = org?.createdBy === userId;

  const { mutate: updateOrg, isPending: saving } = useUpdateOrganisation(org?.id ?? '');
  const { mutate: deleteOrg, isPending: deleting } = useDeleteOrganisation();

  const [slugApiError, setSlugApiError] = useState<string | null>(null);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);

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
