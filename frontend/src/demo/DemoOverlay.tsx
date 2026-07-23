import { useEffect, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronLeft, ChevronRight } from 'lucide-react';

interface DemoOverlayProps {
  onClose: () => void;
  children: ReactNode;
  /** Current slide's label, shown next to the prev/next arrows. Omit slide-nav props
   *  entirely for a single-slide demo — no arrows are rendered. */
  slideLabel?: string;
  slideIndex?: number;
  slideCount?: number;
  onPrevSlide?: () => void;
  onNextSlide?: () => void;
}

/**
 * The reusable full-screen overlay chrome for every /features interactive demo
 * (ADR-0040) — backdrop, persistent "Demo" badge, close button, optional prev/next
 * slide navigation, and a scale/opacity mount transition (the CSS-transition fallback
 * the ADR allows in place of the View Transitions API). Built once against the
 * Approvals card; every later card (JOB-144 onward) just supplies its own `children`.
 *
 * Rendered into its own standalone React root (see DemoTrigger.tsx), not as a normal
 * child of the app tree — the wrapped demo component needs its own MemoryRouter, and
 * React Router refuses to mount a <Router> anywhere beneath another one, even through
 * a portal (portals keep the same React context, they only change DOM placement).
 * Mounting/unmounting this component *is* opening/closing the overlay; there is no
 * `open` prop.
 */
export default function DemoOverlay({
  onClose,
  children,
  slideLabel,
  slideIndex,
  slideCount,
  onPrevSlide,
  onNextSlide,
}: Readonly<DemoOverlayProps>) {
  const { t } = useTranslation('approvalsDashboardSettingsLanding');
  const [mounted, setMounted] = useState(false);
  const hasSlideNav = slideCount != null && slideCount > 1;

  useEffect(() => {
    const raf = requestAnimationFrame(() => setMounted(true));
    return () => cancelAnimationFrame(raf);
  }, []);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
      if (e.key === 'ArrowLeft') onPrevSlide?.();
      if (e.key === 'ArrowRight') onNextSlide?.();
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose, onPrevSlide, onNextSlide]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-8" onClick={onClose}>
      <div className="absolute inset-0 bg-black/60" />
      <div
        className={`relative z-10 w-full max-w-5xl max-h-[88vh] bg-white dark:bg-gray-900 rounded-2xl shadow-2xl overflow-hidden flex flex-col
          transition-all duration-300 ease-out ${mounted ? 'opacity-100' : 'opacity-0 scale-95'}`}
        // No `scale-100`/transform once mounted — even `transform: scale(1)` creates a
        // new containing block for `position: fixed` descendants, which broke any real
        // <Modal> opened from inside a slide (e.g. AddRelationshipModal): its `fixed
        // inset-0` was positioning itself against this panel's box instead of the true
        // viewport. Only the brief pre-mount `scale-95` frame keeps a transform.
        onClick={(e) => e.stopPropagation()}
      >
        <div className="shrink-0 flex items-center justify-between gap-3 px-4 py-2 bg-amber-100 dark:bg-amber-900/40 border-b border-amber-200 dark:border-amber-800">
          <p className="text-xs sm:text-sm font-medium text-amber-800 dark:text-amber-300">
            {t('featuresPage.demo.badge')}
          </p>
          <button
            onClick={onClose}
            className="shrink-0 text-amber-700 dark:text-amber-300 hover:text-amber-900 dark:hover:text-amber-100 transition-colors text-xl leading-none cursor-pointer"
            aria-label={t('featuresPage.demo.close')}
          >
            ×
          </button>
        </div>

        {hasSlideNav && (
          <div className="shrink-0 flex items-center justify-center gap-3 px-4 py-2 bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
            <button
              onClick={onPrevSlide}
              className="p-1 rounded text-gray-500 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-100 hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors cursor-pointer"
              aria-label={t('featuresPage.demo.prevSlide')}
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <p className="text-xs sm:text-sm font-medium text-gray-700 dark:text-gray-300 min-w-0 truncate">
              {slideLabel}
              {slideIndex != null && <span className="text-gray-400 dark:text-gray-500"> · {slideIndex + 1}/{slideCount}</span>}
            </p>
            <button
              onClick={onNextSlide}
              className="p-1 rounded text-gray-500 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-100 hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors cursor-pointer"
              aria-label={t('featuresPage.demo.nextSlide')}
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        )}

        <div className="flex-1 overflow-auto bg-gray-50 dark:bg-gray-950">
          {children}
        </div>
      </div>
    </div>
  );
}
