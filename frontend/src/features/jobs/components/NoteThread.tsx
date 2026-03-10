import { useState } from 'react';
import Button from '../../../components/Button';
import ConfirmModal from '../../../components/ConfirmModal';
import Markdown from '../../../components/Markdown';
import { useNotes, useAddNote } from '../useNotes';
import type { ProjectMemberResponse } from '../../../types';

const NOTE_MAX = 2000;
const SESSION_KEY = 'note_immutability_confirmed';

function formatDateTime(dateStr: string) {
  return new Date(dateStr).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function memberName(members: ProjectMemberResponse[], userId: string): string {
  return members.find((m) => m.userId === userId)?.userName ?? 'Unknown user';
}

interface Props {
  projectId: string;
  jobId: string;
  members: ProjectMemberResponse[];
}

export default function NoteThread({ projectId, jobId, members }: Props) {
  const { data: notes = [] } = useNotes(projectId, jobId);
  const { mutate: addNote, isPending } = useAddNote(projectId, jobId);
  const [content, setContent] = useState('');
  const [tab, setTab] = useState<'write' | 'preview'>('write');
  const [confirmOpen, setConfirmOpen] = useState(false);

  const isOverLimit = content.length > NOTE_MAX;
  const isEmpty = content.trim().length === 0;

  function handleSubmit() {
    if (isEmpty || isOverLimit) return;

    const confirmed = sessionStorage.getItem(SESSION_KEY) === 'true';
    if (!confirmed) {
      setConfirmOpen(true);
      return;
    }

    addNote(content.trim(), { onSuccess: () => { setContent(''); setTab('write'); } });
  }

  function handleConfirmNote() {
    sessionStorage.setItem(SESSION_KEY, 'true');
    setConfirmOpen(false);
    addNote(content.trim(), { onSuccess: () => { setContent(''); setTab('write'); } });
  }

  return (
    <div className="space-y-3">
      {notes.length === 0 && (
        <p className="text-sm text-gray-400 dark:text-gray-500 py-2">No notes yet.</p>
      )}

      {notes.map((note) => (
        <div key={note.id} className="bg-gray-50 dark:bg-gray-700 rounded-lg px-4 py-3">
          <div className="flex items-center justify-between mb-1">
            <span className="text-sm font-medium text-gray-800 dark:text-gray-200">
              {memberName(members, note.authorId)}
            </span>
            <span className="text-xs text-gray-400 dark:text-gray-500">{formatDateTime(note.createdAt)}</span>
          </div>
          <Markdown className="text-sm text-gray-700 dark:text-gray-300">{note.content}</Markdown>
        </div>
      ))}

      <div className="mt-3 border border-gray-200 dark:border-gray-600 rounded-lg overflow-hidden">
        {/* Tabs */}
        <div className="flex border-b border-gray-200 dark:border-gray-600 bg-gray-50 dark:bg-gray-800">
          {(['write', 'preview'] as const).map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => setTab(t)}
              className={`px-4 py-2 text-xs font-medium capitalize transition-colors cursor-pointer ${
                tab === t
                  ? 'text-gray-900 dark:text-gray-100 border-b-2 border-brand -mb-px bg-white dark:bg-gray-900'
                  : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'
              }`}
            >
              {t}
            </button>
          ))}
        </div>

        {/* Content */}
        {tab === 'write' ? (
          <textarea
            rows={4}
            className="w-full px-3 py-2 text-sm bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none resize-none"
            placeholder="Add a note… (markdown supported)"
            value={content}
            onChange={(e) => setContent(e.target.value)}
          />
        ) : (
          <div className="min-h-[96px] px-3 py-2 bg-white dark:bg-gray-900">
            {content.trim() ? (
              <Markdown className="text-sm text-gray-700 dark:text-gray-300">{content}</Markdown>
            ) : (
              <p className="text-sm text-gray-400 dark:text-gray-500 italic">Nothing to preview.</p>
            )}
          </div>
        )}
      </div>

      <div className="flex items-center justify-between mt-1">
        <span className={`text-xs ${isOverLimit ? 'text-red-600' : 'text-gray-400 dark:text-gray-500'}`}>
          {content.length}/{NOTE_MAX}
        </span>
        <Button
          size="sm"
          onClick={handleSubmit}
          disabled={isEmpty || isOverLimit}
          loading={isPending}
        >
          Add Note
        </Button>
      </div>

      <ConfirmModal
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        onConfirm={handleConfirmNote}
        title="Add Note"
        message="Notes cannot be edited or deleted. Add anyway?"
        confirmLabel="Add Note"
        isPending={isPending}
      />
    </div>
  );
}
