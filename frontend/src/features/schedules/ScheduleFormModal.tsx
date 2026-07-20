import { useState, useEffect, useRef } from 'react';
import Button from '../../components/Button';
import { useCreateSchedule, useUpdateSchedule, useSchedulePreview } from './useSchedules';
import { useTemplates } from '../templates/useTemplates';
import { useProjectMembers } from '../projects/useProjects';
import { toReadable, buildCron, detectPreset, parseCronParams } from '../../utils/cron';
import type { RecurringScheduleResponse, ProjectMemberResponse } from '../../types';

const DAYS_OF_WEEK = [
  { value: 'MON', label: 'Monday' },
  { value: 'TUE', label: 'Tuesday' },
  { value: 'WED', label: 'Wednesday' },
  { value: 'THU', label: 'Thursday' },
  { value: 'FRI', label: 'Friday' },
  { value: 'SAT', label: 'Saturday' },
  { value: 'SUN', label: 'Sunday' },
];

type Preset = 'daily' | 'weekly' | 'monthly' | 'advanced';

interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
  schedule?: RecurringScheduleResponse;
  initialTemplateId?: string;
}

function AssigneeRow({
  member,
  index,
  total,
  onRemove,
  onMove,
}: Readonly<{
  member: ProjectMemberResponse;
  index: number;
  total: number;
  onRemove: () => void;
  onMove: (dir: -1 | 1) => void;
}>) {
  return (
    <div className="flex items-center gap-2 bg-gray-50 dark:bg-gray-700/50 rounded-lg px-3 py-2">
      <div
        className="w-7 h-7 rounded-full flex items-center justify-center text-xs font-semibold text-white shrink-0"
        style={{ backgroundColor: 'var(--brand)', opacity: 0.8 }}
      >
        {member.userName.charAt(0).toUpperCase()}
      </div>
      <span className="flex-1 text-sm text-gray-800 dark:text-gray-200 truncate">{member.userName}</span>
      <span className="text-xs text-gray-400 dark:text-gray-500">#{index + 1}</span>
      <button
        type="button"
        onClick={() => onMove(-1)}
        disabled={index === 0}
        className="p-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 disabled:opacity-30 cursor-pointer disabled:cursor-not-allowed"
        title="Move up"
      >
        ▲
      </button>
      <button
        type="button"
        onClick={() => onMove(1)}
        disabled={index === total - 1}
        className="p-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 disabled:opacity-30 cursor-pointer disabled:cursor-not-allowed"
        title="Move down"
      >
        ▼
      </button>
      <button
        type="button"
        onClick={onRemove}
        className="p-1 text-red-400 hover:text-red-600 cursor-pointer"
        title="Remove"
      >
        ×
      </button>
    </div>
  );
}

export default function ScheduleFormModal({ open, onClose, projectId, schedule, initialTemplateId }: Readonly<Props>) {
  const isEdit = !!schedule;
  const userTz = Intl.DateTimeFormat().resolvedOptions().timeZone;

  const { data: templates = [] } = useTemplates(projectId);
  const { data: members = [] } = useProjectMembers(projectId);

  const create = useCreateSchedule(projectId);
  const update = useUpdateSchedule(projectId);
  const isPending = create.isPending || update.isPending;
  const isError = create.isError || update.isError;

  // ── Form state ──
  const [name, setName] = useState(schedule?.name ?? '');
  const [templateId, setTemplateId] = useState(schedule?.templateId ?? initialTemplateId ?? '');
  const [timezone, setTimezone] = useState(schedule?.timezone ?? userTz);
  const [pausedUntil, setPausedUntil] = useState('');
  const [expiresAt, setExpiresAt] = useState('');

  // Cron preset state
  const initPreset = schedule ? detectPreset(schedule.cronExpression) : 'weekly';
  const initParams = schedule ? parseCronParams(schedule.cronExpression) : { time: '09:00', dow: 'MON', dom: 1 };

  const [preset, setPreset] = useState<Preset>(initPreset);
  const [time, setTime] = useState(initParams.time);
  const [dow, setDow] = useState(initParams.dow);
  const [dom, setDom] = useState(String(initParams.dom));
  const [rawCron, setRawCron] = useState(schedule?.cronExpression ?? '0 0 9 * * MON');

  // Assignee rotation state
  const initAssignees =
    schedule?.assignees
      .slice()
      .sort((a, b) => a.order - b.order)
      .map((a) => a.userId) ?? [];
  const [assigneeIds, setAssigneeIds] = useState<string[]>(initAssignees);
  const [memberSearch, setMemberSearch] = useState('');
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const dropRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (dropRef.current && !dropRef.current.contains(e.target as Node)) {
        setDropdownOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  // Compute the effective cron expression from preset state
  function effectiveCron(): string {
    if (preset === 'advanced') return rawCron;
    if (preset === 'daily') return buildCron('daily', time);
    if (preset === 'weekly') return buildCron('weekly', time, { dow });
    return buildCron('monthly', time, { dom: parseInt(dom, 10) || 1 });
  }

  const cronExpr = effectiveCron();
  const cronReadable = toReadable(cronExpr);

  const { data: preview } = useSchedulePreview(cronExpr, timezone);

  // Assignee helpers
  const assigneeMembers = assigneeIds
    .map((id) => members.find((m) => m.userId === id))
    .filter((m): m is ProjectMemberResponse => !!m);

  const availableMembers = members.filter(
    (m) =>
      !assigneeIds.includes(m.userId) &&
      (memberSearch.length === 0 ||
        m.userName.toLowerCase().includes(memberSearch.toLowerCase()) ||
        m.userEmail.toLowerCase().includes(memberSearch.toLowerCase())),
  );

  function addAssignee(userId: string) {
    setAssigneeIds((prev) => [...prev, userId]);
    setMemberSearch('');
    setDropdownOpen(false);
  }

  function removeAssignee(index: number) {
    setAssigneeIds((prev) => prev.filter((_, i) => i !== index));
  }

  function moveAssignee(index: number, dir: -1 | 1) {
    setAssigneeIds((prev) => {
      const next = [...prev];
      const target = index + dir;
      if (target < 0 || target >= next.length) return prev;
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }

  function handleClose() {
    create.reset();
    update.reset();
    onClose();
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const body = {
      name: name.trim(),
      templateId,
      cronExpression: cronExpr,
      timezone,
      pausedUntil: pausedUntil ? new Date(pausedUntil).toISOString() : null,
      expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
      assigneeIds,
    };
    if (isEdit && schedule) {
      update.mutate({ scheduleId: schedule.id, body }, { onSuccess: handleClose });
    } else {
      create.mutate(body, { onSuccess: handleClose });
    }
  }

  const inputCls =
    'w-full border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-[var(--brand)]/40';
  const labelCls = 'block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1';
  const tabCls = (active: boolean) =>
    `px-3 py-1.5 text-xs font-medium rounded-md transition-colors cursor-pointer ${
      active
        ? 'bg-white dark:bg-gray-600 text-gray-900 dark:text-gray-100 shadow-sm'
        : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'
    }`;

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" onClick={handleClose}>
      <div className="absolute inset-0 bg-black/40" />
      <div
        className="relative z-10 w-full max-w-2xl bg-white dark:bg-gray-800 rounded-xl shadow-xl max-h-[90vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700 shrink-0">
          <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100">
            {isEdit ? 'Edit Schedule' : 'New Schedule'}
          </h2>
          <button
            type="button"
            onClick={handleClose}
            className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 text-xl leading-none cursor-pointer"
          >
            ×
          </button>
        </div>

        {/* Form wraps scrollable body + footer */}
        <form onSubmit={handleSubmit} className="flex flex-col flex-1 min-h-0">
          {/* Scrollable body */}
          <div className="overflow-y-auto flex-1 px-6 py-4 space-y-5">
            {/* Name */}
            <div>
              <label className={labelCls}>Name</label>
              <input
                className={inputCls}
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Weekly deploy checklist"
                required
              />
            </div>

            {/* Template */}
            <div>
              <label className={labelCls}>Template</label>
              <select
                className={inputCls}
                value={templateId}
                onChange={(e) => setTemplateId(e.target.value)}
                required
              >
                <option value="">Select a template…</option>
                {templates.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            </div>

            {/* Cron */}
            <div>
              <label className={labelCls}>Schedule</label>

              {/* Preset tabs */}
              <div className="flex gap-1 bg-gray-100 dark:bg-gray-700 rounded-lg p-1 mb-3">
                {(['daily', 'weekly', 'monthly', 'advanced'] as Preset[]).map((p) => (
                  <button key={p} type="button" className={tabCls(preset === p)} onClick={() => setPreset(p)}>
                    {p.charAt(0).toUpperCase() + p.slice(1)}
                  </button>
                ))}
              </div>

              {preset === 'daily' && (
                <div className="flex items-center gap-3">
                  <span className="text-sm text-gray-600 dark:text-gray-400">Every day at</span>
                  <input
                    type="time"
                    value={time}
                    onChange={(e) => setTime(e.target.value)}
                    className={`${inputCls} w-32`}
                  />
                </div>
              )}

              {preset === 'weekly' && (
                <div className="flex items-center gap-3 flex-wrap">
                  <span className="text-sm text-gray-600 dark:text-gray-400">Every</span>
                  <select
                    value={dow}
                    onChange={(e) => setDow(e.target.value)}
                    className={`${inputCls} w-36`}
                  >
                    {DAYS_OF_WEEK.map((d) => (
                      <option key={d.value} value={d.value}>
                        {d.label}
                      </option>
                    ))}
                  </select>
                  <span className="text-sm text-gray-600 dark:text-gray-400">at</span>
                  <input
                    type="time"
                    value={time}
                    onChange={(e) => setTime(e.target.value)}
                    className={`${inputCls} w-32`}
                  />
                </div>
              )}

              {preset === 'monthly' && (
                <div className="flex items-center gap-3 flex-wrap">
                  <span className="text-sm text-gray-600 dark:text-gray-400">Day</span>
                  <input
                    type="number"
                    min={1}
                    max={28}
                    value={dom}
                    onChange={(e) => setDom(e.target.value)}
                    className={`${inputCls} w-20`}
                  />
                  <span className="text-sm text-gray-600 dark:text-gray-400">of every month at</span>
                  <input
                    type="time"
                    value={time}
                    onChange={(e) => setTime(e.target.value)}
                    className={`${inputCls} w-32`}
                  />
                </div>
              )}

              {preset === 'advanced' && (
                <div>
                  <input
                    className={inputCls}
                    value={rawCron}
                    onChange={(e) => setRawCron(e.target.value)}
                    placeholder="0 0 9 * * MON"
                    spellCheck={false}
                  />
                  <p className="mt-1 text-xs text-gray-400 dark:text-gray-500">
                    6-field Spring cron: sec min hour dom month dow
                  </p>
                </div>
              )}

              {/* Human-readable translation */}
              <p className="mt-2 text-sm text-[var(--brand)] font-medium">{cronReadable}</p>

              {/* Next occurrences preview */}
              {preview && preview.nextRuns.length > 0 && (
                <div className="mt-2 bg-gray-50 dark:bg-gray-700/50 rounded-lg px-3 py-2 space-y-1">
                  <p className="text-xs font-medium text-gray-500 dark:text-gray-400">Next occurrences</p>
                  {preview.nextRuns.map((occ) => (
                    <p key={occ} className="text-xs text-gray-700 dark:text-gray-300">
                      {new Date(occ).toLocaleString(undefined, {
                        weekday: 'short',
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </p>
                  ))}
                </div>
              )}
            </div>

            {/* Timezone */}
            <div>
              <label className={labelCls}>Timezone</label>
              <input
                className={inputCls}
                value={timezone}
                onChange={(e) => setTimezone(e.target.value)}
                placeholder="Europe/Belgrade"
                required
              />
            </div>

            {/* Assignee rotation */}
            <div>
              <label className={labelCls}>
                Assignee rotation{' '}
                <span className="font-normal text-gray-400">(optional, round-robin)</span>
              </label>

              {assigneeMembers.length > 0 && (
                <div className="space-y-2 mb-2">
                  {assigneeMembers.map((m, i) => (
                    <AssigneeRow
                      key={m.userId}
                      member={m}
                      index={i}
                      total={assigneeMembers.length}
                      onRemove={() => removeAssignee(i)}
                      onMove={(dir) => moveAssignee(i, dir)}
                    />
                  ))}
                </div>
              )}

              <div className="relative" ref={dropRef}>
                <input
                  className={inputCls}
                  value={memberSearch}
                  onChange={(e) => {
                    setMemberSearch(e.target.value);
                    setDropdownOpen(true);
                  }}
                  onFocus={() => setDropdownOpen(true)}
                  placeholder="Add member to rotation…"
                />
                {dropdownOpen && availableMembers.length > 0 && (
                  <div className="absolute z-10 top-full mt-1 w-full bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg max-h-40 overflow-y-auto">
                    {availableMembers.map((m) => (
                      <button
                        key={m.userId}
                        type="button"
                        className="w-full text-left px-3 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
                        onClick={() => addAssignee(m.userId)}
                      >
                        <span className="font-medium text-gray-900 dark:text-gray-100">{m.userName}</span>
                        <span className="ml-2 text-gray-400 dark:text-gray-500 text-xs">{m.userEmail}</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {/* Optional: Pause until / Expires at */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className={labelCls}>
                  Pause until <span className="font-normal text-gray-400">(optional)</span>
                </label>
                <input
                  type="datetime-local"
                  value={pausedUntil}
                  onChange={(e) => setPausedUntil(e.target.value)}
                  className={inputCls}
                />
              </div>
              <div>
                <label className={labelCls}>
                  Expires at <span className="font-normal text-gray-400">(optional)</span>
                </label>
                <input
                  type="datetime-local"
                  value={expiresAt}
                  onChange={(e) => setExpiresAt(e.target.value)}
                  className={inputCls}
                />
              </div>
            </div>

            {isError && (
              <p className="text-sm text-red-600 dark:text-red-400">
                {(create.error as Error)?.message ?? (update.error as Error)?.message ?? 'Something went wrong.'}
              </p>
            )}
          </div>

          {/* Footer */}
          <div className="flex justify-end gap-2 px-6 py-4 border-t border-gray-200 dark:border-gray-700 shrink-0">
            <Button type="button" variant="secondary" onClick={handleClose} disabled={isPending}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={isPending}>
              {isEdit ? 'Save changes' : 'Create schedule'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
