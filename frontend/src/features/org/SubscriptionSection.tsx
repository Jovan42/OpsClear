import { useEffect, useState } from 'react';
import { isAxiosError } from 'axios';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { CheckoutEventNames, type PaddleEventData } from '@paddle/paddle-js';
import type { OrgSubscriptionResponse, PreviewSubscriptionUpdateResponse, SubscriptionTierResponse } from '../../types';
import Skeleton from '../../components/Skeleton';
import Button from '../../components/Button';
import ConfirmModal from '../../components/ConfirmModal';
import { useDebounce } from '../../hooks/useDebounce';
import { hasRealPaddleBilling } from './paddleBillingStatus';
import { PADDLE_INLINE_FRAME_CLASS, closePaddleCheckout, openPaddleCheckout } from './paddleCheckout';
import { useCatalog, useOrgSubscription, useUpsertOrgSubscription } from './useSubscription';
import {
  useCancelPendingDowngrade,
  useInitiatePaddleSubscription,
  usePreviewPaddleSubscriptionUpdate,
  useUpdatePaddleSubscription,
} from './usePaddleSubscription';

const PROCESSING_POLL_MS = 2000;
const PROCESSING_TIMEOUT_MS = 20000;

function fmt(n: number) {
  return new Intl.NumberFormat('sr-RS').format(n);
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

function tierPrice(tier: SubscriptionTierResponse, annual: boolean) {
  return annual ? tier.priceAnnual : tier.priceMonthly;
}

// Mirrors the backend's requireNotMixedChange guard (PaddleSubscriptionService) —
// checked here too so the UI can block/skip proactively instead of round-tripping
// to the server just to get a 409 back. Uses the subscription's actual billing
// cycle, not the local `annual` toggle, to match what the backend compares.
function isMixedAddonChange(
  currentSub: OrgSubscriptionResponse,
  selectedTier: SubscriptionTierResponse,
  selectedAddons: Set<string>,
) {
  const billingCycleAnnual = currentSub.billingCycle === 'ANNUAL';
  const currentAddonIds = new Set(currentSub.addons.map((a) => a.id));
  const addonAdded = [...selectedAddons].some((id) => !currentAddonIds.has(id));
  const addonRemoved = [...currentAddonIds].some((id) => !selectedAddons.has(id));
  const currentTierPrice = tierPrice(currentSub.tier, billingCycleAnnual);
  const selectedTierPrice = tierPrice(selectedTier, billingCycleAnnual);
  const hasIncrease = selectedTierPrice > currentTierPrice || addonAdded;
  const hasDecrease = selectedTierPrice < currentTierPrice || addonRemoved;
  return hasIncrease && hasDecrease;
}

interface Props {
  orgId: string;
}

export default function SubscriptionSection({ orgId }: Props) {
  const { t } = useTranslation('org');
  const queryClient = useQueryClient();
  const { data: catalog, isLoading: catalogLoading } = useCatalog();
  const [awaitingWebhook, setAwaitingWebhook] = useState(false);
  const { data: currentSub, isLoading: subLoading } = useOrgSubscription(orgId, awaitingWebhook ? PROCESSING_POLL_MS : false);
  const { mutate: upsert, isPending: saving } = useUpsertOrgSubscription(orgId);
  const { mutate: previewPaddleUpdate, isPending: previewing } = usePreviewPaddleSubscriptionUpdate(orgId);
  const { mutate: previewLiveUpdate } = usePreviewPaddleSubscriptionUpdate(orgId);
  const { mutate: updatePaddleSubscription, isPending: savingPaddle } = useUpdatePaddleSubscription(orgId);
  const { mutate: cancelPendingDowngrade, isPending: cancellingPendingDowngrade } = useCancelPendingDowngrade(orgId);
  const { mutate: initiate, isPending: initiating } = useInitiatePaddleSubscription(orgId);

  const [memberIdx,  setMemberIdx]  = useState(0);
  const [projectIdx, setProjectIdx] = useState(0);
  const [selectedAddons, setSelectedAddons] = useState<Set<string>>(new Set());
  const [annual, setAnnual] = useState(false);
  const [saveError,   setSaveError]   = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [pendingPreview, setPendingPreview] = useState<PreviewSubscriptionUpdateResponse | null>(null);
  const [livePreview, setLivePreview] = useState<PreviewSubscriptionUpdateResponse | null>(null);
  const [cancelDowngradeError, setCancelDowngradeError] = useState<string | null>(null);
  const [checkoutOpen, setCheckoutOpen] = useState(false);

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

  const selectionKey = selectedTier
    ? `${selectedTier.id}|${annual}|${[...selectedAddons].sort().join(',')}`
    : null;
  const debouncedSelectionKey = useDebounce(selectionKey, 500);

  // Live "extra to pay now" preview as the customer adjusts sliders/add-ons —
  // debounced so a slider drag doesn't fire a preview call per tick. Only worth
  // asking Paddle once there's a real subscription to compare against, and only
  // when the selection actually differs from what's currently active (otherwise
  // every page load would fire a redundant call for the org's own current plan).
  useEffect(() => {
    if (!debouncedSelectionKey || !selectedTier || !currentSub) {
      setLivePreview(null);
      return;
    }
    if (!hasRealPaddleBilling(currentSub)) {
      setLivePreview(null);
      return;
    }
    const currentAddonIdSet = new Set(currentSub.addons.map((a) => a.id));
    const currentAddonIds = [...currentAddonIdSet].sort().join(',');
    const selectedAddonIds = [...selectedAddons].sort().join(',');
    const unchanged = selectedTier.id === currentSub.tier.id
      && annual === (currentSub.billingCycle === 'ANNUAL')
      && currentAddonIds === selectedAddonIds;
    if (unchanged) {
      setLivePreview(null);
      return;
    }
    // No point previewing a change the Save button would block anyway.
    if (isMixedAddonChange(currentSub, selectedTier, selectedAddons)) {
      setLivePreview(null);
      return;
    }
    previewLiveUpdate(
      { tierId: selectedTier.id, addonIds: [...selectedAddons] },
      { onSuccess: setLivePreview, onError: () => setLivePreview(null) },
    );
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSelectionKey]);

  useEffect(() => {
    if (!awaitingWebhook) return;
    const timeout = setTimeout(() => setAwaitingWebhook(false), PROCESSING_TIMEOUT_MS);
    return () => clearTimeout(timeout);
  }, [awaitingWebhook]);

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

  const hasPaddleBilling = hasRealPaddleBilling(currentSub);

  const availableAddons = catalog.addons.filter((a) => a.available);
  const comingSoonAddons = catalog.addons.filter((a) => !a.available);

  const addonTotal = availableAddons
    .filter((a) => selectedAddons.has(a.id))
    .reduce((sum, a) => sum + (annual ? a.priceAnnual : a.priceMonthly), 0);

  const basePrice = selectedTier ? tierPrice(selectedTier, annual) : 0;
  const total = basePrice + addonTotal;

  const selectedTierPriceId = selectedTier
    ? (annual ? selectedTier.paddlePriceIdAnnual : selectedTier.paddlePriceIdMonthly)
    : null;
  const selectedAddonsMissingPrice = [...selectedAddons].some((id) => {
    const addon = catalog.addons.find((a) => a.id === id);
    return !addon || (annual ? addon.paddlePriceIdAnnual : addon.paddlePriceIdMonthly) === null;
  });
  const priceNotSynced = !selectedTierPriceId || selectedAddonsMissingPrice;

  // Not just "status is null" — a resubscribe after cancellation starts from a
  // real, non-null status ('CANCELED'), which would otherwise make this always
  // false and skip straight back to the picker instead of showing processing
  // while genuinely waiting on the new payment's webhook.
  const processing =
    awaitingWebhook && currentSub?.subscriptionStatus !== 'ACTIVE' && currentSub?.subscriptionStatus !== 'PAST_DUE';

  if (processing) {
    return (
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-6 py-5">
        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{t('paddleProcessingTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('paddleProcessingDesc')}</p>
      </div>
    );
  }

  if (checkoutOpen && selectedTier) {
    return (
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-6 py-5 space-y-4">
        <div className="flex items-center justify-between">
          <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{t('paddleBillingTitle')}</p>
          <button
            onClick={handleAbandonCheckout}
            className="text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
          >
            {t('paddleCheckoutBackButton')}
          </button>
        </div>
        <div className="text-sm space-y-1 border-b border-gray-200 dark:border-gray-700 pb-3">
          <div className="flex justify-between text-gray-700 dark:text-gray-300">
            <span>{t('upTo', { count: selectedTier.maxMembers })}</span>
            <span>{fmt(basePrice)} {selectedTier.currency}</span>
          </div>
          {[...selectedAddons]
            .map((id) => catalog.addons.find((a) => a.id === id))
            .filter((a): a is NonNullable<typeof a> => !!a)
            .map((a) => (
              <div key={a.id} className="flex justify-between text-gray-500 dark:text-gray-400">
                <span>{a.name}</span>
                <span>{fmt(annual ? a.priceAnnual : a.priceMonthly)} {selectedTier.currency}</span>
              </div>
            ))}
          <div className="flex justify-between font-medium text-gray-900 dark:text-white pt-1">
            <span>{t('paddleCheckoutTotalLabel')}</span>
            <span>{fmt(total)} {selectedTier.currency}</span>
          </div>
        </div>
        <div className={PADDLE_INLINE_FRAME_CLASS} />
      </div>
    );
  }

  const isMixedChange = hasPaddleBilling && !!selectedTier && !!currentSub
    && isMixedAddonChange(currentSub, selectedTier, selectedAddons);

  const pendingTier = currentSub?.pendingTierId
    ? (catalog.tiers.find((t) => t.id === currentSub.pendingTierId) ?? null)
    : null;
  const pendingAddons = currentSub?.pendingAddonIds
    ? catalog.addons.filter((a) => currentSub.pendingAddonIds.includes(a.id))
    : [];
  const pendingAnnual = currentSub?.billingCycle === 'ANNUAL';
  const pendingTotal = pendingTier
    ? tierPrice(pendingTier, pendingAnnual)
      + pendingAddons.reduce((sum, a) => sum + (pendingAnnual ? a.priceAnnual : a.priceMonthly), 0)
    : 0;

  function handleCancelPendingDowngrade() {
    setCancelDowngradeError(null);
    cancelPendingDowngrade(undefined, {
      onError: (err) => {
        if (isAxiosError(err) && err.response?.data?.message) {
          setCancelDowngradeError(err.response.data.message as string);
        } else {
          setCancelDowngradeError(t('somethingWentWrongTryAgain'));
        }
      },
    });
  }

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

  function handleCheckoutEvent(event: PaddleEventData) {
    if (event.name === CheckoutEventNames.CHECKOUT_COMPLETED) {
      if (!selectedTier) return;
      setCheckoutOpen(false);
      // Nothing is persisted locally until the transaction actually succeeds —
      // this is the only write that ever sets tier_id/addons for an org that
      // was never on real billing before (JOB-200).
      upsert(
        {
          tierId: selectedTier.id,
          billingCycle: annual ? 'ANNUAL' : 'MONTHLY',
          addonIds: [...selectedAddons],
        },
        {
          onSuccess: () => {
            setAwaitingWebhook(true);
            setSaveSuccess(true);
          },
          onError: handleSaveError,
        },
      );
    } else if (event.name === CheckoutEventNames.CHECKOUT_CLOSED) {
      setCheckoutOpen(false);
      queryClient.invalidateQueries({ queryKey: ['organisations', orgId, 'subscription'] });
    }
  }

  function handleAbandonCheckout() {
    closePaddleCheckout();
    setCheckoutOpen(false);
  }

  // Once an org is on real Paddle billing, plan changes go through the
  // upgrade/downgrade-aware Paddle endpoints (JOB-198) instead of the free upsert —
  // first a preview so the customer can confirm exactly what will happen (charged
  // now vs. deferred to next renewal) before anything is actually changed.
  //
  // Without real billing yet, Save no longer writes anything directly (JOB-200) —
  // every tier/add-on has a real price, so staging a selection for free and never
  // paying would grant it for free forever. Instead this opens Paddle checkout
  // built straight from the current on-screen selection (not the saved record,
  // which may not exist yet); the free upsert above only runs once that checkout
  // actually completes.
  function handleSave() {
    if (!catalog || !selectedTier || isMixedChange) return;
    setSaveError(null);
    setSaveSuccess(false);

    if (!hasPaddleBilling) {
      if (priceNotSynced || !selectedTierPriceId) {
        setSaveError(t('paddlePriceNotSyncedYet'));
        return;
      }

      const addonItems = [...selectedAddons]
        .map((id) => catalog.addons.find((a) => a.id === id))
        .filter((a): a is NonNullable<typeof a> => !!a)
        .map((a) => (annual ? a.paddlePriceIdAnnual : a.paddlePriceIdMonthly))
        .filter((priceId): priceId is string => priceId !== null)
        .map((priceId) => ({ priceId, quantity: 1 }));

      setCheckoutOpen(true);
      initiate(undefined, {
        onSuccess: (data) => {
          openPaddleCheckout(
            {
              items: [{ priceId: selectedTierPriceId, quantity: 1 }, ...addonItems],
              customer: { id: data.paddleCustomerId },
            },
            handleCheckoutEvent,
          );
        },
        onError: (err) => {
          setCheckoutOpen(false);
          handleSaveError(err);
        },
      });
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
      {pendingTier && (
        <div className="bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-xl px-5 py-4 space-y-3">
          <div>
            <p className="text-sm font-medium text-amber-800 dark:text-amber-300">{t('pendingDowngradeTitle')}</p>
            <p className="text-sm text-amber-700 dark:text-amber-400 mt-1">
              {t('pendingDowngradeDesc', {
                date: currentSub?.paddlePendingDowngradeEffectiveAt
                  ? formatDate(currentSub.paddlePendingDowngradeEffectiveAt)
                  : '',
              })}
            </p>
          </div>
          <div className="text-sm text-amber-800 dark:text-amber-300 space-y-1">
            <div className="flex justify-between">
              <span>{t('upTo', { count: pendingTier.maxMembers })}, {projectLabel(pendingTier.maxProjects)}</span>
              <span>{fmt(tierPrice(pendingTier, pendingAnnual))} {pendingTier.currency}</span>
            </div>
            {pendingAddons.map((a) => (
              <div key={a.id} className="flex justify-between">
                <span>{a.name}</span>
                <span>{fmt(pendingAnnual ? a.priceAnnual : a.priceMonthly)} {pendingTier.currency}</span>
              </div>
            ))}
            <div className="flex justify-between font-semibold pt-1 border-t border-amber-200 dark:border-amber-800">
              <span>{pendingAnnual ? t('monthlyTotalAnnual') : t('monthlyTotal')}</span>
              <span>{fmt(pendingTotal)} {pendingTier.currency}</span>
            </div>
          </div>
          <Button
            variant="secondary"
            size="sm"
            onClick={handleCancelPendingDowngrade}
            loading={cancellingPendingDowngrade}
          >
            {t('cancelPendingDowngradeButton')}
          </Button>
          {cancelDowngradeError && (
            <p className="text-sm text-red-600 dark:text-red-400">{cancelDowngradeError}</p>
          )}
        </div>
      )}

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

        {livePreview?.upgrade && (livePreview.immediateChargeAmount ?? 0) > 0 && (
          <div className="flex justify-between items-center text-sm">
            <span className="text-gray-500 dark:text-gray-400">{t('paddleExtraToPayNowLabel')}</span>
            <span className="font-semibold text-green-600 dark:text-green-400">
              {fmt(livePreview.immediateChargeAmount ?? 0)} {livePreview.currency}
            </span>
          </div>
        )}

        <div className="pt-2 space-y-2">
          <Button
            onClick={handleSave}
            loading={saving || previewing || initiating}
            disabled={!selectedTier || isMixedChange || (!hasPaddleBilling && priceNotSynced)}
          >
            {hasPaddleBilling ? t('saveSubscriptionButton') : t('continueToPaymentButton')}
          </Button>

          {isMixedChange && (
            <p className="text-sm text-amber-600 dark:text-amber-400">{t('mixedChangeWarning')}</p>
          )}
          {!hasPaddleBilling && priceNotSynced && (
            <p className="text-sm text-amber-600 dark:text-amber-400">{t('paddlePriceNotSyncedYet')}</p>
          )}
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
            ? (
              <div className="space-y-3">
                <p>{t('paddleUpdateConfirmUpgradeMessage')}</p>
                <div className="flex justify-between items-baseline pt-2 border-t border-gray-200 dark:border-gray-700">
                  <span className="text-sm text-gray-500 dark:text-gray-400">
                    {t('paddleUpdateConfirmChargedNowLabel')}
                  </span>
                  <span className="text-2xl font-bold text-green-600 dark:text-green-400">
                    {fmt(pendingPreview.immediateChargeAmount ?? 0)} {pendingPreview.currency ?? ''}
                  </span>
                </div>
              </div>
            )
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
