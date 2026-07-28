import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Modal from '../../components/Modal';
import Button from '../../components/Button';
import MarkdownEditor from '../../components/MarkdownEditor';
import { useCreateTemplate, useUpdateTemplate, useCreateOrgTemplate, useUpdateOrgTemplate } from './useTemplates';
import { useProjectMembers } from '../projects/useProjects';
import { useMilestones } from '../jobs/useMilestones';
import { useJobTypes } from '../jobTypes/useJobTypes';
import { useCurrentOrg } from '../org/OrgContext';
import type { AssigneeMode, JobPriority, JobTemplateResponse, ProjectMemberResponse } from '../../types';

interface Props {
  open: boolean;
  onClose: () => void;
  projectId?: string;
  orgId?: string;
  template?: JobTemplateResponse;
}

const WILDCARD_TOKENS = [
  '{{date}}', '{{date+N}}', '{{day}}', '{{month}}', '{{year}}', '{{week}}',
  '{{quarter}}', '{{project}}', '{{creator}}', '{{assignee}}', '{{occurrence}}',
];
const WILDCARD_DESC_KEYS = [
  'wildcardDate', 'wildcardDateOffset', 'wildcardDay', 'wildcardMonth', 'wildcardYear',
  'wildcardWeek', 'wildcardQuarter', 'wildcardProject', 'wildcardCreator', 'wildcardAssignee', 'wildcardOccurrence',
] as const;

export default function TemplateFormModal({ open, onClose, projectId, orgId, template }: Readonly<Props>) {
  const { t } = useTranslation(['milestonesTemplatesSchedules', 'common']);
  const isEdit    = !!template;
  const isOrgMode = !!orgId && !projectId;

  const PRIORITIES: { value: JobPriority; label: string }[] = [
    { value: 'LOW',      label: t('milestonesTemplatesSchedules:templateModal.priorityLow') },
    { value: 'MEDIUM',   label: t('milestonesTemplatesSchedules:templateModal.priorityMedium') },
    { value: 'HIGH',     label: t('milestonesTemplatesSchedules:templateModal.priorityHigh') },
    { value: 'CRITICAL', label: t('milestonesTemplatesSchedules:templateModal.priorityCritical') },
  ];

  const WILDCARDS = WILDCARD_TOKENS.map((token, i) => ({
    token,
    desc: t(`milestonesTemplatesSchedules:templateModal.${WILDCARD_DESC_KEYS[i]}`),
  }));

  const [name, setName]                   = useState(template?.name ?? '');
  const [title, setTitle]                 = useState(template?.title ?? '');
  const [description, setDescription]     = useState(template?.description ?? '');
  const [client, setClient]               = useState(template?.client ?? '');
  const [priority, setPriority]           = useState<JobPriority | ''>(template?.priority ?? '');
  const [assigneeMode, setAssigneeMode]   = useState<AssigneeMode>(template?.assigneeMode ?? 'NONE');
  const [assigneeId, setAssigneeId]       = useState<string | null>(template?.assigneeId ?? null);
  const [milestoneId, setMilestoneId]     = useState<string>(template?.milestoneId ?? '');
  const [defaultTypeId, setDefaultTypeId]     = useState<string>(template?.defaultTypeId ?? '');
  const [defaultTypeName, setDefaultTypeName] = useState<string>(template?.defaultTypeName ?? '');
  const [deadlineOffset, setDeadlineOffset] = useState<string>(
    template?.deadlineOffsetDays != null ? String(template.deadlineOffsetDays) : '',
  );
  const [memberSearch, setMemberSearch]   = useState('');
  const [dropdownOpen, setDropdownOpen]   = useState(false);
  const [wildcardOpen, setWildcardOpen]   = useState(false);

  const containerRef = useRef<HTMLDivElement>(null);

  const { data: members = [] } = useProjectMembers(projectId ?? '');
  const { data: milestones = [] } = useMilestones(projectId ?? '');
  const { data: jobTypes = [] } = useJobTypes(projectId ?? '');
  const { hasAddon } = useCurrentOrg();
  const showTypeField = hasAddon('JOB_TYPES') && (isOrgMode || jobTypes.length > 0);

  const createProject = useCreateTemplate(projectId ?? '');
  const updateProject = useUpdateTemplate(projectId ?? '');
  const createOrg     = useCreateOrgTemplate(orgId ?? '');
  const updateOrg     = useUpdateOrgTemplate(orgId ?? '');

  const create = isOrgMode ? createOrg : createProject;
  const update = isOrgMode ? updateOrg : updateProject;
  const isPending = create.isPending || update.isPending;
  const isError   = create.isError   || update.isError;

  const selectedAssignee = members.find((m) => m.userId === assigneeId) ?? null;

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

  function handleClose() {
    create.reset();
    update.reset();
    onClose();
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;

    const offset = deadlineOffset ? parseInt(deadlineOffset, 10) : undefined;
    const body = {
      name:               name.trim(),
      title:              title.trim() || undefined,
      description:        description.trim() || undefined,
      client:             client.trim() || undefined,
      priority:           (priority || undefined) as JobPriority | undefined,
      assigneeMode,
      assigneeId:         assigneeMode === 'FIXED' ? (assigneeId ?? undefined) : undefined,
      milestoneId:        milestoneId || undefined,
      defaultTypeId:      !isOrgMode ? (defaultTypeId || undefined) : undefined,
      defaultTypeName:    isOrgMode ? (defaultTypeName.trim() || undefined) : undefined,
      deadlineOffsetDays: offset && !isNaN(offset) ? offset : undefined,
    };

    if (isEdit) {
      update.mutate({ templateId: template.id, body }, { onSuccess: handleClose });
    } else {
      create.mutate(body, { onSuccess: handleClose });
    }
  }

  const inputClass =
    'w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent';
  const labelClass = 'block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1';

  return (
    <Modal open={open} onClose={handleClose} title={isEdit ? t('milestonesTemplatesSchedules:templateModal.editTitle') : t('milestonesTemplatesSchedules:templateModal.newTitle')}>
      <form onSubmit={handleSubmit} className="space-y-4">

        <div>
          <label className={labelClass}>{t('milestonesTemplatesSchedules:templateModal.nameLabel')} <span className="text-red-500">*</span></label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={t('milestonesTemplatesSchedules:templateModal.namePlaceholder')}
            maxLength={100}
            className={inputClass}
            autoFocus
          />
        </div>

        <div>
          <label className={labelClass}>{t('milestonesTemplatesSchedules:templateModal.titleLabel')}</label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Report for {{project}} — {{year}}-W{{week}}"
            maxLength={255}
            className={inputClass}
          />
        </div>

        <div>
          <label className={labelClass}>{t('milestonesTemplatesSchedules:templateModal.descriptionLabel')}</label>
          <MarkdownEditor
            value={description}
            onChange={setDescription}
            placeholder={t('milestonesTemplatesSchedules:templateModal.descriptionPlaceholder')}
            rows={5}
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t('milestonesTemplatesSchedules:templateModal.clientLabel')}</label>
            <input
              type="text"
              value={client}
              onChange={(e) => setClient(e.target.value)}
              placeholder={t('milestonesTemplatesSchedules:templateModal.clientPlaceholder')}
              maxLength={255}
              className={inputClass}
            />
          </div>
          <div>
            <label className={labelClass}>{t('milestonesTemplatesSchedules:templateModal.priorityLabel')}</label>
            <select
              value={priority}
              onChange={(e) => setPriority(e.target.value as JobPriority | '')}
              className={inputClass}
            >
              <option value="">{t('milestonesTemplatesSchedules:templateModal.priorityNoneOption')}</option>
              {PRIORITIES.map(({ value, label }) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t('milestonesTemplatesSchedules:templateModal.assigneeModeLabel')}</label>
            <select
              value={assigneeMode}
              onChange={(e) => {
                setAssigneeMode(e.target.value as AssigneeMode);
                if (e.target.value !== 'FIXED') { setAssigneeId(null); setMemberSearch(''); }
              }}
              className={inputClass}
            >
              <option value="NONE">{t('milestonesTemplatesSchedules:templateModal.assigneeModeNone')}</option>
              {!isOrgMode && <option value="FIXED">{t('milestonesTemplatesSchedules:templateModal.assigneeModeFixed')}</option>}
              <option value="ASK">{t('milestonesTemplatesSchedules:templateModal.assigneeModeAsk')}</option>
            </select>
          </div>
          <div>
            <label className={labelClass}>{t('milestonesTemplatesSchedules:templateModal.deadlineOffsetLabel')}</label>
            <input
              type="number"
              min={1}
              value={deadlineOffset}
              onChange={(e) => setDeadlineOffset(e.target.value)}
              placeholder={t('milestonesTemplatesSchedules:templateModal.deadlineOffsetPlaceholder')}
              className={inputClass}
            />
          </div>
        </div>

        {assigneeMode === 'FIXED' && !isOrgMode && (
          <div>
            <label className={labelClass}>{t('milestonesTemplatesSchedules:templateModal.assigneeLabel')}</label>
            <div className="relative" ref={containerRef}>
              <input
                className={inputClass}
                placeholder={t('milestonesTemplatesSchedules:templateModal.assigneeSearchPlaceholder')}
                value={selectedAssignee ? selectedAssignee.userName : memberSearch}
                onChange={(e) => {
                  setMemberSearch(e.target.value);
                  setAssigneeId(null);
                  setDropdownOpen(true);
                }}
                onFocus={() => setDropdownOpen(true)}
                autoComplete="off"
              />
              {selectedAssignee && (
                <button
                  type="button"
                  onClick={() => { setAssigneeId(null); setMemberSearch(''); }}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 cursor-pointer"
                >
                  ×
                </button>
              )}
              {dropdownOpen && !selectedAssignee && filteredMembers.length > 0 && (
                <ul className="absolute z-10 mt-1 w-full bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg shadow-lg overflow-hidden">
                  {filteredMembers.map((m: ProjectMemberResponse) => (
                    <li key={m.id}>
                      <button
                        type="button"
                        onClick={() => { setAssigneeId(m.userId); setMemberSearch(''); setDropdownOpen(false); }}
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
        )}

        {!isOrgMode && milestones.length > 0 && (
          <div>
            <label className={labelClass}>{t('milestonesTemplatesSchedules:templateModal.milestoneLabel')}</label>
            <select
              value={milestoneId}
              onChange={(e) => setMilestoneId(e.target.value)}
              className={inputClass}
            >
              <option value="">{t('milestonesTemplatesSchedules:templateModal.milestoneNoneOption')}</option>
              {milestones.map((ms) => (
                <option key={ms.id} value={ms.id}>{ms.name}</option>
              ))}
            </select>
          </div>
        )}

        {showTypeField && !isOrgMode && (
          <div>
            <label className={labelClass}>{t('milestonesTemplatesSchedules:templateModal.defaultTypeLabel')}</label>
            <select
              value={defaultTypeId}
              onChange={(e) => setDefaultTypeId(e.target.value)}
              className={inputClass}
            >
              <option value="">{t('milestonesTemplatesSchedules:templateModal.defaultTypeNoneOption')}</option>
              {jobTypes.map((jt) => (
                <option key={jt.id} value={jt.id}>{jt.name}</option>
              ))}
            </select>
          </div>
        )}

        {showTypeField && isOrgMode && (
          <div>
            <label className={labelClass}>{t('milestonesTemplatesSchedules:templateModal.defaultTypeLabel')}</label>
            <input
              type="text"
              value={defaultTypeName}
              onChange={(e) => setDefaultTypeName(e.target.value)}
              placeholder={t('milestonesTemplatesSchedules:templateModal.defaultTypeNamePlaceholder')}
              maxLength={100}
              className={inputClass}
            />
            <p className="mt-1 text-xs text-gray-400 dark:text-gray-500">
              {t('milestonesTemplatesSchedules:templateModal.defaultTypeNameHelp')}
            </p>
          </div>
        )}

        <div>
          <button
            type="button"
            onClick={() => setWildcardOpen((v) => !v)}
            className="flex items-center gap-1 text-xs text-blue-600 dark:text-blue-400 hover:underline"
          >
            <span>{wildcardOpen ? '▾' : '▸'}</span>
            {t('milestonesTemplatesSchedules:templateModal.wildcardsToggle')}
          </button>
          {wildcardOpen && (
            <div className="mt-2 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50 p-3">
              <dl className="grid grid-cols-1 gap-1">
                {WILDCARDS.map(({ token, desc }) => (
                  <div key={token} className="flex gap-2 text-xs">
                    <dt className="font-mono text-blue-700 dark:text-blue-300 shrink-0">{token}</dt>
                    <dd className="text-gray-500 dark:text-gray-400">{desc}</dd>
                  </div>
                ))}
              </dl>
            </div>
          )}
        </div>

        {isError && (
          <p className="text-sm text-red-600 dark:text-red-400">
            {isEdit ? t('milestonesTemplatesSchedules:templateModal.errorUpdate') : t('milestonesTemplatesSchedules:templateModal.errorCreate')}
          </p>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={handleClose}>{t('common:cancel')}</Button>
          <Button variant="primary" type="submit" disabled={!name.trim()} loading={isPending}>
            {isEdit ? t('common:save') : t('common:create')}
          </Button>
        </div>

      </form>
    </Modal>
  );
}
