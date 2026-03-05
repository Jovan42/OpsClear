import { useState } from 'react';
import Button from '../../../components/Button';
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

  const isOverLimit = content.length > NOTE_MAX;
  const isEmpty = content.trim().length === 0;

  function handleSubmit() {
    if (isEmpty || isOverLimit) return;

    const confirmed = sessionStorage.getItem(SESSION_KEY) === 'true';
    if (!confirmed) {
      const ok = window.confirm(
        'Notes cannot be edited or deleted. Add anyway?',
      );
      if (!ok) return;
      sessionStorage.setItem(SESSION_KEY, 'true');
    }

    addNote(content.trim(), {
      onSuccess: () => setContent(''),
    });
  }

  return (
    <div className="space-y-3">
      {notes.length === 0 && (
        <p className="text-sm text-gray-400 py-2">No notes yet.</p>
      )}

      {notes.map((note) => (
        <div key={note.id} className="bg-gray-50 rounded-lg px-4 py-3">
          <div className="flex items-center justify-between mb-1">
            <span className="text-sm font-medium text-gray-800">
              {memberName(members, note.authorId)}
            </span>
            <span className="text-xs text-gray-400">{formatDateTime(note.createdAt)}</span>
          </div>
          <p className="text-sm text-gray-700 whitespace-pre-wrap">{note.content}</p>
        </div>
      ))}

      <div className="mt-3">
        <textarea
          rows={3}
          className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:border-transparent resize-none"
          placeholder="Add a note…"
          value={content}
          onChange={(e) => setContent(e.target.value)}
        />
        <div className="flex items-center justify-between mt-1">
          <span className={`text-xs ${isOverLimit ? 'text-red-600' : 'text-gray-400'}`}>
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
      </div>
    </div>
  );
}
