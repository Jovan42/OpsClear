import type { MDXComponents } from 'mdx/types';
import { GuideAnchor } from './GuideAnchor';
import { Steps, Step } from './Steps';
import { Callout } from './Callout';
import { Screenshot } from './Screenshot';
import { RoleBadge } from './RoleBadge';
import { FeatureLink } from './FeatureLink';

/**
 * ADR-0062: the only components guide pages get (through GuidePage's MDXProvider). Pages
 * don't import anything themselves.
 */
export const guideComponents: MDXComponents = {
  a: GuideAnchor,
  Steps,
  Step,
  Callout,
  Screenshot,
  RoleBadge,
  FeatureLink,
};
