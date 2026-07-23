import DemoQueryScope from './DemoQueryScope';
import ApiKeySection from '../features/settings/ApiKeySection';
import type { DemoSlide } from './types';

/**
 * The API keys card's demo slide (ADR-0040 / JOB-145): just ApiKeySection in
 * isolation, backed by its own small mock slice (BASE_API_KEYS in mockData.ts) —
 * scoped to the current user rather than a project, so it needs no router/org
 * context, only its own QueryClient (useApiKeys/useCreateApiKey/useRevokeApiKey).
 */
export const slides: DemoSlide[] = [
  {
    labelKey: 'featuresPage.demo.slideLabels.apiKeys',
    render: () => (
      <DemoQueryScope>
        <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8">
          <ApiKeySection />
        </div>
      </DemoQueryScope>
    ),
  },
];

export default slides;
