import type { CSSProperties } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import Modal from '../../components/Modal';
import Button from '../../components/Button';
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

export default function NewProjectModal({ open, onClose }: NewProjectModalProps) {
  const { mutate, isPending } = useCreateProject();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  function onSubmit(values: FormValues) {
    mutate(
      { name: values.name, description: values.description || undefined },
      {
        onSuccess: () => {
          reset();
          onClose();
        },
      },
    );
  }

  function handleClose() {
    reset();
    onClose();
  }

  return (
    <Modal open={open} onClose={handleClose} title="New Project">
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
          <textarea
            {...register('description')}
            className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent resize-none"
            style={{ '--tw-ring-color': 'var(--brand)' } as CSSProperties}
            placeholder="Optional — what is this project about?"
            rows={3}
          />
          {errors.description && (
            <p className="mt-1 text-xs text-red-600">{errors.description.message}</p>
          )}
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={handleClose}>
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
