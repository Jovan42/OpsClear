import type { CSSProperties } from 'react';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import Modal from '../../components/Modal';
import Button from '../../components/Button';
import MarkdownEditor from '../../components/MarkdownEditor';
import { useCreateProject } from './useProjects';

function buildSchema(t: TFunction) {
  return z.object({
    name: z.string().min(1, t('projects:validation.nameRequired')).max(80, t('projects:validation.nameMaxLength')),
    description: z.string().max(10000, t('projects:validation.descriptionMaxLength')).optional(),
  });
}

type FormValues = z.infer<ReturnType<typeof buildSchema>>;

interface NewProjectModalProps {
  open: boolean;
  onClose: () => void;
}

export default function NewProjectModal({ open, onClose }: Readonly<NewProjectModalProps>) {
  const { t } = useTranslation(['projects', 'common']);
  const { mutate, isPending } = useCreateProject();
  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(buildSchema(t)) });

  const [accordionOpen, setAccordionOpen] = useState(false);
  const [reasonInput, setReasonInput] = useState('');
  const [reasons, setReasons] = useState<string[]>([]);

  function addReason() {
    const trimmed = reasonInput.trim();
    if (!trimmed || reasons.includes(trimmed)) return;
    setReasons((prev) => [...prev, trimmed]);
    setReasonInput('');
  }

  function removeReason(r: string) {
    setReasons((prev) => prev.filter((x) => x !== r));
  }

  function onSubmit(values: FormValues) {
    mutate(
      {
        name: values.name,
        description: values.description || undefined,
        blockReasons: reasons.length > 0 ? reasons : undefined,
      },
      { onSuccess: doClose },
    );
  }

  function doClose() {
    reset();
    setReasonInput('');
    setReasons([]);
    setAccordionOpen(false);
    onClose();
  }

  return (
    <Modal open={open} onClose={doClose} title={t('projects:newProjectModal.title')}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {t('projects:newProjectModal.nameLabel')} <span className="text-red-500">*</span>
          </label>
          <input
            {...register('name')}
            className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent"
            style={{ '--tw-ring-color': 'var(--brand)' } as CSSProperties}
            placeholder={t('projects:newProjectModal.namePlaceholder')}
            autoFocus
          />
          {errors.name && (
            <p className="mt-1 text-xs text-red-600">{errors.name.message}</p>
          )}
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {t('projects:newProjectModal.descriptionLabel')}
          </label>
          <Controller
            name="description"
            control={control}
            render={({ field }) => (
              <MarkdownEditor
                value={field.value ?? ''}
                onChange={field.onChange}
                placeholder={t('projects:newProjectModal.descriptionPlaceholder')}
                rows={3}
              />
            )}
          />
          {errors.description && (
            <p className="mt-1 text-xs text-red-600">{errors.description.message}</p>
          )}
        </div>

        {/* ── Block reasons accordion ── */}
        <div className="border border-gray-200 dark:border-gray-700 rounded-lg">
          <button
            type="button"
            onClick={() => setAccordionOpen((v) => !v)}
            className="w-full flex items-center justify-between px-4 py-2.5 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700/50 rounded-lg transition-colors"
          >
            <span>
              {t('projects:newProjectModal.blockReasonsHeading')}
              {reasons.length > 0 && (
                <span className="ml-2 text-xs font-normal text-gray-500 dark:text-gray-400">
                  ({reasons.length})
                </span>
              )}
            </span>
            <svg
              className={`w-4 h-4 text-gray-400 transition-transform ${accordionOpen ? 'rotate-180' : ''}`}
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={2}
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
            </svg>
          </button>

          {accordionOpen && (
            <div className="px-4 pb-4 space-y-3 border-t border-gray-100 dark:border-gray-700 pt-3">
              <p className="text-xs text-gray-500 dark:text-gray-400">
                {t('projects:newProjectModal.blockReasonsHelp')}
              </p>
              {reasons.length > 0 && (
                <ul className="space-y-1">
                  {reasons.map((r) => (
                    <li key={r} className="flex items-center justify-between rounded-lg bg-gray-50 dark:bg-gray-700/50 px-3 py-1.5">
                      <span className="text-sm text-gray-800 dark:text-gray-200">{r}</span>
                      <button
                        type="button"
                        onClick={() => removeReason(r)}
                        className="text-xs text-red-500 hover:text-red-700 transition-colors ml-3 shrink-0 cursor-pointer"
                      >
                        {t('common:remove')}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              <div className="flex gap-2">
                <input
                  value={reasonInput}
                  onChange={(e) => setReasonInput(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addReason(); } }}
                  placeholder={t('projects:newProjectModal.reasonPlaceholder')}
                  className="flex-1 rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-1.5 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent"
                />
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={addReason}
                  disabled={!reasonInput.trim()}
                >
                  {t('common:add')}
                </Button>
              </div>
            </div>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={doClose}>
            {t('common:cancel')}
          </Button>
          <Button type="submit" loading={isPending}>
            {t('projects:newProjectModal.createButton')}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
