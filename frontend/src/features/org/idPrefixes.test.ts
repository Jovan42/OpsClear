import { describe, expect, it } from 'vitest';
import type { OrgSettings } from '../../types';
import {
  changedPrefixes,
  duplicatePrefixFields,
  fieldForPrefixError,
  normalisePrefixInput,
  prefixValues,
  previewNextId,
} from './idPrefixes';

const settings: OrgSettings = {
  projectPrefix: 'PRJ',
  jobPrefix: 'JOB',
  milestonePrefix: 'MIL',
  templatePrefix: 'TPL',
  nextProjectId: 'PRJ-015',
  nextJobId: 'JOB-347',
  nextMilestoneId: 'MIL-045',
  nextTemplateId: 'TPL-1005',
};

describe('normalisePrefixInput', () => {
  it('uppercases, drops non-letters and keeps at most 3 letters', () => {
    expect(normalisePrefixInput('tsk')).toBe('TSK');
    expect(normalisePrefixInput('t-1s k')).toBe('TSK');
    expect(normalisePrefixInput('TASK')).toBe('TAS');
  });
});

describe('previewNextId', () => {
  it('swaps the edited prefix into the next ID', () => {
    expect(previewNextId(settings, 'jobPrefix', 'TSK')).toBe('TSK-347');
    expect(previewNextId(settings, 'templatePrefix', 'TM')).toBe('TM-1005');
  });

  it('keeps the server next ID while the prefix is not valid', () => {
    expect(previewNextId(settings, 'jobPrefix', 'T')).toBe('JOB-347');
    expect(previewNextId(settings, 'jobPrefix', '')).toBe('JOB-347');
  });
});

describe('changedPrefixes', () => {
  it('returns only the changed prefixes', () => {
    expect(changedPrefixes({ ...prefixValues(settings), jobPrefix: 'TSK' }, settings)).toEqual({
      jobPrefix: 'TSK',
    });
    expect(changedPrefixes(prefixValues(settings), settings)).toEqual({});
  });
});

describe('duplicatePrefixFields', () => {
  it('lists every field that shares its value', () => {
    expect(duplicatePrefixFields({ ...prefixValues(settings), milestonePrefix: 'JOB' })).toEqual([
      'jobPrefix',
      'milestonePrefix',
    ]);
  });

  it('ignores empty fields and distinct values', () => {
    expect(duplicatePrefixFields(prefixValues(settings))).toEqual([]);
    expect(duplicatePrefixFields({ ...prefixValues(settings), jobPrefix: '', milestonePrefix: '' })).toEqual([]);
  });
});

describe('fieldForPrefixError', () => {
  const values = { ...prefixValues(settings), milestonePrefix: 'ABC' };

  it('finds the field set to the prefix the message names', () => {
    expect(fieldForPrefixError('Prefix "ABC" is already used by existing jobs', values)).toBe('milestonePrefix');
  });

  it('finds the field a validation message starts with', () => {
    expect(fieldForPrefixError('jobPrefix: must match "^\\s*[A-Za-z]{2,3}\\s*$"', values)).toBe('jobPrefix');
  });

  it('returns null when no field matches', () => {
    expect(fieldForPrefixError('Prefixes must be different from each other', values)).toBeNull();
    expect(fieldForPrefixError('Prefix "XYZ" is already used by existing jobs', values)).toBeNull();
  });
});
