import { usePreferences } from '../../hooks/usePreferences';
import type { Theme } from '../../hooks/usePreferences';
import { usePageTitle } from '../../hooks/usePageTitle';

const THEME_OPTIONS: { value: Theme; label: string }[] = [
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
  { value: 'system', label: 'System' },
];

export default function SettingsPage() {
  usePageTitle('Settings');
  const { prefs, update } = usePreferences();

  return (
    <div className="max-w-lg mx-auto px-4 py-10">
      <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-8">Settings</h1>

      <section>
        <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3">
          Appearance
        </h2>
        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-900 dark:text-gray-100">Theme</p>
              <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
                Choose how OpsClear looks on this device.
              </p>
            </div>
            <div className="flex rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden shrink-0">
              {THEME_OPTIONS.map(({ value, label }) => (
                <button
                  key={value}
                  onClick={() => update({ theme: value })}
                  className={`px-3 py-1.5 text-sm font-medium transition-colors cursor-pointer ${
                    prefs.theme === value
                      ? 'bg-gray-900 text-white dark:bg-white dark:text-gray-900'
                      : 'bg-white text-gray-600 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
