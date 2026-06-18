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

// Detect preset type from a Spring cron expression, or 'advanced'.
export function detectPreset(cron: string): 'daily' | 'weekly' | 'monthly' | 'advanced' {
  const parts = cron.trim().split(/\s+/);
  if (parts.length !== 6) return 'advanced';
  const [, , , dom, month, dow] = parts;
  if (month !== '*') return 'advanced';
  if (dom === '*' && dow === '*') return 'advanced';
  if (dom !== '*' && dow === '*') return 'monthly';
  if (dom === '*' && dow !== '*' && !dow.includes(',') && !dow.includes('-')) return 'weekly';
  if (dom === '*' && dow === '*') return 'daily';
  // daily: dom=* dow=*
  if (dom === '*' && dow === '*') return 'daily';
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
