# Writing the user guide

Style rules for the pages in `en/` and `sr/` (ADR-0062). Set in JOB-359 (Getting started) and
followed by every other guide page. Read one finished page, such as `en/05-create-job.mdx`,
before writing a new one.

## Voice

- Write for a non-technical owner of a small business. Say "you", in the present tense, with short
  sentences and everyday words.
- No internal terms: no ADR, job or PR numbers, API, token, endpoint, add-on keys or other
  developer words. Say what the person sees and does, not how the app works inside.
- Explain a word the app uses the first time it appears (for example "Slug: a short code for your
  organisation"), then use the app's word.
- Examples use the screenshot sample data: the organisation Acme Plumbing, the project Kitchen
  refit and the jobs in `cypress/guide/support/sample.ts`. The images are in English in both
  languages, so keep these names as they are in the Serbian text.
- Don't mention dates, times or "N minutes ago" from the screenshots. They show when the images were
  generated. Don't describe the sample users' email addresses.

## Labels

- Anything the reader clicks, types into or looks for on screen is **bold**, spelled exactly as in
  that language's app, in that language's pages. Look it up in `src/i18n/locales/<lang>/` (and the
  Keycloak theme messages for sign-up and the emails), and check it in the running app.
- A menu path uses `>`: **Organisation > Invites**.
- Other words are plain. Don't bold for emphasis.

## Page structure

- Frontmatter: `title` (the same as the page's entry in `i18n/locales/<lang>/guide.json`) and a
  one-sentence `description` (under about 160 characters). The `# Heading` repeats the title.
- Open with one or two sentences: what it is, and why you'd do it.
- `<RoleBadge>` right under the intro (or above the section it applies to) only where some roles
  can't act. Leave it out when everyone can. Don't use it for "the assigned person": say that in
  a sentence.
- `<Steps>` for every procedure. Each `<Step>` has a short imperative `title` and a body with the
  detail. Put key words in the body: the search ignores the `title` attribute.
- `<Screenshot>` after the steps (or the step) it shows, in order of its number. The path is
  `/guide/<lang>/<slug>-<n>.webp`. Alt text says what the picture shows, in the page's language,
  without "screenshot of". The caption is one sentence about what to look at.
- `<FeatureLink>` right under the intro when `pages.ts` lists a `/features` card for the page.
- `##` headings in sentence case, a handful per page. Link another guide page with
  `[its title](/guide/<slug>)` the first time you mention it.

## Callouts

- `note`: something good to know. Every add-on feature gets one that says it is an add-on and links
  to `/guide/subscription`.
- `tip`: a shortcut or advice.
- `warning`: something that can't be undone, loses data or stops working.

## Serbian

- Latin script, ekavian, formal "vi" (lowercase "vi", "vaš").
- Use the app's terms: posao (job), projekat, organizacija, etapa, Vlasnik / Administrator /
  Član. The app also says "zadatak" in a few buttons (for example **Blokiraj zadatak**): use
  that word only where it's the label.
- "OpsClear-om", "OpsClear-a": the hyphen goes before the ending.
- Translate the meaning, not the sentence. The page should read as if written in Serbian.

## Checks

- Every `<Screenshot>` file exists in `public/guide/<lang>/`.
- `npm run lint`, `npx tsc -b` and `npx vite build` pass (the build compiles every page).
- The pages' coverage globs in `pages.ts` list the app files that page documents.
