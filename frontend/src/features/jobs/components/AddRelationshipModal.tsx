import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { SearchX } from 'lucide-react';
import Modal from '../../../components/Modal';
import Button from '../../../components/Button';
import EmptyState from '../../../components/EmptyState';
import { useJobList, useCreateRelationship } from '../useJobs';
import type { JobRelationshipType } from '../../../types';

interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
  jobId: string;
}

export default function AddRelationshipModal({ open, onClose, projectId, jobId }: Props) {
  const { t } = useTranslation(['jobsComponents', 'common']);
  const RELATIONSHIP_TYPES: { value: JobRelationshipType; label: string; description: string }[] = [
    { value: 'BLOCKED_BY', label: t('jobsComponents:addRelationshipModal.types.blockedBy.label'), description: t('jobsComponents:addRelationshipModal.types.blockedBy.description') },
    { value: 'RELATED_TO', label: t('jobsComponents:addRelationshipModal.types.relatedTo.label'), description: t('jobsComponents:addRelationshipModal.types.relatedTo.description') },
    { value: 'DUPLICATES', label: t('jobsComponents:addRelationshipModal.types.duplicates.label'), description: t('jobsComponents:addRelationshipModal.types.duplicates.description') },
  ];
  const [selectedJobId, setSelectedJobId] = useState('');
  const [type, setType] = useState<JobRelationshipType>('RELATED_TO');
  const [search, setSearch] = useState('');

  const { data: jobs = [] } = useJobList(projectId, undefined, undefined, undefined);
  const { mutate: createRel, isPending } = useCreateRelationship(projectId, jobId);

  const filtered = jobs.filter(
    (j) =>
      // `jobId` is the current job's friendlyId (from the route), not its UUID —
      // comparing against j.id (a UUID) never matched, so the current job was
      // never actually excluded from its own search results.
      j.friendlyId !== jobId &&
      (search.length === 0 || j.title.toLowerCase().includes(search.toLowerCase())),
  );

  function handleSubmit() {
    if (!selectedJobId) return;
    createRel({ targetJobId: selectedJobId, type }, { onSuccess: handleClose });
  }

  function handleClose() {
    setSelectedJobId('');
    setType('RELATED_TO');
    setSearch('');
    onClose();
  }

  return (
    <Modal open={open} onClose={handleClose} title={t('jobsComponents:addRelationshipModal.title')}>
      <div className="space-y-4">
        {/* Relationship type */}
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {t('jobsComponents:addRelationshipModal.typeLabel')} <span className="text-red-500">*</span>
          </label>
          <div className="space-y-1.5">
            {RELATIONSHIP_TYPES.map((rt) => (
              <label key={rt.value} className="flex items-start gap-2.5 cursor-pointer">
                <input
                  type="radio"
                  name="relType"
                  value={rt.value}
                  checked={type === rt.value}
                  onChange={() => setType(rt.value)}
                  className="mt-0.5"
                />
                <span className="text-sm">
                  <span className="font-medium text-gray-900 dark:text-gray-100">{rt.label}</span>
                  <span className="text-gray-500 dark:text-gray-400"> — {rt.description}</span>
                </span>
              </label>
            ))}
          </div>
        </div>

        {/* Job search */}
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {t('jobsComponents:addRelationshipModal.jobLabel')} <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            placeholder={t('jobsComponents:addRelationshipModal.searchPlaceholder')}
            value={search}
            onChange={(e) => { setSearch(e.target.value); setSelectedJobId(''); }}
            className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent mb-1.5"
          />
          <div className="max-h-48 overflow-y-auto border border-gray-200 dark:border-gray-600 rounded-lg divide-y divide-gray-100 dark:divide-gray-700">
            {filtered.length === 0 ? (
              <EmptyState
                icon={SearchX}
                message={t('jobsComponents:addRelationshipModal.noJobsFound')}
                className="py-4"
                iconClassName="w-6 h-6 text-gray-300 dark:text-gray-600 mb-1.5"
              />
            ) : (
              filtered.map((j) => (
                <button
                  key={j.id}
                  type="button"
                  onClick={() => setSelectedJobId(j.id)}
                  className={`w-full text-left px-3 py-2 text-sm cursor-pointer transition-colors ${
                    selectedJobId === j.id
                      ? 'bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300'
                      : 'text-gray-900 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-gray-700'
                  }`}
                >
                  {j.title}
                </button>
              ))
            )}
          </div>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={handleClose}>
            {t('common:cancel')}
          </Button>
          <Button
            variant="primary"
            onClick={handleSubmit}
            disabled={!selectedJobId}
            loading={isPending}
          >
            {t('common:add')}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
