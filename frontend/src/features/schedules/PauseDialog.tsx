import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Modal from '../../components/Modal';
import Button from '../../components/Button';
import type { RecurringScheduleResponse } from '../../types';

interface Props {
  schedule: RecurringScheduleResponse;
  open: boolean;
  onClose: () => void;
  onPause: (until: string | null) => void;
  isPending: boolean;
}

type Option = '1d' | '1w' | '1m' | 'custom' | 'indefinite';

function addDays(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() + n);
  return d.toISOString();
}

function addMonths(n: number): string {
  const d = new Date();
  d.setMonth(d.getMonth() + n);
  return d.toISOString();
}

export default function PauseDialog({ schedule, open, onClose, onPause, isPending }: Readonly<Props>) {
  const { t } = useTranslation(['milestonesTemplatesSchedules', 'common']);
  const [selected, setSelected] = useState<Option>('1d');

  const OPTIONS: { value: Option; label: string }[] = [
    { value: '1d', label: t('milestonesTemplatesSchedules:pauseDialog.option1Day') },
    { value: '1w', label: t('milestonesTemplatesSchedules:pauseDialog.option1Week') },
    { value: '1m', label: t('milestonesTemplatesSchedules:pauseDialog.option1Month') },
    { value: 'custom', label: t('milestonesTemplatesSchedules:pauseDialog.optionCustom') },
    { value: 'indefinite', label: t('milestonesTemplatesSchedules:pauseDialog.optionIndefinite') },
  ];
  const [customDate, setCustomDate] = useState('');

  function handleConfirm() {
    if (selected === 'indefinite') { onPause(null); return; }
    if (selected === '1d') { onPause(addDays(1)); return; }
    if (selected === '1w') { onPause(addDays(7)); return; }
    if (selected === '1m') { onPause(addMonths(1)); return; }
    if (selected === 'custom' && customDate) { onPause(new Date(customDate).toISOString()); return; }
  }

  const canConfirm = selected !== 'custom' || !!customDate;

  return (
    <Modal open={open} onClose={onClose} title={t('milestonesTemplatesSchedules:pauseDialog.title', { name: schedule.name })}>
      <div className="space-y-4">
        <div className="space-y-2">
          {OPTIONS.map((opt) => (
            <label key={opt.value} className="flex items-center gap-3 cursor-pointer">
              <input
                type="radio"
                name="pause-option"
                value={opt.value}
                checked={selected === opt.value}
                onChange={() => setSelected(opt.value)}
                className="accent-[var(--brand)]"
              />
              <span className="text-sm text-gray-800 dark:text-gray-200">{opt.label}</span>
            </label>
          ))}
        </div>

        {selected === 'custom' && (
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              {t('milestonesTemplatesSchedules:pauseDialog.resumeOnLabel')}
            </label>
            <input
              type="datetime-local"
              value={customDate}
              onChange={(e) => setCustomDate(e.target.value)}
              min={new Date().toISOString().slice(0, 16)}
              className="w-full border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
            />
          </div>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={isPending}>{t('common:cancel')}</Button>
          <Button variant="primary" onClick={handleConfirm} loading={isPending} disabled={!canConfirm}>
            {t('milestonesTemplatesSchedules:schedulesPage.pauseAction')}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
