import { useTranslation } from 'react-i18next';
import { usePreferences } from '../hooks/usePreferences';

/** Quick language switch for a brand-coloured top bar: toggles English and Serbian. */
export default function LanguageToggleButton() {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const { prefs, update } = usePreferences();

  return (
    <button
      type="button"
      onClick={() => update({ locale: prefs.locale === 'en' ? 'sr' : 'en' })}
      aria-label={t('settings.appearance.language.label')}
      title={t('settings.appearance.language.label')}
      className="px-2.5 py-1 text-xs font-semibold rounded-md border border-white/30 text-white/80 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
    >
      {prefs.locale.toUpperCase()}
    </button>
  );
}
