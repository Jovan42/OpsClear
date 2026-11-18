/// <reference types="vitest/config" />
import { defineConfig, type PluginOption } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import istanbul from 'vite-plugin-istanbul'
import mdx from '@mdx-js/rollup'
import remarkGfm from 'remark-gfm'
import rehypeSlug from 'rehype-slug'
import rehypeAutolinkHeadings from 'rehype-autolink-headings'

// ADR-0057: Istanbul instrumentation for E2E coverage, opt-in via `npm run dev:coverage`.
// With `requireEnv: true`, vite-plugin-istanbul 9.x instruments only when `VITE_COVERAGE=true`
// (Vite's env prefix + `COVERAGE`), and `forceBuildInstrument: false` keeps it out of
// `vite build`, so production is never instrumented. The plugin is also only registered when
// `VITE_COVERAGE=true`: its `config` hook otherwise still prints a sourcemap warning on every
// plain `npm run dev`, even though it then leaves the code uninstrumented.
// `src/main.tsx` is excluded here as well as in `.nycrc.json`: @cypress/code-coverage's
// built-in report doesn't apply nyc's `exclude` to files that were instrumented, so a file
// excluded only in `.nycrc.json` would still show up in the local report.
const coverage = process.env.VITE_COVERAGE === 'true'

// ADR-0062: the user guide's MDX pages (src/guide/). `enforce: 'pre'` so MDX compiles to JSX
// before the React plugin sees it; `providerImportSource` lets GuidePage pass the guide
// components through an MDXProvider. Heading ids (rehype-slug) feed "On this page" and the
// search anchors; autolink wraps each heading in a link to itself.
// @mdx-js/rollup strips the query before matching, so it would also compile the
// `?raw` imports the guide search reads as plain text: skip those.
function guideMdx(): PluginOption {
  const plugin = mdx({
    providerImportSource: '@mdx-js/react',
    remarkPlugins: [remarkGfm],
    rehypePlugins: [rehypeSlug, [rehypeAutolinkHeadings, { behavior: 'wrap' }]],
  })
  const transform = plugin.transform as (this: unknown, code: string, id: string) => unknown
  return {
    ...plugin,
    enforce: 'pre',
    transform(code: string, id: string) {
      if (/[?&]raw\b/.test(id)) return null
      return transform.call(this, code, id)
    },
  } as PluginOption
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    guideMdx(),
    react({ include: /\.(mdx|js|jsx|ts|tsx)$/ }),
    tailwindcss(),
    ...(coverage
      ? [
          istanbul({
            include: 'src/*',
            exclude: ['node_modules', 'cypress', 'src/main.tsx'],
            extension: ['.ts', '.tsx'],
            requireEnv: true,
            forceBuildInstrument: false,
          }),
        ]
      : []),
  ],
  server: {
    port: 5173,
  },
  test: {
    // Unit tests only; Cypress specs live in cypress/ and run with `npm run e2e:run`.
    include: ['src/**/*.test.ts'],
    environment: 'node',
  },
})
