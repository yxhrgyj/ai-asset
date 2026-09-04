# Project Management Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore a buildable, navigable AI asset platform whose project-management list and detail flows use one correct frontend/backend contract.

**Architecture:** Keep the Vue 3/Vite SPA and Spring Boot REST API. The frontend API client will return typed JSON payloads directly and apply exactly one `/api` prefix; project pages will render inside the existing `MainLayout`. The backend keeps PostgreSQL/Flyway and enforces project visibility, archive, and rule-binding checks without changing applied migrations.

**Tech Stack:** Vue 3, TypeScript, Vite 6, Pinia, Vue Router, Node 24 built-in test runner, Spring Boot 3.3.5, Java 21, Spring Data JPA, PostgreSQL 17, Maven.

## Global Constraints

- Preserve all pre-existing uncommitted user changes; never reset, checkout, or delete unrelated files.
- Use `E:/Objects/ai-asset-platform` as the code root; `E:/Objects/团队AI资产管理` contains design documents only.
- Do not modify an applied Flyway migration; this repair must work without a schema change.
- API consumers pass paths relative to `/api` and must never produce `/api/api/...`.
- Keep strict TypeScript checks, including `noUnusedLocals`, `noUnusedParameters`, and `noUncheckedSideEffectImports`.
- Every production fix has a focused failing regression check first.
- Do not report completion without fresh frontend build, backend test, and HTTP smoke evidence.

## File Map

- Create `frontend/test/project-api-contract.test.mjs`: URL, payload, and layout regression checks.
- Modify `frontend/package.json`: focused contract-test script.
- Modify `frontend/src/api/client.ts`, `frontend/src/api/projects.ts`, `frontend/src/api/teams.ts`: typed API contract.
- Modify `frontend/src/views/Projects.vue`, `frontend/src/views/ProjectDetail.vue`: layout, loading/error states, and data flow.
- Modify `frontend/src/router/index.ts`, `frontend/src/components/MainLayout.vue`: valid navigation and strictness cleanup.
- Create `frontend/src/env.d.ts`: CSS/Vite declarations.
- Modify `frontend/src/views/Users.vue`, `frontend/src/components/CustomSelect.vue`, `frontend/src/views/AssetDetail.vue`, `frontend/src/views/AssetEdit.vue`: adjacent build/runtime fixes.
- Modify `src/main/java/com/company/aiasset/project/{ProjectController,ProjectRepository,CreateProjectRequest,UpdateProjectRequest,AddProjectRuleRequest}.java`: backend boundaries.
- Create `src/test/java/com/company/aiasset/project/ProjectControllerTest.java`: focused backend regression tests.
- Create/update `PROJECT_PROGRESS.md`: dated handoff evidence.

---

### Task 1: Add failing frontend contract checks

**Files:**
- Create: `frontend/test/project-api-contract.test.mjs`
- Modify: `frontend/package.json`

**Interfaces:** Produces `npm run test:contract`; it must fail against the current `/api/api/projects` request and missing layout.

- [ ] **Step 1: Write the failing tests**

```js
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import test from 'node:test'

test('projects list uses one /api prefix and returns the JSON array', async () => {
  const calls = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (url, init) => {
    calls.push({ url: String(url), init })
    return new Response(JSON.stringify([{ id: 'p1', name: '演示项目' }]), {
      status: 200, headers: { 'content-type': 'application/json' }
    })
  }
  try {
    const { projectsApi } = await import('../src/api/projects.ts')
    assert.deepEqual(await projectsApi.list(), [{ id: 'p1', name: '演示项目' }])
    assert.equal(calls[0].url, 'http://localhost/api/projects')
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('project list is rendered inside the shared application layout', async () => {
  const source = await fs.readFile(new URL('../src/views/Projects.vue', import.meta.url), 'utf8')
  assert.match(source, /<MainLayout>/)
  assert.match(source, /import MainLayout from ['"]\.\.\/components\/MainLayout\.vue['"]/)
})
```

- [ ] **Step 2: Add the script without changing production code**

Add to `frontend/package.json`:

```json
"test:contract": "node --experimental-strip-types --test test/project-api-contract.test.mjs"
```

- [ ] **Step 3: Run and verify RED**

```powershell
cd E:/Objects/ai-asset-platform/frontend
npm.cmd run test:contract
```

Expected: URL and layout assertions fail for the current code.

- [ ] **Step 4: Commit only this test harness**

```powershell
git add frontend/test/project-api-contract.test.mjs frontend/package.json
git commit -m "test: reproduce project page contract regressions"
```

### Task 2: Normalize and type the frontend API client

**Files:** `frontend/src/api/client.ts`, `frontend/src/api/projects.ts`, `frontend/src/api/teams.ts`; test `frontend/test/project-api-contract.test.mjs`.

**Interfaces:** `get<T>`, `post<T>`, `put<T>`, and `delete<T = void>` return `Promise<T>` directly; consumers pass `/projects` or `/teams` and receive `/api/...` requests.

- [ ] **Step 1: Re-run Task 1's check and confirm RED**

- [ ] **Step 2: Implement the direct-payload client**

`client.ts` must normalize a leading slash, preserve an already-prefixed `/api/...` path without duplicating it, send `credentials: 'include'`, parse 204 as `undefined`, and throw `Error(text || \`HTTP ${status}\`)` for non-2xx responses. Its core must be equivalent to:

```ts
const BASE = '/api'
function apiUrl(path: string) {
  const normalized = path.startsWith('/') ? path : `/${path}`
  return normalized === BASE || normalized.startsWith(`${BASE}/`)
    ? normalized : `${BASE}${normalized}`
}
async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(apiUrl(path), {
    ...options, credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...options.headers }
  })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `HTTP ${response.status}`)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
```

Export methods that call `request<T>` with explicit payload types and `unknown` request bodies.

- [ ] **Step 3: Update project/team methods**

For every method, remove `/api` from the path and return the direct result, e.g. `return apiClient.get<Project[]>('/projects')` and `return apiClient.post<Project>('/projects', data)`. Remove all `response.data` reads in these two API modules.

- [ ] **Step 4: Run and verify GREEN**

```powershell
npm.cmd run test:contract
```

- [ ] **Step 5: Commit**

```powershell
git add frontend/src/api/client.ts frontend/src/api/projects.ts frontend/src/api/teams.ts frontend/test/project-api-contract.test.mjs
git commit -m "fix: normalize typed frontend api responses"
```

### Task 3: Repair project views and navigation

**Files:** `frontend/src/views/Projects.vue`, `frontend/src/views/ProjectDetail.vue`, `frontend/src/router/index.ts`, `frontend/src/components/MainLayout.vue`; test `frontend/test/project-api-contract.test.mjs`.

**Interfaces:** Views consume `Project[]`, `Team[]`, `Project`, `ProjectRule[]`, `MergedRulesResult`, and asset API results directly.

- [ ] **Step 1: Run the RED layout check**

- [ ] **Step 2: Wrap project views and expose state**

Import `MainLayout` and make it the template root in both project views. `Projects.vue` must maintain `loading` and `error`, assign `projects.value = await projectsApi.list()` and `teams.value = await teamsApi.list()`, show a loading indicator while requests are pending, show a page-level error for failed requests, and reserve the empty state for a successful empty list.

In `ProjectDetail.vue`, import `assetApi` from `../api/asset`, remove the nonexistent `../api/assets` and unused `computed`, replace every `res.data` access with direct results, and load rule options from `assetApi.list({ type: 'RULE', size: 100 })`; fetch each detail and retain only published versions, using the version UUID as the select value. Refresh project rules and merged rules after add/remove.

- [ ] **Step 3: Make navigation valid**

Change the router guard parameter to `(to, _from, next)`. Remove the `/project-rules` link because no route exists; keep `/projects` and the lazy `/projects/:id` route.

- [ ] **Step 4: Run checks**

```powershell
npm.cmd run test:contract
npx.cmd vue-tsc -b --pretty false
```

Expected: layout and project errors are gone; only adjacent errors remain for Task 4.

- [ ] **Step 5: Commit**

```powershell
git add frontend/src/views/Projects.vue frontend/src/views/ProjectDetail.vue frontend/src/router/index.ts frontend/src/components/MainLayout.vue frontend/test/project-api-contract.test.mjs
git commit -m "fix: restore project page layout and data flow"
```

### Task 4: Make the whole frontend compile under strict checks

**Files:** create `frontend/src/env.d.ts`; modify `frontend/src/views/Users.vue`, `frontend/src/components/CustomSelect.vue`, `frontend/src/views/AssetDetail.vue`, `frontend/src/views/AssetEdit.vue`.

- [ ] **Step 1: Capture the RED checker output**

```powershell
cd E:/Objects/ai-asset-platform/frontend
npx.cmd vue-tsc -b --pretty false
```

- [ ] **Step 2: Add declarations**

Create:

```ts
/// <reference types="vite/client" />
declare module '*.css'
```

- [ ] **Step 3: Fix user-management contracts**

Use backend role values (`USER`, `AUTHOR`, `APPROVER`, `ADMIN`), include `email: null` in create/update payloads, call `userApi.resetPassword(user.id, { newPassword })`, and render team names through a `getTeamName(user.teamId)` lookup rather than nonexistent `user.teamName`.

- [ ] **Step 4: Fix strict Vue/DOM issues**

Remove unused `onMounted` from `CustomSelect.vue`; replace `$refs.editorFileInput?.click()` with `editorFileInput.value?.click()` in `AssetDetail.vue`; remove unused `publishDraft`; let the CSS declaration cover both editor style imports.

- [ ] **Step 5: Verify and commit**

```powershell
npx.cmd vue-tsc -b --pretty false
npm.cmd run build
```

Both must exit 0. Then:

```powershell
git add frontend/src/env.d.ts frontend/src/views/Users.vue frontend/src/components/CustomSelect.vue frontend/src/views/AssetDetail.vue frontend/src/views/AssetEdit.vue
git commit -m "fix: restore strict frontend build"
```

### Task 5: Harden project backend boundaries and add tests

**Files:** project controller/repository/request classes listed in the file map; create `src/test/java/com/company/aiasset/project/ProjectControllerTest.java`.

**Interfaces:** `GET /api/projects` returns non-archived projects owned by or belonging to the current user's existing `users.team_id`. Mutations reject blank input, archived projects, missing versions, non-published versions, and duplicate bindings with explicit 4xx responses.

- [ ] **Step 1: Write Mockito tests in RED state**

Instantiate `ProjectController` with mocks and cover: owner list calls `findByOwnerUserIdAndArchivedFalseOrderByCreatedAtDesc`; team-visible projects are merged without duplicate IDs; archived update/rule-add returns `409` or `400` according to the chosen documented status; duplicate slug returns `409`; missing asset version returns `404`; and a DRAFT version returns `400`. Use JUnit 5 and AssertJ already supplied by `spring-boot-starter-test`.

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
cd E:/Objects/ai-asset-platform
mvn.cmd -Dtest=ProjectControllerTest test
```

- [ ] **Step 3: Implement the minimal controller/repository fixes**

Use the existing unarchived repository methods, derive the current user's team from `AuthUtil.getCurrentUser().getTeamId()`, merge/de-duplicate results by ID, reject archived records before mutation, validate request fields before repository calls, map missing versions to `404`, and explicitly check `PUBLISHED`/`DEPRECATED` before saving. Keep the database trigger as the final invariant and do not edit V1–V7.

- [ ] **Step 4: Verify GREEN and commit**

```powershell
mvn.cmd -Dtest=ProjectControllerTest test
```

Then:

```powershell
git add src/main/java/com/company/aiasset/project src/test/java/com/company/aiasset/project/ProjectControllerTest.java
git commit -m "fix: enforce project visibility and rule binding boundaries"
```

### Task 6: Integrated verification and handoff

**Files:** create/update `PROJECT_PROGRESS.md`; no production changes unless a fresh verification failure identifies a new root cause.

- [ ] **Step 1: Run complete checks**

```powershell
cd E:/Objects/ai-asset-platform/frontend
npm.cmd run test:contract
npm.cmd run build
cd E:/Objects/ai-asset-platform
mvn.cmd test
```

- [ ] **Step 2: Run authenticated HTTP smoke checks**

With a temporary session, verify `GET /api/auth/me`, `GET /api/projects`, `GET /api/teams`, and `GET /actuator/health` return the documented 200 shapes. If create/update must be checked, use a unique temporary slug and archive that same record immediately; do not modify existing records.

- [ ] **Step 3: Check the running Vite route**

After login, open `/projects` and verify the shared navigation is present, the list is not falsely empty when the API has rows, and browser/module requests have no console errors.

- [ ] **Step 4: Record `PROJECT_PROGRESS.md`**

Include date, changed commits/files, exact command results, remaining risks, and next continuation point; never include credentials.

- [ ] **Step 5: Review final diff/status**

```powershell
cd E:/Objects/ai-asset-platform
git diff --check
git status --short --branch
git log -8 --oneline --decorate
```

Confirm unrelated pre-existing dirty files remain and no generated secrets or database dumps were added.
