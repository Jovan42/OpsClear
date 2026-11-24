import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { SOCIAL_LINKS, type SocialPlatform } from './socialAccounts';

// Outline brand marks on a 24×24 grid, drawn like the lucide icons used elsewhere.
const ICONS: Record<SocialPlatform, ReactNode> = {
  LinkedIn: (
    <>
      <path d="M16 8a6 6 0 0 1 6 6v7h-4v-7a2 2 0 0 0-2-2 2 2 0 0 0-2 2v7h-4v-7a6 6 0 0 1 6-6z" />
      <rect width="4" height="12" x="2" y="9" />
      <circle cx="4" cy="4" r="2" />
    </>
  ),
  X: (
    <>
      <path d="M4 4l11.733 16h4.267l-11.733-16z" />
      <path d="M4 20l6.768-6.768m2.46-2.46L20 4" />
    </>
  ),
  Facebook: <path d="M18 2h-3a5 5 0 0 0-5 5v3H7v4h3v8h4v-8h3l1-4h-4V7a1 1 0 0 1 1-1h3z" />,
  Instagram: (
    <>
      <rect width="20" height="20" x="2" y="2" rx="5" />
      <path d="M16 11.37A4 4 0 1 1 12.63 8 4 4 0 0 1 16 11.37z" />
      <path d="M17.5 6.5h.01" />
    </>
  ),
};

export default function SocialLinks() {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  return (
    <div>
      <p className="text-sm text-white/70 mb-3">{t('landing.social.follow')}</p>
      <ul className="flex justify-center gap-5">
        {SOCIAL_LINKS.map(({ platform, url }) => (
          <li key={platform}>
            <a
              href={url}
              target="_blank"
              rel="noopener noreferrer"
              aria-label={t('landing.social.label', { platform })}
              title={platform}
              className="block text-white/80 hover:text-white transition-colors"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                width="24"
                height="24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                {ICONS[platform]}
              </svg>
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}
