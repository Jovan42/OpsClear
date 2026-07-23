import { setupWorker } from 'msw/browser';
import { demoHandlers } from './handlers';

export const demoWorker = setupWorker(...demoHandlers);
