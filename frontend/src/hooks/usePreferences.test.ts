import { describe, expect, it } from 'vitest';
import { defaults, sanitize, sanitizePartial, type Preferences } from './usePreferences';

const PROJECT_ID = '3f2b8c1e-5d4a-4b6f-9e7d-1a2b3c4d5e6f';

// One valid non-default value per field, so every validator is exercised.
const valid: Preferences = {
  theme: 'dark',
  locale: 'sr',
  defaultViewMode: 'FLAT',
  milestoneAccordionState: 'COLLAPSED',
  defaultStatusTab: 'BLOCKED',
  hideCompletedFromAll: true,
  defaultSortOrder: 'PRIORITY_DESC',
  showBlockedSection: false,
  showOverdueSection: false,
  showPendingApprovalsSection: false,
  defaultProjectPage: 'APPROVALS',
  deadlineFormat: 'RELATIVE',
  milestoneProgressFormat: 'PERCENTAGE',
  notificationSound: false,
  dueSoonDays: 0,
  defaultAssigneeFilter: 'MINE',
  rowDensity: 'COMPACT',
  dateFormat: 'MDY_SLASH',
  landingProject: 'SPECIFIC',
  landingProjectId: PROJECT_ID,
  statsDefaultRange: 26,
  showOverviewApprovals: false,
};

// One invalid value per field: wrong type, unknown option, or out of range.
const invalid: Record<keyof Preferences, unknown> = {
  theme: 'purple',
  locale: 'de',
  defaultViewMode: 'grouped',
  milestoneAccordionState: true,
  defaultStatusTab: 'DONE',
  hideCompletedFromAll: 'true',
  defaultSortOrder: 'NAME',
  showBlockedSection: 1,
  showOverdueSection: null,
  showPendingApprovalsSection: 'false',
  defaultProjectPage: 'SETTINGS',
  deadlineFormat: 0,
  milestoneProgressFormat: 'RATIO',
  notificationSound: 'on',
  dueSoonDays: 2,
  defaultAssigneeFilter: 'TEAM',
  rowDensity: 'DENSE',
  dateFormat: 'YMD',
  landingProject: 'FIRST',
  landingProjectId: 'PRJ-001',
  statsDefaultRange: '12',
  showOverviewApprovals: 'yes',
};

describe('sanitize', () => {
  it('has a default for every new ADR-0061 preference', () => {
    expect(defaults).toMatchObject({
      dueSoonDays: 3,
      defaultAssigneeFilter: 'EVERYONE',
      rowDensity: 'COMFORTABLE',
      dateFormat: 'AUTO',
      landingProject: 'LIST',
      landingProjectId: null,
      statsDefaultRange: 12,
      showOverviewApprovals: true,
    });
  });

  it('keeps every valid value', () => {
    expect(sanitize(valid)).toEqual(valid);
  });

  it.each(Object.keys(invalid) as (keyof Preferences)[])('replaces an invalid %s with its default', (key) => {
    expect(sanitize({ ...valid, [key]: invalid[key] })).toEqual({ ...valid, [key]: defaults[key] });
  });

  it('drops unknown keys', () => {
    const result = sanitize({ theme: 'dark', legacyThing: 1, __proto__x: 'y' });
    expect(result).toEqual({ ...defaults, theme: 'dark' });
    expect(Object.keys(result).sort()).toEqual(Object.keys(defaults).sort());
  });

  it('fills missing keys with defaults', () => {
    expect(sanitize({ locale: 'sr' })).toEqual({ ...defaults, locale: 'sr' });
  });

  it.each([null, undefined, 'dark', 42, true, ['theme']])('gives the defaults for %j', (raw) => {
    expect(sanitize(raw)).toEqual(defaults);
  });

  it('accepts a null landing project', () => {
    expect(sanitize({ ...valid, landingProjectId: null }).landingProjectId).toBeNull();
  });
});

describe('sanitizePartial', () => {
  it('keeps only known keys with valid values, without adding defaults', () => {
    expect(sanitizePartial({ theme: 'dark', locale: 'de', other: true })).toEqual({ theme: 'dark' });
  });

  it('gives {} for something that is not an object', () => {
    expect(sanitizePartial('{"theme":"dark"}')).toEqual({});
  });
});
