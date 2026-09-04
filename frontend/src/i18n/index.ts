import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

import enCommon from './locales/en/common.json';
import enErrors from './locales/en/errors.json';
import enShared1 from './locales/en/shared1.json';
import enShared2 from './locales/en/shared2.json';
import enJobsPages from './locales/en/jobsPages.json';
import enJobsComponents from './locales/en/jobsComponents.json';
import enProjects from './locales/en/projects.json';
import enOrg from './locales/en/org.json';
import enMilestonesTemplatesSchedules from './locales/en/milestonesTemplatesSchedules.json';
import enApprovalsDashboardSettingsLanding from './locales/en/approvalsDashboardSettingsLanding.json';
import enJobTypes from './locales/en/jobTypes.json';
import enSuperAdmin from './locales/en/superAdmin.json';
import enFeedback from './locales/en/feedback.json';

import srCommon from './locales/sr/common.json';
import srErrors from './locales/sr/errors.json';
import srShared1 from './locales/sr/shared1.json';
import srShared2 from './locales/sr/shared2.json';
import srJobsPages from './locales/sr/jobsPages.json';
import srJobsComponents from './locales/sr/jobsComponents.json';
import srProjects from './locales/sr/projects.json';
import srOrg from './locales/sr/org.json';
import srMilestonesTemplatesSchedules from './locales/sr/milestonesTemplatesSchedules.json';
import srJobTypes from './locales/sr/jobTypes.json';
import srApprovalsDashboardSettingsLanding from './locales/sr/approvalsDashboardSettingsLanding.json';
import srSuperAdmin from './locales/sr/superAdmin.json';
import srFeedback from './locales/sr/feedback.json';

export const NAMESPACES = [
  'common',
  'errors',
  'shared1',
  'shared2',
  'jobsPages',
  'jobsComponents',
  'projects',
  'org',
  'milestonesTemplatesSchedules',
  'approvalsDashboardSettingsLanding',
  'jobTypes',
  'superAdmin',
  'feedback',
] as const;

void i18n.use(initReactI18next).init({
  resources: {
    en: {
      common: enCommon,
      errors: enErrors,
      shared1: enShared1,
      shared2: enShared2,
      jobsPages: enJobsPages,
      jobsComponents: enJobsComponents,
      projects: enProjects,
      org: enOrg,
      milestonesTemplatesSchedules: enMilestonesTemplatesSchedules,
      approvalsDashboardSettingsLanding: enApprovalsDashboardSettingsLanding,
      jobTypes: enJobTypes,
      superAdmin: enSuperAdmin,
      feedback: enFeedback,
    },
    sr: {
      common: srCommon,
      errors: srErrors,
      shared1: srShared1,
      shared2: srShared2,
      jobsPages: srJobsPages,
      jobsComponents: srJobsComponents,
      projects: srProjects,
      org: srOrg,
      milestonesTemplatesSchedules: srMilestonesTemplatesSchedules,
      approvalsDashboardSettingsLanding: srApprovalsDashboardSettingsLanding,
      jobTypes: srJobTypes,
      superAdmin: srSuperAdmin,
      feedback: srFeedback,
    },
  },
  lng: 'en',
  fallbackLng: 'en',
  defaultNS: 'common',
  ns: NAMESPACES,
  interpolation: { escapeValue: false },
});

// JOB-227: exposes the singleton i18n instance for E2E tests to inject a resource key
// missing from one locale (proving the fallbackLng behavior against the real running
// instance, not a reimplementation). Gated on import.meta.env.DEV rather than
// window.Cypress — Cypress injects window.Cypress into the AUT's window, but this
// module can evaluate before that injection lands (it's imported very early, needed
// before anything else can render), so checking for it here is a timing race. E2E
// always runs against the Vite dev server (never a production build), so DEV is both
// reliable and — the property that actually matters — still never true in a real
// production bundle.
declare global {
  interface Window {
    __i18nForE2E?: typeof i18n;
  }
}
if (typeof window !== 'undefined' && import.meta.env.DEV) {
  window.__i18nForE2E = i18n;
}

export default i18n;
