import { useState } from 'react';
import { Link } from 'react-router-dom';

interface Addon {
  id: string;
  name: string;
  tagline: string;
  price: number;
  comingSoon?: true;
}

const ADDONS: Addon[] = [
  { id: 'dashboard',     name: 'Dashboard',            tagline: 'Single-screen ops overview',                           price: 990  },
  { id: 'approvals',     name: 'Approvals',             tagline: 'Sign-off workflows with logged decisions',             price: 1490 },
  { id: 'notes',         name: 'Notes',                 tagline: 'Immutable audit trail on any job',                    price: 990  },
  { id: 'history',       name: 'Job status history',    tagline: 'Full chronological status log',                       price: 990  },
  { id: 'milestones',    name: 'Milestones',            tagline: 'Group jobs into phases (max 5 per project)',          price: 1490 },
  { id: 'relationships', name: 'Job relationships',     tagline: 'Blocks / depends on / related to links',             price: 1490 },
  { id: 'api-keys',      name: 'API keys',              tagline: 'Programmatic access & integrations',                 price: 1990 },
  { id: 'templates',     name: 'Job templates',         tagline: 'Reusable job presets',                               price: 990,  comingSoon: true },
  { id: 'recurring',     name: 'Recurring scheduling',  tagline: 'Auto-create jobs on a schedule (requires Templates)', price: 1490, comingSoon: true },
];

const MEMBER_BANDS  = [5, 10, 15, 20, 30, 40, 50];
const PROJECT_BANDS = [3, 5, 10, 20, 25];

const TIER_MATRIX = [
  [2900,  3900,  5400,  7400,  9900],
  [4900,  5900,  7400,  9400, 11900],
  [6900,  7900,  9400, 11400, 13900],
  [8900,  9900, 11400, 13400, 15900],
  [12900, 13900, 15400, 17400, 19900],
  [16900, 17900, 19400, 21400, 23900],
  [20900, 21900, 23400, 25400, 27900],
];

function fmt(n: number) {
  return new Intl.NumberFormat('sr-RS').format(n);
}

export default function ProductAndPricing() {
  const [memberIdx,  setMemberIdx]  = useState(0);
  const [projectIdx, setProjectIdx] = useState(0);
  const [selected,   setSelected]   = useState<Set<string>>(new Set());
  const [annual,     setAnnual]     = useState(false);

  const base       = TIER_MATRIX[memberIdx][projectIdx];
  const addonTotal = ADDONS.filter(a => !a.comingSoon && selected.has(a.id)).reduce((s, a) => s + a.price, 0);
  const monthly    = base + addonTotal;
  const displayed  = annual ? Math.round(monthly * 10 / 12) : monthly;
  const saving     = monthly * 2;

  function toggle(id: string) {
    setSelected(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  return (
    <section className="px-6 py-20 bg-gray-50 dark:bg-gray-800">
      <div className="max-w-3xl mx-auto space-y-8">

        <div className="text-center">
          <h2 className="text-2xl font-bold text-gray-900 dark:text-white">See what's included. Know what it costs.</h2>
          <p className="text-gray-500 dark:text-gray-400 mt-2">Start with the base plan and add only what your team needs.</p>
          <Link
            to="/features"
            className="inline-block mt-3 text-sm font-medium hover:underline"
            style={{ color: 'var(--brand)' }}
          >
            See all features in detail
          </Link>
        </div>

        {/* Sliders */}
        <div className="bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-700 p-6 space-y-6">
          <p className="text-xs font-semibold text-gray-400 dark:text-gray-500 uppercase tracking-wider">Base plan</p>

          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-gray-700 dark:text-gray-300">Team members</span>
              <span className="font-medium text-gray-900 dark:text-white">Up to {MEMBER_BANDS[memberIdx]}</span>
            </div>
            <input type="range" min={0} max={MEMBER_BANDS.length - 1} step={1} value={memberIdx}
              onChange={e => setMemberIdx(Number(e.target.value))}
              className="w-full" style={{ accentColor: 'var(--brand)' }} />
            <div className="flex justify-between text-xs text-gray-400"><span>5</span><span>50</span></div>
          </div>

          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-gray-700 dark:text-gray-300">Active projects</span>
              <span className="font-medium text-gray-900 dark:text-white">
                {PROJECT_BANDS[projectIdx] === 25 ? 'Unlimited' : `Up to ${PROJECT_BANDS[projectIdx]}`}
              </span>
            </div>
            <input type="range" min={0} max={PROJECT_BANDS.length - 1} step={1} value={projectIdx}
              onChange={e => setProjectIdx(Number(e.target.value))}
              className="w-full" style={{ accentColor: 'var(--brand)' }} />
            <div className="flex justify-between text-xs text-gray-400"><span>3</span><span>Unlimited</span></div>
          </div>

          <div className="flex justify-between items-center pt-2 border-t border-gray-200 dark:border-gray-700">
            <span className="text-sm text-gray-500">Base price</span>
            <span className="font-semibold text-gray-900 dark:text-white">{fmt(base)} RSD/mo</span>
          </div>
        </div>

        {/* Add-on cards */}
        <div className="space-y-3">
          <p className="text-xs font-semibold text-gray-400 dark:text-gray-500 uppercase tracking-wider">Add-ons — click to include</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {ADDONS.map(a => {
              const active = !a.comingSoon && selected.has(a.id);
              return (
                <button
                  key={a.id}
                  disabled={!!a.comingSoon}
                  onClick={() => toggle(a.id)}
                  className={`text-left rounded-xl border p-4 transition-all ${
                    a.comingSoon
                      ? 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 opacity-50 cursor-not-allowed'
                      : active
                        ? 'border-[var(--brand)] bg-white dark:bg-gray-900 ring-1 ring-[var(--brand)]'
                        : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 hover:border-gray-300 dark:hover:border-gray-600'
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <p className="text-sm font-medium text-gray-900 dark:text-white">{a.name}</p>
                    {a.comingSoon
                      ? <span className="text-xs text-gray-400 shrink-0">Soon</span>
                      : <span className="text-xs font-medium shrink-0" style={{ color: active ? 'var(--brand)' : undefined }}>
                          {active ? '− ' : '+ '}{fmt(a.price)} RSD
                        </span>
                    }
                  </div>
                  <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">{a.tagline}</p>
                </button>
              );
            })}
          </div>
        </div>

        {/* Annual toggle + total */}
        <div className="bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-700 p-6 space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-sm text-gray-700 dark:text-gray-300">Annual billing</span>
            <button
              role="switch" aria-checked={annual} onClick={() => setAnnual(a => !a)}
              className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none ${annual ? 'bg-[var(--brand)]' : 'bg-gray-300 dark:bg-gray-600'}`}
            >
              <span className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${annual ? 'translate-x-6' : 'translate-x-1'}`} />
            </button>
          </div>

          {annual && (
            <p className="text-sm text-green-600 dark:text-green-400">
              Save {fmt(saving)} RSD/yr compared to monthly billing.
            </p>
          )}

          <div className="flex justify-between items-baseline pt-2 border-t border-gray-200 dark:border-gray-700">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
              {annual ? 'Monthly total (annual)' : 'Monthly total'}
            </span>
            <div className="text-right">
              <span className="text-2xl font-bold text-gray-900 dark:text-white">{fmt(displayed)}</span>
              <span className="text-sm text-gray-500 dark:text-gray-400 ml-1">RSD/mo</span>
            </div>
          </div>
        </div>

      </div>
    </section>
  );
}
