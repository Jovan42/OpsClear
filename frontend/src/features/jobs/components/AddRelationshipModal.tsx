import { useState } from 'react';
import Modal from '../../../components/Modal';
import Button from '../../../components/Button';
import { useJobList, useCreateRelationship, useUpdateJobStatus } from '../useJobs';
import type { JobRelationshipType, JobStatus } from '../../../types';

interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
  jobId: string;
  currentJobStatus: JobStatus;
}

const RELATIONSHIP_TYPES: { value: JobRelationshipType; label: string; description: string }[] = [
  { value: 'BLOCKED_BY', label: 'Blocked by', description: 'This job is blocked by the selected job' },
  { value: 'RELATED_TO', label: 'Related to', description: 'These jobs are related' },
  { value: 'DUPLICATES', label: 'Duplicates', description: 'This job duplicates the selected job' },
];

export default function AddRelationshipModal({ open, onClose, projectId, jobId, currentJobStatus }: Props) {
  const [selectedJobId, setSelectedJobId] = useState('');
  const [type, setType] = useState<JobRelationshipType>('RELATED_TO');
  const [search, setSearch] = useState('');
  const [offerBlock, setOfferBlock] = useState(false);

  const { data: jobs = [] } = useJobList(projectId, undefined, undefined, undefined);
  const { mutate: createRel, isPending } = useCreateRelationship(projectId, jobId);
  const { mutate: updateStatus, isPending: isStatusPending } = useUpdateJobStatus(projectId);

  const filtered = jobs.filter(
    (j) =>
      j.id !== jobId &&
      (search.length === 0 || j.title.toLowerCase().includes(search.toLowerCase())),
  );

  const selectedJob = jobs.find((j) => j.id === selectedJobId);

  function handleSubmit() {
    if (!selectedJobId) return;
    createRel(
      { targetJobId: selectedJobId, type },
      {
        onSuccess: () => {
          if (type === 'BLOCKED_BY' && currentJobStatus !== 'BLOCKED') {
            setOfferBlock(true);
          } else {
            handleClose();
          }
        },
      },
    );
  }

  function handleMarkBlocked() {
    updateStatus(
      { jobId, status: 'BLOCKED' },
      { onSuccess: () => handleClose() },
    );
  }

  function handleClose() {
    setSelectedJobId('');
    setType('RELATED_TO');
    setSearch('');
    setOfferBlock(false);
    onClose();
  }

  if (offerBlock) {
    return (
      <Modal open={open} onClose={handleClose} title="Mark as Blocked?">
        <div className="space-y-4">
          <p className="text-sm text-gray-600 dark:text-gray-300">
            You linked this job as <span className="font-medium">Blocked by</span>{' '}
            <span className="font-medium">{selectedJob?.title}</span>. Do you want to set
            this job&apos;s status to <span className="font-medium">Blocked</span>?
          </p>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={handleClose}>
              Keep current status
            </Button>
            <Button variant="danger" onClick={handleMarkBlocked} loading={isStatusPending}>
              Mark as Blocked
            </Button>
          </div>
        </div>
      </Modal>
    );
  }

  return (
    <Modal open={open} onClose={handleClose} title="Add Relationship">
      <div className="space-y-4">
        {/* Relationship type */}
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            Type <span className="text-red-500">*</span>
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
            Job <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            placeholder="Search jobs…"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setSelectedJobId(''); }}
            className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent mb-1.5"
          />
          <div className="max-h-48 overflow-y-auto border border-gray-200 dark:border-gray-600 rounded-lg divide-y divide-gray-100 dark:divide-gray-700">
            {filtered.length === 0 ? (
              <p className="px-3 py-2 text-sm text-gray-400 dark:text-gray-500">No jobs found.</p>
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
            Cancel
          </Button>
          <Button
            variant="primary"
            onClick={handleSubmit}
            disabled={!selectedJobId}
            loading={isPending}
          >
            Add
          </Button>
        </div>
      </div>
    </Modal>
  );
}
