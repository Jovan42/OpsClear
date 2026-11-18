// The guide specs run once per language and click the app's real labels, so they need the
// label in the current language. `tr('Create project')` finds that English text in the app's
// own locale files and returns the same key's text in GUIDE_LANG, so a rename in the app
// moves the spec and the screenshot together. Text with `{{values}}` isn't supported:
// use a regular expression or the visible element instead.

import { guideLang } from './sample';

import en_approvalsDashboardSettingsLanding from '../../../src/i18n/locales/en/approvalsDashboardSettingsLanding.json';
import sr_approvalsDashboardSettingsLanding from '../../../src/i18n/locales/sr/approvalsDashboardSettingsLanding.json';
import en_common from '../../../src/i18n/locales/en/common.json';
import sr_common from '../../../src/i18n/locales/sr/common.json';
import en_errors from '../../../src/i18n/locales/en/errors.json';
import sr_errors from '../../../src/i18n/locales/sr/errors.json';
import en_feedback from '../../../src/i18n/locales/en/feedback.json';
import sr_feedback from '../../../src/i18n/locales/sr/feedback.json';
import en_guide from '../../../src/i18n/locales/en/guide.json';
import sr_guide from '../../../src/i18n/locales/sr/guide.json';
import en_jobTypes from '../../../src/i18n/locales/en/jobTypes.json';
import sr_jobTypes from '../../../src/i18n/locales/sr/jobTypes.json';
import en_jobsComponents from '../../../src/i18n/locales/en/jobsComponents.json';
import sr_jobsComponents from '../../../src/i18n/locales/sr/jobsComponents.json';
import en_jobsPages from '../../../src/i18n/locales/en/jobsPages.json';
import sr_jobsPages from '../../../src/i18n/locales/sr/jobsPages.json';
import en_milestonesTemplatesSchedules from '../../../src/i18n/locales/en/milestonesTemplatesSchedules.json';
import sr_milestonesTemplatesSchedules from '../../../src/i18n/locales/sr/milestonesTemplatesSchedules.json';
import en_notifications from '../../../src/i18n/locales/en/notifications.json';
import sr_notifications from '../../../src/i18n/locales/sr/notifications.json';
import en_org from '../../../src/i18n/locales/en/org.json';
import sr_org from '../../../src/i18n/locales/sr/org.json';
import en_projects from '../../../src/i18n/locales/en/projects.json';
import sr_projects from '../../../src/i18n/locales/sr/projects.json';
import en_shared1 from '../../../src/i18n/locales/en/shared1.json';
import sr_shared1 from '../../../src/i18n/locales/sr/shared1.json';
import en_shared2 from '../../../src/i18n/locales/en/shared2.json';
import sr_shared2 from '../../../src/i18n/locales/sr/shared2.json';
import en_superAdmin from '../../../src/i18n/locales/en/superAdmin.json';
import sr_superAdmin from '../../../src/i18n/locales/sr/superAdmin.json';

type Tree = { [key: string]: string | Tree };

const EN: Tree[] = [en_approvalsDashboardSettingsLanding, en_common, en_errors, en_feedback, en_guide, en_jobTypes, en_jobsComponents, en_jobsPages, en_milestonesTemplatesSchedules, en_notifications, en_org, en_projects, en_shared1, en_shared2, en_superAdmin] as Tree[];
const SR: Tree[] = [sr_approvalsDashboardSettingsLanding, sr_common, sr_errors, sr_feedback, sr_guide, sr_jobTypes, sr_jobsComponents, sr_jobsPages, sr_milestonesTemplatesSchedules, sr_notifications, sr_org, sr_projects, sr_shared1, sr_shared2, sr_superAdmin] as Tree[];

function flatten(tree: Tree, prefix = ''): Array<[string, string]> {
  return Object.entries(tree).flatMap(([key, value]) =>
    typeof value === 'string' ? [[`${prefix}${key}`, value] as [string, string]] : flatten(value, `${prefix}${key}.`),
  );
}

let index: Map<string, string[]> | null = null;

function build() {
  const result = new Map<string, string[]>();
  for (const [i, tree] of EN.entries()) {
    const en = flatten(tree);
    const sr = new Map(flatten(SR[i]));
    for (const [key, text] of en) {
      const translated = sr.get(key);
      if (translated === undefined) continue;
      const list = result.get(text) ?? [];
      if (!list.includes(translated)) list.push(translated);
      result.set(text, list);
    }
  }
  return result;
}

/** `text` (English, as written in the app's locale files) in the language being captured. */
export function tr(text: string, pick = 0): string {
  if (guideLang() === 'en') return text;
  index ??= build();
  const found = index.get(text);
  if (!found) throw new Error(`tr: "${text}" is not a text in the English locale files`);
  return found[pick] ?? found[0];
}

/** Case-insensitive exact match for `cy.contains`, so "Start" doesn't match "Started". */
export function exact(text: string, pick = 0): RegExp {
  return new RegExp(`^\\s*${tr(text, pick).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`, 'i');
}
