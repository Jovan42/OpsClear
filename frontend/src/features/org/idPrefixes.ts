import type { OrgSettings, UpdateOrgSettingsRequest } from '../../types';

// ADR-0060: helpers for the ID prefixes section on the org settings page.

export const PREFIX_FIELDS = ['projectPrefix', 'jobPrefix', 'milestonePrefix', 'templatePrefix'] as const;
export type PrefixField = (typeof PREFIX_FIELDS)[number];
export type PrefixValues = Record<PrefixField, string>;

const NEXT_ID_FIELD: Record<PrefixField, keyof OrgSettings> = {
  projectPrefix: 'nextProjectId',
  jobPrefix: 'nextJobId',
  milestonePrefix: 'nextMilestoneId',
  templatePrefix: 'nextTemplateId',
};

/** Same rule as the backend after trimming and uppercasing: 2–3 letters A–Z. */
export const PREFIX_PATTERN = /^[A-Z]{2,3}$/;

/** What an input keeps of what was typed: uppercase letters only, at most 3. */
export function normalisePrefixInput(raw: string): string {
  return raw.toUpperCase().replace(/[^A-Z]/g, '').slice(0, 3);
}

export function prefixValues(settings: OrgSettings): PrefixValues {
  return {
    projectPrefix: settings.projectPrefix,
    jobPrefix: settings.jobPrefix,
    milestonePrefix: settings.milestonePrefix,
    templatePrefix: settings.templatePrefix,
  };
}

/**
 * The next ID for a type with `prefix` swapped in, e.g. `JOB-347` + `TSK` → `TSK-347`.
 * Falls back to the server's next ID while the edited prefix isn't valid yet.
 */
export function previewNextId(settings: OrgSettings, field: PrefixField, prefix: string): string {
  const nextId = settings[NEXT_ID_FIELD[field]];
  if (!PREFIX_PATTERN.test(prefix)) return nextId;
  const number = nextId.slice(nextId.lastIndexOf('-') + 1);
  return `${prefix}-${number}`;
}

/** Only the prefixes that differ from the saved ones, for the PATCH body. */
export function changedPrefixes(values: PrefixValues, settings: OrgSettings): UpdateOrgSettingsRequest {
  const patch: UpdateOrgSettingsRequest = {};
  for (const field of PREFIX_FIELDS) {
    if (values[field] !== settings[field]) patch[field] = values[field];
  }
  return patch;
}

/** Fields whose value is also used by another field. */
export function duplicatePrefixFields(values: PrefixValues): PrefixField[] {
  return PREFIX_FIELDS.filter((field) =>
    PREFIX_FIELDS.some((other) => other !== field && values[other] !== '' && values[other] === values[field]),
  );
}

/**
 * Which field a backend 400 message is about, or null to show it as a toast.
 * - `Prefix "JOB" is already used by existing jobs`: the field set to that prefix
 * - a DTO validation message such as `jobPrefix: must match …`: the field it starts with
 */
export function fieldForPrefixError(message: string, values: PrefixValues): PrefixField | null {
  const named = /^Prefix "([A-Za-z]+)"/.exec(message);
  if (named) {
    const prefix = named[1].toUpperCase();
    return PREFIX_FIELDS.find((field) => values[field] === prefix) ?? null;
  }
  const property = /^(\w+):/.exec(message)?.[1];
  return PREFIX_FIELDS.find((field) => field === property) ?? null;
}
