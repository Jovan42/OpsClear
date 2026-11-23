import { describe, expect, it } from 'vitest';
import i18next, { type TFunction } from 'i18next';
import enShared2 from '../i18n/locales/en/shared2.json';
import enJobsComponents from '../i18n/locales/en/jobsComponents.json';
import srShared2 from '../i18n/locales/sr/shared2.json';
import srJobsComponents from '../i18n/locales/sr/jobsComponents.json';
import { jobStatusLabel, STATUS_KEYS } from './jobStatusLabel';
import type { JobStatus } from '../types';

async function tFor(lng: 'en' | 'sr'): Promise<TFunction> {
  const instance = i18next.createInstance();
  await instance.init({
    lng,
    resources: {
      en: { shared2: enShared2, jobsComponents: enJobsComponents },
      sr: { shared2: srShared2, jobsComponents: srJobsComponents },
    },
    interpolation: { escapeValue: false },
  });
  return instance.t;
}

/** The status history duration line, built the way StatusHistory builds it. */
function durationLine(t: TFunction, status: JobStatus): string {
  return t('jobsComponents:statusHistory.durationIn', {
    duration: '47 min',
    status: jobStatusLabel(status, t),
  });
}

describe('jobStatusLabel', () => {
  it('maps every job status to its English label', async () => {
    const t = await tFor('en');
    expect(jobStatusLabel('NEW', t)).toBe('New');
    expect(jobStatusLabel('IN_PROGRESS', t)).toBe('In Progress');
    expect(jobStatusLabel('BLOCKED', t)).toBe('Blocked');
    expect(jobStatusLabel('COMPLETED', t)).toBe('Completed');
  });

  it('maps every job status to its Serbian label', async () => {
    const t = await tFor('sr');
    expect(jobStatusLabel('NEW', t)).toBe('Novo');
    expect(jobStatusLabel('IN_PROGRESS', t)).toBe('U toku');
    expect(jobStatusLabel('BLOCKED', t)).toBe('Blokirano');
    expect(jobStatusLabel('COMPLETED', t)).toBe('Završeno');
  });

  it('shows an unknown value as is and a missing one as a dash', async () => {
    const t = await tFor('en');
    expect(jobStatusLabel('ARCHIVED', t)).toBe('ARCHIVED');
    expect(jobStatusLabel(null, t)).toBe('—');
    expect(jobStatusLabel(undefined, t)).toBe('—');
  });
});

describe('status history duration line', () => {
  it('reads the status label, not the raw code, in English', async () => {
    const t = await tFor('en');
    expect(durationLine(t, 'NEW')).toBe('47 min in New');
    expect(durationLine(t, 'IN_PROGRESS')).toBe('47 min in In Progress');
    expect(durationLine(t, 'BLOCKED')).toBe('47 min in Blocked');
    expect(durationLine(t, 'COMPLETED')).toBe('47 min in Completed');
  });

  it('reads the Serbian label with no English code left, in Serbian', async () => {
    const t = await tFor('sr');
    expect(durationLine(t, 'NEW')).toBe('47 min u statusu Novo');
    expect(durationLine(t, 'IN_PROGRESS')).toBe('47 min u statusu U toku');
    expect(durationLine(t, 'BLOCKED')).toBe('47 min u statusu Blokirano');
    expect(durationLine(t, 'COMPLETED')).toBe('47 min u statusu Završeno');
    for (const status of Object.keys(STATUS_KEYS) as JobStatus[]) {
      expect(durationLine(t, status)).not.toContain(status);
    }
  });
});
