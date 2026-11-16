// Markdown summary of merged E2E coverage for $GITHUB_STEP_SUMMARY (ADR-0057, JOB-328).
// Usage: node e2e-coverage-summary.mjs <coverage-summary.json> <shards-found> <shards-expected>
// Prints markdown to stdout. Report only: always exits 0, never judges the numbers.
import { existsSync, readFileSync } from 'node:fs'

const [summaryPath, foundArg, expectedArg] = process.argv.slice(2)
const found = Number(foundArg)
const expected = Number(expectedArg)

const lines = ['## E2E Coverage', '']

if (!found) {
  lines.push('No coverage collected: no shard uploaded raw coverage.')
  console.log(lines.join('\n'))
  process.exit(0)
}

if (found < expected) {
  lines.push(
    `**Incomplete:** only ${found} of ${expected} shards uploaded coverage, so the numbers below undercount.`,
    '',
  )
}

if (!existsSync(summaryPath)) {
  lines.push('No report produced: `nyc report` wrote no `coverage-summary.json`.')
  console.log(lines.join('\n'))
  process.exit(0)
}

const summary = JSON.parse(readFileSync(summaryPath, 'utf8'))
const pct = (m) => `${m.pct}% (${m.covered}/${m.total})`

const { total } = summary
lines.push(
  '| Lines | Statements | Functions | Branches |',
  '|---|---|---|---|',
  `| ${pct(total.lines)} | ${pct(total.statements)} | ${pct(total.functions)} | ${pct(total.branches)} |`,
  '',
)

// One row per top-level folder under src/ (files directly in src/ are grouped as "(src root)").
const folders = {}
for (const [file, data] of Object.entries(summary)) {
  if (file === 'total') continue
  const rel = file.split('/src/')[1]
  if (!rel) continue
  const parts = rel.split('/')
  const key = parts.length > 1 ? `src/${parts[0]}/` : '(src root)'
  const f = (folders[key] ??= {
    lines: { covered: 0, total: 0 },
    statements: { covered: 0, total: 0 },
    functions: { covered: 0, total: 0 },
    branches: { covered: 0, total: 0 },
  })
  for (const m of Object.keys(f)) {
    f[m].covered += data[m].covered
    f[m].total += data[m].total
  }
}
const rate = (m) => (m.total ? `${((100 * m.covered) / m.total).toFixed(2)}%` : 'n/a')

lines.push(
  '| Folder | Lines | Statements | Functions | Branches |',
  '|---|---|---|---|---|',
)
for (const key of Object.keys(folders).sort()) {
  const f = folders[key]
  lines.push(`| ${key} | ${rate(f.lines)} | ${rate(f.statements)} | ${rate(f.functions)} | ${rate(f.branches)} |`)
}
lines.push('', 'Report only: reach, not assertions. Full report in the `e2e-coverage-report` artifact.')
console.log(lines.join('\n'))
