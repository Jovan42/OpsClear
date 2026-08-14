import { initializePaddle } from '@paddle/paddle-js';
import type { CheckoutOpenOptions, Paddle, PaddleEventData } from '@paddle/paddle-js';

// Paddle.js only supports one registered eventCallback per Paddle instance (set once
// at initializePaddle time), so a single lazily-created instance is shared across the
// app and events are routed to whichever handler the most recent openCheckout call
// registered — matches Paddle's own overlay UX, which only ever shows one checkout
// at a time anyway.
let paddlePromise: Promise<Paddle | undefined> | null = null;
let currentHandler: ((event: PaddleEventData) => void) | null = null;

function getPaddle(): Promise<Paddle | undefined> {
  if (!paddlePromise) {
    paddlePromise = initializePaddle({
      environment: 'sandbox',
      token: import.meta.env.VITE_PADDLE_CLIENT_TOKEN as string,
      eventCallback: (event) => currentHandler?.(event),
    });
  }
  return paddlePromise;
}

// Paddle's inline checkout renders into an existing DOM element identified by this
// class name — the caller must mount a container with this class before opening.
export const PADDLE_INLINE_FRAME_CLASS = 'paddle-checkout-frame';

export async function openPaddleCheckout(
  options: CheckoutOpenOptions,
  onEvent?: (event: PaddleEventData) => void,
) {
  const paddle = await getPaddle();
  currentHandler = onEvent ?? null;
  paddle?.Checkout.open({
    ...options,
    settings: {
      ...options.settings,
      displayMode: 'inline',
      frameTarget: PADDLE_INLINE_FRAME_CLASS,
      frameInitialHeight: 450,
      frameStyle: 'width: 100%; min-width: 312px; background-color: transparent; border: none;',
    },
  });
}

export async function closePaddleCheckout() {
  const paddle = await getPaddle();
  currentHandler = null;
  paddle?.Checkout.close();
}
