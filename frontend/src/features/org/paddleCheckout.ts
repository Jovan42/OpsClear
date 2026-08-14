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

export async function openPaddleCheckout(
  options: CheckoutOpenOptions,
  onEvent?: (event: PaddleEventData) => void,
) {
  const paddle = await getPaddle();
  currentHandler = onEvent ?? null;
  paddle?.Checkout.open(options);
}
