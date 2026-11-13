import type { AddonCode, OrgSubscriptionResponse } from '../../types';
import { hasRealPaddleBilling } from './paddleBillingStatus';

type Subscription = OrgSubscriptionResponse | null | undefined;

/**
 * Whether the org can use `key` at all (reads and the UI). Internal orgs always can.
 * Every tier and add-on in the catalog has a real price (no free plan), so selecting one
 * via the free picker only stages it: access needs real, active Paddle billing. A
 * PAST_DUE subscription still counts here; see `isAddonUsableForWrites`.
 */
export function hasAddonAccess(subscription: Subscription, key: AddonCode): boolean {
  if (subscription?.internal) return true;
  if (!hasRealPaddleBilling(subscription)) return false;
  return subscription?.addons.some((a) => a.key === key) ?? false;
}

/**
 * Whether the org can use `key` for writes, mirroring the backend's
 * `AddonAccessChecker.isAddonUsableForWrites` (ADR-0044): internal orgs always can (even
 * PAST_DUE); otherwise real Paddle billing, the add-on, and a subscription that isn't
 * PAST_DUE. Use it where the backend applies the same rule, e.g. whether the ADR-0052
 * completion approval gate can be turned on and is enforced.
 */
export function isAddonUsableForWrites(subscription: Subscription, key: AddonCode): boolean {
  if (subscription?.internal) return true;
  return hasAddonAccess(subscription, key) && subscription?.subscriptionStatus !== 'PAST_DUE';
}
