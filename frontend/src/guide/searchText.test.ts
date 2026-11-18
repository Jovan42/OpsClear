import { describe, expect, it } from 'vitest';
import { inlineToText, mdxToSections } from './searchText';

describe('inlineToText', () => {
  it('keeps the visible text of Markdown and JSX', () => {
    expect(inlineToText('Click **Save** in [the form](/x) and `Enter` <RoleBadge role="Owner" />')).toBe(
      'Click Save in the form and Enter',
    );
  });
});

describe('mdxToSections', () => {
  const source = `export const frontmatter = {
  title: 'Getting started',
  description: 'Intro',
};

Intro text.

# Getting started

Welcome to **OpsClear**.

## The four statuses

- New
- In Progress

<Screenshot
  src="/guide/en/index-1.webp"
  alt="The job list"
/>

## The four statuses

\`\`\`
## not a heading
\`\`\`
`;

  it('splits by heading with rehype-slug anchors and drops ESM and tags, and ignores headings in code', () => {
    expect(mdxToSections(source)).toEqual([
      { heading: null, anchor: null, text: 'Intro text.' },
      { heading: 'Getting started', anchor: 'getting-started', text: 'Welcome to OpsClear.' },
      { heading: 'The four statuses', anchor: 'the-four-statuses', text: 'New In Progress' },
      { heading: 'The four statuses', anchor: 'the-four-statuses-1', text: '## not a heading' },
    ]);
  });
});
