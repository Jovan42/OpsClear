import { useEffect, useState } from 'react';
import { isAxiosError } from 'axios';
import { useTranslation } from 'react-i18next';
import type { PreviewSubscriptionUpdateResponse, SubscriptionTierResponse } from '../../types';
import Skeleton from '../../components/Skeleton';
import Button from '../../components/Button';
import ConfirmModal from '../../components/ConfirmModal';
import { useCatalog, useOrgSubscription, useUpsertOrgSubscription } from './useSubscription';
import { usePreviewPaddleSubscriptionUpdate, useUpdatePaddleSubscription } from './usePaddleSubscription';

function fmt(n: number) {
  return new Intl.NumberFormat('sr-RS').format(n);
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

function tierPrice(tier: SubscriptionTierResponse, annual: boolean) {
  return annual ? tier.priceAnnual : tier.priceMonthly;
}

interface Props {
  orgId: string;
}

export default function SubscriptionSection({ orgId }: Props) {
  const { t } = useTranslation('org');
  const { data: catalog, isLoading: catalogLoading } = useCatalog();
  const { data: currentSub, isLoading: subLoading } = useOrgSubscription(orgId);
  const { mutate: upsert, isPending: saving } = useUpsertOrgSubscription(orgId);
  const { mutate: previewPaddleUpdate, isPending: previewing } = usePreviewPaddleSubscriptionUpdate(orgId);
  const { mutate: updatePaddleSubscription, isPending: savingPaddle } = useUpdatePaddleSubscription(orgId);

  const [memberIdx,  setMemberIdx]  = useState(0);
  const [projectIdx, setProjectIdx] = useState(0);
  const [selectedAddons, setSelectedAddons] = useState<Set<string>>(new Set());
  const [annual, setAnnual] = useState(false);
  const [saveError,   setSaveError]   = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [pendingPreview, setPendingPreview] = useState<PreviewSubscriptionUpdateResponse | null>(null);

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

  if (currentSub?.internal) {
    return (
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-6 py-5">
        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{t('internalAccountTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('internalAccountDesc')}</p>
      </div>
    );
  }

  // Same "does this org have real, non-canceled Paddle billing" check as
  // PaddleBillingSection.tsx — anything short of that keeps using the free upsert.
  const status = currentSub?.subscriptionStatus ?? null;
  const hasPaddleBilling = !!currentSub?.paddleSubscriptionId && status !== null && status !== 'CANCELED';

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

  function handleSaveError(err: unknown) {
    if (isAxiosError(err) && err.response?.data?.message) {
      setSaveError(err.response.data.message as string);
    } else {
      setSaveError(t('somethingWentWrongTryAgain'));
    }
  }

  // Once an org is on real Paddle billing, plan changes go through the
  // upgrade/downgrade-aware Paddle endpoints (JOB-198) instead of the free upsert —
  // first a preview so the customer can confirm exactly what will happen (charged
  // now vs. deferred to next renewal) before anything is actually changed.
  function handleSave() {
    if (!selectedTier) return;
    setSaveError(null);
    setSaveSuccess(false);

    if (!hasPaddleBilling) {
      upsert(
        {
          tierId: selectedTier.id,
          billingCycle: annual ? 'ANNUAL' : 'MONTHLY',
          addonIds: [...selectedAddons],
        },
        {
          onSuccess: () => setSaveSuccess(true),
          onError: handleSaveError,
        },
      );
      return;
    }

    previewPaddleUpdate(
      { tierId: selectedTier.id, addonIds: [...selectedAddons] },
      {
        onSuccess: (preview) => setPendingPreview(preview),
        onError: handleSaveError,
      },
    );
  }

  function handleConfirmPaddleUpdate() {
    if (!selectedTier) return;
    updatePaddleSubscription(
      { tierId: selectedTier.id, addonIds: [...selectedAddons] },
      {
        onSuccess: () => {
          setPendingPreview(null);
          setSaveSuccess(true);
        },
        onError: (err) => {
          setPendingPreview(null);
          handleSaveError(err);
        },
      },
    );
  }

  const projectLabel = (p: number | null) => p === null ? t('unlimited') : t('upTo', { count: p });

  return (
    <div className="space-y-4">
      {/* Base plan sliders */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5 space-y-5">
        <p className="text-xs font-semibold text-gray-400 dark:text-gray-500 uppercase tracking-wider">{t('basePlanLabel')}</p>

        <div className="space-y-2">
          <div className="flex justify-between text-sm">
            <span className="text-gray-700 dark:text-gray-300">{t('teamMembersLabel')}</span>
            <span className="font-medium text-gray-900 dark:text-white">{t('upTo', { count: memberBands[memberIdx] })}</span>
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
            <span className="text-gray-700 dark:text-gray-300">{t('activeProjectsLabel')}</span>
            <span className="font-medium text-gray-900 dark:text-white">{projectLabel(projectBands[projectIdx])}</span>
          </div>
          <input
            type="range" min={0} max={projectBands.length - 1} step={1} value={projectIdx}
            onChange={(e) => { setProjectIdx(Number(e.target.value)); setSaveSuccess(false); setSaveError(null); }}
            className="w-full" style={{ accentColor: 'var(--brand)' }}
          />
          <div className="flex justify-between text-xs text-gray-400">
            <span>{projectBands[0]}</span><span>{t('unlimited')}</span>
          </div>
        </div>

        <div className="flex justify-between items-center pt-2 border-t border-gray-200 dark:border-gray-700">
          <span className="text-sm text-gray-500 dark:text-gray-400">{t('basePriceLabel')}</span>
          <span className="font-semibold text-gray-900 dark:text-white">
            {selectedTier ? t('priceLine', { price: fmt(basePrice), currency: selectedTier.currency }) : '—'}
          </span>
        </div>
      </div>

      {/* Add-on cards */}
      <div className="space-y-2">
        <p className="text-xs font-semibold text-gray-400 dark:text-gray-500 uppercase tracking-wider">{t('addOnsLabel')}</p>
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
                  {t(`addonTaglines.${addon.key}`, { defaultValue: '' })}
                </p>
              </button>
            );
          })}
          {comingSoonAddons.map((addon) => {
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
                  <span className="text-xs text-gray-400 dark:text-gray-500 shrink-0">{t('preview')}</span>
                </div>
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                  {t(`addonTaglines.${addon.key}`, { defaultValue: '' })}
                </p>
              </button>
            );
          })}
        </div>
      </div>

      {/* Billing cycle toggle + total + save */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5 space-y-4">
        <div className="flex items-center justify-between">
          <span className="text-sm text-gray-700 dark:text-gray-300">{t('annualBillingLabel')}</span>
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
            {t('saveVsMonthly', { amount: fmt((selectedTier.priceMonthly - selectedTier.priceAnnual) * 12), currency: selectedTier.currency })}
          </p>
        )}

        <div className="flex justify-between items-baseline pt-2 border-t border-gray-200 dark:border-gray-700">
          <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
            {annual ? t('monthlyTotalAnnual') : t('monthlyTotal')}
          </span>
          <div className="text-right">
            <span className="text-2xl font-bold text-gray-900 dark:text-white">{fmt(total)}</span>
            <span className="text-sm text-gray-500 dark:text-gray-400 ml-1">
              {catalog.tiers[0]?.currency ?? 'RSD'}{t('perMonth')}
            </span>
          </div>
        </div>

        <div className="pt-2 space-y-2">
          <Button onClick={handleSave} loading={saving || previewing} disabled={!selectedTier}>
            {t('saveSubscriptionButton')}
          </Button>

          {saveSuccess && (
            <p className="text-sm text-green-600 dark:text-green-400">{t('subscriptionSaved')}</p>
          )}
          {saveError && (
            <p className="text-sm text-red-600 dark:text-red-400">{saveError}</p>
          )}
        </div>
      </div>

      <ConfirmModal
        open={!!pendingPreview}
        onClose={() => setPendingPreview(null)}
        onConfirm={handleConfirmPaddleUpdate}
        title={pendingPreview?.upgrade ? t('paddleUpdateConfirmUpgradeTitle') : t('paddleUpdateConfirmDowngradeTitle')}
        message={
          pendingPreview?.upgrade
            ? t('paddleUpdateConfirmUpgradeMessage', {
                amount: fmt(pendingPreview.immediateChargeAmount ?? 0),
                currency: pendingPreview.currency ?? '',
              })
            : t('paddleUpdateConfirmDowngradeMessage', {
                date: pendingPreview?.effectiveAt ? formatDate(pendingPreview.effectiveAt) : '',
              })
        }
        confirmLabel={t('paddleUpdateConfirmButton')}
        isPending={savingPaddle}
      />
    </div>
  );
}
