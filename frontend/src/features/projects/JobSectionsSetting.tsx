import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import LockIcon from '../../components/LockIcon';
import Switch from '../../components/Switch';
import { useCurrentOrg } from '../org/OrgContext';
import { SECTION_ADDON } from '../jobs/useSectionVisibility';
import type { JobSection, ProjectResponse } from '../../types';
import { useUpdateProjectSettings } from './useProjects';

interface Props {
  readonly projectId: string;
  readonly project: ProjectResponse;
  /** Project Owner or Admin. Everyone else sees the toggles read-only. */
  readonly canEdit: boolean;
  /** Completed projects are read-only (the backend returns 409). */
  readonly isCompleted: boolean;
}

/** Display order, and the order the hidden list is sent in. Only these values are ever sent. */
const SECTIONS: readonly JobSection[] = ['NOTES', 'APPROVALS', 'RELATIONSHIPS', 'LINKS', 'STATUS_HISTORY'];

const LABEL_ID = 'job-sections-label';
const HELP_ID = 'job-sections-help';
const READ_ONLY_ID = 'job-sections-read-only-note';

/**
 * ADR-0053 "Sections shown on jobs": one switch per optional job-detail section (on = the
 * project doesn't hide it). Each switch saves on its own, optimistically, with a rollback
 * on error (see `useUpdateProjectSettings`); the switches lock while a save is in flight so
 * two saves can't race.
 *
 * A section whose add-on the org lacks is locked and off with "Not in your plan": jobs can't
 * show it whatever the project chooses. Its stored hidden state is kept as is.
 */
export default function JobSectionsSetting({ projectId, project, canEdit, isCompleted }: Props) {
  const { t } = useTranslation('projects');
  const { subscription, hasAddon } = useCurrentOrg();
  const { mutate, isPending } = useUpdateProjectSettings(projectId);

  const hidden = project.hiddenJobSections ?? [];
  const canChange = canEdit && !isCompleted;
  const showReadOnlyNote = !canEdit && !isCompleted;
  // A null subscription means OrgContext hasn't synced it yet (OrgRequiredRoute only renders
  // once there is one): don't flash "Not in your plan" on every row meanwhile.
  const subscriptionKnown = subscription !== null;

  function onChange(section: JobSection, show: boolean) {
    const next = SECTIONS.filter((s) => (s === section ? !show : hidden.includes(s)));
    const name = t(`jobSections.names.${section}`);
    mutate(
      { hiddenJobSections: next },
      {
        onSuccess: () =>
          toast.success(show ? t('jobSections.shownToast', { section: name }) : t('jobSections.hiddenToast', { section: name })),
      },
    );
  }

  return (
    <div
      className="border border-gray-200 dark:border-gray-700 rounded-xl p-4"
      data-testid="job-sections-setting"
    >
      <p id={LABEL_ID} className="text-sm font-medium text-gray-900 dark:text-gray-100">
        {t('jobSections.label')}
      </p>
      <p id={HELP_ID} className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
        {t('jobSections.help')}
      </p>
      {showReadOnlyNote && (
        <p
          id={READ_ONLY_ID}
          className="text-xs text-gray-500 dark:text-gray-400 mt-2"
          data-testid="job-sections-read-only"
        >
          {t('jobSections.readOnly')}
        </p>
      )}
      <ul className="mt-3 divide-y divide-gray-100 dark:divide-gray-700" aria-labelledby={LABEL_ID}>
        {SECTIONS.map((section) => {
          const notInPlan = subscriptionKnown && !hasAddon(SECTION_ADDON[section]);
          const nameId = `job-section-${section}-label`;
          const lockedId = `job-section-${section}-locked`;
          const describedBy = [notInPlan && lockedId, showReadOnlyNote && READ_ONLY_ID].filter(Boolean).join(' ');
          return (
            <li
              key={section}
              className="flex items-center justify-between gap-4 py-2.5"
              data-testid={`job-section-row-${section}`}
            >
              <div className="min-w-0">
                <p id={nameId} className="text-sm text-gray-800 dark:text-gray-200">
                  {t(`jobSections.names.${section}`)}
                </p>
                {notInPlan && (
                  <p
                    id={lockedId}
                    className="text-xs text-gray-500 dark:text-gray-400 mt-0.5 flex items-center gap-1.5"
                    data-testid={`job-section-locked-${section}`}
                  >
                    <LockIcon />
                    <span>{t('jobSections.notInPlan')}</span>
                  </p>
                )}
              </div>
              <Switch
                checked={!notInPlan && !hidden.includes(section)}
                locked={!canChange || isPending || notInPlan || !subscriptionKnown}
                labelledBy={nameId}
                describedBy={describedBy || undefined}
                testId={`job-section-toggle-${section}`}
                onChange={(show) => onChange(section, show)}
              />
            </li>
          );
        })}
      </ul>
    </div>
  );
}
