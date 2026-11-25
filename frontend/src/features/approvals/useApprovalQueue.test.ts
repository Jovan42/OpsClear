import { beforeEach, describe, expect, it, vi } from 'vitest';

// The callers are called as plain functions with every hook they use mocked, and useQuery
// records the options each one passes, so the tests can see who polls the pending queue.
const queryOptions: Array<Record<string, unknown>> = [];

vi.mock('@tanstack/react-query', () => ({
  useQuery: vi.fn((options: Record<string, unknown>) => {
    queryOptions.push(options);
    return { data: [], isLoading: false, isError: false, isFetching: false, dataUpdatedAt: 0, refetch: vi.fn() };
  }),
}));
vi.mock('react', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react')>()),
  useState: (initial: unknown) => [initial, vi.fn()],
}));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
vi.mock('../../i18n', () => ({ default: { t: (key: string) => key } }));
vi.mock('../../api/approvals', () => ({ approvalsApi: {} }));
vi.mock('react-router-dom', () => ({
  Navigate: () => null,
  useNavigate: () => vi.fn(),
  useParams: () => ({ projectFriendlyId: 'PRJ-1' }),
}));
vi.mock('../org/OrgContext', () => ({ useCurrentOrg: () => ({ hasAddon: () => true }) }));
vi.mock('../projects/useProjects', () => ({
  useProject: () => ({ data: { name: 'Project', links: [] } }),
  useProjectMembers: () => ({ data: [] }),
  useProjectRole: () => 'OWNER',
}));
vi.mock('../../hooks/usePageTitle', () => ({ usePageTitle: vi.fn() }));
vi.mock('../../hooks/usePreferences', () => ({ usePreferences: () => ({ prefs: {} }) }));

const { APPROVAL_QUEUE_POLL_MS } = await import('./approvalRefresh');
const { useApprovalQueue, usePendingApprovalsAcrossOrgs } = await import('./useApprovalQueue');
const { useProjectNavItems } = await import('../../hooks/useProjectNavItems');
const { default: ApprovalQueuePage } = await import('./ApprovalQueuePage');
const { default: PendingApprovalsSection } = await import('../overview/PendingApprovalsSection');

/** The options of the one approvals query the call made. */
function approvalsQueryOf(call: () => unknown) {
  queryOptions.length = 0;
  call();
  const approvals = queryOptions.filter((o) => (o.queryKey as unknown[])[0] === 'approvals');
  expect(approvals).toHaveLength(1);
  return approvals[0];
}

function expectPolls(options: Record<string, unknown>) {
  expect(options.refetchInterval).toBe(APPROVAL_QUEUE_POLL_MS);
  expect(options.refetchOnWindowFocus).toBe('always');
}

function expectNoPoll(options: Record<string, unknown>) {
  expect(options.refetchInterval).toBeUndefined();
  expect(options.refetchOnWindowFocus).toBeUndefined();
  expect(options.staleTime).toBe(5 * 60 * 1000);
}

beforeEach(() => {
  queryOptions.length = 0;
});

describe('pending queue refresh', () => {
  it('the project queue hook does not poll by default', () => {
    expectNoPoll(approvalsQueryOf(() => useApprovalQueue('PRJ-1')));
  });

  it('the project queue hook polls and refetches on focus when live', () => {
    expectPolls(approvalsQueryOf(() => useApprovalQueue('PRJ-1', { live: true })));
  });

  it('the cross-org hook does not poll by default, and polls when live', () => {
    expectNoPoll(approvalsQueryOf(() => usePendingApprovalsAcrossOrgs()));
    expectPolls(approvalsQueryOf(() => usePendingApprovalsAcrossOrgs({ live: true })));
  });

  it('the project nav pending badge does not poll', () => {
    expectNoPoll(approvalsQueryOf(() => useProjectNavItems('PRJ-1')));
  });

  it('the project Approvals page polls', () => {
    expectPolls(approvalsQueryOf(() => ApprovalQueuePage()));
  });

  it('the Overview pending approvals section polls', () => {
    expectPolls(approvalsQueryOf(() => PendingApprovalsSection()));
  });
});
