import { useState } from 'react';
import Markdown from './Markdown';

interface MarkdownEditorProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  rows?: number;
  disabled?: boolean;
}

export default function MarkdownEditor({
  value,
  onChange,
  placeholder = 'Write something… (markdown supported)',
  rows = 6,
  disabled = false,
}: Readonly<MarkdownEditorProps>) {
  const [tab, setTab] = useState<'write' | 'preview'>('write');

  return (
    <div className="border border-gray-300 dark:border-gray-600 rounded-lg overflow-hidden bg-white dark:bg-gray-900">
      <div className="flex border-b border-gray-200 dark:border-gray-600 bg-gray-50 dark:bg-gray-800">
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

      {tab === 'write' ? (
        <textarea
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
