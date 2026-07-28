import type { JobTypeColor } from '../types';

export const JOB_TYPE_COLORS: JobTypeColor[] = [
  'RED', 'ORANGE', 'AMBER', 'GREEN', 'TEAL', 'BLUE', 'INDIGO', 'PURPLE', 'PINK', 'GRAY',
];

// Chart fill / swatch preview — Tailwind's 500 shade per color.
export const JOB_TYPE_HEX: Record<JobTypeColor, string> = {
  RED: '#ef4444',
  ORANGE: '#f97316',
  AMBER: '#f59e0b',
  GREEN: '#22c55e',
  TEAL: '#14b8a6',
  BLUE: '#3b82f6',
  INDIGO: '#6366f1',
  PURPLE: '#a855f7',
  PINK: '#ec4899',
  GRAY: '#6b7280',
};

export const JOB_TYPE_BADGE_CLASSES: Record<JobTypeColor, string> = {
  RED: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  ORANGE: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
  AMBER: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  GREEN: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  TEAL: 'bg-teal-100 text-teal-700 dark:bg-teal-900/30 dark:text-teal-400',
  BLUE: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  INDIGO: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-400',
  PURPLE: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
  PINK: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
  GRAY: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
};
