import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { isAxiosError } from 'axios';
import { useTranslation } from 'react-i18next';
import { FileText } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import Button from '../../components/Button';
import EmptyState from '../../components/EmptyState';
import Modal from '../../components/Modal';
import Skeleton from '../../components/Skeleton';
import { useCurrentOrg } from './OrgContext';
import { useOrganisation, useUpdateOrganisation, useDeleteOrganisation, useOrgMembers } from './useOrganisation';
import { usePageTitle } from '../../hooks/usePageTitle';
import SubscriptionSection from './SubscriptionSection';
import PaddleBillingSection from './PaddleBillingSection';
import BillingHistorySection from './BillingHistorySection';
import { useOrgCreditBalance } from './useCredits';
import { useOrgTemplates, useDeleteOrgTemplate } from '../templates/useTemplates';
import TemplateFormModal from '../templates/TemplateFormModal';
import ConfirmModal from '../../components/ConfirmModal';
import PriorityBadge from '../../components/PriorityBadge';
import type { JobTemplateResponse } from '../../types';

export default function OrgSettingsPage() {
  const { t } = useTranslation(['org', 'common', 'feedback']);
  const navigate = useNavigate();
  const { userId } = useAuth();
  const { org: ctxOrg, setOrg, clearOrg, hasAddon } = useCurrentOrg();

  const { data: org, isLoading } = useOrganisation(ctxOrg?.id ?? null);
  const { data: members = [] } = useOrgMembers(ctxOrg?.id ?? null);
  usePageTitle(t('org:settingsPageTitle'));

  const callerRole = members.find((m) => m.userId === userId)?.role ?? null;
  const isOwner = callerRole === 'OWNER';
  const isOwnerOrAdmin = callerRole === 'OWNER' || callerRole === 'ADMIN';
  const { data: creditBalance } = useOrgCreditBalance(org?.id ?? null, isOwnerOrAdmin);

  const { mutate: updateOrg, isPending: saving } = useUpdateOrganisation(org?.id ?? '');
  const { mutate: deleteOrg, isPending: deleting } = useDeleteOrganisation();

  const [slugApiError, setSlugApiError] = useState<string | null>(null);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [billingHistoryOpen, setBillingHistoryOpen] = useState(false);

  const { data: orgTemplates = [], isLoading: templatesLoading } = useOrgTemplates(ctxOrg?.id ?? null);
  const deleteOrgTemplate = useDeleteOrgTemplate(ctxOrg?.id ?? '');
  const [showCreateTemplate, setShowCreateTemplate] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<JobTemplateResponse | null>(null);
  const [deletingTemplate, setDeletingTemplate] = useState<JobTemplateResponse | null>(null);
  const [deleteTemplateError, setDeleteTemplateError] = useState<string | null>(null);

  const schema = useMemo(
    () =>
      z.object({
        name: z.string().min(1, t('nameRequired')).max(100, t('maxNameLength')),
        slug: z
          .string()
          .min(2, t('slugLengthError'))
          .max(3, t('slugLengthError'))
          .regex(/^[A-Za-z]+$/, t('lettersOnly')),
      }),
    [t],
  );
  type FormValues = z.infer<typeof schema>;

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
          {t('org:noOrgFound')}{' '}
          <button
            onClick={() => navigate('/org/new')}
            className="text-blue-600 dark:text-blue-400 hover:underline"
          >
            {t('org:createOne')}
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
        {t('org:orgSettingsHeading')}
      </h1>

      {/* ── Members ── */}
      <section>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-widest mb-4">
          {t('org:membersSectionHeading')}
        </h2>
        <div className="border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
          <div className="p-4 flex items-center justify-between">
            <p className="text-sm text-gray-600 dark:text-gray-300">
              {t('org:manageMembersDesc')}
            </p>
            <Button variant="secondary" size="sm" onClick={() => navigate('/org/members')}>
              {t('org:manageMembersButton')}
            </Button>
          </div>
          {isOwnerOrAdmin && (
            <div className="p-4 flex items-center justify-between">
              <p className="text-sm text-gray-600 dark:text-gray-300">
                {t('org:inviteDesc')}
              </p>
              <Button variant="secondary" size="sm" onClick={() => navigate('/org/invites')}>
                {t('org:invitesButton')}
              </Button>
            </div>
          )}
        </div>
      </section>

      {/* ── Details ── */}
      <section>
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-widest mb-4">
          {t('org:detailsHeading')}
        </h2>
        <form onSubmit={handleSubmit(onSave)} className="space-y-4">
          <div>
            <label
              htmlFor="org-name"
              className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1"
            >
              {t('org:nameLabel')}
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
              {t('org:slugLabel')}{' '}
              <span className="text-xs font-normal text-gray-400 dark:text-gray-500">
                {t('org:slugHintShort')}
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
            <p>{t('org:createdByPrefix')} <span className="text-gray-600 dark:text-gray-300">{org.createdByName ?? org.createdBy}</span></p>
          </div>

          {isOwner && (
            <div className="flex gap-2">
              <Button type="submit" loading={saving} disabled={!isDirty}>
                {t('org:saveChanges')}
              </Button>
              {isDirty && (
                <Button variant="secondary" type="button" onClick={() => reset()}>
                  {t('common:cancel')}
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
              {t('org:orgTemplatesHeading')}
            </h2>
            {isOwnerOrAdmin && (
              <Button variant="secondary" size="sm" onClick={() => setShowCreateTemplate(true)}>
                {t('org:newTemplateButton')}
              </Button>
            )}
          </div>

          {templatesLoading ? (
            <div className="space-y-2">
              <Skeleton className="h-14 rounded-xl" />
              <Skeleton className="h-14 rounded-xl" />
            </div>
          ) : orgTemplates.length === 0 ? (
            <div className="border border-gray-200 dark:border-gray-700 rounded-xl">
              <EmptyState
                icon={FileText}
                message={t('org:noOrgTemplates')}
                action={{
                  label: t('org:createOne'),
                  onClick: () => setShowCreateTemplate(true),
                  canPerform: isOwnerOrAdmin,
                }}
              />
            </div>
          ) : (
            <div className="border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
              {orgTemplates.map((tpl) => (
                <div key={tpl.id} className="flex items-start justify-between gap-4 p-4">
                  <div className="min-w-0 space-y-1 flex-1">
                    <div className="flex items-center gap-2">
                      <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{tpl.name}</p>
                      {tpl.friendlyId && (
                        <span className="text-xs text-gray-400 dark:text-gray-500 font-mono">{tpl.friendlyId}</span>
                      )}
                      {tpl.priority && <PriorityBadge priority={tpl.priority} />}
                    </div>
                    {tpl.title && (
                      <p className="text-sm text-gray-500 dark:text-gray-400 truncate">{tpl.title}</p>
                    )}
                    <div className="flex items-center gap-3 text-xs text-gray-400 dark:text-gray-500">
                      {tpl.deadlineOffsetDays != null && <span>{t('org:deadlineOffset', { days: tpl.deadlineOffsetDays })}</span>}
                      {tpl.occurrenceCount > 0 && <span>{t('org:usedCount', { count: tpl.occurrenceCount })}</span>}
                    </div>
                  </div>
                  {isOwnerOrAdmin && (
                    <div className="flex items-center gap-2 shrink-0">
                      <Button variant="ghost" size="sm" onClick={() => setEditingTemplate(tpl)}>{t('common:edit')}</Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => { setDeletingTemplate(tpl); setDeleteTemplateError(null); }}
                        className="text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20"
                      >
                        {t('common:delete')}
                      </Button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {/* ── Credit balance ── */}
      {isOwnerOrAdmin && creditBalance && (
        <section>
          <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-widest mb-4">
            {t('feedback:creditBalance.heading')}
          </h2>
          <div className="border border-gray-200 dark:border-gray-700 rounded-xl px-6 py-5 flex items-center justify-between">
            <p className="text-sm text-gray-500 dark:text-gray-400">{t('feedback:creditBalance.description')}</p>
            <p className="text-lg font-semibold text-gray-900 dark:text-gray-100">{creditBalance.balance}</p>
          </div>
        </section>
      )}

      {/* ── Subscription ── */}
      {isOwner && (
        <section>
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-widest">
              {t('org:subscriptionHeading')}
            </h2>
            <Link to="/features" className="text-sm font-medium hover:underline" style={{ color: 'var(--brand)' }}>
              {t('org:seeAllFeatures')}
            </Link>
          </div>
          <SubscriptionSection orgId={org.id} />
          <div className="mt-4">
            <PaddleBillingSection orgId={org.id} />
          </div>
        </section>
      )}

      {/* ── Billing history (accordion, collapsed by default — settings already has a lot on the page) ── */}
      {isOwner && (
        <section>
          <div className="border border-gray-200 dark:border-gray-700 rounded-xl">
            <button
              type="button"
              onClick={() => setBillingHistoryOpen((v) => !v)}
              className="w-full flex items-center justify-between px-4 py-3 text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-widest hover:bg-gray-50 dark:hover:bg-gray-700/50 rounded-xl transition-colors"
            >
              <span>{t('org:billingHistoryHeading')}</span>
              <svg
                className={`w-4 h-4 text-gray-400 transition-transform ${billingHistoryOpen ? 'rotate-180' : ''}`}
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={2}
              >
                <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
              </svg>
            </button>
            {billingHistoryOpen && (
              <div className="px-4 pb-4 border-t border-gray-100 dark:border-gray-700 pt-3">
                <BillingHistorySection orgId={org.id} />
              </div>
            )}
          </div>
        </section>
      )}

      {/* ── Danger zone ── */}
      {isOwner && (
        <section>
          <h2 className="text-sm font-semibold text-red-500 uppercase tracking-widest mb-4">
            {t('org:dangerZoneHeading')}
          </h2>
          <div className="border border-red-200 dark:border-red-900/50 rounded-xl p-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
                {t('org:deleteOrgTitle')}
              </p>
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                {t('org:deleteOrgDesc')}
              </p>
            </div>
            <Button variant="danger" size="sm" onClick={() => setDeleteModalOpen(true)}>
              {t('org:deleteOrgButton')}
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
        title={t('org:deleteTemplateTitle')}
        message={t('org:deleteTemplateMessage', { name: deletingTemplate?.name })}
        confirmLabel={t('common:delete')}
        variant="danger"
        isPending={deleteOrgTemplate.isPending}
        errorMessage={deleteTemplateError}
      />

      {/* ── Delete confirmation modal ── */}
      <Modal
        open={deleteModalOpen}
        onClose={() => setDeleteModalOpen(false)}
        title={t('org:deleteOrgModalTitle')}
      >
        <div className="space-y-4">
          <p className="text-sm text-gray-600 dark:text-gray-300">
            {t('org:deleteOrgModalMessagePrefix')}{' '}
            <span className="font-semibold">{org.name}</span>.{' '}
            <span className="font-medium text-red-600 dark:text-red-400">
              {t('org:cannotBeUndone')}
            </span>
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setDeleteModalOpen(false)}>
              {t('common:cancel')}
            </Button>
            <Button variant="danger" loading={deleting} onClick={handleDelete}>
              {t('org:deleteOrgButton')}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
