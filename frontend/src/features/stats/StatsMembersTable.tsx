import { useTranslation } from 'react-i18next';
import { ChevronRight } from 'lucide-react';
import { formatAvg, formatLateRate } from './statsFormat';
import type { StatsMember } from '../../types';

const TH = 'px-4 py-2.5 text-xs font-medium text-gray-500 dark:text-gray-400 whitespace-nowrap';
const TD = 'px-4 py-3 text-right tabular-nums text-gray-700 dark:text-gray-300 whitespace-nowrap';

/**
 * Owner/Admin per-member breakdown (ADR-0050): one row per entry in `members`, in the order the
 * backend returns them (no ranking, so the page doesn't read as a leaderboard). Clicking a row
 * drills the whole page down to that member.
 */
export default function StatsMembersTable({
  members,
  onSelect,
}: Readonly<{ members: StatsMember[]; onSelect: (member: StatsMember) => void }>) {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');

  return (
    <div
      data-testid="stats-members-table"
      className="border border-gray-200 dark:border-gray-700 rounded-xl overflow-x-auto"
    >
      <table className="w-full text-sm min-w-[44rem]">
        <thead className="bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
          <tr>
            <th className={`${TH} text-left`}>{t('stats.members.colMember')}</th>
            <th className={`${TH} text-right`}>{t('stats.tiles.completed')}</th>
            <th className={`${TH} text-right`}>{t('stats.tiles.avgLeadTime')}</th>
            <th className={`${TH} text-right`}>{t('stats.tiles.avgCycleTime')}</th>
            <th className={`${TH} text-right`}>{t('stats.tiles.avgBlocked')}</th>
            <th className={`${TH} text-right`}>{t('stats.tiles.lateRate')}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
          {members.map((m) => (
            <tr
              key={m.userId}
              data-testid="stats-member-row"
              onClick={() => onSelect(m)}
              className="bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/50 cursor-pointer"
            >
              <td className="px-4 py-3 text-left">
                {/* The button gives keyboard and screen-reader users the same drill-down as the row click. */}
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onSelect(m);
                  }}
                  aria-label={t('stats.members.showMember', { name: m.name })}
                  className="inline-flex items-center gap-1 font-medium text-gray-900 dark:text-gray-100 hover:text-brand cursor-pointer text-left"
                >
                  {m.name}
                  <ChevronRight className="w-3.5 h-3.5 text-gray-400 shrink-0" aria-hidden="true" />
                </button>
              </td>
              <td className={TD}>{m.completedCount}</td>
              <td className={TD}>{formatAvg(m.avgLeadTimeSeconds)}</td>
              <td className={TD}>{formatAvg(m.avgCycleTimeSeconds)}</td>
              <td className={TD}>{formatAvg(m.avgBlockedSeconds)}</td>
              <td className={TD}>
                <span>{formatLateRate(m.lateRate)}</span>
                {m.completedWithDeadline > 0 && (
                  <span className="block text-xs text-gray-400 dark:text-gray-500">
                    {t('stats.tiles.lateDetail', { late: m.completedLate, total: m.completedWithDeadline })}
                  </span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
