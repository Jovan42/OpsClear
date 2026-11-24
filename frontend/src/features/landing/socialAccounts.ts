/** OpsClear's social media accounts (JOB-412). A new account is one entry here. */
export type SocialPlatform = 'LinkedIn' | 'X' | 'Facebook' | 'Instagram';

export interface SocialLink {
  platform: SocialPlatform;
  url: string;
}

export const SOCIAL_LINKS: SocialLink[] = [
  { platform: 'LinkedIn', url: 'https://www.linkedin.com/company/145500105/' },
  { platform: 'X', url: 'https://x.com/OpsClear' },
  { platform: 'Facebook', url: 'https://www.facebook.com/profile.php?id=61596683198605' },
  { platform: 'Instagram', url: 'https://www.instagram.com/opsclear/' },
];
