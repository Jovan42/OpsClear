import { useTranslation } from 'react-i18next';
import { FolderKanban } from 'lucide-react';
import EmptyState from '../../components/EmptyState';
import Skeleton from '../../components/Skeleton';
import PageError from '../../components/PageError';
import { useCurrentOrg } from '../org/OrgContext';
import { useProjectDirectory } from '../org/useOrganisation';
import type { ProjectStatus } from '../../types';

const STATUS_BADGE: Record<ProjectStatus, string> = {
  ACTIVE: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  COMPLETED: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
};

export default function ProjectDirectorySection() {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const { org } = useCurrentOrg();
  const { data: entries = [], isLoading, isError, refetch } = useProjectDirectory(org?.id ?? null);

  return (
    <section>
      <h2 className="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-3">
        {t('overview.projectDirectoryHeading')}
      </h2>

      {isLoading && (
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      )}

      {isError && (
        <PageError message={t('overview.projectDirectoryLoadError')} onRetry={() => void refetch()} />
      )}

      {!isLoading && !isError && entries.length === 0 && (
        <EmptyState
          icon={FolderKanban}
          message={t('overview.projectDirectoryEmptyState')}
          className="py-10"
        />
      )}

      {!isLoading && !isError && entries.length > 0 && (
        <div className="border border-gray-200 dark:border-gray-700 rounded-xl overflow-x-auto">
          <table className="w-full text-sm min-w-[28rem]">
            <thead className="bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
              <tr>
                <th className="text-left px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                  {t('overview.projectDirectoryColProject')}
                </th>
                <th className="text-left px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                  {t('overview.projectDirectoryColOwner')}
                </th>
                <th className="text-left px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                  {t('overview.projectDirectoryColStatus')}
                </th>
                <th className="text-right px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                  {t('overview.projectDirectoryColMembers')}
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
              {/* Sorted by member count ascending server-side — surfaces likely blind spots
                  first (ADR-0045), so no client-side re-sort here. */}
              {entries.map((entry) => (
                <tr key={entry.id} className="bg-white dark:bg-gray-800">
                  <td className="px-4 py-3 font-medium text-gray-900 dark:text-gray-100">{entry.name}</td>
                  <td className="px-4 py-3 text-gray-600 dark:text-gray-300">
                    {entry.ownerName ?? t('overview.projectDirectoryUnknownOwner')}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_BADGE[entry.status]}`}>
                      {t(`overview.projectDirectoryStatus.${entry.status}`)}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right text-gray-600 dark:text-gray-300">{entry.memberCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
