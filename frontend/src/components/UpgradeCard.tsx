import { useNavigate } from 'react-router-dom';

interface Props {
  featureName: string;
}

export default function UpgradeCard({ featureName }: Props) {
  const navigate = useNavigate();
  return (
    <div className="flex items-center justify-center min-h-[60vh]">
      <div className="text-center max-w-sm px-4">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          className="w-10 h-10 mx-auto mb-4 text-gray-300 dark:text-gray-600"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
          <path d="M7 11V7a5 5 0 0 1 10 0v4" />
        </svg>
        <h2 className="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-2">
          {featureName} is not included in your plan
        </h2>
        <p className="text-gray-500 dark:text-gray-400 mb-6">
          Add this feature to your subscription to unlock it for your team.
        </p>
        <button
          onClick={() => navigate('/org/settings')}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium text-white cursor-pointer transition-opacity hover:opacity-90"
          style={{ backgroundColor: 'var(--brand)' }}
        >
          Upgrade in org settings
        </button>
      </div>
    </div>
  );
}
