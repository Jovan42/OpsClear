import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Modal from '../../components/Modal';
import Button from '../../components/Button';
import { useCreateJobType, useUpdateJobType } from './useJobTypes';
import { JOB_TYPE_COLORS, JOB_TYPE_HEX } from '../../utils/jobTypeColors';
import type { JobTypeColor, JobTypeResponse } from '../../types';

interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
  type?: JobTypeResponse;
}

export default function JobTypeFormModal({ open, onClose, projectId, type }: Readonly<Props>) {
  const { t } = useTranslation(['jobTypes', 'common']);
  const isEdit = !!type;

  const [name, setName] = useState(type?.name ?? '');
  const [color, setColor] = useState<JobTypeColor>(type?.color ?? 'BLUE');

  const create = useCreateJobType(projectId);
  const update = useUpdateJobType(projectId);
  const isPending = create.isPending || update.isPending;
  const isError = create.isError || update.isError;

  function handleClose() {
    create.reset();
    update.reset();
    onClose();
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;

    if (isEdit && type) {
      update.mutate(
        { typeId: type.id, body: { name: name.trim(), color, displayOrder: type.displayOrder } },
        { onSuccess: handleClose },
      );
    } else {
      create.mutate({ name: name.trim(), color }, { onSuccess: handleClose });
    }
  }

  const inputClass =
    'w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent';
  const labelClass = 'block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1';

  return (
    <Modal open={open} onClose={handleClose} title={isEdit ? t('jobTypes:typeModal.editTitle') : t('jobTypes:typeModal.newTitle')}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className={labelClass}>{t('jobTypes:typeModal.nameLabel')} <span className="text-red-500">*</span></label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={t('jobTypes:typeModal.namePlaceholder')}
            maxLength={100}
            className={inputClass}
            autoFocus
          />
        </div>

        <div>
          <label className={labelClass}>{t('jobTypes:typeModal.colorLabel')}</label>
          <div className="flex flex-wrap gap-2">
            {JOB_TYPE_COLORS.map((c) => (
              <button
                key={c}
                type="button"
                onClick={() => setColor(c)}
                aria-label={t(`jobTypes:colorNames.${c}`)}
                title={t(`jobTypes:colorNames.${c}`)}
                className={`w-8 h-8 rounded-full cursor-pointer transition-all ${
                  color === c ? 'ring-2 ring-offset-2 ring-gray-900 dark:ring-offset-gray-800 dark:ring-gray-100' : ''
                }`}
                style={{ backgroundColor: JOB_TYPE_HEX[c] }}
              />
            ))}
          </div>
        </div>

        {isError && (
          <p className="text-sm text-red-600 dark:text-red-400">
            {isEdit ? t('jobTypes:typeModal.errorUpdate') : t('jobTypes:typeModal.errorCreate')}
          </p>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={handleClose}>{t('common:cancel')}</Button>
          <Button variant="primary" type="submit" disabled={!name.trim()} loading={isPending}>
            {isEdit ? t('common:save') : t('common:create')}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
