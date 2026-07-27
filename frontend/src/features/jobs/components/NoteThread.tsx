import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { MessageSquare } from 'lucide-react';
import Button from '../../../components/Button';
import ConfirmModal from '../../../components/ConfirmModal';
import EmptyState from '../../../components/EmptyState';
import Markdown from '../../../components/Markdown';
import MarkdownEditor from '../../../components/MarkdownEditor';
import { useNotes, useAddNote } from '../useNotes';
import type { ProjectMemberResponse } from '../../../types';
import type { TFunction } from 'i18next';

const NOTE_MAX = 10000;
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

function memberName(members: ProjectMemberResponse[], userId: string, t: TFunction): string {
  return members.find((m) => m.userId === userId)?.userName ?? t('jobsComponents:unknownUser');
}

interface Props {
  projectId: string;
  jobId: string;
  members: ProjectMemberResponse[];
  projectCompleted?: boolean;
}

export default function NoteThread({ projectId, jobId, members, projectCompleted }: Props) {
  const { t } = useTranslation('jobsComponents');
  const { data: notes = [] } = useNotes(projectId, jobId);
  const { mutate: addNote, isPending } = useAddNote(projectId, jobId);
  const [content, setContent] = useState('');
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

    addNote(content.trim(), { onSuccess: () => setContent('') });
  }

  function handleConfirmNote() {
    sessionStorage.setItem(SESSION_KEY, 'true');
    setConfirmOpen(false);
    addNote(content.trim(), { onSuccess: () => setContent('') });
  }

  return (
    <div className="space-y-3">
      {notes.length === 0 && (
        <EmptyState
          icon={MessageSquare}
          message={t('jobsComponents:noteThread.noNotes')}
          className="py-4"
          iconClassName="w-7 h-7 text-gray-300 dark:text-gray-600 mb-2"
        />
      )}

      {notes.map((note) => (
        <div key={note.id} className="bg-gray-50 dark:bg-gray-700 rounded-lg px-4 py-3">
          <div className="flex items-center justify-between mb-1">
            <span className="text-sm font-medium text-gray-800 dark:text-gray-200">
              {memberName(members, note.authorId, t)}
            </span>
            <span className="text-xs text-gray-400 dark:text-gray-500">{formatDateTime(note.createdAt)}</span>
          </div>
          <Markdown className="text-sm text-gray-700 dark:text-gray-300">{note.content}</Markdown>
        </div>
      ))}

      {!projectCompleted && (
        <>
          <div className="mt-3">
            <MarkdownEditor
              value={content}
              onChange={setContent}
              placeholder={t('jobsComponents:noteThread.placeholder')}
              rows={4}
            />
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
              {t('jobsComponents:noteThread.addNote')}
            </Button>
          </div>
        </>
      )}

      <ConfirmModal
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        onConfirm={handleConfirmNote}
        title={t('jobsComponents:noteThread.addNote')}
        message={t('jobsComponents:noteThread.confirmMessage')}
        confirmLabel={t('jobsComponents:noteThread.addNote')}
        isPending={isPending}
      />
    </div>
  );
}
