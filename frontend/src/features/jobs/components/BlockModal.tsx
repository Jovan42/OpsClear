import { useEffect, useRef, useState } from 'react';
import Modal from '../../../components/Modal';
import Button from '../../../components/Button';
import { useBlockReasons } from '../useBlockReasons';

interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
  onConfirm: (reason: string) => void;
  isPending: boolean;
}

export default function BlockModal({ open, onClose, projectId, onConfirm, isPending }: Props) {
  const [inputValue, setInputValue] = useState('');
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const { data: reasons = [] } = useBlockReasons(projectId, open);

  const filtered = inputValue.length > 0
    ? reasons.filter((r) => r.reason.toLowerCase().includes(inputValue.toLowerCase()))
    : reasons;

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setDropdownOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  function handleConfirm() {
    const reason = inputValue.trim();
    if (!reason) return;
    onConfirm(reason);
  }

  return (
    <Modal open={open} onClose={onClose} title="Block Job">
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            Block reason <span className="text-red-500">*</span>
          </label>
          <div className="relative" ref={containerRef}>
            <input
              className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent"
              placeholder="Select or type a reason…"
              value={inputValue}
              onChange={(e) => {
                setInputValue(e.target.value);
                setDropdownOpen(true);
              }}
              onFocus={() => setDropdownOpen(true)}
              autoFocus
              autoComplete="off"
            />
            {dropdownOpen && filtered.length > 0 && (
              <ul className="absolute z-10 mt-1 w-full bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg shadow-lg overflow-hidden max-h-48 overflow-y-auto">
                {filtered.map((r) => (
                  <li key={r.id}>
                    <button
                      type="button"
                      onClick={() => {
                        setInputValue(r.reason);
                        setDropdownOpen(false);
                      }}
                      className="w-full text-left px-3 py-2 text-sm text-gray-900 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-gray-600 cursor-pointer"
                    >
                      {r.reason}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="danger"
            onClick={handleConfirm}
            disabled={!inputValue.trim()}
            loading={isPending}
          >
            Block Job
          </Button>
        </div>
      </div>
    </Modal>
  );
}
