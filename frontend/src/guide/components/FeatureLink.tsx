import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PlayCircle } from 'lucide-react';
import { featureLinkPath, type FeatureCardId } from '../pages';

/** "See it in action": the matching interactive demo on `/features`. */
export function FeatureLink({ card }: Readonly<{ card: FeatureCardId }>) {
  const { t } = useTranslation('guide');
  return (
    <Link
      to={featureLinkPath(card)}
      className="not-prose my-4 inline-flex items-center gap-2 rounded-lg border border-gray-200 dark:border-gray-700 px-3 py-2 text-sm font-medium text-brand dark:text-green-400 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
      data-testid="guide-feature-link"
    >
      <PlayCircle className="h-4 w-4" aria-hidden />
      {t('seeItInAction')}
    </Link>
  );
}
