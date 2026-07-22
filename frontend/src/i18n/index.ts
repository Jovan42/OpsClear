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

import srCommon from './locales/sr/common.json';
import srErrors from './locales/sr/errors.json';
import srShared1 from './locales/sr/shared1.json';
import srShared2 from './locales/sr/shared2.json';
import srJobsPages from './locales/sr/jobsPages.json';
import srJobsComponents from './locales/sr/jobsComponents.json';
import srProjects from './locales/sr/projects.json';
import srOrg from './locales/sr/org.json';
import srMilestonesTemplatesSchedules from './locales/sr/milestonesTemplatesSchedules.json';
import srApprovalsDashboardSettingsLanding from './locales/sr/approvalsDashboardSettingsLanding.json';

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
    },
  },
  lng: 'en',
  fallbackLng: 'en',
  defaultNS: 'common',
  ns: NAMESPACES,
  interpolation: { escapeValue: false },
});

export default i18n;
