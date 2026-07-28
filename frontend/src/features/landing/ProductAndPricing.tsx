import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useCatalog } from '../org/useSubscription';
import type { SubscriptionTierResponse } from '../../types';

function fmt(n: number) {
  return new Intl.NumberFormat('sr-RS').format(n);
}

function tierPrice(tier: SubscriptionTierResponse, annual: boolean) {
  return annual ? tier.priceAnnual : tier.priceMonthly;
}

export default function ProductAndPricing() {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const { data: catalog } = useCatalog();

  const ADDON_TAGLINES: Record<string, string> = {
    DASHBOARD: t('productPricing.addonTaglines.DASHBOARD'),
    APPROVALS: t('productPricing.addonTaglines.APPROVALS'),
    NOTES: t('productPricing.addonTaglines.NOTES'),
    JOB_STATUS_HISTORY: t('productPricing.addonTaglines.JOB_STATUS_HISTORY'),
    MILESTONES: t('productPricing.addonTaglines.MILESTONES'),
    JOB_RELATIONSHIPS: t('productPricing.addonTaglines.JOB_RELATIONSHIPS'),
    API_KEYS: t('productPricing.addonTaglines.API_KEYS'),
    JOB_TEMPLATES: t('productPricing.addonTaglines.JOB_TEMPLATES'),
    RECURRING_SCHEDULING: t('productPricing.addonTaglines.RECURRING_SCHEDULING'),
    JOB_LINKS: t('productPricing.addonTaglines.JOB_LINKS'),
    JOB_TYPES: t('productPricing.addonTaglines.JOB_TYPES'),
  };

  const [memberIdx,  setMemberIdx]  = useState(0);
  const [projectIdx, setProjectIdx] = useState(0);
  const [selected,   setSelected]   = useState<Set<string>>(new Set());
  const [annual,     setAnnual]     = useState(false);

  const memberBands = [...new Set(catalog?.tiers.map((t) => t.maxMembers) ?? [])].sort((a, b) => a - b);
  const projectBands = [
    ...[...new Set(catalog?.tiers.map((t) => t.maxProjects) ?? [])].filter((v): v is number => v !== null).sort((a, b) => a - b),
    null,
  ];

  const selectedTier = catalog?.tiers.find(
    (t) => t.maxMembers === memberBands[memberIdx] && t.maxProjects === projectBands[projectIdx],
  ) ?? null;

  if (!catalog) return null;

  const availableAddons = catalog.addons.filter((a) => a.available);
  const comingSoonAddons = catalog.addons.filter((a) => !a.available);

  const addonTotal = availableAddons
    .filter((a) => selected.has(a.id))
    .reduce((s, a) => s + (annual ? a.priceAnnual : a.priceMonthly), 0);

  const base      = selectedTier ? tierPrice(selectedTier, annual) : 0;
  const displayed = base + addonTotal;
  const saving    = selectedTier ? (selectedTier.priceMonthly - selectedTier.priceAnnual) * 12 : 0;
  const currency  = catalog.tiers[0]?.currency ?? 'RSD';

  function toggle(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  return (
    <section className="px-6 py-20 bg-gray-50 dark:bg-gray-800">
      <div className="max-w-3xl mx-auto space-y-8">

        <div className="text-center">
          <h2 className="text-2xl font-bold text-gray-900 dark:text-white">{t('productPricing.heading')}</h2>
          <p className="text-gray-500 dark:text-gray-400 mt-2">{t('productPricing.subheading')}</p>
          <Link
            to="/features"
            className="inline-block mt-3 text-sm font-medium hover:underline"
            style={{ color: 'var(--brand)' }}
          >
            {t('productPricing.seeAllFeatures')}
          </Link>
        </div>

        {/* Sliders */}
        <div className="bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-700 p-6 space-y-6">
          <p className="text-xs font-semibold text-gray-400 dark:text-gray-500 uppercase tracking-wider">{t('productPricing.basePlanLabel')}</p>

          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-gray-700 dark:text-gray-300">{t('productPricing.teamMembersLabel')}</span>
              <span className="font-medium text-gray-900 dark:text-white">{t('productPricing.upTo', { count: memberBands[memberIdx] })}</span>
            </div>
            <input type="range" min={0} max={memberBands.length - 1} step={1} value={memberIdx}
              onChange={(e) => setMemberIdx(Number(e.target.value))}
              className="w-full" style={{ accentColor: 'var(--brand)' }} />
            <div className="flex justify-between text-xs text-gray-400">
              <span>{memberBands[0]}</span><span>{memberBands[memberBands.length - 1]}</span>
            </div>
          </div>

          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-gray-700 dark:text-gray-300">{t('productPricing.activeProjectsLabel')}</span>
              <span className="font-medium text-gray-900 dark:text-white">
                {projectBands[projectIdx] === null ? t('productPricing.unlimited') : t('productPricing.upTo', { count: projectBands[projectIdx] })}
              </span>
            </div>
            <input type="range" min={0} max={projectBands.length - 1} step={1} value={projectIdx}
              onChange={(e) => setProjectIdx(Number(e.target.value))}
              className="w-full" style={{ accentColor: 'var(--brand)' }} />
            <div className="flex justify-between text-xs text-gray-400"><span>{projectBands[0]}</span><span>{t('productPricing.unlimited')}</span></div>
          </div>

          <div className="flex justify-between items-center pt-2 border-t border-gray-200 dark:border-gray-700">
            <span className="text-sm text-gray-500">{t('productPricing.basePriceLabel')}</span>
            <span className="font-semibold text-gray-900 dark:text-white">{t('productPricing.perMonth', { amount: fmt(base), currency })}</span>
          </div>
        </div>

        {/* Add-on cards */}
        <div className="space-y-3">
          <p className="text-xs font-semibold text-gray-400 dark:text-gray-500 uppercase tracking-wider">{t('productPricing.addonsHeading')}</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {availableAddons.map((a) => {
              const active = selected.has(a.id);
              return (
                <button
                  key={a.id}
                  onClick={() => toggle(a.id)}
                  className={`text-left rounded-xl border p-4 transition-all ${
                    active
                      ? 'border-[var(--brand)] bg-white dark:bg-gray-900 ring-1 ring-[var(--brand)]'
                      : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 hover:border-gray-300 dark:hover:border-gray-600'
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <p className="text-sm font-medium text-gray-900 dark:text-white">{a.name}</p>
                    <span className="text-xs font-medium shrink-0" style={{ color: active ? 'var(--brand)' : undefined }}>
                      {active ? '− ' : '+ '}{fmt(annual ? a.priceAnnual : a.priceMonthly)} {currency}
                    </span>
                  </div>
                  <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">{ADDON_TAGLINES[a.key] ?? ''}</p>
                </button>
              );
            })}
            {comingSoonAddons.map((a) => (
              <div
                key={a.id}
                className="text-left rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 opacity-50"
              >
                <div className="flex items-start justify-between gap-2">
                  <p className="text-sm font-medium text-gray-900 dark:text-white">{a.name}</p>
                  <span className="text-xs text-gray-400 shrink-0">{t('productPricing.comingSoon')}</span>
                </div>
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">{ADDON_TAGLINES[a.key] ?? ''}</p>
              </div>
            ))}
          </div>
        </div>

        {/* Annual toggle + total */}
        <div className="bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-700 p-6 space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-sm text-gray-700 dark:text-gray-300">{t('productPricing.annualBillingLabel')}</span>
            <button
              role="switch" aria-checked={annual} onClick={() => setAnnual((a) => !a)}
              className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none ${annual ? 'bg-[var(--brand)]' : 'bg-gray-300 dark:bg-gray-600'}`}
            >
              <span className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${annual ? 'translate-x-6' : 'translate-x-1'}`} />
            </button>
          </div>

          {annual && (
            <p className="text-sm text-green-600 dark:text-green-400">
              {t('productPricing.saveAnnual', { amount: fmt(saving), currency })}
            </p>
          )}

          <div className="flex justify-between items-baseline pt-2 border-t border-gray-200 dark:border-gray-700">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
              {annual ? t('productPricing.monthlyTotalAnnual') : t('productPricing.monthlyTotal')}
            </span>
            <div className="text-right">
              <span className="text-2xl font-bold text-gray-900 dark:text-white">{fmt(displayed)}</span>
              <span className="text-sm text-gray-500 dark:text-gray-400 ml-1">{t('productPricing.perMonthSuffix', { currency })}</span>
            </div>
          </div>
        </div>

      </div>
    </section>
  );
}
