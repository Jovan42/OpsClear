import { defineConfig } from 'cypress';
import baseConfig from './cypress.config';

// ADR-0062: the user guide's screenshot specs (cypress/guide/), run on demand with
// `npm run guide:screenshots`. A separate config so `cypress/e2e/**` stays the only thing
// CI's specPattern matches. Same support file, tasks and plugins as the E2E config.
export default defineConfig({
  ...baseConfig,
  e2e: {
    ...baseConfig.e2e,
    specPattern: 'cypress/guide/**/*.cy.ts',
    viewportWidth: 1280,
    viewportHeight: 800,
    video: false,
    // A step that never finds its element should fail the spec rather than hang it
    defaultCommandTimeout: 10000,
    setupNodeEvents(on, config) {
      // Headless Electron's window is 1280x720 by default, which would crop (and scale) a
      // 1280x800 viewport in the screenshot. Make the window as big as the viewport.
      on('before:browser:launch', (browser, launchOptions) => {
        if (browser.name === 'electron') {
          launchOptions.preferences.width = 1280;
          launchOptions.preferences.height = 800;
        }
        // English date and number formats in the browser's own inputs
        launchOptions.args.push('--lang=en-US');
        return launchOptions;
      });
      return baseConfig.e2e?.setupNodeEvents?.(on, config) ?? config;
    },
  },
});
