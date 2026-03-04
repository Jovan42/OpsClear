import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import Modal from '../../components/Modal';
import Button from '../../components/Button';
import { useCreateJob } from './useJobs';
import { useProjectMembers } from '../projects/useProjects';
import type { ProjectMemberResponse } from '../../types';

const schema = z.object({
  title: z.string().min(1, 'Title is required').max(255, 'Max 255 characters'),
  description: z.string().max(1000, 'Max 1000 characters').optional(),
  client: z.string().max(255, 'Max 255 characters').optional(),
  deadline: z.string().optional(),
});
type FormValues = z.infer<typeof schema>;

interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
}

export default function NewJobModal({ open, onClose, projectId }: Props) {
  const { mutate: createJob, isPending } = useCreateJob(projectId);
  const { data: members = [] } = useProjectMembers(projectId);

  const [assignedTo, setAssignedTo] = useState<ProjectMemberResponse | null>(null);
  const [memberSearch, setMemberSearch] = useState('');
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const filteredMembers = memberSearch.length >= 1
    ? members.filter(
        (m) =>
          m.userName.toLowerCase().includes(memberSearch.toLowerCase()) ||
          m.userEmail.toLowerCase().includes(memberSearch.toLowerCase()),
      )
    : members;

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setDropdownOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  function handleClose() {
    reset();
    setAssignedTo(null);
    setMemberSearch('');
    onClose();
  }

  function onSubmit(values: FormValues) {
    createJob(
      {
        title: values.title,
        description: values.description || undefined,
        client: values.client || undefined,
        assignedTo: assignedTo?.userId || undefined,
        deadline: values.deadline ? new Date(values.deadline).toISOString() : undefined,
      },
      { onSuccess: handleClose },
    );
  }

  return (
    <Modal open={open} onClose={handleClose} title="New Job">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Title <span className="text-red-500">*</span>
          </label>
          <input
            {...register('title')}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:border-transparent"
            placeholder="e.g. Fix login bug"
            autoFocus
          />
          {errors.title && <p className="mt-1 text-xs text-red-600">{errors.title.message}</p>}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
          <textarea
            {...register('description')}
            rows={2}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:border-transparent resize-none"
            placeholder="Optional details…"
          />
          {errors.description && (
            <p className="mt-1 text-xs text-red-600">{errors.description.message}</p>
          )}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Client</label>
            <input
              {...register('client')}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:border-transparent"
              placeholder="Client name"
            />
            {errors.client && (
              <p className="mt-1 text-xs text-red-600">{errors.client.message}</p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Deadline</label>
            <input
              type="date"
              {...register('deadline')}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:border-transparent"
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Assign to</label>
          <div className="relative" ref={containerRef}>
            <input
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:border-transparent"
              placeholder="Search member…"
              value={assignedTo ? assignedTo.userName : memberSearch}
              onChange={(e) => {
                setMemberSearch(e.target.value);
                setAssignedTo(null);
                setDropdownOpen(true);
              }}
              onFocus={() => setDropdownOpen(true)}
              autoComplete="off"
            />
            {assignedTo && (
              <button
                type="button"
                onClick={() => { setAssignedTo(null); setMemberSearch(''); }}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 cursor-pointer"
              >
                ×
              </button>
            )}
            {dropdownOpen && !assignedTo && filteredMembers.length > 0 && (
              <ul className="absolute z-10 mt-1 w-full bg-white border border-gray-200 rounded-lg shadow-lg overflow-hidden">
                {filteredMembers.map((m) => (
                  <li key={m.id}>
                    <button
                      type="button"
                      onClick={() => {
                        setAssignedTo(m);
                        setMemberSearch('');
                        setDropdownOpen(false);
                      }}
                      className="w-full text-left px-3 py-2 hover:bg-gray-50 cursor-pointer"
                    >
                      <p className="text-sm font-medium text-gray-900">{m.userName}</p>
                      <p className="text-xs text-gray-500">{m.userEmail}</p>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={handleClose}>
            Cancel
          </Button>
          <Button type="submit" loading={isPending}>
            Create job
          </Button>
        </div>
      </form>
    </Modal>
  );
}
