import { describe, expect, it } from 'vitest';
import { SUPPORT_EMAIL, supportMailto } from './support';

describe('supportMailto', () => {
  it('builds a mailto href for the support address', () => {
    expect(SUPPORT_EMAIL).toBe('support@opsclear.app');
    expect(supportMailto()).toBe('mailto:support@opsclear.app');
  });
});
