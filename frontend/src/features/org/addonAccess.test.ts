import { describe, expect, it } from 'vitest';
import type { OrgSubscriptionResponse } from '../../types';
import { hasAddonAccess, isAddonUsableForWrites } from './addonAccess';

function subscription(overrides: Partial<OrgSubscriptionResponse> = {}): OrgSubscriptionResponse {
  return {
    id: 'sub-1',
    billingCycle: 'MONTHLY',
    internal: false,
    updatedAt: '2026-11-01T00:00:00Z',
    tier: {} as OrgSubscriptionResponse['tier'],
    addons: [],
    totalMonthly: 0,
    paddleSubscriptionId: null,
    subscriptionStatus: null,
    paddleScheduledCancellationAt: null,
    pendingTierId: null,
    pendingAddonIds: [],
    ...overrides,
  } as OrgSubscriptionResponse;
}

const approvals = [{ id: 'a-1', key: 'APPROVALS', name: 'Approvals', priceMonthly: 5, priceAnnual: 50, available: true }] as
  OrgSubscriptionResponse['addons'];
const realBilling = { paddleSubscriptionId: 'sub_123', subscriptionStatus: 'ACTIVE' as const };

describe('isAddonUsableForWrites', () => {
  it('is true for an internal org, without billing or the add-on', () => {
    expect(isAddonUsableForWrites(subscription({ internal: true }), 'APPROVALS')).toBe(true);
  });

  it('is true for an internal org even when PAST_DUE', () => {
    const sub = subscription({ internal: true, paddleSubscriptionId: 'sub_123', subscriptionStatus: 'PAST_DUE' });
    expect(isAddonUsableForWrites(sub, 'APPROVALS')).toBe(true);
  });

  it('is false without a subscription', () => {
    expect(isAddonUsableForWrites(null, 'APPROVALS')).toBe(false);
    expect(isAddonUsableForWrites(undefined, 'APPROVALS')).toBe(false);
  });

  it('is false when the add-on is only staged (no real billing)', () => {
    expect(isAddonUsableForWrites(subscription({ addons: approvals }), 'APPROVALS')).toBe(false);
  });

  it('is false with real billing but without the add-on', () => {
    expect(isAddonUsableForWrites(subscription({ ...realBilling }), 'APPROVALS')).toBe(false);
  });

  it('is false with real billing and the add-on while PAST_DUE', () => {
    const sub = subscription({ ...realBilling, subscriptionStatus: 'PAST_DUE', addons: approvals });
    expect(isAddonUsableForWrites(sub, 'APPROVALS')).toBe(false);
  });

  it('is false when the subscription is CANCELED', () => {
    const sub = subscription({ ...realBilling, subscriptionStatus: 'CANCELED', addons: approvals });
    expect(isAddonUsableForWrites(sub, 'APPROVALS')).toBe(false);
  });

  it('is true with real, active billing and the add-on', () => {
    expect(isAddonUsableForWrites(subscription({ ...realBilling, addons: approvals }), 'APPROVALS')).toBe(true);
  });
});

describe('hasAddonAccess', () => {
  it('still grants access while PAST_DUE (reads stay available)', () => {
    const sub = subscription({ ...realBilling, subscriptionStatus: 'PAST_DUE', addons: approvals });
    expect(hasAddonAccess(sub, 'APPROVALS')).toBe(true);
  });

  it('denies a staged-only add-on', () => {
    expect(hasAddonAccess(subscription({ addons: approvals }), 'APPROVALS')).toBe(false);
  });
});
