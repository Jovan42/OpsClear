import { useEffect, useMemo, useRef, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import Modal from '../../components/Modal';
import Button from '../../components/Button';
import MarkdownEditor from '../../components/MarkdownEditor';
import { useCreateJob, useUpdateJob } from './useJobs';
import { useProjectMembers, useProject } from '../projects/useProjects';
import { useCurrentOrg } from '../org/OrgContext';
import { useAuth } from '../../auth/AuthContext';
import { useTemplates } from '../templates/useTemplates';
import { templatesApi } from '../../api/templates';
import { resolveWildcards } from '../../utils/resolveWildcards';
import type { JobResponse, MilestoneResponse, ProjectMemberResponse } from '../../types';

function buildSchema(t: ReturnType<typeof useTranslation>['t']) {
  return z.object({
    title: z.string().min(1, t('newJobModal.titleRequired')).max(255, t('newJobModal.max255Characters')),
    description: z.string().max(10000, t('newJobModal.max10000Characters')).optional(),
    client: z.string().max(255, t('newJobModal.max255Characters')).optional(),
    deadline: z.string().optional(),
    priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']),
    milestoneId: z.string().optional(),
  });
}
type FormValues = z.infer<ReturnType<typeof buildSchema>>;

interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
  job?: JobResponse;
  milestones?: MilestoneResponse[];
}

export default function NewJobModal({ open, onClose, projectId, job, milestones = [] }: Props) {
  const { t } = useTranslation(['jobsPages', 'common']);
  const isEdit = Boolean(job);
  const schema = useMemo(() => buildSchema(t), [t]);
  const PRIORITIES = useMemo(() => ([
    { value: 'LOW' as const, label: t('priorityLow') },
    { value: 'MEDIUM' as const, label: t('priorityMedium') },
    { value: 'HIGH' as const, label: t('priorityHigh') },
    { value: 'CRITICAL' as const, label: t('priorityCritical') },
  ]), [t]);
  const { mutate: createJob, isPending: isCreating } = useCreateJob(projectId);
  const { mutate: updateJob, isPending: isUpdating } = useUpdateJob(projectId);
  const isPending = isCreating || isUpdating;

  const { data: members = [] } = useProjectMembers(projectId);
  const { data: project } = useProject(projectId);
  const { hasAddon } = useCurrentOrg();
  const { name: creatorName } = useAuth();
  const { data: templates = [] } = useTemplates(projectId);
  const showTemplates = !isEdit && hasAddon('JOB_TEMPLATES') && templates.length > 0;

  const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null);
  const [assignedTo, setAssignedTo] = useState<ProjectMemberResponse | null>(null);
  const [memberSearch, setMemberSearch] = useState('');
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const containerRef    = useRef<HTMLDivElement>(null);
  const assigneeInputRef = useRef<HTMLInputElement>(null);

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
    setSelectedTemplateId(null);
    onClose();
  }

  function handleTemplateSelect(templateId: string) {
    if (!templateId) {
      setSelectedTemplateId(null);
      reset({ title: '', description: '', client: '', deadline: '', priority: 'MEDIUM', milestoneId: '' });
      setAssignedTo(null);
      setMemberSearch('');
      return;
    }
    const template = templates.find((t) => t.id === templateId);
    if (!template) return;
    setSelectedTemplateId(templateId);
    try {
      const ctx = { project: project?.name ?? undefined, creator: creatorName ?? undefined, occurrence: template.occurrenceCount + 1 };
      const deadline = template.deadlineOffsetDays != null
        ? new Date(Date.now() + template.deadlineOffsetDays * 86_400_000).toISOString().split('T')[0]
        : '';
      const milestoneValid = milestones.some((ms) => ms.id === template.milestoneId);
      reset({
        title:       resolveWildcards(template.title ?? '', ctx),
        description: resolveWildcards(template.description ?? '', ctx),
        client:      template.client ?? '',
        priority:    template.priority ?? 'MEDIUM',
        milestoneId: milestoneValid ? (template.milestoneId ?? '') : '',
        deadline,
      });
      if (template.assigneeMode === 'FIXED') {
        setAssignedTo(members.find((m) => m.userId === template.assigneeId) ?? null);
      } else {
        setAssignedTo(null);
        if (template.assigneeMode === 'ASK') assigneeInputRef.current?.focus();
      }
    } catch {
      // malformed template data — leave fields as-is
    }
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
      createJob(body, {
        onSuccess: () => {
          if (selectedTemplateId) {
            void templatesApi.recordUsage(projectId, selectedTemplateId).catch(() => {});
          }
          handleClose();
        },
      });
    }
  }

  const inputClass = 'w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent';
  const labelClass = 'block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1';

  return (
    <Modal open={open} onClose={handleClose} title={isEdit ? t('newJobModal.editTitle') : t('newJobModal.newTitle')}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {showTemplates && (
          <div>
            <label className={labelClass}>{t('newJobModal.startFromTemplateLabel')}</label>
            <select
              className={inputClass}
              defaultValue=""
              onChange={(e) => handleTemplateSelect(e.target.value)}
            >
              <option value="">{t('newJobModal.optionalPlaceholder')}</option>
              {templates.map((tpl) => (
                <option key={tpl.id} value={tpl.id}>
                  {tpl.scope === 'ORG' ? t('newJobModal.orgTemplateName', { name: tpl.name }) : tpl.name}
                </option>
              ))}
            </select>
          </div>
        )}

        <div>
          <label className={labelClass}>
            {t('titleLabel')} <span className="text-red-500">*</span>
          </label>
          <input
            {...register('title')}
            className={inputClass}
            placeholder={t('newJobModal.titlePlaceholder')}
            autoFocus
          />
          {errors.title && <p className="mt-1 text-xs text-red-600">{errors.title.message}</p>}
        </div>

        <div>
          <label className={labelClass}>{t('descriptionLabel')}</label>
          <Controller
            name="description"
            control={control}
            render={({ field }) => (
              <MarkdownEditor
                value={field.value ?? ''}
                onChange={field.onChange}
                placeholder={t('newJobModal.descriptionPlaceholder')}
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
            <label className={labelClass}>{t('clientLabel')}</label>
            <input
              {...register('client')}
              className={inputClass}
              placeholder={t('newJobModal.clientPlaceholder')}
            />
            {errors.client && (
              <p className="mt-1 text-xs text-red-600">{errors.client.message}</p>
            )}
          </div>

          <div>
            <label className={labelClass}>{t('deadlineLabel')}</label>
            <input
              type="date"
              {...register('deadline')}
              className={inputClass}
            />
          </div>
        </div>

        <div>
          <label className={labelClass}>{t('priorityLabel')}</label>
          <select {...register('priority')} className={inputClass}>
            {PRIORITIES.map(({ value, label }) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </div>

        {milestones.length > 0 && (
          <div>
            <label className={labelClass}>{t('newJobModal.milestoneLabel')}</label>
            <select {...register('milestoneId')} className={inputClass}>
              <option value="">{t('newJobModal.noMilestoneOption')}</option>
              {milestones.map((ms) => (
                <option key={ms.id} value={ms.id}>{ms.name}</option>
              ))}
            </select>
          </div>
        )}

        <div>
          <label className={labelClass}>{t('newJobModal.assignToLabel')}</label>
          <div className="relative" ref={containerRef}>
            <input
              ref={assigneeInputRef}
              className={inputClass}
              placeholder={t('newJobModal.searchMemberPlaceholder')}
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
            {t('common:cancel')}
          </Button>
          <Button type="submit" loading={isPending}>
            {isEdit ? t('newJobModal.saveChanges') : t('newJobModal.createJob')}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
