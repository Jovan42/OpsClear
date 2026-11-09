/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
  },
  test: {
    // Unit tests only; Cypress specs live in cypress/ and run with `npm run e2e:run`.
    include: ['src/**/*.test.ts'],
    environment: 'node',
  },
})
