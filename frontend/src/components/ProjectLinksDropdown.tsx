import { useEffect, useRef, useState } from 'react';
import Button from './Button';
import ConfirmModal from './ConfirmModal';
import LinkIcon from './LinkIcon';
import { detectLinkIcon } from '../utils/linkIcon';
import {
  useCreateProjectLink,
  useDeleteProjectLink,
  useUpdateProjectLink,
} from '../features/projects/useProjects';
import type { LinkResponse } from '../types';

const inputClass =
  'w-full text-sm rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 ' +
  'text-gray-900 dark:text-gray-100 px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-[var(--brand)]';

interface Props {
  projectId: string;
  links: LinkResponse[];
  canManage: boolean;
  onClose: () => void;
  /** Overrides the default floating-dropdown positioning — used by the mobile NavDrawer
   *  (ADR-0038) to render the panel inline instead, since an absolutely-positioned
   *  `right-0` panel can get clipped inside the drawer's scrollable item list. */
  className?: string;
}

export default function ProjectLinksDropdown({ projectId, links, canManage, onClose, className }: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const { mutate: createLink, isPending: isCreating } = useCreateProjectLink(projectId);
  const { mutate: updateLink, isPending: isUpdating } = useUpdateProjectLink(projectId);
  const { mutate: deleteLink, isPending: isDeleting } = useDeleteProjectLink(projectId);

  const [adding, setAdding] = useState(false);
  const [url, setUrl] = useState('');
  const [label, setLabel] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editUrl, setEditUrl] = useState('');
  const [editLabel, setEditLabel] = useState('');
  const [deleteTarget, setDeleteTarget] = useState<LinkResponse | null>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        onClose();
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [onClose]);

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

  return (
    <div
      ref={ref}
      className={className ?? 'absolute right-0 mt-2 w-80 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-lg z-50 p-3 space-y-2'}
    >
      <p className="text-xs font-medium text-gray-500 dark:text-gray-400 px-1">Project Links</p>

      {links.length === 0 && !adding && (
        <p className="text-sm text-gray-400 dark:text-gray-500 px-1 py-1">No links yet.</p>
      )}

      {links.map((link) =>
        editingId === link.id ? (
          <div key={link.id} className="flex flex-col gap-2 bg-gray-50 dark:bg-gray-700 rounded-lg px-3 py-2">
            <input value={editUrl} onChange={(e) => setEditUrl(e.target.value)} placeholder="https://…" className={inputClass} />
            <input value={editLabel} onChange={(e) => setEditLabel(e.target.value)} placeholder="Label" className={inputClass} />
            <div className="flex gap-2">
              <Button size="sm" onClick={handleSaveEdit} loading={isUpdating} disabled={!editUrl.trim()}>
                Save
              </Button>
              <Button size="sm" variant="secondary" onClick={() => setEditingId(null)}>
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <div key={link.id} className="flex items-center gap-2 px-1 py-1 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700">
            <LinkIcon url={link.url} className="w-4 h-4 shrink-0" />
            <a
              href={link.url}
              target="_blank"
              rel="noopener noreferrer"
              className="flex-1 min-w-0 truncate text-sm font-medium text-gray-900 dark:text-gray-100 hover:underline"
            >
              {link.label || link.url}
            </a>
            {canManage && (
              <>
                <button
                  onClick={() => startEdit(link)}
                  className="shrink-0 text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 cursor-pointer"
                  title="Edit link"
                >
                  <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M12 20h9" />
                    <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4Z" />
                  </svg>
                </button>
                <button
                  onClick={() => setDeleteTarget(link)}
                  className="shrink-0 text-gray-400 dark:text-gray-500 hover:text-red-500 dark:hover:text-red-400 text-base leading-none cursor-pointer"
                  title="Delete link"
                >
                  ×
                </button>
              </>
            )}
          </div>
        ),
      )}

      {adding ? (
        <div className="flex flex-col gap-2 mt-1">
          <input value={url} onChange={(e) => handleUrlChange(e.target.value)} placeholder="https://…" className={inputClass} />
          <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="Label (optional)" className={inputClass} />
          <div className="flex gap-2">
            <Button size="sm" onClick={handleAdd} loading={isCreating} disabled={!url.trim()}>
              Save
            </Button>
            <Button size="sm" variant="secondary" onClick={resetAddForm}>
              Cancel
            </Button>
          </div>
        </div>
      ) : (
        <button
          onClick={() => setAdding(true)}
          className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline cursor-pointer px-1 mt-1"
        >
          + Add link
        </button>
      )}

      <ConfirmModal
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleDelete}
        title="Delete Link"
        message={`Remove "${deleteTarget?.label || deleteTarget?.url}"?`}
        confirmLabel="Delete"
        variant="danger"
        isPending={isDeleting}
      />
    </div>
  );
}
