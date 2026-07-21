import {
  siConfluence,
  siFigma,
  siGithub,
  siGitlab,
  siJira,
  siLinear,
  siNotion,
  siVercel,
} from 'simple-icons';
import type { SimpleIcon } from 'simple-icons';

// simple-icons no longer ships a Slack icon (trademark request) — Slack links
// fall through to the favicon tier below, same as any other unrecognized host.
const KNOWN_SERVICES: Record<string, SimpleIcon> = {
  'github.com': siGithub,
  'gitlab.com': siGitlab,
  'figma.com': siFigma,
  'notion.so': siNotion,
  'notion.site': siNotion,
  'linear.app': siLinear,
  'vercel.com': siVercel,
  'vercel.app': siVercel,
};

export type DetectedLinkIcon =
  | { type: 'known'; label: string; icon: SimpleIcon }
  | { type: 'favicon'; faviconUrl: string };

export function detectLinkIcon(url: string): DetectedLinkIcon | null {
  let hostname: string;
  try {
    hostname = new URL(url).hostname.toLowerCase().replace(/^www\./, '');
  } catch {
    return null;
  }

  if (hostname.includes('confluence')) {
    return { type: 'known', label: siConfluence.title, icon: siConfluence };
  }
  if (hostname.includes('atlassian.net') || hostname.includes('jira')) {
    return { type: 'known', label: siJira.title, icon: siJira };
  }

  const known = KNOWN_SERVICES[hostname];
  if (known) {
    return { type: 'known', label: known.title, icon: known };
  }

  return {
    type: 'favicon',
    faviconUrl: `https://www.google.com/s2/favicons?domain=${hostname}&sz=32`,
  };
}
