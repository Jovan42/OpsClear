import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { MessageSquare } from 'lucide-react';
import Button from '../../components/Button';
import Skeleton from '../../components/Skeleton';
import EmptyState from '../../components/EmptyState';
import Markdown from '../../components/Markdown';
import MarkdownEditor from '../../components/MarkdownEditor';
import { usePageTitle } from '../../hooks/usePageTitle';
import { useMyFeedbackSubmissions, useSubmitFeedback } from './useFeedback';
import type { FeedbackStatus, FeedbackType } from '../../types';

type View = 'mine' | 'new';

const TYPES: FeedbackType[] = ['BUG', 'FEATURE', 'OTHER'];

// Section keys per type, shared between the description template and the
// side-by-side guidance panel so the two always describe the same structure.
const GUIDANCE_SECTIONS: Record<FeedbackType, string[]> = {
  BUG: ['steps', 'expected', 'actual'],
  FEATURE: ['problem', 'solution', 'howItWorks'],
  OTHER: ['anything'],
};

const SECTION_BODY: Partial<Record<string, string>> = {
  steps: '1. \n2. \n3. \n',
};

function buildDescriptionTemplate(type: FeedbackType, t: TFunction): string {
  if (type === 'OTHER') return '';
  return GUIDANCE_SECTIONS[type]
    .map((key) => `## ${t(`form.guidance.${type}.${key}.heading`)}\n${SECTION_BODY[key] ?? '\n'}`)
    .join('\n');
}

function TabButton({ active, onClick, children }: Readonly<{ active: boolean; onClick: () => void; children: React.ReactNode }>) {
  return (
    <button
      onClick={onClick}
      className={`px-4 py-2 text-sm font-medium transition-colors cursor-pointer ${
        active
          ? 'bg-gray-900 text-white dark:bg-white dark:text-gray-900'
          : 'bg-white text-gray-600 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
      }`}
    >
      {children}
    </button>
  );
}

function StatusPill({ status }: Readonly<{ status: FeedbackStatus }>) {
  const { t } = useTranslation('feedback');
  const cls = status === 'REVIEWED'
    ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
    : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400';
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${cls}`}>
      {t(status === 'REVIEWED' ? 'status.creditGranted' : 'status.pending')}
    </span>
  );
}

function TypePill({ type }: Readonly<{ type: FeedbackType }>) {
  const { t } = useTranslation('feedback');
  return (
    <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400">
      {t(`type.${type}`)}
    </span>
  );
}

export default function FeedbackPage() {
  const { t } = useTranslation('feedback');
  usePageTitle(t('pageTitle'));

  const [view, setView] = useState<View>('mine');
  const { data: submissions, isLoading } = useMyFeedbackSubmissions();
  const { mutate: submit, isPending: submitting } = useSubmitFeedback();

  const [type, setType] = useState<FeedbackType>('OTHER');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  // Tracks the last template we auto-inserted so switching type again can safely
  // swap it out — but only while it's still untouched; any edit the user makes
  // is preserved even if they change type afterward.
  const appliedTemplateRef = useRef('');

  function handleTypeChange(next: FeedbackType) {
    if (description.trim() === '' || description === appliedTemplateRef.current) {
      const template = buildDescriptionTemplate(next, t);
      setDescription(template);
      appliedTemplateRef.current = template;
    }
    setType(next);
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    submit(
      { type, title: title.trim(), description: description.trim() },
      {
        onSuccess: () => {
          setTitle('');
          setDescription('');
          setType('OTHER');
          appliedTemplateRef.current = '';
          setView('mine');
        },
      },
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8 space-y-6">
      <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">{t('pageTitle')}</h1>

      <div className="flex rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden w-fit">
        <TabButton active={view === 'mine'} onClick={() => setView('mine')}>{t('tabs.mine')}</TabButton>
        <TabButton active={view === 'new'} onClick={() => setView('new')}>{t('tabs.new')}</TabButton>
      </div>

      {view === 'mine' && (
        isLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-16 rounded-xl" />
            <Skeleton className="h-16 rounded-xl" />
          </div>
        ) : !submissions || submissions.length === 0 ? (
          <div className="border border-gray-200 dark:border-gray-700 rounded-xl">
            <EmptyState
              icon={MessageSquare}
              message={t('emptyMine')}
              action={{ label: t('tabs.new'), onClick: () => setView('new') }}
            />
          </div>
        ) : (
          <div className="border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
            {submissions.map((s) => (
              <div key={s.id} className="p-4 space-y-1">
                <div className="flex items-center gap-2">
                  <TypePill type={s.type} />
                  <StatusPill status={s.status} />
                </div>
                <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{s.title}</p>
                <Markdown className="text-sm text-gray-600 dark:text-gray-300">{s.description}</Markdown>
                <p className="text-xs text-gray-400 dark:text-gray-500">
                  {new Date(s.createdAt).toLocaleDateString()}
                </p>
              </div>
            ))}
          </div>
        )
      )}

      {view === 'new' && (
        <form onSubmit={handleSubmit} className="space-y-6">
          <p className="text-sm text-gray-600 dark:text-gray-300">{t('intro')}</p>

          <div className="grid grid-cols-1 md:grid-cols-5 gap-6">
            <div className="md:col-span-3 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  {t('form.typeLabel')}
                </label>
                <div className="flex gap-2">
                  {TYPES.map((opt) => (
                    <button
                      key={opt}
                      type="button"
                      onClick={() => handleTypeChange(opt)}
                      className={`px-3 py-1.5 rounded-lg text-sm font-medium border transition-colors cursor-pointer ${
                        type === opt
                          ? 'border-[var(--brand)] text-[var(--brand)]'
                          : 'border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800'
                      }`}
                    >
                      {t(`type.${opt}`)}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label htmlFor="feedback-title" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  {t('form.titleLabel')}
                </label>
                <input
                  id="feedback-title"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  maxLength={255}
                  required
                  className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:border-transparent"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  {t('form.descriptionLabel')}
                </label>
                <MarkdownEditor
                  value={description}
                  onChange={setDescription}
                  placeholder={t('form.descriptionPlaceholder')}
                  rows={14}
                />
              </div>

              <Button type="submit" loading={submitting} disabled={!title.trim() || !description.trim()}>
                {t('form.submitButton')}
              </Button>
            </div>

            <div className="md:col-span-2 bg-gray-50 dark:bg-gray-800/60 border border-gray-200 dark:border-gray-700 rounded-xl p-4 h-fit space-y-4">
              <p className="text-sm font-medium text-gray-700 dark:text-gray-300">{t('form.guidanceHeading')}</p>
              {GUIDANCE_SECTIONS[type].map((key) => (
                <div key={key}>
                  <p className="text-sm font-medium text-gray-700 dark:text-gray-300">
                    {t(`form.guidance.${type}.${key}.heading`)}
                  </p>
                  <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
                    {t(`form.guidance.${type}.${key}.description`)}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </form>
      )}
    </div>
  );
}
