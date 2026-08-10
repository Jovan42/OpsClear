import DemoQueryScope from './DemoQueryScope';
import FeedbackPage from '../features/feedback/FeedbackPage';
import type { DemoSlide } from './types';

/**
 * The Feedback & Credits card's demo slide (ADR-0040 / JOB-170): the real Feedback
 * hub, unmodified. A single slide is enough — FeedbackPage already has its own
 * internal "My submissions" / "New submission" tabs, and submitting flips back to
 * My submissions to show the live result, so splitting that flow across two
 * DemoSlide entries would just give each tab a separate fresh QueryClient and break
 * the very connection the demo is meant to show. Seeded with one of each outcome
 * (Pending, Declined, Credited — see BASE_FEEDBACK_SUBMISSIONS in mockData.ts) so a
 * visitor sees the full lifecycle without needing the super-admin review/grant flow
 * (JOB-169), which is out of scope for this customer-facing card. Only needs its own
 * QueryClient (useMyFeedbackSubmissions/useSubmitFeedback) — FeedbackPage reads no
 * route params, auth, or org context, so no Router/AuthContext/OrgContext override.
 */
export const slides: DemoSlide[] = [
  {
    labelKey: 'featuresPage.demo.slideLabels.feedbackCredits',
    render: () => (
      <DemoQueryScope>
        <FeedbackPage />
      </DemoQueryScope>
    ),
  },
];

export default slides;
