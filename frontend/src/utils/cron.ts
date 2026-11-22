import cronstrue from 'cronstrue';

// Converts a Spring 6-field cron (sec min hour dom month dow) to human-readable string.
// cronstrue supports 6-field Quartz/Spring cron natively.
export function toReadable(springCron: string): string {
  try {
    return cronstrue.toString(springCron, { use24HourTimeFormat: true });
  } catch {
    return springCron;
  }
}

// Build Spring 6-field cron from preset params.
// time is "HH:MM" string.
export function buildCron(
  preset: 'daily' | 'weekly' | 'monthly',
  time: string,
  extra?: { dow?: string; dom?: number },
): string {
  const [hh, mm] = time.split(':');
  const h = parseInt(hh, 10);
  const m = parseInt(mm, 10);
  if (preset === 'daily') return `0 ${m} ${h} * * *`;
  if (preset === 'weekly') return `0 ${m} ${h} * * ${extra?.dow ?? 'MON'}`;
  if (preset === 'monthly') return `0 ${m} ${h} ${extra?.dom ?? 1} * *`;
  return '';
}

const PRESET_DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

function isIntIn(value: string, min: number, max: number): boolean {
  if (!/^\d+$/.test(value)) return false;
  const n = parseInt(value, 10);
  return n >= min && n <= max;
}

// Detect which preset tab can show a Spring cron expression, or 'advanced'.
// Only crons the preset would build again unchanged map to a preset: second 0, a single
// minute and hour, every month, and then every day (daily), one day of the week (weekly)
// or one day of the month in the 1-28 range the Monthly tab allows (monthly).
export function detectPreset(cron: string): 'daily' | 'weekly' | 'monthly' | 'advanced' {
  const parts = cron.trim().split(/\s+/);
  if (parts.length !== 6) return 'advanced';
  const [sec, min, hour, dom, month, dow] = parts;
  if (sec !== '0' || !isIntIn(min, 0, 59) || !isIntIn(hour, 0, 23) || month !== '*') return 'advanced';
  if (dom === '*' && dow === '*') return 'daily';
  if (dom === '*' && PRESET_DAYS.includes(dow)) return 'weekly';
  if (dow === '*' && isIntIn(dom, 1, 28)) return 'monthly';
  return 'advanced';
}

// Parse preset parameters from a Spring cron string (best-effort).
export function parseCronParams(cron: string): {
  time: string;
  dow: string;
  dom: number;
} {
  const parts = cron.trim().split(/\s+/);
  if (parts.length !== 6) return { time: '09:00', dow: 'MON', dom: 1 };
  const [, min, hour, dom] = parts;
  const h = parseInt(hour, 10);
  const m = parseInt(min, 10);
  const time = `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
  const dow = parts[5] !== '*' ? parts[5] : 'MON';
  const domNum = dom !== '*' ? parseInt(dom, 10) : 1;
  return { time, dow, dom: domNum };
}
