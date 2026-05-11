# Frontend

React 19 · Vite 7 · TypeScript strict · Tailwind v4 · TanStack Query · React Router v7

---

## Structure

```
src/
  api/          # typed axios calls, one file per domain
  components/   # shared UI components, no feature logic
  features/     # feature domains — pages, hooks, feature-specific components
    {domain}/
      {Domain}Page.tsx
      use{Domain}.ts
  types/        # all shared TypeScript types in index.ts
```

---

## API layer (`api/*.ts`)

- One file per domain: `api/jobs.ts`, `api/organisations.ts`, etc.
- Named export: `export const xxxApi = { ... }` — plain object with methods
- Each method: typed axios call unwrapped with `.then((r) => r.data)`
- Use type parameters: `apiClient.get<JobResponse[]>(...)`

```ts
export const jobsApi = {
  list: (projectId: string) =>
    apiClient.get<JobResponse[]>(`/api/projects/${projectId}/jobs`).then((r) => r.data),
};
```

---

## Hooks (`use*.ts`)

- One file per domain in `features/{domain}/use{Domain}.ts`
- `useQuery` for reads — always provide `queryKey`, `queryFn`, and `enabled: !!id` when the ID may be null
- Query keys are hierarchical arrays: `['jobs', projectId]`, `['jobs', projectId, jobId]`, `['projects', projectId, 'members']`
- `useMutation` for writes — `onSuccess` either invalidates or sets/removes query data:
  - `void queryClient.invalidateQueries(...)` — always `void` the promise
  - `queryClient.setQueryData(key, data)` when the response contains the updated entity
  - `queryClient.removeQueries(...)` on delete

---

## Forms

- react-hook-form + zod: define schema with `z.object({})`, infer type with `z.infer<typeof schema>`
- Pass `zodResolver(schema)` to `useForm`
- Use `Controller` for complex controlled fields (e.g. `MarkdownEditor`)

---

## Components

Shared UI lives in `components/` — no feature logic, no API calls. Key components:

| Component | Use for |
|-----------|---------|
| `Button` | All buttons — variants: `primary`, `secondary`, `danger`, `ghost`; sizes: `sm`, `md` |
| `Modal` | Any overlay dialog |
| `ConfirmModal` | Destructive action confirmation |
| `Skeleton` | Loading placeholders |
| `PriorityBadge` | Job priority display |
| `StatusBadge` | Job status display |
| `MarkdownEditor` | Markdown input fields |
| `Markdown` | Markdown render output |
| `PageError` | Full-page error state with retry |

---

## Addon gating

```ts
const { hasAddon } = useCurrentOrg();
```

- Full-page block: `if (!hasAddon('FEATURE')) return <UpgradeCard featureName="..." />`
- Section block: `{hasAddon('FEATURE') && <section>...</section>}`
- Row-level block: `<LockedSectionRow sections={['Feature A', 'Feature B']} />`

---

## Types

All shared types live in `src/types/index.ts`. Add new API response interfaces and string union types there.

---

## Definition of done

- `npm run lint` clean
- `tsc -b` passes
- Manual testing checklist in the job description verified before opening the PR
