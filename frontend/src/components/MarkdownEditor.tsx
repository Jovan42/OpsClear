import { useRef, useState } from 'react';
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
  label: string;
  icon: typeof Bold;
  apply: (value: string, selectionStart: number, selectionEnd: number) => MarkdownFormatResult;
}

const TOOLBAR_BUTTONS: ToolbarButton[] = [
  { key: 'bold', label: 'Bold', icon: Bold, apply: (v, s, e) => applyMarkdownFormat(v, s, e, { prefix: '**', suffix: '**', placeholder: 'bold text' }) },
  { key: 'italic', label: 'Italic', icon: Italic, apply: (v, s, e) => applyMarkdownFormat(v, s, e, { prefix: '_', suffix: '_', placeholder: 'italic text' }) },
  { key: 'code', label: 'Inline code', icon: Code, apply: (v, s, e) => applyMarkdownFormat(v, s, e, { prefix: '`', suffix: '`', placeholder: 'code' }) },
  { key: 'quote', label: 'Blockquote', icon: Quote, apply: (v, s, e) => applyMarkdownFormat(v, s, e, { prefix: '> ', suffix: '', placeholder: 'Quoted text', perLine: true }) },
  { key: 'hr', label: 'Horizontal rule', icon: Minus, apply: (v, s, e) => applyMarkdownFormat(v, s, e, { prefix: '\n\n---\n\n', suffix: '', placeholder: '', ignoreSelection: true }) },
  { key: 'ordered-list', label: 'Ordered list', icon: ListOrdered, apply: (v, s, e) => applyMarkdownFormat(v, s, e, { prefix: '1. ', suffix: '', placeholder: 'List item', perLine: true }) },
  { key: 'unordered-list', label: 'Unordered list', icon: List, apply: (v, s, e) => applyMarkdownFormat(v, s, e, { prefix: '- ', suffix: '', placeholder: 'List item', perLine: true }) },
  { key: 'link', label: 'Link', icon: LinkIcon, apply: (v, s, e) => applyMarkdownLink(v, s, e) },
];

export default function MarkdownEditor({
  value,
  onChange,
  placeholder = 'Write something… (markdown supported)',
  rows = 6,
  disabled = false,
}: Readonly<MarkdownEditorProps>) {
  const [tab, setTab] = useState<'write' | 'preview'>('write');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

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
          {(['write', 'preview'] as const).map((t) => (
            <button
              key={t}
              type="button"
              disabled={disabled}
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

        {tab === 'write' && (
          <div className="flex items-center gap-0.5 pr-2">
            {TOOLBAR_BUTTONS.map(({ key, label, icon: Icon, apply }) => (
              <button
                key={key}
                type="button"
                disabled={disabled}
                title={label}
                aria-label={label}
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
          placeholder={placeholder}
          className="w-full px-3 py-2 text-sm bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none resize-none disabled:bg-gray-50 dark:disabled:bg-gray-800 disabled:text-gray-400 dark:disabled:text-gray-500"
        />
      ) : (
        <div className="min-h-[80px] px-3 py-2 bg-white dark:bg-gray-900">
          {value.trim() ? (
            <Markdown className="text-sm text-gray-700 dark:text-gray-300">{value}</Markdown>
          ) : (
            <p className="text-sm text-gray-400 dark:text-gray-500 italic">Nothing to preview.</p>
          )}
        </div>
      )}
    </div>
  );
}
