import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

interface Props {
  sections: string[];
}

export default function LockedSectionRow({ sections }: Props) {
  const navigate = useNavigate();
  const { t } = useTranslation('shared2');
  if (sections.length === 0) return null;
  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-5 py-3.5 flex items-center gap-3 text-sm text-gray-500 dark:text-gray-400">
      <svg
        xmlns="http://www.w3.org/2000/svg"
        className="w-3.5 h-3.5 shrink-0 text-gray-400 dark:text-gray-500"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
        <path d="M7 11V7a5 5 0 0 1 10 0v4" />
      </svg>
      <span className="flex-1 min-w-0">
        {sections.join(' · ')}
      </span>
      <button
        onClick={() => navigate('/org/settings')}
        className="shrink-0 text-brand hover:underline cursor-pointer font-medium whitespace-nowrap"
      >
        {t('upgrade.inOrgSettings')}
      </button>
    </div>
  );
}
