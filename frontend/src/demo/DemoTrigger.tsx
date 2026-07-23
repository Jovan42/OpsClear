import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { useTranslation } from 'react-i18next';
import DemoOverlay from './DemoOverlay';
import ErrorBoundary from '../components/ErrorBoundary';
import { resetDemoData } from './mockData';
import type { DemoSlide } from './ApprovalsDemo';

interface DemoTriggerProps {
  /** Dynamic import of the demo's slide list — still a dynamic import (keeps MSW +
   *  the demo's own composition module out of the *main app* bundle), but now kicked
   *  off eagerly on mount rather than waiting for a click, since the first slide is
   *  shown live (shrunk) as the card's idle-state preview, not a static screenshot. */
  loadSlides: () => Promise<{ default: DemoSlide[] }>;
  /** Shown in place of the live preview until the demo bundle finishes loading. */
  fallback: ReactNode;
}

interface StandaloneRoot {
  root: Root;
  container: HTMLDivElement;
}

// Matches the original static screenshot box (`h-56`) — the box itself doesn't grow,
// only how much the live content inside it is shrunk down (see SCALE below).
const PREVIEW_BOX_HEIGHT = 'h-56';
// A real page's natural size, scaled down to fit inside that box — tuned to
// ApprovalQueuePage's typical content height, not a precise measurement. The box
// crops (overflow: hidden) rather than showing everything, so a higher SCALE trades
// less shrinking for more of the page being cut off below the fold.
const NATURAL_WIDTH = 900;
const NATURAL_HEIGHT = 640;
const SCALE = 0.34;

/**
 * Generic click-to-expand wrapper for every /features interactive demo (ADR-0040).
 * Shows the real first slide live, scaled down via `transform: scale()`, as the
 * card's idle-state preview (loaded eagerly on mount — MSW + the mock dataset are
 * still their own dynamically-imported chunk, just fetched immediately rather than
 * deferred to a click). Clicking expands into a full-screen overlay with prev/next
 * navigation across every slide the demo defines.
 *
 * Both the shrunk preview *and* the full overlay are mounted into their own standalone
 * React roots (via createRoot, not returned as JSX from this component) — the wrapped
 * demo component needs its own MemoryRouter, and React Router refuses to mount a
 * <Router> anywhere beneath the app's real one, even through a portal (a portal only
 * changes DOM placement, not React context ancestry) or as an ordinary nested render
 * inside the app's own component tree.
 *
 * The trigger itself is a <div role="button">, not a real <button> — the live preview
 * it wraps renders the real page's own interactive elements (links, buttons), and a
 * real <button> can never contain another one (invalid HTML, breaks hydration).
 */
export default function DemoTrigger({ loadSlides, fallback }: Readonly<DemoTriggerProps>) {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const [slides, setSlides] = useState<DemoSlide[] | null>(null);
  const previewContainerRef = useRef<HTMLDivElement>(null);
  const previewRootRef = useRef<Root | null>(null);
  const overlayRef = useRef<StandaloneRoot | null>(null);
  const slideIndexRef = useRef(0);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const [{ demoWorker }, mod] = await Promise.all([import('./browser'), loadSlides()]);
      if (cancelled) return;
      resetDemoData();
      await demoWorker.start({ onUnhandledRequest: 'error', quiet: true });
      if (cancelled) return;
      setSlides(mod.default);
    })();

    return () => {
      cancelled = true;
      // The worker stays alive for as long as this card is mounted (so the shrunk
      // preview keeps showing live data even after the overlay is closed) — only
      // stopped when the card itself goes away (e.g. navigating off /features).
      void import('./browser').then(({ demoWorker }) => demoWorker.stop());
    };
  }, [loadSlides]);

  // Mount the shrunk idle-state preview (slide 0) into its own standalone root, anchored
  // to a plain ref'd <div> in this component's own layout rather than appended to
  // document.body like the overlay — same "separate root" reasoning, just positioned
  // in place instead of full-screen.
  useEffect(() => {
    if (!slides || !previewContainerRef.current) return;

    const root = createRoot(previewContainerRef.current);
    previewRootRef.current = root;
    root.render(
      <div
        style={{ width: NATURAL_WIDTH, height: NATURAL_HEIGHT, transform: `scale(${SCALE})`, transformOrigin: 'top left' }}
      >
        <ErrorBoundary>{slides[0].render()}</ErrorBoundary>
      </div>,
    );

    return () => {
      root.unmount();
      previewRootRef.current = null;
    };
  }, [slides]);

  function renderOverlay() {
    const current = overlayRef.current;
    if (!current || !slides) return;
    const index = slideIndexRef.current;

    current.root.render(
      <DemoOverlay
        onClose={() => void handleClose()}
        slideLabel={t(slides[index].labelKey)}
        slideIndex={slides.length > 1 ? index : undefined}
        slideCount={slides.length > 1 ? slides.length : undefined}
        onPrevSlide={slides.length > 1 ? () => goToSlide(index === 0 ? slides.length - 1 : index - 1) : undefined}
        onNextSlide={slides.length > 1 ? () => goToSlide(index === slides.length - 1 ? 0 : index + 1) : undefined}
      >
        {/* Keyed on slide index so switching slides fully remounts the wrapped page —
            a fresh QueryClient and MemoryRouter history per slide, not a prop update.
            ErrorBoundary is keyed too, so switching away from a broken slide resets it
            rather than getting stuck showing the fallback forever. */}
        <ErrorBoundary key={index}>
          <div>{slides[index].render()}</div>
        </ErrorBoundary>
      </DemoOverlay>,
    );
  }

  function goToSlide(index: number) {
    slideIndexRef.current = index;
    renderOverlay();
  }

  function handleClose() {
    const current = overlayRef.current;
    overlayRef.current = null;
    if (current) {
      current.root.unmount();
      current.container.remove();
    }
  }

  function handleOpen() {
    if (!slides || overlayRef.current) return;
    slideIndexRef.current = 0;

    const container = document.createElement('div');
    document.body.appendChild(container);
    const root = createRoot(container);
    overlayRef.current = { root, container };

    renderOverlay();
  }

  function handleKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      handleOpen();
    }
  }

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={handleOpen}
      onKeyDown={handleKeyDown}
      className="relative block w-full text-left cursor-pointer group"
      aria-label={t('featuresPage.demo.tryLive')}
    >
      <div className={`${PREVIEW_BOX_HEIGHT} bg-gray-100 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 overflow-hidden flex items-center justify-center`}>
        {slides ? (
          <div
            className="pointer-events-none rounded-lg shadow-lg overflow-hidden bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700"
            style={{ width: NATURAL_WIDTH * SCALE, height: NATURAL_HEIGHT * SCALE }}
          >
            <div ref={previewContainerRef} />
          </div>
        ) : (
          fallback
        )}
      </div>
      <div className="absolute inset-0 flex items-center justify-center bg-black/0 group-hover:bg-black/10 transition-colors">
        <span className="opacity-0 group-hover:opacity-100 transition-opacity px-3 py-1.5 rounded-lg bg-white/90 dark:bg-gray-900/90 text-sm font-medium text-gray-900 dark:text-gray-100 shadow">
          {t('featuresPage.demo.tryLive')}
        </span>
      </div>
    </div>
  );
}
