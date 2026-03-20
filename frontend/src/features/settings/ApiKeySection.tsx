import { useState } from 'react';
import { useApiKeys, useRevokeApiKey } from './useApiKeys';
import NewApiKeyModal from './NewApiKeyModal';
import ConfirmModal from '../../components/ConfirmModal';
import Button from '../../components/Button';
import type { ApiKeyResponse } from '../../types';

const NINETY_DAYS_MS = 90 * 24 * 60 * 60 * 1000;

function isUnusedWarning(key: ApiKeyResponse): boolean {
  const reference = key.lastUsedAt ?? key.createdAt;
  return Date.now() - new Date(reference).getTime() > NINETY_DAYS_MS;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

interface KeyRowProps {
  apiKey: ApiKeyResponse;
  onRevoke: (key: ApiKeyResponse) => void;
}

function KeyRow({ apiKey, onRevoke }: Readonly<KeyRowProps>) {
  const warn = isUnusedWarning(apiKey);

  return (
    <div className="flex items-start justify-between gap-4 p-5">
      <div className="min-w-0 space-y-1">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
            {apiKey.name}
          </span>
          {warn && (
            <span className="inline-flex items-center gap-1 text-xs font-medium text-amber-700 dark:text-amber-400 bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-700 rounded-full px-2 py-0.5">
              ⚠ Unused 90+ days
            </span>
          )}
        </div>
        <p className="text-xs font-mono text-gray-500 dark:text-gray-400">{apiKey.keyPrefix}…</p>
        <div className="flex gap-4 text-xs text-gray-400 dark:text-gray-500">
          <span>Created {formatDate(apiKey.createdAt)}</span>
          <span>
            {apiKey.lastUsedAt ? `Last used ${formatDate(apiKey.lastUsedAt)}` : 'Never used'}
          </span>
        </div>
      </div>
      <Button
        variant="ghost"
        size="sm"
        onClick={() => onRevoke(apiKey)}
        className="shrink-0 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20"
      >
        Revoke
      </Button>
    </div>
  );
}

export default function ApiKeySection() {
  const { data: keys, isLoading } = useApiKeys();
  const revoke = useRevokeApiKey();
  const [showNew, setShowNew] = useState(false);
  const [revoking, setRevoking] = useState<ApiKeyResponse | null>(null);

  function handleConfirmRevoke() {
    if (!revoking) return;
    revoke.mutate(revoking.id, { onSuccess: () => setRevoking(null) });
  }

  return (
    <>
      <section>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">
            API Keys
          </h2>
          <Button variant="secondary" size="sm" onClick={() => setShowNew(true)}>
            + New API Key
          </Button>
        </div>

        <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl">
          {isLoading ? (
            <p className="p-5 text-sm text-gray-500 dark:text-gray-400">Loading…</p>
          ) : !keys || keys.length === 0 ? (
            <p className="p-5 text-sm text-gray-500 dark:text-gray-400">
              No active API keys. Create one to authenticate scripts and tools.
            </p>
          ) : (
            <div className="divide-y divide-gray-100 dark:divide-gray-700">
              {keys.map((key) => (
                <KeyRow key={key.id} apiKey={key} onRevoke={setRevoking} />
              ))}
            </div>
          )}
        </div>
      </section>

      <NewApiKeyModal open={showNew} onClose={() => setShowNew(false)} />

      <ConfirmModal
        open={revoking !== null}
        onClose={() => setRevoking(null)}
        onConfirm={handleConfirmRevoke}
        title="Revoke API Key"
        message={`Revoke "${revoking?.name}"? Any scripts using this key will stop working immediately.`}
        confirmLabel="Revoke"
        variant="danger"
        isPending={revoke.isPending}
      />
    </>
  );
}
