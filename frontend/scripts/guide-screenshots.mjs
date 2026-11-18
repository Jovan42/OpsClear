// ADR-0062 (JOB-357): `npm run guide:screenshots`. Runs the guide's Cypress specs, then
// converts what they captured to WebP in frontend/public/guide/<lang>/<slug>-<n>.webp.
//
//   npm run guide:screenshots                                  all sections, en and sr
//   GUIDE_LANG=sr npm run guide:screenshots                    one language
//   npm run guide:screenshots -- --section getting-started     one section
//   npm run guide:screenshots -- --convert-only                skip Cypress, re-convert
//
// Needs the local stack (docker-compose up, backend, `npm run dev`). Fails if any image is
// over 150 KB, or if a spec fails.

import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, rmSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SECTIONS = ['getting-started', 'daily-work', 'planning', 'admin-settings', 'account'];
const LANGS = ['en', 'sr'];
const MAX_BYTES = 150 * 1024;
// Screenshots come out at the display's pixel density; the guide shows them at most 1280 wide.
const MAX_WIDTH = 1280;

const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const option = (name) => {
  const i = args.indexOf(name);
  return i === -1 ? undefined : args[i + 1];
};

const section = option('--section');
if (section && !SECTIONS.includes(section)) {
  console.error(`Unknown section "${section}". Sections: ${SECTIONS.join(', ')}`);
  process.exit(1);
}
const lang = process.env.GUIDE_LANG;
if (lang && !LANGS.includes(lang)) {
  console.error(`GUIDE_LANG must be one of ${LANGS.join(', ')}, got "${lang}"`);
  process.exit(1);
}
const langs = lang ? [lang] : LANGS;
const sections = (section ? [section] : SECTIONS).filter((s) => existsSync(join(root, 'cypress/guide', `${s}.cy.ts`)));
if (sections.length === 0) {
  console.error('No guide spec found for that section yet.');
  process.exit(1);
}

function* pngs(dir) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) yield* pngs(path);
    else if (entry.name.endsWith('.png')) yield path;
  }
}

async function toWebp(png, target) {
  // Lower the quality step by step until the image fits
  for (const quality of [82, 74, 66, 58, 50, 42]) {
    await sharp(png).resize({ width: MAX_WIDTH, withoutEnlargement: true }).webp({ quality }).toFile(target);
    if (statSync(target).size <= MAX_BYTES) return;
  }
}

let failed = false;

for (const l of langs) {
  const shots = join(root, 'cypress/screenshots-guide', l);
  for (const s of sections) {
    const specShots = join(shots, `${s}.cy.ts`);
    if (!flag('--convert-only')) {
      rmSync(specShots, { recursive: true, force: true });
      console.log(`\n== ${s} (${l}) ==`);
      const run = spawnSync(
        'npx',
        [
          'cypress', 'run',
          '--config-file', 'cypress.guide.config.ts',
          '--spec', `cypress/guide/${s}.cy.ts`,
          '--env', `GUIDE_LANG=${l}`,
          '--config', `screenshotsFolder=cypress/screenshots-guide/${l}`,
        ],
        // UTC so times in the images don't depend on the machine's time zone
        { cwd: root, stdio: 'inherit', env: { ...process.env, TZ: 'UTC' } },
      );
      if (run.status !== 0) {
        console.error(`The ${s} spec failed for ${l}; its images were not copied.`);
        failed = true;
        continue;
      }
    }
    if (!existsSync(specShots)) {
      console.error(`No screenshots for ${s} (${l}) in ${specShots}`);
      failed = true;
      continue;
    }
    const outDir = join(root, 'public/guide', l);
    mkdirSync(outDir, { recursive: true });
    for (const png of pngs(specShots)) {
      const name = png.split('/').pop().replace(/\.png$/, '');
      if (!/^[a-z0-9-]+-\d+$/.test(name)) {
        console.error(`Skipped ${png}: not named <slug>-<n> (a failed step's screenshot?)`);
        failed = true;
        continue;
      }
      const target = join(outDir, `${name}.webp`);
      await toWebp(png, target);
      const size = statSync(target).size;
      const ok = size <= MAX_BYTES;
      if (!ok) failed = true;
      console.log(`${ok ? 'ok  ' : 'BIG '} ${l}/${name}.webp  ${(size / 1024).toFixed(0)} KB`);
    }
  }
}

process.exit(failed ? 1 : 0);
