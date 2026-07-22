import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Bold, Italic, Code, Quote, Minus, ListOrdered, List, Link as LinkIcon } from 'lucide-react';
import Markdown from './Markdown';
import { applyMarkdownFormat, applyMarkdownLink, type MarkdownFormatResult } from '../utils/markdownFormatting';

interface MarkdownEditorProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  rows?: number;
  disabled?: boolean;
}

interface ToolbarButton {
  key: string;
  labelKey: string;
  icon: typeof Bold;
  apply: (value: string, selectionStart: number, selectionEnd: number) => MarkdownFormatResult;
}

const TOOLBAR_BUTTONS: ToolbarButton[] = [
  { key: 'bold', labelKey: 'toolbarBold', icon: Bold, apply: (v, s, e) => applyMarkdownFormat(v, s, e, { prefix: '**', suffix: '**', placeholder: 'bold text' }) },
  { key: 'italic', labelKey: 'toolbarItalic', icon: Italic, apply: (v, s, e) => applyMarkdownFormat(v, s, e, { prefix: '_', suffix: '_', placeholder: 'italic text' }) },
  { key: 'code', labelKey: 'toolbarInlineCode', icon: Code, apply: (v, s, e) => applyMarkdownFormat(v, s, e, { prefix: '`', suffix: '`', placeholder: 'code' }) },
  { key: 'quote', labelKey: 'toolbarBlockquote', icon: Quote, apply: (v, s, e) => applyMarkdownFormat(v, s, e, { prefix: '> ', suffix: '', placeholder: 'Quoted text', perLine: true }) },
  { key: 'hr', labelKey: 'toolbarHorizontalRule', icon: Minus, apply: (v, s, e) => applyMarkdownFormat(v, s, e, { prefix: '\n\n---\n\n', suffix: '', placeholder: '', ignoreSelection: true }) },
  { key: 'ordered-list', labelKey: 'toolbarOrderedList', icon: ListOrdered, apply: (v, s, e) => applyMarkdownFormat(v, s, e, { prefix: '1. ', suffix: '', placeholder: 'List item', perLine: true }) },
  { key: 'unordered-list', labelKey: 'toolbarUnorderedList', icon: List, apply: (v, s, e) => applyMarkdownFormat(v, s, e, { prefix: '- ', suffix: '', placeholder: 'List item', perLine: true }) },
  { key: 'link', labelKey: 'toolbarLink', icon: LinkIcon, apply: (v, s, e) => applyMarkdownLink(v, s, e) },
];

export default function MarkdownEditor({
  value,
  onChange,
  placeholder,
  rows = 6,
  disabled = false,
}: Readonly<MarkdownEditorProps>) {
  const { t } = useTranslation('shared1');
  const [tab, setTab] = useState<'write' | 'preview'>('write');
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const resolvedPlaceholder = placeholder ?? t('defaultPlaceholder');

  function handleFormat(apply: ToolbarButton['apply']) {
    const textarea = textareaRef.current;
    if (!textarea) return;

    const result = apply(value, textarea.selectionStart, textarea.selectionEnd);
    onChange(result.value);

    requestAnimationFrame(() => {
      textarea.focus();
      textarea.setSelectionRange(result.selectionStart, result.selectionEnd);
    });
  }

  return (
    <div className="border border-gray-300 dark:border-gray-600 rounded-lg overflow-hidden bg-white dark:bg-gray-900">
      <div className="flex items-center justify-between border-b border-gray-200 dark:border-gray-600 bg-gray-50 dark:bg-gray-800">
        <div className="flex">
          {(['write', 'preview'] as const).map((tabKey) => (
            <button
              key={tabKey}
              type="button"
              disabled={disabled}
              onClick={() => setTab(tabKey)}
              className={`px-4 py-2 text-xs font-medium transition-colors cursor-pointer ${
                tab === tabKey
                  ? 'text-gray-900 dark:text-gray-100 border-b-2 border-brand -mb-px bg-white dark:bg-gray-900'
                  : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'
              }`}
            >
              {tabKey === 'write' ? t('tabWrite') : t('tabPreview')}
            </button>
          ))}
        </div>

        {tab === 'write' && (
          <div className="flex items-center gap-0.5 pr-2">
            {TOOLBAR_BUTTONS.map(({ key, labelKey, icon: Icon, apply }) => (
              <button
                key={key}
                type="button"
                disabled={disabled}
                title={t(labelKey)}
                aria-label={t(labelKey)}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => handleFormat(apply)}
                className="p-1.5 rounded text-gray-500 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-100 hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <Icon className="w-3.5 h-3.5" />
              </button>
            ))}
          </div>
        )}
      </div>

      {tab === 'write' ? (
        <textarea
          ref={textareaRef}
          rows={rows}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          placeholder={resolvedPlaceholder}
          className="w-full px-3 py-2 text-sm bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none resize-none disabled:bg-gray-50 dark:disabled:bg-gray-800 disabled:text-gray-400 dark:disabled:text-gray-500"
        />
      ) : (
        <div className="min-h-[80px] px-3 py-2 bg-white dark:bg-gray-900">
          {value.trim() ? (
            <Markdown className="text-sm text-gray-700 dark:text-gray-300">{value}</Markdown>
          ) : (
            <p className="text-sm text-gray-400 dark:text-gray-500 italic">{t('nothingToPreview')}</p>
          )}
        </div>
      )}
    </div>
  );
}
