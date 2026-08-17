import type { OrgSubscriptionResponse } from '../../types';

// Whether an org has a real, currently-collectible Paddle subscription — the
// single condition that must hold before a paid tier/add-on the org has merely
// *selected* (org_subscriptions.tier_id/addons, staged via the free picker)
// actually translates into granted feature access or billing UI. Every tier and
// add-on in the catalog has a real price, so without this check an org could
// select and use any paid feature for free, forever, by never completing
// checkout.
export function hasRealPaddleBilling(subscription: OrgSubscriptionResponse | null | undefined): boolean {
  if (!subscription) return false;
  const status = subscription.subscriptionStatus;
  return !!subscription.paddleSubscriptionId && status !== null && status !== 'CANCELED';
}
