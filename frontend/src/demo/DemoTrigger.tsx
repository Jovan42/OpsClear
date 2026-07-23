import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { useTranslation } from 'react-i18next';
import DemoOverlay from './DemoOverlay';
import ErrorBoundary from '../components/ErrorBoundary';
import { resetDemoData } from './mockData';
import { loadDemoWorkerModule } from './workerLoader';
import type { DemoSlide } from './types';

interface DemoTriggerProps {
  /** Dynamic import of the demo's slide list — still a dynamic import (keeps MSW +
   *  the demo's own composition module out of the *main app* bundle), but now kicked
   *  off eagerly on mount rather than waiting for a click, since the first slide is
   *  shown live (shrunk) as the card's idle-state preview, not a static screenshot. */
  loadSlides: () => Promise<{ default: DemoSlide[] }>;
  /** Shown in place of the live preview until the demo bundle finishes loading. */
  fallback: ReactNode;
  /** Slide 0's approximate natural (unscaled) content size — every real page/component
   *  differs wildly (a full job list table vs. a small blocked-job banner), so this is
   *  tuned per card rather than shared as one global constant. */
  previewNaturalWidth?: number;
  previewNaturalHeight?: number;
  /** How much to shrink the natural size by. The box (h-56) crops via overflow:
   *  hidden rather than showing everything, so a higher scale trades less shrinking
   *  for more of the content being cut off below the fold. */
  previewScale?: number;
  /** Tailwind height class for the preview box itself. Defaults to the original
   *  static screenshot box (`h-56`) — override for cards whose content is naturally
   *  much shorter (e.g. a single banner), where forcing the tall default box just
   *  guarantees empty space no matter how the content is scaled. */
  previewBoxHeight?: string;
}

interface StandaloneRoot {
  root: Root;
  container: HTMLDivElement;
}

const DEFAULT_PREVIEW_BOX_HEIGHT = 'h-56';
// Defaults tuned to ApprovalQueuePage's typical content size.
const DEFAULT_NATURAL_WIDTH = 900;
const DEFAULT_NATURAL_HEIGHT = 640;
const DEFAULT_SCALE = 0.34;

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
export default function DemoTrigger({
  loadSlides,
  fallback,
  previewNaturalWidth = DEFAULT_NATURAL_WIDTH,
  previewNaturalHeight = DEFAULT_NATURAL_HEIGHT,
  previewScale = DEFAULT_SCALE,
  previewBoxHeight = DEFAULT_PREVIEW_BOX_HEIGHT,
}: Readonly<DemoTriggerProps>) {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const [slides, setSlides] = useState<DemoSlide[] | null>(null);
  const previewContainerRef = useRef<HTMLDivElement>(null);
  const previewRootRef = useRef<Root | null>(null);
  const overlayRef = useRef<StandaloneRoot | null>(null);
  const slideIndexRef = useRef(0);
  // Bumped every time the preview needs a hard remount (fresh QueryClient + fresh
  // MemoryRouter history), not just a prop update — see renderPreview() below.
  const previewGenerationRef = useRef(0);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const [{ acquireDemoWorker }, mod] = await Promise.all([loadDemoWorkerModule(), loadSlides()]);
        if (cancelled) return;
        resetDemoData();
        await acquireDemoWorker();
        if (cancelled) return;
        setSlides(mod.default);
      } catch (error) {
        // Service Worker registration can fail for reasons outside this component's
        // control — most notably, it requires a properly-trusted HTTPS certificate;
        // a self-signed one (e.g. serving straight off a bare IP with no real domain)
        // fails registration outright. Fall back to the static `fallback` prop (slides
        // stays null) rather than leaving the card in permanent limbo with an
        // unhandled rejection and no visible content.
        if (!cancelled) console.warn('[demo] failed to start interactive preview:', error);
      }
    })();

    return () => {
      cancelled = true;
      // Ref-counted (see browser.ts) — the worker only actually stops once every
      // mounted card has released it, not the moment any single card unmounts.
      void loadDemoWorkerModule().then(({ releaseDemoWorker }) => releaseDemoWorker());
    };
  }, [loadSlides]);

  // Renders the shrunk idle-state preview (slide 0) into its own standalone root,
  // anchored to a plain ref'd <div> in this component's own layout rather than
  // appended to document.body like the overlay — same "separate root" reasoning,
  // just positioned in place instead of full-screen. Keyed on a generation counter so
  // calling this again after resetDemoData() (see handleClose) forces a full remount —
  // a fresh QueryClient and MemoryRouter, refetching against the just-reset baseline —
  // rather than an in-place prop update that would keep showing stale cached data.
  function renderPreview() {
    const root = previewRootRef.current;
    if (!root || !slides) return;
    previewGenerationRef.current += 1;

    root.render(
      <div
        key={previewGenerationRef.current}
        style={{ width: previewNaturalWidth, height: previewNaturalHeight, transform: `scale(${previewScale})`, transformOrigin: 'top left' }}
      >
        <ErrorBoundary>{slides[0].render()}</ErrorBoundary>
      </div>,
    );
  }

  useEffect(() => {
    if (!slides || !previewContainerRef.current) return;

    const root = createRoot(previewContainerRef.current);
    previewRootRef.current = root;
    renderPreview();

    return () => {
      root.unmount();
      previewRootRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [slides, previewNaturalWidth, previewNaturalHeight, previewScale]);

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
    // Per ADR-0040, every open should start from the same baseline — reset the shared
    // mock data now, then force the shrunk background preview to remount against it
    // (a plain re-render would just keep showing whatever was cached before close).
    resetDemoData();
    renderPreview();
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
      <div className={`${previewBoxHeight} bg-gray-100 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 overflow-hidden flex items-center justify-center`}>
        {slides ? (
          <div
            className="pointer-events-none shrink-0 rounded-lg shadow-lg overflow-hidden bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700"
            style={{ width: previewNaturalWidth * previewScale, height: previewNaturalHeight * previewScale }}
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
