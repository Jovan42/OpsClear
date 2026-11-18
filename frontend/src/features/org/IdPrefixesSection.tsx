import { useMemo, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import Button from '../../components/Button';
import ConfirmModal from '../../components/ConfirmModal';
import Skeleton from '../../components/Skeleton';
import { useOrgSettings, useUpdateOrgSettings } from './useOrganisation';
import {
  PREFIX_FIELDS,
  PREFIX_PATTERN,
  changedPrefixes,
  duplicatePrefixFields,
  fieldForPrefixError,
  normalisePrefixInput,
  prefixValues,
  previewNextId,
  type PrefixField,
  type PrefixValues,
} from './idPrefixes';

interface Props {
  orgId: string;
  isOwner: boolean;
}

const FIELD_COPY: Record<PrefixField, { id: string; label: string; preview: string }> = {
  projectPrefix: { id: 'prefix-project', label: 'prefixProjectsLabel', preview: 'prefixNextProject' },
  jobPrefix: { id: 'prefix-job', label: 'prefixJobsLabel', preview: 'prefixNextJob' },
  milestonePrefix: { id: 'prefix-milestone', label: 'prefixMilestonesLabel', preview: 'prefixNextMilestone' },
  templatePrefix: { id: 'prefix-template', label: 'prefixTemplatesLabel', preview: 'prefixNextTemplate' },
};

/**
 * ADR-0060: the org's four friendly-ID prefixes. The Owner edits them, with a live preview
 * of the next ID and a confirmation that existing IDs keep their prefix; others read them.
 */
export default function IdPrefixesSection({ orgId, isOwner }: Props) {
  const { t } = useTranslation(['org', 'common']);
  const { data: settings, isLoading } = useOrgSettings(orgId);
  const { mutate: updateSettings, isPending: saving } = useUpdateOrgSettings(orgId);
  const [pendingValues, setPendingValues] = useState<PrefixValues | null>(null);

  const schema = useMemo(() => {
    const prefix = z.string().regex(PREFIX_PATTERN, t('org:prefixFormatError'));
    return z
      .object({ projectPrefix: prefix, jobPrefix: prefix, milestonePrefix: prefix, templatePrefix: prefix })
      .superRefine((values, ctx) => {
        for (const field of duplicatePrefixFields(values)) {
          ctx.addIssue({ code: 'custom', path: [field], message: t('org:prefixDuplicateError') });
        }
      });
  }, [t]);

  const {
    register,
    handleSubmit,
    setValue,
    setError,
    reset,
    control,
    formState: { errors, isDirty },
  } = useForm<PrefixValues>({
    resolver: zodResolver(schema),
    values: settings ? prefixValues(settings) : undefined,
  });

  const values = useWatch({ control });

  function confirmSave() {
    if (!settings || !pendingValues) return;
    const submitted = pendingValues;
    updateSettings(changedPrefixes(submitted, settings), {
      onSuccess: () => setPendingValues(null),
      onError: (err) => {
        setPendingValues(null);
        // Show the backend's message, not its error label ("Bad Request" / "Validation Error")
        const message = isAxiosError(err) && err.response?.status === 400
          ? (err.response.data as { message?: string } | undefined)?.message
          : undefined;
        if (!message) return; // other errors already got the API client's toast
        const field = fieldForPrefixError(message, submitted);
        if (field) setError(field, { type: 'server', message });
        else toast.error(message);
      },
    });
  }

  if (isLoading) {
    return (
      <section>
        <Skeleton className="h-4 w-32 mb-4" />
        <Skeleton className="h-40 rounded-xl" />
      </section>
    );
  }

  if (!settings) return null;

  const exampleId = `${settings.jobPrefix}-042`;

  return (
    <section>
      <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-widest mb-4">
        {t('org:idPrefixesHeading')}
      </h2>
      <form onSubmit={handleSubmit((v) => setPendingValues(v))} className="space-y-4" noValidate>
        <p className="text-sm text-gray-500 dark:text-gray-400">
          {t('org:idPrefixesDesc', { example: exampleId })}
        </p>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {PREFIX_FIELDS.map((field) => {
            const copy = FIELD_COPY[field];
            const error = errors[field]?.message;
            return (
              <div key={field}>
                <label
                  htmlFor={copy.id}
                  className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1"
                >
                  {t(`org:${copy.label}`)}
                </label>
                <input
                  id={copy.id}
                  {...register(field)}
                  onChange={(e) =>
                    setValue(field, normalisePrefixInput(e.target.value), {
                      shouldValidate: true,
                      shouldDirty: true,
                    })
                  }
                  disabled={!isOwner}
                  maxLength={3}
                  aria-invalid={error ? true : undefined}
                  aria-describedby={`${copy.id}-preview`}
                  className="w-28 rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm font-mono bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:border-transparent disabled:bg-gray-50 dark:disabled:bg-gray-800 disabled:text-gray-400 dark:disabled:text-gray-500 uppercase"
                />
                <p id={`${copy.id}-preview`} className="mt-1 text-xs text-gray-400 dark:text-gray-500">
                  {t(`org:${copy.preview}`, { id: previewNextId(settings, field, values[field] ?? '') })}
                </p>
                {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
              </div>
            );
          })}
        </div>

        {isOwner && (
          <div className="flex gap-2">
            <Button type="submit" disabled={!isDirty}>
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

      <ConfirmModal
        open={pendingValues !== null}
        onClose={() => setPendingValues(null)}
        onConfirm={confirmSave}
        title={t('org:prefixConfirmTitle')}
        message={t('org:prefixConfirmMessage', { example: exampleId })}
        confirmLabel={t('org:prefixConfirmButton')}
        isPending={saving}
      />
    </section>
  );
}
