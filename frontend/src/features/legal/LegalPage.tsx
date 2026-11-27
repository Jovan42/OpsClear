import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import PublicNav from '../../components/PublicNav';
import PublicFooter from './PublicFooter';

export type LegalKind = 'terms' | 'privacy' | 'refunds';

interface LegalSection {
  heading: string;
  paragraphs: string[];
  items?: string[];
}

/** Date the texts were last changed. Update it with every edit to the legal locale files. */
export const LEGAL_LAST_UPDATED = '2026-11-25';

/**
 * JOB-420: one public legal page (/terms, /privacy, /refunds). The text lives in the `legal`
 * locale files. The draft banner stays until the texts are reviewed and the [[PLACEHOLDERS]]
 * are replaced with the company's real details.
 */
export default function LegalPage({ kind }: Readonly<{ kind: LegalKind }>) {
  const { t, i18n } = useTranslation('legal');
  const sections = t(`${kind}.sections`, { returnObjects: true }) as unknown as LegalSection[];
  const date = new Date(`${LEGAL_LAST_UPDATED}T00:00:00Z`).toLocaleDateString(
    i18n.language === 'sr' ? 'sr-Latn' : 'en-GB',
    { dateStyle: 'long', timeZone: 'UTC' },
  );

  return (
    <div className="min-h-screen flex flex-col bg-white dark:bg-gray-900">
      <PublicNav />
      <main className="flex-1 px-4 sm:px-6 py-10 sm:py-14">
        <article className="max-w-3xl mx-auto text-gray-700 dark:text-gray-300 break-words" data-testid={`legal-${kind}`}>
          <div
            role="note"
            data-testid="legal-draft-banner"
            className="mb-8 rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-700 dark:bg-amber-950 dark:text-amber-200"
          >
            {t('common.draftBanner')}
          </div>
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-3">{t(`${kind}.title`)}</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">{t('common.lastUpdated', { date })}</p>
          <p className="text-sm text-gray-500 dark:text-gray-400 mb-8" data-testid="legal-binding-note">
            {t('common.bindingNote')}
          </p>

          {sections.map((section) => (
            <section key={section.heading} className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">{section.heading}</h2>
              {section.paragraphs.map((p) => (
                <p key={p} className="mb-3 leading-relaxed">{p}</p>
              ))}
              {section.items && (
                <ul className="list-disc pl-6 space-y-1 mb-3">
                  {section.items.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              )}
            </section>
          ))}

          <section className="mb-8">
            <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">{t('common.contactHeading')}</h2>
            <p className="leading-relaxed">{t('common.contactBody')}</p>
          </section>

          <Link to="/" className="text-sm font-medium hover:underline" style={{ color: 'var(--brand)' }}>
            {t('common.backToHome')}
          </Link>
        </article>
      </main>
      <PublicFooter />
    </div>
  );
}
