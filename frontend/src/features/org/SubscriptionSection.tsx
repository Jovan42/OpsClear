import { useEffect, useState } from 'react';
import { isAxiosError } from 'axios';
import type { SubscriptionTierResponse } from '../../types';
import Skeleton from '../../components/Skeleton';
import Button from '../../components/Button';
import { useCatalog, useOrgSubscription, useUpsertOrgSubscription } from './useSubscription';

const ADDON_TAGLINES: Record<string, string> = {
  DASHBOARD:             'Single-screen ops overview',
  APPROVALS:             'Sign-off workflows with logged decisions',
  NOTES:                 'Immutable audit trail on any job',
  JOB_STATUS_HISTORY:   'Full chronological status log',
  MILESTONES:            'Group jobs into phases (max 5 per project)',
  JOB_RELATIONSHIPS:     'Blocks / depends on / related to links',
  API_KEYS:              'Programmatic access & integrations',
  JOB_TEMPLATES:         'Reusable job presets',
  RECURRING_SCHEDULING:  'Auto-create jobs on a schedule',
};

function fmt(n: number) {
  return new Intl.NumberFormat('sr-RS').format(n);
}

function tierPrice(tier: SubscriptionTierResponse, annual: boolean) {
  return annual ? tier.priceAnnual : tier.priceMonthly;
}

interface Props {
  orgId: string;
}

export default function SubscriptionSection({ orgId }: Props) {
  const { data: catalog, isLoading: catalogLoading } = useCatalog();
  const { data: currentSub, isLoading: subLoading } = useOrgSubscription(orgId);
  const { mutate: upsert, isPending: saving } = useUpsertOrgSubscription(orgId);

  const [memberIdx,  setMemberIdx]  = useState(0);
  const [projectIdx, setProjectIdx] = useState(0);
  const [selectedAddons, setSelectedAddons] = useState<Set<string>>(new Set());
  const [annual, setAnnual] = useState(false);
  const [saveError,   setSaveError]   = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  const memberBands  = [...new Set(catalog?.tiers.map((t) => t.maxMembers) ?? [])].sort((a, b) => a - b);
  const projectBands = [
    ...[...new Set(catalog?.tiers.map((t) => t.maxProjects) ?? [])].filter((v): v is number => v !== null).sort((a, b) => a - b),
    null,
  ];

  const selectedTier = catalog?.tiers.find(
    (t) => t.maxMembers === memberBands[memberIdx] && t.maxProjects === projectBands[projectIdx],
  ) ?? null;

  useEffect(() => {
    if (!catalog || currentSub === undefined) return;
    if (currentSub === null) return;

    const mIdx = memberBands.findIndex((b) => b === currentSub.tier.maxMembers);
    const pIdx = projectBands.findIndex((b) => b === currentSub.tier.maxProjects);
    if (mIdx !== -1) setMemberIdx(mIdx);
    if (pIdx !== -1) setProjectIdx(pIdx);
    setAnnual(currentSub.billingCycle === 'ANNUAL');
    setSelectedAddons(new Set(currentSub.addons.map((a) => a.id)));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [catalog, currentSub]);

  if (catalogLoading || subLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-24 rounded-xl" />
        <Skeleton className="h-24 rounded-xl" />
        <Skeleton className="h-32 rounded-xl" />
      </div>
    );
  }

  if (!catalog) return null;

  const availableAddons = catalog.addons.filter((a) => a.available);
  const comingSoonAddons = catalog.addons.filter((a) => !a.available);

  const addonTotal = availableAddons
    .filter((a) => selectedAddons.has(a.id))
    .reduce((sum, a) => sum + (annual ? a.priceAnnual : a.priceMonthly), 0);

  const basePrice = selectedTier ? tierPrice(selectedTier, annual) : 0;
  const total = basePrice + addonTotal;

  function toggleAddon(id: string) {
    setSelectedAddons((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
    setSaveSuccess(false);
    setSaveError(null);
  }

  function handleSave() {
    if (!selectedTier) return;
    setSaveError(null);
    setSaveSuccess(false);
    upsert(
      {
        tierId: selectedTier.id,
        billingCycle: annual ? 'ANNUAL' : 'MONTHLY',
        addonIds: [...selectedAddons],
      },
      {
        onSuccess: () => setSaveSuccess(true),
        onError: (err) => {
          if (isAxiosError(err) && err.response?.data?.message) {
            setSaveError(err.response.data.message as string);
          } else {
            setSaveError('Something went wrong. Please try again.');
          }
        },
      },
    );
  }

  const projectLabel = (p: number | null) => p === null ? 'Unlimited' : `Up to ${p}`;

  return (
    <div className="space-y-4">
      {/* Base plan sliders */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5 space-y-5">
        <p className="text-xs font-semibold text-gray-400 dark:text-gray-500 uppercase tracking-wider">Base plan</p>

        <div className="space-y-2">
          <div className="flex justify-between text-sm">
            <span className="text-gray-700 dark:text-gray-300">Team members</span>
            <span className="font-medium text-gray-900 dark:text-white">Up to {memberBands[memberIdx]}</span>
          </div>
          <input
            type="range" min={0} max={memberBands.length - 1} step={1} value={memberIdx}
            onChange={(e) => { setMemberIdx(Number(e.target.value)); setSaveSuccess(false); setSaveError(null); }}
            className="w-full" style={{ accentColor: 'var(--brand)' }}
          />
          <div className="flex justify-between text-xs text-gray-400">
            <span>{memberBands[0]}</span><span>{memberBands[memberBands.length - 1]}</span>
          </div>
        </div>

        <div className="space-y-2">
          <div className="flex justify-between text-sm">
            <span className="text-gray-700 dark:text-gray-300">Active projects</span>
            <span className="font-medium text-gray-900 dark:text-white">{projectLabel(projectBands[projectIdx])}</span>
          </div>
          <input
            type="range" min={0} max={projectBands.length - 1} step={1} value={projectIdx}
            onChange={(e) => { setProjectIdx(Number(e.target.value)); setSaveSuccess(false); setSaveError(null); }}
            className="w-full" style={{ accentColor: 'var(--brand)' }}
          />
          <div className="flex justify-between text-xs text-gray-400">
            <span>{projectBands[0]}</span><span>Unlimited</span>
          </div>
        </div>

        <div className="flex justify-between items-center pt-2 border-t border-gray-200 dark:border-gray-700">
          <span className="text-sm text-gray-500 dark:text-gray-400">Base price</span>
          <span className="font-semibold text-gray-900 dark:text-white">
            {selectedTier ? `${fmt(basePrice)} ${selectedTier.currency}/mo` : '—'}
          </span>
        </div>
      </div>

      {/* Add-on cards */}
      <div className="space-y-2">
        <p className="text-xs font-semibold text-gray-400 dark:text-gray-500 uppercase tracking-wider">Add-ons</p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {availableAddons.map((addon) => {
            const active = selectedAddons.has(addon.id);
            return (
              <button
                key={addon.id}
                onClick={() => toggleAddon(addon.id)}
                className={`text-left rounded-xl border p-4 transition-all ${
                  active
                    ? 'border-[var(--brand)] bg-white dark:bg-gray-800 ring-1 ring-[var(--brand)]'
                    : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 hover:border-gray-300 dark:hover:border-gray-600'
                }`}
              >
                <div className="flex items-start justify-between gap-2">
                  <p className="text-sm font-medium text-gray-900 dark:text-white">{addon.name}</p>
                  <span
                    className="text-xs font-medium shrink-0"
                    style={{ color: active ? 'var(--brand)' : undefined }}
                  >
                    {active ? '− ' : '+ '}{fmt(annual ? addon.priceAnnual : addon.priceMonthly)} {catalog.tiers[0]?.currency ?? 'RSD'}
                  </span>
                </div>
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                  {ADDON_TAGLINES[addon.key] ?? ''}
                </p>
              </button>
            );
          })}
          {comingSoonAddons.map((addon) => (
            <div
              key={addon.id}
              className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4 opacity-50"
            >
              <div className="flex items-start justify-between gap-2">
                <p className="text-sm font-medium text-gray-900 dark:text-white">{addon.name}</p>
                <span className="text-xs text-gray-400 shrink-0">Soon</span>
              </div>
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                {ADDON_TAGLINES[addon.key] ?? ''}
              </p>
            </div>
          ))}
        </div>
      </div>

      {/* Billing cycle toggle + total + save */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5 space-y-4">
        <div className="flex items-center justify-between">
          <span className="text-sm text-gray-700 dark:text-gray-300">Annual billing</span>
          <button
            role="switch"
            aria-checked={annual}
            onClick={() => { setAnnual((a) => !a); setSaveSuccess(false); setSaveError(null); }}
            className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none ${
              annual ? 'bg-[var(--brand)]' : 'bg-gray-300 dark:bg-gray-600'
            }`}
          >
            <span
              className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
                annual ? 'translate-x-6' : 'translate-x-1'
              }`}
            />
          </button>
        </div>

        {annual && selectedTier && (
          <p className="text-sm text-green-600 dark:text-green-400">
            Save {fmt((selectedTier.priceMonthly - selectedTier.priceAnnual) * 12)} {selectedTier.currency}/yr compared to monthly.
          </p>
        )}

        <div className="flex justify-between items-baseline pt-2 border-t border-gray-200 dark:border-gray-700">
          <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
            {annual ? 'Monthly total (annual)' : 'Monthly total'}
          </span>
          <div className="text-right">
            <span className="text-2xl font-bold text-gray-900 dark:text-white">{fmt(total)}</span>
            <span className="text-sm text-gray-500 dark:text-gray-400 ml-1">
              {catalog.tiers[0]?.currency ?? 'RSD'}/mo
            </span>
          </div>
        </div>

        <div className="pt-2 space-y-2">
          <Button onClick={handleSave} loading={saving} disabled={!selectedTier}>
            Save subscription
          </Button>

          {saveSuccess && (
            <p className="text-sm text-green-600 dark:text-green-400">Subscription saved.</p>
          )}
          {saveError && (
            <p className="text-sm text-red-600 dark:text-red-400">{saveError}</p>
          )}
        </div>
      </div>
    </div>
  );
}
