// The chime for new live notifications, generated with the Web Audio API: no audio asset
// and no dependency. Two soft sine notes (A5 then E6), about 0.4s, at low volume.

const PEAK_GAIN = 0.05;

type AudioContextCtor = new () => AudioContext;

let context: AudioContext | null = null;

function tone(ctx: AudioContext, frequency: number, at: number, duration: number) {
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  osc.type = 'sine';
  osc.frequency.value = frequency;
  // Short attack and an exponential fade, so the note has no click at either end.
  gain.gain.setValueAtTime(0.0001, at);
  gain.gain.exponentialRampToValueAtTime(PEAK_GAIN, at + 0.015);
  gain.gain.exponentialRampToValueAtTime(0.0001, at + duration);
  osc.connect(gain).connect(ctx.destination);
  osc.start(at);
  osc.stop(at + duration + 0.02);
}

function chime(ctx: AudioContext) {
  const now = ctx.currentTime;
  tone(ctx, 880, now, 0.25);
  tone(ctx, 1318.5, now + 0.12, 0.3);
}

/**
 * Plays the chime if the browser lets the page play audio, otherwise does nothing.
 * Browsers block audio until the user has interacted with the page, so before that this
 * returns without creating an AudioContext (which would only warn). The context is
 * created on first use and resumed if the browser suspended it. Every failure is
 * swallowed: a missing sound is never worth an error or a console message.
 */
export function playChime(): void {
  try {
    const activation = (navigator as Navigator & { userActivation?: { hasBeenActive: boolean } }).userActivation;
    if (activation && !activation.hasBeenActive) return;

    const w = window as Window & { webkitAudioContext?: AudioContextCtor };
    const Ctor: AudioContextCtor | undefined = window.AudioContext ?? w.webkitAudioContext;
    if (!Ctor) return;
    context ??= new Ctor();
    const ctx = context;

    if (ctx.state === 'running') {
      chime(ctx);
      return;
    }
    ctx.resume().then(
      () => {
        try {
          if (ctx.state === 'running') chime(ctx);
        } catch {
          // ignore
        }
      },
      () => undefined,
    );
  } catch {
    // Audio unavailable or blocked: stay silent.
  }
}
