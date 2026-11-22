import { describe, expect, it } from 'vitest';
import { buildCron, detectPreset, parseCronParams } from './cron';

describe('detectPreset', () => {
  it('detects a daily cron', () => {
    expect(detectPreset('0 0 7 * * *')).toBe('daily');
    expect(detectPreset('0 45 23 * * *')).toBe('daily');
  });

  it('detects a weekly cron on one day', () => {
    expect(detectPreset('0 0 9 * * MON')).toBe('weekly');
    expect(detectPreset('0 30 17 * * SUN')).toBe('weekly');
  });

  it('detects a monthly cron on a day from 1 to 28', () => {
    expect(detectPreset('0 0 7 1 * *')).toBe('monthly');
    expect(detectPreset('0 0 7 28 * *')).toBe('monthly');
  });

  it('keeps crons no preset can show on advanced', () => {
    // a range or a list of days
    expect(detectPreset('0 30 7 * * MON-FRI')).toBe('advanced');
    expect(detectPreset('0 30 7 * * MON,WED')).toBe('advanced');
    // a step or a wildcard in the time fields, or a second other than 0
    expect(detectPreset('0 0 */2 * * *')).toBe('advanced');
    expect(detectPreset('0 */15 9 * * *')).toBe('advanced');
    expect(detectPreset('0 * * * * *')).toBe('advanced');
    expect(detectPreset('30 0 7 * * *')).toBe('advanced');
    // a day of the month the Monthly tab doesn't allow, or a specific month
    expect(detectPreset('0 0 7 31 * *')).toBe('advanced');
    expect(detectPreset('0 0 9 30 2 *')).toBe('advanced');
    // both day fields set, or not a 6-field cron
    expect(detectPreset('0 0 7 1 * MON')).toBe('advanced');
    expect(detectPreset('0 7 * * *')).toBe('advanced');
  });

  it('maps every cron a preset builds back to that preset', () => {
    expect(detectPreset(buildCron('daily', '07:00'))).toBe('daily');
    expect(detectPreset(buildCron('weekly', '09:30', { dow: 'FRI' }))).toBe('weekly');
    expect(detectPreset(buildCron('monthly', '06:05', { dom: 15 }))).toBe('monthly');
  });
});

describe('parseCronParams', () => {
  it('reads the time from a daily cron', () => {
    expect(parseCronParams('0 0 7 * * *')).toEqual({ time: '07:00', dow: 'MON', dom: 1 });
  });

  it('reads the day of the week and the day of the month', () => {
    expect(parseCronParams('0 30 17 * * FRI')).toEqual({ time: '17:30', dow: 'FRI', dom: 1 });
    expect(parseCronParams('0 5 6 15 * *')).toEqual({ time: '06:05', dow: 'MON', dom: 15 });
  });
});
