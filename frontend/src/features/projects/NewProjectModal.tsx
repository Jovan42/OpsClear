import type { CSSProperties } from 'react';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import Modal from '../../components/Modal';
import Button from '../../components/Button';
import MarkdownEditor from '../../components/MarkdownEditor';
import { useCreateProject } from './useProjects';

const schema = z.object({
  name: z.string().min(1, 'Name is required').max(80, 'Max 80 characters'),
  description: z.string().max(255, 'Max 255 characters').optional(),
});

type FormValues = z.infer<typeof schema>;

interface NewProjectModalProps {
  open: boolean;
  onClose: () => void;
}

export default function NewProjectModal({ open, onClose }: Readonly<NewProjectModalProps>) {
  const { mutate, isPending } = useCreateProject();
  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

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
    <Modal open={open} onClose={doClose} title="New Project">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            Name <span className="text-red-500">*</span>
          </label>
          <input
            {...register('name')}
            className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent"
            style={{ '--tw-ring-color': 'var(--brand)' } as CSSProperties}
            placeholder="e.g. Website Redesign"
            autoFocus
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
                placeholder="Optional — what is this project about?"
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
              Block reasons
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
                Optional — pre-populate reasons team members can select when blocking a job.
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
                        Remove
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
                  placeholder="e.g. Waiting on client"
                  className="flex-1 rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-1.5 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent"
                />
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={addReason}
                  disabled={!reasonInput.trim()}
                >
                  Add
                </Button>
              </div>
            </div>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={doClose}>
            Cancel
          </Button>
          <Button type="submit" loading={isPending}>
            Create project
          </Button>
        </div>
      </form>
    </Modal>
  );
}
