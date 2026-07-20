import Modal from './Modal';
import Button from './Button';

interface Props {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: string;
  confirmLabel?: string;
  variant?: 'danger' | 'primary';
  isPending?: boolean;
  errorMessage?: string | null;
}

export default function ConfirmModal({
  open,
  onClose,
  onConfirm,
  title,
  message,
  confirmLabel = 'Confirm',
  variant = 'primary',
  isPending = false,
  errorMessage,
}: Props) {
  return (
    <Modal open={open} onClose={onClose} title={title}>
      <div className="space-y-4">
        <p className="text-sm text-gray-600 dark:text-gray-300">{message}</p>
        {errorMessage && (
          <p className="text-sm text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20 rounded-lg px-3 py-2">
            {errorMessage}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button variant={variant} onClick={onConfirm} loading={isPending}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
