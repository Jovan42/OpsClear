import type { ReactNode } from 'react';

export interface DemoSlide {
  /** A key into the approvalsDashboardSettingsLanding:featuresPage.demo.slideLabels
   *  namespace — translated at render time by DemoOverlay/DemoTrigger, not baked in
   *  where slide arrays are built (module scope, outside any component). */
  labelKey: string;
  render: () => ReactNode;
}
