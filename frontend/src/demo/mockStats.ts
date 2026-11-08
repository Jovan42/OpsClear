import { DEMO_USERS } from './mockData';
import type { StatsBucket, StatsMember, StatsResponse, StatsSummary, StatsWeeks } from '../types';

/**
 * Sample data for the Dashboard card's Stats slide (ADR-0050, JOB-319). Unlike the rest of the
 * demo, stats aren't derived from the shared job dataset: a handful of seeded jobs can't produce
 * a believable 26-week trend. Instead each demo member has a fixed weekly completion series and
 * fixed averages, and the team view is built from those, so the tiles, chart, members table and
 * drill-down all agree with each other for every range.
 */

const HOUR = 60 * 60;
const DAY = 24 * HOUR;

interface MemberProfile {
  userId: string;
  name: string;
  /** Completions per week, oldest first, 26 weeks; the last one is the current (partial) week. */
  weekly: number[];
  leadSeconds: number;
  cycleSeconds: number;
  blockedSeconds: number;
  /** Share of completed jobs that had a deadline. */
  deadlineShare: number;
  /** Share of those that finished after the deadline's day. */
  lateShare: number;
}

const PROFILES: MemberProfile[] = [
  {
    userId: DEMO_USERS.ana.id,
    name: DEMO_USERS.ana.name,
    weekly: [2, 3, 2, 4, 3, 3, 2, 4, 3, 5, 4, 3, 4, 3, 5, 4, 4, 3, 5, 4, 6, 4, 5, 5, 6, 2],
    leadSeconds: 5 * DAY + 4 * HOUR,
    cycleSeconds: 3 * DAY + 2 * HOUR,
    blockedSeconds: 9 * HOUR,
    deadlineShare: 0.75,
    lateShare: 0.15,
  },
  {
    userId: DEMO_USERS.marko.id,
    name: DEMO_USERS.marko.name,
    weekly: [3, 2, 4, 3, 4, 2, 3, 3, 4, 3, 5, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 4, 6, 5, 3],
    leadSeconds: 4 * DAY + 10 * HOUR,
    cycleSeconds: 2 * DAY + 15 * HOUR,
    blockedSeconds: 5 * HOUR,
    deadlineShare: 0.7,
    lateShare: 0.1,
  },
  {
    userId: DEMO_USERS.iva.id,
    name: DEMO_USERS.iva.name,
    weekly: [1, 2, 1, 2, 2, 1, 3, 2, 2, 2, 3, 2, 3, 2, 3, 3, 2, 3, 3, 2, 4, 3, 3, 4, 3, 1],
    leadSeconds: 6 * DAY + 18 * HOUR,
    cycleSeconds: 4 * DAY + 3 * HOUR,
    blockedSeconds: DAY + 6 * HOUR,
    deadlineShare: 0.8,
    lateShare: 0.25,
  },
];

/** Small week-to-week wobble so the averages don't look flat (deterministic, ±12%). */
function wobble(seconds: number, weekIndex: number): number {
  return Math.round(seconds * (1 + 0.12 * Math.sin(weekIndex * 1.7)));
}

function isoDate(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

/** Mondays of the last `weeks` weeks in the viewer's zone, oldest first, ending with this week. */
function weekStarts(weeks: number): string[] {
  const monday = new Date();
  monday.setHours(0, 0, 0, 0);
  monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7));
  return Array.from({ length: weeks }, (_, i) => {
    const d = new Date(monday);
    d.setDate(monday.getDate() - (weeks - 1 - i) * 7);
    return isoDate(d);
  });
}

interface Row {
  completedCount: number;
  leadTotal: number;
  cycleTotal: number;
  blockedTotal: number;
  completedWithDeadline: number;
  completedLate: number;
}

const EMPTY_ROW: Row = {
  completedCount: 0, leadTotal: 0, cycleTotal: 0, blockedTotal: 0, completedWithDeadline: 0, completedLate: 0,
};

/**
 * Late jobs per week. Rounding each week on its own would round most weeks to zero, so spread
 * them by the running total of jobs with a deadline instead (still deterministic).
 */
function lateSeries(p: MemberProfile): number[] {
  let cumulative = 0;
  return p.weekly.map((count) => {
    const withDeadline = Math.round(count * p.deadlineShare);
    const before = Math.floor(cumulative * p.lateShare);
    cumulative += withDeadline;
    return Math.min(withDeadline, Math.floor(cumulative * p.lateShare) - before);
  });
}

const LATE = new Map(PROFILES.map((p) => [p.userId, lateSeries(p)]));

function memberWeek(p: MemberProfile, weekIndex: number): Row {
  const count = p.weekly[weekIndex] ?? 0;
  return {
    completedCount: count,
    leadTotal: count * wobble(p.leadSeconds, weekIndex),
    cycleTotal: count * wobble(p.cycleSeconds, weekIndex + 1),
    blockedTotal: count * wobble(p.blockedSeconds, weekIndex + 2),
    completedWithDeadline: Math.round(count * p.deadlineShare),
    completedLate: LATE.get(p.userId)?.[weekIndex] ?? 0,
  };
}

function add(a: Row, b: Row): Row {
  return {
    completedCount: a.completedCount + b.completedCount,
    leadTotal: a.leadTotal + b.leadTotal,
    cycleTotal: a.cycleTotal + b.cycleTotal,
    blockedTotal: a.blockedTotal + b.blockedTotal,
    completedWithDeadline: a.completedWithDeadline + b.completedWithDeadline,
    completedLate: a.completedLate + b.completedLate,
  };
}

function avg(total: number, count: number): number | null {
  return count > 0 ? Math.round(total / count) : null;
}

function toSummary(r: Row): StatsSummary {
  return {
    completedCount: r.completedCount,
    avgLeadTimeSeconds: avg(r.leadTotal, r.completedCount),
    avgCycleTimeSeconds: avg(r.cycleTotal, r.completedCount),
    avgBlockedSeconds: avg(r.blockedTotal, r.completedCount),
    completedWithDeadline: r.completedWithDeadline,
    completedLate: r.completedLate,
    lateRate: r.completedWithDeadline > 0 ? r.completedLate / r.completedWithDeadline : null,
  };
}

/** Same shape as GET /api/projects/{id}/stats for the demo's Owner persona (so `members` is set). */
export function buildDemoStats(weeks: StatsWeeks, timezone: string, memberId: string | null): StatsResponse {
  const offset = 26 - weeks;
  const profiles = memberId ? PROFILES.filter((p) => p.userId === memberId) : PROFILES;
  const starts = weekStarts(weeks);

  const buckets: StatsBucket[] = starts.map((weekStart, i) => {
    const row = profiles.reduce((acc, p) => add(acc, memberWeek(p, offset + i)), EMPTY_ROW);
    const s = toSummary(row);
    return {
      weekStart,
      completedCount: s.completedCount,
      avgLeadTimeSeconds: s.avgLeadTimeSeconds,
      avgCycleTimeSeconds: s.avgCycleTimeSeconds,
      avgBlockedSeconds: s.avgBlockedSeconds,
      completedWithDeadline: s.completedWithDeadline,
      completedLate: s.completedLate,
    };
  });

  const memberRows = PROFILES.map((p) => ({
    profile: p,
    row: starts.reduce((acc, _s, i) => add(acc, memberWeek(p, offset + i)), EMPTY_ROW),
  }));
  const selected = memberRows.filter(({ profile }) => profiles.includes(profile));
  const summary = toSummary(selected.reduce((acc, { row }) => add(acc, row), EMPTY_ROW));

  const members: StatsMember[] | null = memberId
    ? null
    : memberRows
        .map(({ profile, row }) => ({ userId: profile.userId, name: profile.name, ...toSummary(row) }))
        // By name, like the backend: no ranking, so it doesn't read as a leaderboard (ADR-0050).
        .sort((a, b) => a.name.localeCompare(b.name));

  return { weeks, timezone, memberId, summary, buckets, members };
}
