import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Modal from '../../components/Modal';
import Button from '../../components/Button';
import { useCreateApiKey } from './useApiKeys';
import type { CreateApiKeyResponse } from '../../types';

interface Props {
  open: boolean;
  onClose: () => void;
}

export default function NewApiKeyModal({ open, onClose }: Readonly<Props>) {
  const { t } = useTranslation(['approvalsDashboardSettingsLanding', 'common']);
  const [name, setName] = useState('');
  const [copied, setCopied] = useState(false);
  const [createdKey, setCreatedKey] = useState<CreateApiKeyResponse | null>(null);
  const create = useCreateApiKey();

  function handleClose() {
    setName('');
    setCopied(false);
    setCreatedKey(null);
    create.reset();
    onClose();
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    create.mutate(name.trim(), {
      onSuccess: (data) => setCreatedKey(data),
    });
  }

  function handleCopy() {
    if (!createdKey) return;
    void navigator.clipboard.writeText(createdKey.key);
    setCopied(true);
  }

  return (
    <Modal open={open} onClose={handleClose} title={t('newApiKeyModal.title')}>
      {createdKey ? (
        <div className="space-y-4">
          <div className="rounded-lg bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 p-3">
            <p className="text-xs font-medium text-green-800 dark:text-green-300 mb-1">
              {t('newApiKeyModal.copyWarning')}
            </p>
            <div className="flex items-center gap-2 mt-2">
              <code className="flex-1 text-sm font-mono bg-white dark:bg-gray-900 border border-green-200 dark:border-green-700 rounded px-3 py-2 text-gray-900 dark:text-gray-100 break-all">
                {createdKey.key}
              </code>
              <button
                onClick={handleCopy}
                className="shrink-0 text-xs px-3 py-2 rounded-lg border border-green-300 dark:border-green-700 bg-white dark:bg-gray-800 text-green-700 dark:text-green-300 hover:bg-green-50 dark:hover:bg-green-900/30 transition-colors cursor-pointer"
              >
                {copied ? t('newApiKeyModal.copied') : t('newApiKeyModal.copy')}
              </button>
            </div>
          </div>
          <div className="flex justify-end">
            <Button variant="primary" onClick={handleClose}>
              {t('newApiKeyModal.done')}
            </Button>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              {t('newApiKeyModal.nameLabel')}
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={t('newApiKeyModal.namePlaceholder')}
              maxLength={100}
              className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:border-transparent"
              autoFocus
            />
          </div>
          {create.isError && (
            <p className="text-sm text-red-600 dark:text-red-400">
              {t('newApiKeyModal.createError')}
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="secondary" type="button" onClick={handleClose}>
              {t('common:cancel')}
            </Button>
            <Button
              variant="primary"
              type="submit"
              disabled={!name.trim()}
              loading={create.isPending}
            >
              {t('common:create')}
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
}
