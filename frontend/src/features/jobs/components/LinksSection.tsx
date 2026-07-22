import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import Button from '../../../components/Button';
import ConfirmModal from '../../../components/ConfirmModal';
import LinkIcon from '../../../components/LinkIcon';
import { detectLinkIcon } from '../../../utils/linkIcon';
import { useCreateJobLink, useDeleteJobLink, useUpdateJobLink } from '../useJobs';
import type { LinkResponse, ProjectMemberResponse } from '../../../types';

const inputClass =
  'flex-1 text-sm rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 ' +
  'text-gray-900 dark:text-gray-100 px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-[var(--brand)]';

function memberName(members: ProjectMemberResponse[], userId: string, t: TFunction): string {
  return members.find((m) => m.userId === userId)?.userName ?? t('jobsComponents:unknownUser');
}

interface Props {
  projectId: string;
  jobId: string;
  links: LinkResponse[];
  members: ProjectMemberResponse[];
  canManage: boolean;
  projectCompleted: boolean;
}

export default function LinksSection({ projectId, jobId, links, members, canManage, projectCompleted }: Props) {
  const { t } = useTranslation(['jobsComponents', 'common']);
  const { mutate: createLink, isPending: isCreating } = useCreateJobLink(projectId, jobId);
  const { mutate: updateLink, isPending: isUpdating } = useUpdateJobLink(projectId, jobId);
  const { mutate: deleteLink, isPending: isDeleting } = useDeleteJobLink(projectId, jobId);

  const [adding, setAdding] = useState(false);
  const [url, setUrl] = useState('');
  const [label, setLabel] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editUrl, setEditUrl] = useState('');
  const [editLabel, setEditLabel] = useState('');
  const [deleteTarget, setDeleteTarget] = useState<LinkResponse | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  function resetAddForm() {
    setAdding(false);
    setUrl('');
    setLabel('');
  }

  function handleUrlChange(value: string) {
    setUrl(value);
    if (!label.trim()) {
      const detected = detectLinkIcon(value);
      if (detected?.type === 'known') setLabel(detected.label);
    }
  }

  function handleAdd() {
    if (!url.trim()) return;
    createLink({ url: url.trim(), label: label.trim() || undefined }, { onSuccess: resetAddForm });
  }

  function startEdit(link: LinkResponse) {
    setEditingId(link.id);
    setEditUrl(link.url);
    setEditLabel(link.label ?? '');
  }

  function handleSaveEdit() {
    if (!editingId || !editUrl.trim()) return;
    updateLink(
      { linkId: editingId, body: { url: editUrl.trim(), label: editLabel.trim() || undefined } },
      { onSuccess: () => setEditingId(null) },
    );
  }

  function handleDelete() {
    if (!deleteTarget) return;
    deleteLink(deleteTarget.id, { onSuccess: () => setDeleteTarget(null) });
  }

  function handleCopy(link: LinkResponse) {
    void navigator.clipboard.writeText(link.url);
    setCopiedId(link.id);
    setTimeout(() => setCopiedId((current) => (current === link.id ? null : current)), 1500);
  }

  return (
    <div className="space-y-2">
      {links.length === 0 && !adding && (
        <p className="text-sm text-gray-400 dark:text-gray-500 py-2">{t('jobsComponents:linksSection.noLinks')}</p>
      )}

      {links.map((link) =>
        editingId === link.id ? (
          <div key={link.id} className="flex flex-col sm:flex-row gap-2 bg-gray-50 dark:bg-gray-700 rounded-lg px-4 py-3">
            <input
              value={editUrl}
              onChange={(e) => setEditUrl(e.target.value)}
              placeholder={t('jobsComponents:linksSection.urlPlaceholder')}
              className={inputClass}
            />
            <input
              value={editLabel}
              onChange={(e) => setEditLabel(e.target.value)}
              placeholder={t('jobsComponents:linksSection.labelPlaceholder')}
              className={`${inputClass} sm:w-40 sm:flex-none`}
            />
            <div className="flex gap-2 shrink-0">
              <Button size="sm" onClick={handleSaveEdit} loading={isUpdating} disabled={!editUrl.trim()}>
                {t('common:save')}
              </Button>
              <Button size="sm" variant="secondary" onClick={() => setEditingId(null)}>
                {t('common:cancel')}
              </Button>
            </div>
          </div>
        ) : (
          <div key={link.id} className="flex items-center gap-3 bg-gray-50 dark:bg-gray-700 rounded-lg px-4 py-2.5">
            <LinkIcon url={link.url} className="w-4 h-4 shrink-0" />

            <div className="flex-1 min-w-0">
              <a
                href={link.url}
                target="_blank"
                rel="noopener noreferrer"
                className="block truncate text-sm font-medium text-gray-900 dark:text-gray-100 hover:underline"
              >
                {link.label || link.url}
              </a>
              <span className="text-xs text-gray-400 dark:text-gray-500">
                {t('jobsComponents:linksSection.addedBy', { name: memberName(members, link.createdBy, t) })}
              </span>
            </div>

            <button
              onClick={() => handleCopy(link)}
              className="shrink-0 text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 cursor-pointer"
              title={copiedId === link.id ? t('jobsComponents:linksSection.copied') : t('jobsComponents:linksSection.copyUrl')}
            >
              {copiedId === link.id ? (
                <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M20 6 9 17l-5-5" />
                </svg>
              ) : (
                <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                </svg>
              )}
            </button>

            {canManage && !projectCompleted && (
              <>
                <button
                  onClick={() => startEdit(link)}
                  className="shrink-0 text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 cursor-pointer"
                  title={t('jobsComponents:linksSection.editLink')}
                >
                  <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M12 20h9" />
                    <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4Z" />
                  </svg>
                </button>
                <button
                  onClick={() => setDeleteTarget(link)}
                  className="shrink-0 text-gray-400 dark:text-gray-500 hover:text-red-500 dark:hover:text-red-400 text-lg leading-none cursor-pointer"
                  title={t('jobsComponents:linksSection.deleteLink')}
                >
                  ×
                </button>
              </>
            )}
          </div>
        ),
      )}

      {!projectCompleted &&
        (adding ? (
          <div className="flex flex-col sm:flex-row gap-2 mt-2">
            <input
              value={url}
              onChange={(e) => handleUrlChange(e.target.value)}
              placeholder={t('jobsComponents:linksSection.urlPlaceholder')}
              className={inputClass}
            />
            <input
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              placeholder={t('jobsComponents:linksSection.labelOptionalPlaceholder')}
              className={`${inputClass} sm:w-40 sm:flex-none`}
            />
            <div className="flex gap-2 shrink-0">
              <Button size="sm" onClick={handleAdd} loading={isCreating} disabled={!url.trim()}>
                {t('common:save')}
              </Button>
              <Button size="sm" variant="secondary" onClick={resetAddForm}>
                {t('common:cancel')}
              </Button>
            </div>
          </div>
        ) : (
          <button
            onClick={() => setAdding(true)}
            className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline cursor-pointer mt-2"
          >
            {t('jobsComponents:linksSection.addLink')}
          </button>
        ))}

      <ConfirmModal
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleDelete}
        title={t('jobsComponents:linksSection.deleteModal.title')}
        message={t('jobsComponents:linksSection.deleteModal.message', { name: deleteTarget?.label || deleteTarget?.url })}
        confirmLabel={t('common:delete')}
        variant="danger"
        isPending={isDeleting}
      />
    </div>
  );
}
