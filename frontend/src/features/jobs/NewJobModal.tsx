import { useEffect, useRef, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import Modal from '../../components/Modal';
import Button from '../../components/Button';
import MarkdownEditor from '../../components/MarkdownEditor';
import { useCreateJob, useUpdateJob } from './useJobs';
import { useProjectMembers } from '../projects/useProjects';
import type { JobPriority, JobResponse, MilestoneResponse, ProjectMemberResponse } from '../../types';

const PRIORITIES: { value: JobPriority; label: string }[] = [
  { value: 'LOW',      label: 'Low' },
  { value: 'MEDIUM',   label: 'Medium' },
  { value: 'HIGH',     label: 'High' },
  { value: 'CRITICAL', label: 'Critical' },
];

const schema = z.object({
  title: z.string().min(1, 'Title is required').max(255, 'Max 255 characters'),
  description: z.string().max(1000, 'Max 1000 characters').optional(),
  client: z.string().max(255, 'Max 255 characters').optional(),
  deadline: z.string().optional(),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']),
  milestoneId: z.string().optional(),
});
type FormValues = z.infer<typeof schema>;

interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
  job?: JobResponse;
  milestones?: MilestoneResponse[];
}

export default function NewJobModal({ open, onClose, projectId, job, milestones = [] }: Props) {
  const isEdit = Boolean(job);
  const { mutate: createJob, isPending: isCreating } = useCreateJob(projectId);
  const { mutate: updateJob, isPending: isUpdating } = useUpdateJob(projectId);
  const isPending = isCreating || isUpdating;

  const { data: members = [] } = useProjectMembers(projectId);

  const [assignedTo, setAssignedTo] = useState<ProjectMemberResponse | null>(null);
  const [memberSearch, setMemberSearch] = useState('');
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const [prevKey, setPrevKey] = useState('');
  const currentKey = open ? (job?.id ?? 'new') : '';
  if (currentKey !== prevKey) {
    setPrevKey(currentKey);
    if (open) {
      setAssignedTo(job ? (members.find((m) => m.userId === job.assignedTo) ?? null) : null);
      setMemberSearch('');
    }
  }

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
    control,
    formState: { errors },
    reset,
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  useEffect(() => {
    if (!open) return;
    if (job) {
      reset({
        title: job.title,
        description: job.description ?? '',
        client: job.client ?? '',
        deadline: job.deadline ? new Date(job.deadline).toISOString().split('T')[0] : '',
        priority: job.priority,
        milestoneId: job.milestoneId ?? '',
      });
    } else {
      reset({ title: '', description: '', client: '', deadline: '', priority: 'MEDIUM', milestoneId: '' });
    }
  }, [open, job, reset]);

  function handleClose() {
    reset();
    setAssignedTo(null);
    setMemberSearch('');
    onClose();
  }

  function onSubmit(values: FormValues) {
    const body = {
      title: values.title,
      description: values.description || undefined,
      client: values.client || undefined,
      assignedTo: assignedTo?.userId || undefined,
      deadline: values.deadline ? new Date(values.deadline).toISOString() : undefined,
      priority: values.priority,
      milestoneId: values.milestoneId || undefined,
    };

    if (isEdit && job) {
      updateJob({ jobId: job.id, body }, { onSuccess: handleClose });
    } else {
      createJob(body, { onSuccess: handleClose });
    }
  }

  const inputClass = 'w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent';
  const labelClass = 'block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1';

  return (
    <Modal open={open} onClose={handleClose} title={isEdit ? 'Edit Job' : 'New Job'}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label className={labelClass}>
            Title <span className="text-red-500">*</span>
          </label>
          <input
            {...register('title')}
            className={inputClass}
            placeholder="e.g. Fix login bug"
            autoFocus
          />
          {errors.title && <p className="mt-1 text-xs text-red-600">{errors.title.message}</p>}
        </div>

        <div>
          <label className={labelClass}>Description</label>
          <Controller
            name="description"
            control={control}
            render={({ field }) => (
              <MarkdownEditor
                value={field.value ?? ''}
                onChange={field.onChange}
                placeholder="Optional details… (markdown supported)"
                rows={8}
              />
            )}
          />
          {errors.description && (
            <p className="mt-1 text-xs text-red-600">{errors.description.message}</p>
          )}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Client</label>
            <input
              {...register('client')}
              className={inputClass}
              placeholder="Client name"
            />
            {errors.client && (
              <p className="mt-1 text-xs text-red-600">{errors.client.message}</p>
            )}
          </div>

          <div>
            <label className={labelClass}>Deadline</label>
            <input
              type="date"
              {...register('deadline')}
              className={inputClass}
            />
          </div>
        </div>

        <div>
          <label className={labelClass}>Priority</label>
          <select {...register('priority')} className={inputClass}>
            {PRIORITIES.map(({ value, label }) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </div>

        {milestones.length > 0 && (
          <div>
            <label className={labelClass}>Milestone</label>
            <select {...register('milestoneId')} className={inputClass}>
              <option value="">No milestone</option>
              {milestones.map((ms) => (
                <option key={ms.id} value={ms.id}>{ms.name}</option>
              ))}
            </select>
          </div>
        )}

        <div>
          <label className={labelClass}>Assign to</label>
          <div className="relative" ref={containerRef}>
            <input
              className={inputClass}
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
                className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 cursor-pointer"
              >
                ×
              </button>
            )}
            {dropdownOpen && !assignedTo && filteredMembers.length > 0 && (
              <ul className="absolute z-10 mt-1 w-full bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg shadow-lg overflow-hidden">
                {filteredMembers.map((m) => (
                  <li key={m.id}>
                    <button
                      type="button"
                      onClick={() => {
                        setAssignedTo(m);
                        setMemberSearch('');
                        setDropdownOpen(false);
                      }}
                      className="w-full text-left px-3 py-2 hover:bg-gray-50 dark:hover:bg-gray-600 cursor-pointer"
                    >
                      <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{m.userName}</p>
                      <p className="text-xs text-gray-500 dark:text-gray-400">{m.userEmail}</p>
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
            {isEdit ? 'Save changes' : 'Create job'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
