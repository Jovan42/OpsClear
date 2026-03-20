import { useState } from 'react';
import Modal from '../../components/Modal';
import Button from '../../components/Button';
import { useCreateMilestone, useUpdateMilestone } from '../jobs/useMilestones';
import type { MilestoneResponse } from '../../types';

interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
  milestone?: MilestoneResponse;
}

export default function MilestoneFormModal({ open, onClose, projectId, milestone }: Readonly<Props>) {
  const isEdit = !!milestone;
  const [name, setName] = useState(milestone?.name ?? '');
  const [description, setDescription] = useState(milestone?.description ?? '');
  const [deadline, setDeadline] = useState(
    milestone?.deadline ? milestone.deadline.substring(0, 10) : '',
  );

  const create = useCreateMilestone(projectId);
  const update = useUpdateMilestone(projectId);
  const isPending = create.isPending || update.isPending;
  const isError = create.isError || update.isError;

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    const body = {
      name: name.trim(),
      description: description.trim() || undefined,
      deadline: deadline || undefined,
    };
    if (isEdit) {
      update.mutate({ milestoneId: milestone.id, body }, { onSuccess: onClose });
    } else {
      create.mutate(body, { onSuccess: onClose });
    }
  }

  const inputClass =
    'w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:border-transparent';

  return (
    <Modal open={open} onClose={onClose} title={isEdit ? 'Edit Milestone' : 'New Milestone'}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            Name <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Beta release"
            maxLength={100}
            className={inputClass}
            autoFocus
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            Description
          </label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Optional description"
            rows={3}
            maxLength={500}
            className={`${inputClass} resize-none`}
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            Deadline
          </label>
          <input
            type="date"
            value={deadline}
            onChange={(e) => setDeadline(e.target.value)}
            className={inputClass}
          />
        </div>
        {isError && (
          <p className="text-sm text-red-600 dark:text-red-400">
            Failed to {isEdit ? 'update' : 'create'} milestone. Please try again.
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" disabled={!name.trim()} loading={isPending}>
            {isEdit ? 'Save' : 'Create'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
