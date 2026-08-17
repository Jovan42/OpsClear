import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { CheckoutEventNames, type PaddleEventData } from '@paddle/paddle-js';
import Button from '../../components/Button';
import ConfirmModal from '../../components/ConfirmModal';
import { PADDLE_INLINE_FRAME_CLASS, closePaddleCheckout, openPaddleCheckout } from './paddleCheckout';
import { hasRealPaddleBilling } from './paddleBillingStatus';
import { useOrgSubscription } from './useSubscription';
import {
  useUpdatePaymentMethod,
  useCancelSubscription,
  useResumeSubscription,
} from './usePaddleSubscription';

const PROCESSING_POLL_MS = 2000;
const PROCESSING_TIMEOUT_MS = 20000;

type CheckoutMode = 'update-method' | null;

interface Props {
  orgId: string;
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

export default function PaddleBillingSection({ orgId }: Props) {
  const { t } = useTranslation('org');
  const queryClient = useQueryClient();
  const [awaitingWebhook, setAwaitingWebhook] = useState(false);
  const [cancelModalOpen, setCancelModalOpen] = useState(false);
  const [checkoutMode, setCheckoutMode] = useState<CheckoutMode>(null);
  // cancel() only calls Paddle — paddleScheduledCancellationAt is written by the
  // webhook (JOB-197), which may take a moment (or, on a local dev server Paddle
  // can't reach, never arrive) to land. This gives immediate feedback in the
  // meantime; the durable field takes over (with a real date) once it does.
  const [justCancelled, setJustCancelled] = useState(false);

  const { data: currentSub } = useOrgSubscription(orgId, awaitingWebhook ? PROCESSING_POLL_MS : false);
  const { mutate: getUpdateTransaction, isPending: loadingUpdateTransaction } = useUpdatePaymentMethod(orgId);
  const { mutate: cancelSubscription, isPending: cancelling } = useCancelSubscription(orgId);
  const { mutate: resumeSubscription, isPending: resuming } = useResumeSubscription(orgId);

  // Not just "status is null" — a resubscribe after cancellation starts from a
  // real, non-null status ('CANCELED'), which would otherwise make this always
  // false and skip straight back to the pre-checkout view instead of showing
  // processing while genuinely waiting on the new payment's webhook.
  const processing =
    awaitingWebhook && currentSub?.subscriptionStatus !== 'ACTIVE' && currentSub?.subscriptionStatus !== 'PAST_DUE';

  useEffect(() => {
    if (!awaitingWebhook) return;
    const timeout = setTimeout(() => setAwaitingWebhook(false), PROCESSING_TIMEOUT_MS);
    return () => clearTimeout(timeout);
  }, [awaitingWebhook]);

  if (!currentSub || currentSub.internal) return null;

  function handleCheckoutEvent(event: PaddleEventData) {
    if (event.name === CheckoutEventNames.CHECKOUT_COMPLETED) {
      setCheckoutMode(null);
      setAwaitingWebhook(true);
    } else if (event.name === CheckoutEventNames.CHECKOUT_CLOSED) {
      setCheckoutMode(null);
      queryClient.invalidateQueries({ queryKey: ['organisations', orgId, 'subscription'] });
    }
  }

  function handleAbandonCheckout() {
    closePaddleCheckout();
    setCheckoutMode(null);
  }

  function handleUpdatePaymentMethod() {
    setCheckoutMode('update-method');
    getUpdateTransaction(undefined, {
      onSuccess: (data) => {
        openPaddleCheckout({ transactionId: data.transactionId }, handleCheckoutEvent);
      },
      onError: () => setCheckoutMode(null),
    });
  }

  function handleCancel() {
    cancelSubscription(undefined, {
      onSuccess: () => {
        setCancelModalOpen(false);
        setJustCancelled(true);
      },
    });
  }

  function handleResume() {
    resumeSubscription(undefined, {
      onSuccess: () => {
        // Defensive — clearScheduledCancellation is synchronous server-side and the
        // invalidated query should already reflect it, but this guarantees the
        // optimistic flag never outlives an actual resume.
        setJustCancelled(false);
        toast.success(t('paddleResumedToast'));
      },
    });
  }

  const status = currentSub.subscriptionStatus;
  const hasSubscription = hasRealPaddleBilling(currentSub);

  if (processing) {
    return (
      <div className="mt-4 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-6 py-5">
        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{t('paddleProcessingTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('paddleProcessingDesc')}</p>
      </div>
    );
  }

  if (checkoutMode) {
    return (
      <div className="mt-4 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-6 py-5 space-y-4">
        <div className="flex items-center justify-between">
          <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{t('paddleBillingTitle')}</p>
          <button
            onClick={handleAbandonCheckout}
            className="text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
          >
            {t('paddleCheckoutBackButton')}
          </button>
        </div>
        <div className={PADDLE_INLINE_FRAME_CLASS} />
      </div>
    );
  }

  // First-time payment now happens entirely from SubscriptionSection's plan
  // picker (JOB-200) — nothing gets saved locally until checkout actually
  // succeeds, so there's no "selected but unpaid" org for this component to
  // show an entry point for. Once real billing exists, this renders below.
  if (!hasSubscription) return null;

  return (
    <div className="mt-4 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-6 py-5 space-y-4">
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

      {currentSub.paddleScheduledCancellationAt ? (
        <p className="text-sm text-gray-500 dark:text-gray-400">
          {t('paddleCancelScheduledDesc', { date: formatDate(currentSub.paddleScheduledCancellationAt) })}
        </p>
      ) : (
        justCancelled && (
          <p className="text-sm text-gray-500 dark:text-gray-400">{t('paddleCancelModalMessage')}</p>
        )
      )}

      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" size="sm" onClick={handleUpdatePaymentMethod} loading={loadingUpdateTransaction}>
          {t('paddleUpdatePaymentMethodButton')}
        </Button>
        {justCancelled || currentSub.paddleScheduledCancellationAt ? (
          <Button variant="secondary" size="sm" onClick={handleResume} loading={resuming}>
            {t('paddleResumeSubscriptionButton')}
          </Button>
        ) : (
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
