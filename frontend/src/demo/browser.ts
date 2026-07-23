import { setupWorker } from 'msw/browser';
import { demoHandlers } from './handlers';

declare global {
  interface Window {
    /** See the cleanup block below — survives HMR reloads because `window` does,
     *  unlike this module's own top-level state. */
    __opsclearDemoWorkerCleanup?: () => void;
  }
}

/**
 * Editing this file or one of its dependencies (handlers.ts, mockData.ts) triggers a
 * Vite HMR reload, re-running this module from scratch. The `import.meta.hot.dispose`
 * hook below is the normal way to clean up before that happens, but this module is
 * only ever reached via *dynamic* import (`import('./browser')`, from 7 different
 * DemoTrigger instances) — and dynamic-import HMR boundaries don't reliably fire
 * dispose in every Vite version/scenario. Left unchecked, every edit during a live
 * debugging session leaves one more overlapping Service Worker registration
 * answering every intercepted request, which is exactly what caused requests to get
 * handled 2-3x over the course of this session.
 *
 * `window` survives across HMR module reloads (only the module's own top-level state
 * gets thrown away), so it's the one place that can reliably detect "a previous
 * instance of this module already registered a worker" regardless of exactly how/
 * whether Vite decided to propagate the update — self-healing rather than relying on
 * dispose timing being correct.
 */
if (typeof window !== 'undefined') {
  window.__opsclearDemoWorkerCleanup?.();
}

export const demoWorker = setupWorker(...demoHandlers);

if (typeof window !== 'undefined') {
  window.__opsclearDemoWorkerCleanup = () => demoWorker.stop();
}

/**
 * Every /features card mounts its own DemoTrigger, and every one of them wants the
 * same shared worker running for as long as it's on screen. Since this module is a
 * singleton (cached after the first dynamic import), a plain refCount here is safely
 * shared across all of them — without it, six cards independently calling
 * start()/stop() as they mount/unmount (especially under React StrictMode's
 * double-invoke-then-cleanup dev behavior) start/stop the same worker out from under
 * each other, which is exactly what produced the "Failed to fetch" / redundant
 * start-stop warnings seen in practice.
 */
let refCount = 0;
let startPromise: ReturnType<typeof demoWorker.start> | null = null;

export function acquireDemoWorker(): ReturnType<typeof demoWorker.start> {
  refCount += 1;
  startPromise ??= demoWorker.start({ onUnhandledRequest: 'error', quiet: true });
  return startPromise;
}

export function releaseDemoWorker(): void {
  refCount = Math.max(0, refCount - 1);
  if (refCount === 0) {
    startPromise = null;
    demoWorker.stop();
  }
}

// Belt-and-suspenders alongside the window-based cleanup above — stops this instance
// the moment HMR actually does dispose it correctly, rather than waiting for the next
// module evaluation to notice. Stripped entirely in production builds (import.meta.hot
// is undefined there).
if (import.meta.hot) {
  import.meta.hot.dispose(() => {
    demoWorker.stop();
  });
}
