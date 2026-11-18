import { describe, expect, it } from 'vitest';
import { landingPath, landingProjectRef } from './landingTarget';

const PROJECT_ID = '3f2b8c1e-5d4a-4b6f-9e7d-1a2b3c4d5e6f';
const OTHER_ID = '9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d';
const projects = [
  { id: PROJECT_ID, friendlyId: 'PRJ-001' },
  { id: OTHER_ID, friendlyId: 'PRJ-002' },
];

describe('landingProjectRef', () => {
  it('is null for the project list', () => {
    expect(landingProjectRef('LIST', PROJECT_ID, 'PRJ-002')).toBeNull();
  });

  it('is the most recent project for Last opened', () => {
    expect(landingProjectRef('LAST', PROJECT_ID, 'PRJ-002')).toBe('PRJ-002');
    expect(landingProjectRef('LAST', PROJECT_ID, null)).toBeNull();
  });

  it('is the chosen project for Specific', () => {
    expect(landingProjectRef('SPECIFIC', PROJECT_ID, 'PRJ-002')).toBe(PROJECT_ID);
    expect(landingProjectRef('SPECIFIC', null, 'PRJ-002')).toBeNull();
  });
});

describe('landingPath', () => {
  it('opens the project list when there is no project', () => {
    expect(landingPath(null, projects, 'DASHBOARD')).toBe('/projects');
  });

  it('opens a project found by UUID on the default project page', () => {
    expect(landingPath(PROJECT_ID, projects, 'JOBS')).toBe('/projects/PRJ-001/jobs');
  });

  it('opens a project found by friendly id, ignoring case', () => {
    expect(landingPath('prj-002', projects, 'APPROVALS')).toBe('/projects/PRJ-002/approvals');
  });

  it('falls back to the project list for a project the user does not have', () => {
    expect(landingPath('PRJ-404', projects, 'DASHBOARD')).toBe('/projects');
    expect(landingPath(PROJECT_ID, [], 'DASHBOARD')).toBe('/projects');
  });
});
