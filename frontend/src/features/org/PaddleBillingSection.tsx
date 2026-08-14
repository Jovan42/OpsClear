import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { CheckoutEventNames, type PaddleEventData } from '@paddle/paddle-js';
import Button from '../../components/Button';
import ConfirmModal from '../../components/ConfirmModal';
import { openPaddleCheckout } from './paddleCheckout';
import { useOrgSubscription } from './useSubscription';
import { useInitiatePaddleSubscription, useUpdatePaymentMethod, useCancelSubscription } from './usePaddleSubscription';

const PROCESSING_POLL_MS = 2000;
const PROCESSING_TIMEOUT_MS = 20000;

interface Props {
  orgId: string;
}

export default function PaddleBillingSection({ orgId }: Props) {
  const { t } = useTranslation('org');
  const queryClient = useQueryClient();
  const [awaitingWebhook, setAwaitingWebhook] = useState(false);
  const [cancelModalOpen, setCancelModalOpen] = useState(false);
  const [cancelledMessage, setCancelledMessage] = useState(false);

  const { data: currentSub } = useOrgSubscription(orgId, awaitingWebhook ? PROCESSING_POLL_MS : false);
  const { mutate: initiate, isPending: initiating } = useInitiatePaddleSubscription(orgId);
  const { mutate: getUpdateTransaction, isPending: loadingUpdateTransaction } = useUpdatePaymentMethod(orgId);
  const { mutate: cancelSubscription, isPending: cancelling } = useCancelSubscription(orgId);

  const processing = awaitingWebhook && !currentSub?.subscriptionStatus;

  useEffect(() => {
    if (!awaitingWebhook) return;
    const timeout = setTimeout(() => setAwaitingWebhook(false), PROCESSING_TIMEOUT_MS);
    return () => clearTimeout(timeout);
  }, [awaitingWebhook]);

  if (!currentSub || currentSub.internal) return null;

  function handleCheckoutEvent(event: PaddleEventData) {
    if (event.name === CheckoutEventNames.CHECKOUT_COMPLETED) {
      setAwaitingWebhook(true);
    } else if (event.name === CheckoutEventNames.CHECKOUT_CLOSED) {
      queryClient.invalidateQueries({ queryKey: ['organisations', orgId, 'subscription'] });
    }
  }

  function handleEnterPaymentDetails() {
    if (!currentSub) return;
    initiate(undefined, {
      onSuccess: (data) => {
        const annual = currentSub.billingCycle === 'ANNUAL';
        const tierPriceId = annual ? currentSub.tier.paddlePriceIdAnnual : currentSub.tier.paddlePriceIdMonthly;
        if (!tierPriceId) return;
        const addonItems = currentSub.addons
          .map((a) => (annual ? a.paddlePriceIdAnnual : a.paddlePriceIdMonthly))
          .filter((priceId): priceId is string => priceId !== null)
          .map((priceId) => ({ priceId, quantity: 1 }));

        openPaddleCheckout(
          {
            items: [{ priceId: tierPriceId, quantity: 1 }, ...addonItems],
            customer: { id: data.paddleCustomerId },
          },
          handleCheckoutEvent,
        );
      },
    });
  }

  function handleUpdatePaymentMethod() {
    getUpdateTransaction(undefined, {
      onSuccess: (data) => {
        openPaddleCheckout({ transactionId: data.transactionId }, handleCheckoutEvent);
      },
    });
  }

  function handleCancel() {
    cancelSubscription(undefined, {
      onSuccess: () => {
        setCancelModalOpen(false);
        setCancelledMessage(true);
      },
    });
  }

  const annual = currentSub.billingCycle === 'ANNUAL';
  const tierPriceId = annual ? currentSub.tier.paddlePriceIdAnnual : currentSub.tier.paddlePriceIdMonthly;
  const missingAddonPrice = currentSub.addons.some((a) => (annual ? a.paddlePriceIdAnnual : a.paddlePriceIdMonthly) === null);
  const priceNotSynced = !tierPriceId || missingAddonPrice;

  const status = currentSub.subscriptionStatus;
  const hasSubscription = !!currentSub.paddleSubscriptionId && status !== null;

  if (processing) {
    return (
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-6 py-5">
        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{t('paddleProcessingTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('paddleProcessingDesc')}</p>
      </div>
    );
  }

  if (!hasSubscription) {
    return (
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-6 py-5 space-y-3">
        <div>
          <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{t('paddleNotSetUpTitle')}</p>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            {status === 'CANCELED' ? t('paddleCanceledDesc') : t('paddleNotSetUpDesc')}
          </p>
        </div>
        {priceNotSynced ? (
          <p className="text-sm text-amber-600 dark:text-amber-400">{t('paddlePriceNotSyncedYet')}</p>
        ) : (
          <Button onClick={handleEnterPaymentDetails} loading={initiating}>
            {t('paddleEnterPaymentDetailsButton')}
          </Button>
        )}
      </div>
    );
  }

  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-6 py-5 space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{t('paddleBillingTitle')}</p>
        <span
          className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
            status === 'PAST_DUE'
              ? 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400'
              : 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
          }`}
        >
          {status === 'PAST_DUE' ? t('paddleStatusPastDue') : t('paddleStatusActive')}
        </span>
      </div>

      {status === 'PAST_DUE' && (
        <p className="text-sm text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-900/20 rounded-lg px-3 py-2">
          {t('paddlePastDueBanner')}
        </p>
      )}

      {cancelledMessage && (
        <p className="text-sm text-gray-500 dark:text-gray-400">{t('paddleCancelScheduledDesc')}</p>
      )}

      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" size="sm" onClick={handleUpdatePaymentMethod} loading={loadingUpdateTransaction}>
          {t('paddleUpdatePaymentMethodButton')}
        </Button>
        {!cancelledMessage && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setCancelModalOpen(true)}
            className="text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20"
          >
            {t('paddleCancelSubscriptionButton')}
          </Button>
        )}
      </div>

      <ConfirmModal
        open={cancelModalOpen}
        onClose={() => setCancelModalOpen(false)}
        onConfirm={handleCancel}
        title={t('paddleCancelModalTitle')}
        message={t('paddleCancelModalMessage')}
        confirmLabel={t('paddleCancelSubscriptionButton')}
        variant="danger"
        isPending={cancelling}
      />
    </div>
  );
}
