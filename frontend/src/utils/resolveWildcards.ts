export interface WildcardContext {
  now?: Date;
  project?: string;
  creator?: string;
  assignee?: string;
  occurrence?: number;
}

function isoWeek(d: Date): number {
  const jan4 = new Date(d.getFullYear(), 0, 4);
  const startOfWeek1 = new Date(jan4);
  startOfWeek1.setDate(jan4.getDate() - ((jan4.getDay() + 6) % 7));
  const diff = d.getTime() - startOfWeek1.getTime();
  return Math.floor(diff / 604800000) + 1;
}

function pad2(n: number): string {
  return String(n).padStart(2, '0');
}

function addDays(base: Date, offset: number): Date {
  const d = new Date(base);
  d.setDate(d.getDate() + offset);
  return d;
}

function formatDate(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

function resolveBase(token: string, now: Date, ctx: WildcardContext): string | null {
  switch (token) {
    case 'date':        return formatDate(now);
    case 'day':         return pad2(now.getDate());
    case 'month':       return pad2(now.getMonth() + 1);
    case 'year':        return String(now.getFullYear());
    case 'week':        return String(isoWeek(now));
    case 'quarter':     return `Q${Math.ceil((now.getMonth() + 1) / 3)}`;
    case 'occurrence':  return ctx.occurrence !== undefined ? String(ctx.occurrence) : null;
    case 'project':     return ctx.project ?? null;
    case 'creator':     return ctx.creator ?? null;
    case 'assignee':    return ctx.assignee ?? null;
    default:            return null;
  }
}

function resolveWithArithmetic(token: string, offsetStr: string, sign: number, now: Date, ctx: WildcardContext): string | null {
  const offset = parseInt(offsetStr, 10);
  if (isNaN(offset)) return null;
  const n = offset * sign;

  if (token === 'date') return formatDate(addDays(now, n));

  if (token === 'month') {
    const shifted = new Date(now.getFullYear(), now.getMonth() + n, 1);
    return pad2(shifted.getMonth() + 1);
  }

  if (token === 'year') {
    return String(now.getFullYear() + n);
  }

  if (token === 'week') {
    return String(isoWeek(addDays(now, n * 7)));
  }

  if (token === 'quarter') {
    const totalMonths = now.getMonth() + n * 3;
    const adjustedMonth = ((totalMonths % 12) + 12) % 12;
    return `Q${Math.ceil((adjustedMonth + 1) / 3)}`;
  }

  return null;
}

export function resolveWildcards(template: string, ctx: WildcardContext = {}): string {
  const now = ctx.now ?? new Date();

  return template.replace(/\{\{([^}]+)\}\}/g, (match, inner: string) => {
    const trimmed = inner.trim();

    const arithMatch = trimmed.match(/^([a-z]+)([+-])(\d+)$/);
    if (arithMatch) {
      const [, token, sign, offsetStr] = arithMatch;
      const result = resolveWithArithmetic(token, offsetStr, sign === '+' ? 1 : -1, now, ctx);
      return result ?? match;
    }

    const result = resolveBase(trimmed, now, ctx);
    return result ?? match;
  });
}
