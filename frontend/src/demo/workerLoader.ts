/**
 * A single, statically-imported cache around the dynamic `import('./browser')` call.
 *
 * All 7 /features cards' DemoTrigger instances need the exact same worker module
 * instance — but each one calling `import('./browser')` directly turned out to not
 * reliably dedupe (MSW logged one redundant stop()/start() warning per card, matching
 * the card count exactly, meaning each was operating on its own independent
 * refCount/startPromise state rather than sharing it). Static imports are guaranteed
 * to resolve to a single module instance by the ES module spec — no bundler-specific
 * dynamic-import caching behavior to rely on — so caching the *dynamic* import's
 * promise inside this *statically* imported file's module-level state guarantees
 * every caller shares the same cache, regardless of how many places call it.
 */
let modulePromise: Promise<typeof import('./browser')> | null = null;

export function loadDemoWorkerModule(): Promise<typeof import('./browser')> {
  modulePromise ??= import('./browser');
  return modulePromise;
}
