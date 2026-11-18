import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { pickActiveHeading, type GuideHeading } from './headings';

/** Sticky "On this page" list that highlights the section being read. */
export default function OnThisPage({ headings }: Readonly<{ headings: GuideHeading[] }>) {
  const { t } = useTranslation('guide');
  const [activeId, setActiveId] = useState<string | null>(null);

  useEffect(() => {
    const elements = headings
      .map((h) => document.getElementById(h.id))
      .filter((el): el is HTMLElement => el !== null);
    if (elements.length === 0) return;

    const ids = elements.map((el) => el.id);
    const inView = new Set<string>();

    function update() {
      const root = document.documentElement;
      const scrollable = root.scrollHeight > window.innerHeight + 2;
      const atBottom = scrollable && window.innerHeight + window.scrollY >= root.scrollHeight - 2;
      const passed = new Set(elements.filter((el) => el.getBoundingClientRect().top < 0).map((el) => el.id));
      const next = pickActiveHeading({ ids, inView, passed, atBottom });
      if (next) setActiveId(next);
    }

    // A heading counts as "in view" while it's in the top 30% of the viewport.
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) inView.add(entry.target.id);
          else inView.delete(entry.target.id);
        }
        update();
      },
      { rootMargin: '0px 0px -70% 0px' },
    );
    elements.forEach((el) => observer.observe(el));
    // Only for reaching the bottom of the page, which the observer doesn't report.
    window.addEventListener('scroll', update, { passive: true });
    return () => {
      observer.disconnect();
      window.removeEventListener('scroll', update);
    };
  }, [headings]);

  if (headings.length === 0) return null;

  return (
    <nav aria-label={t('onThisPage')} data-testid="guide-on-this-page">
      <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{t('onThisPage')}</p>
      <ul className="space-y-1.5 text-sm">
        {headings.map((h) => {
          const active = h.id === activeId;
          return (
            <li key={h.id} className={h.level === 3 ? 'pl-3' : ''}>
              <a
                href={`#${h.id}`}
                aria-current={active ? 'location' : undefined}
                className={`block transition-colors ${
                  active
                    ? 'font-medium text-brand dark:text-green-400'
                    : 'text-gray-500 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white'
                }`}
              >
                {h.text}
              </a>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
