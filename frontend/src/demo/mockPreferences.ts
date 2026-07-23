import { defaults, type PreferencesContextValue } from '../hooks/usePreferences';

/**
 * Fixed preferences for every /features demo — deliberately NOT the real
 * PreferencesProvider (which reads the visitor's actual localStorage). A developer
 * or existing user testing the demo would otherwise see the demo reflect their own
 * saved settings (e.g. collapsed milestone groups) instead of a consistent, ideal
 * default view. `update()` is a no-op — nothing in a demo should persist preference
 * changes to the real app's localStorage.
 */
export const mockPreferences: PreferencesContextValue = {
  prefs: {
    ...defaults,
    defaultViewMode: 'GROUPED',
    milestoneAccordionState: 'EXPANDED',
  },
  update: () => {},
};
