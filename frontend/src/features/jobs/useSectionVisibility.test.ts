import { describe, expect, it } from 'vitest';
import { sectionVisibility } from './useSectionVisibility';
import type { AddonCode, JobSection } from '../../types';

const ALL_SECTIONS: JobSection[] = ['NOTES', 'APPROVALS', 'RELATIONSHIPS', 'LINKS', 'STATUS_HISTORY'];

const addons = (...codes: AddonCode[]) => (key: AddonCode) => codes.includes(key);
const allAddons = addons('NOTES', 'APPROVALS', 'JOB_RELATIONSHIPS', 'JOB_LINKS', 'JOB_STATUS_HISTORY');
const project = (...hidden: JobSection[]) => ({ hiddenJobSections: hidden });

describe('sectionVisibility', () => {
  it('shows every section when the org has its add-on and the project hides nothing', () => {
    const { isVisible, isLocked } = sectionVisibility(allAddons, project());
    for (const s of ALL_SECTIONS) {
      expect(isVisible(s)).toBe(true);
      expect(isLocked(s)).toBe(false);
    }
  });

  it('hides a section the project hides, without locking it, and leaves the others shown', () => {
    const { isVisible, isLocked } = sectionVisibility(allAddons, project('NOTES'));
    expect(isVisible('NOTES')).toBe(false);
    expect(isLocked('NOTES')).toBe(false);
    expect(isVisible('APPROVALS')).toBe(true);
  });

  it('locks a section whose add-on the org lacks when the project does not hide it', () => {
    const { isVisible, isLocked } = sectionVisibility(addons('NOTES'), project());
    expect(isVisible('APPROVALS')).toBe(false);
    expect(isLocked('APPROVALS')).toBe(true);
    expect(isVisible('NOTES')).toBe(true);
    expect(isLocked('NOTES')).toBe(false);
  });

  it('neither shows nor locks a section that is both hidden and missing its add-on', () => {
    const { isVisible, isLocked } = sectionVisibility(addons(), project('LINKS'));
    expect(isVisible('LINKS')).toBe(false);
    expect(isLocked('LINKS')).toBe(false);
    expect(isLocked('NOTES')).toBe(true);
  });

  it('maps each section to its own add-on', () => {
    const cases: Array<[JobSection, AddonCode]> = [
      ['NOTES', 'NOTES'],
      ['APPROVALS', 'APPROVALS'],
      ['RELATIONSHIPS', 'JOB_RELATIONSHIPS'],
      ['LINKS', 'JOB_LINKS'],
      ['STATUS_HISTORY', 'JOB_STATUS_HISTORY'],
    ];
    for (const [section, addon] of cases) {
      const { isVisible } = sectionVisibility(addons(addon), project());
      expect(ALL_SECTIONS.filter(isVisible)).toEqual([section]);
    }
  });

  it('shows and locks nothing until the project has loaded', () => {
    const { isVisible, isLocked } = sectionVisibility(allAddons, undefined);
    const noAddons = sectionVisibility(addons(), undefined);
    for (const s of ALL_SECTIONS) {
      expect(isVisible(s)).toBe(false);
      expect(noAddons.isLocked(s)).toBe(false);
      expect(isLocked(s)).toBe(false);
    }
  });
});
