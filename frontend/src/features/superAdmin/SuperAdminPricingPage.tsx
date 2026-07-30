import { useState } from 'react';
import { isAxiosError } from 'axios';
import { useTranslation } from 'react-i18next';
import { Navigate } from 'react-router-dom';
import Skeleton from '../../components/Skeleton';
import {
  useSuperAdminAddons,
  useSuperAdminTiers,
  useUpdateAddonPrice,
  useUpdateTierPrice,
} from './useSuperAdminPricing';

function fmt(n: number) {
  return new Intl.NumberFormat('sr-RS').format(n);
}

interface EditablePriceProps {
  readonly value: number;
  readonly saving: boolean;
  readonly onSave: (next: number) => void;
}

function EditablePrice({ value, saving, onSave }: EditablePriceProps) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(String(value));

  function startEditing() {
    setDraft(String(value));
    setEditing(true);
  }

  function commit() {
    const parsed = Number(draft);
    setEditing(false);
    if (Number.isInteger(parsed) && parsed >= 0 && parsed !== value) {
      onSave(parsed);
    } else {
      setDraft(String(value));
    }
  }

  if (editing) {
    return (
      <input
        type="number"
        min={0}
        step={1}
        autoFocus
        value={draft}
        disabled={saving}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === 'Enter') e.currentTarget.blur();
          if (e.key === 'Escape') { setDraft(String(value)); setEditing(false); }
        }}
        className="w-24 rounded border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700
          px-2 py-1 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-[var(--brand)]"
      />
    );
  }

  return (
    <button
      onClick={startEditing}
      disabled={saving}
      className="rounded px-2 py-1 text-sm text-gray-900 dark:text-gray-100 hover:bg-gray-100
        dark:hover:bg-gray-700 transition-colors cursor-pointer disabled:opacity-50"
    >
      {saving ? '…' : fmt(value)}
    </button>
  );
}

export default function SuperAdminPricingPage() {
  const { t } = useTranslation('superAdmin');
  const { data: tiers, isLoading: tiersLoading, error: tiersError } = useSuperAdminTiers();
  const { data: addons, isLoading: addonsLoading, error: addonsError } = useSuperAdminAddons();
  const { mutate: updateTierPrice, isPending: tierSaving, variables: tierVars } = useUpdateTierPrice();
  const { mutate: updateAddonPrice, isPending: addonSaving, variables: addonVars } = useUpdateAddonPrice();

  const forbidden = [tiersError, addonsError].some(
    (err) => isAxiosError(err) && err.response?.status === 403,
  );

  // Matches the router's catch-all target exactly, so a non-super_user visiting this
  // URL sees the same thing as visiting any nonexistent route — no hint it exists.
  if (forbidden) return <Navigate to="/projects" replace />;

  if (tiersLoading || addonsLoading) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-8 space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-48 rounded-xl" />
        <Skeleton className="h-48 rounded-xl" />
      </div>
    );
  }

  if (!tiers || !addons) return null;

  return (
    <div className="max-w-4xl mx-auto px-4 py-8 space-y-8">
      <div>
        <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">{t('pageHeading')}</h1>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('pageSubtitle')}</p>
      </div>

      <section className="space-y-3">
        <h2 className="text-xs font-semibold text-gray-400 dark:text-gray-500 uppercase tracking-wider">
          {t('tiersHeading')}
        </h2>
        <div className="overflow-x-auto rounded-xl border border-gray-200 dark:border-gray-700">
          <table className="w-full text-left">
            <thead className="bg-gray-50 dark:bg-gray-800/60 text-xs text-gray-500 dark:text-gray-400 uppercase">
              <tr>
                <th className="px-4 py-3 font-medium">{t('columns.tier')}</th>
                <th className="px-4 py-3 font-medium">{t('columns.monthly')}</th>
                <th className="px-4 py-3 font-medium">{t('columns.annual')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-800">
              {[...tiers].sort((a, b) => a.displayOrder - b.displayOrder).map((tier) => (
                <tr key={tier.id}>
                  <td className="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
                    {t('tierLabel', {
                      members: tier.maxMembers,
                      projects: tier.maxProjects ?? t('unlimited'),
                    })}
                  </td>
                  <td className="px-4 py-3">
                    <EditablePrice
                      value={tier.priceMonthly}
                      saving={tierSaving && tierVars?.tierId === tier.id}
                      onSave={(priceMonthly) =>
                        updateTierPrice({ tierId: tier.id, priceMonthly, priceAnnual: tier.priceAnnual })
                      }
                    />
                    <span className="text-xs text-gray-400 dark:text-gray-500 ml-1">{tier.currency}</span>
                  </td>
                  <td className="px-4 py-3">
                    <EditablePrice
                      value={tier.priceAnnual}
                      saving={tierSaving && tierVars?.tierId === tier.id}
                      onSave={(priceAnnual) =>
                        updateTierPrice({ tierId: tier.id, priceMonthly: tier.priceMonthly, priceAnnual })
                      }
                    />
                    <span className="text-xs text-gray-400 dark:text-gray-500 ml-1">{tier.currency}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-xs font-semibold text-gray-400 dark:text-gray-500 uppercase tracking-wider">
          {t('addonsHeading')}
        </h2>
        <div className="overflow-x-auto rounded-xl border border-gray-200 dark:border-gray-700">
          <table className="w-full text-left">
            <thead className="bg-gray-50 dark:bg-gray-800/60 text-xs text-gray-500 dark:text-gray-400 uppercase">
              <tr>
                <th className="px-4 py-3 font-medium">{t('columns.addon')}</th>
                <th className="px-4 py-3 font-medium">{t('columns.monthly')}</th>
                <th className="px-4 py-3 font-medium">{t('columns.annual')}</th>
                <th className="px-4 py-3 font-medium">{t('columns.available')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-800">
              {[...addons].sort((a, b) => a.displayOrder - b.displayOrder).map((addon) => (
                <tr key={addon.id}>
                  <td className="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
                    {addon.name}
                    <span className="text-xs text-gray-400 dark:text-gray-500 ml-2">{addon.key}</span>
                  </td>
                  <td className="px-4 py-3">
                    <EditablePrice
                      value={addon.priceMonthly}
                      saving={addonSaving && addonVars?.addonKey === addon.key}
                      onSave={(priceMonthly) =>
                        updateAddonPrice({ addonKey: addon.key, priceMonthly, priceAnnual: addon.priceAnnual })
                      }
                    />
                  </td>
                  <td className="px-4 py-3">
                    <EditablePrice
                      value={addon.priceAnnual}
                      saving={addonSaving && addonVars?.addonKey === addon.key}
                      onSave={(priceAnnual) =>
                        updateAddonPrice({ addonKey: addon.key, priceMonthly: addon.priceMonthly, priceAnnual })
                      }
                    />
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">
                    {addon.available ? t('yes') : t('comingSoon')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
