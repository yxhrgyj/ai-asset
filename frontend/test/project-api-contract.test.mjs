import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import test from 'node:test'

test('API client applies one /api prefix and returns the JSON payload directly', async () => {
  const calls = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (url, init) => {
    calls.push({ url: String(url), init })
    return new Response(JSON.stringify([{ id: 'p1', name: '演示项目' }]), {
      status: 200,
      headers: { 'content-type': 'application/json' }
    })
  }

  try {
    const { apiClient } = await import('../src/api/client.ts')
    const result = await apiClient.get('/projects')

    assert.deepEqual(result, [{ id: 'p1', name: '演示项目' }])
    assert.equal(calls[0].url, '/api/projects')
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('application declares a favicon instead of requesting a missing default icon', async () => {
  const source = await fs.readFile(new URL('../index.html', import.meta.url), 'utf8')

  assert.match(source, /<link\s+rel=["']icon["'][^>]+href=["'][^"']+["']/)
})

test('projects API uses paths relative to the shared /api client', async () => {
  const source = await fs.readFile(new URL('../src/api/projects.ts', import.meta.url), 'utf8')

  assert.match(source, /apiClient\.get(?:<Project\[\]>)?\(['"]\/projects['"]\)/)
  assert.doesNotMatch(source, /apiClient\.(?:get|post|put|delete)\([^\n]*['"]\/api\/projects/)
})

test('project views render inside the shared application layout', async () => {
  for (const view of ['Projects.vue', 'ProjectDetail.vue']) {
    const source = await fs.readFile(new URL(`../src/views/${view}`, import.meta.url), 'utf8')

    assert.match(source, /<MainLayout>/, `${view} should render inside MainLayout`)
    assert.match(
      source,
      /import MainLayout from ['"]\.\.\/components\/MainLayout\.vue['"]/,
      `${view} should import MainLayout`
    )
  }
})

test('project list retries project and team loading together', async () => {
  const source = await fs.readFile(new URL('../src/views/Projects.vue', import.meta.url), 'utf8')

  assert.match(source, /@click=["']loadPage["']/)
  assert.match(source, /onMounted\(loadPage\)/)
  assert.match(source, /Promise\.all\(\[\s*projectsApi\.list\(\),\s*teamsApi\.list\(\)\s*\]\)/)
})

test('project detail consumes the backend merged-rule contract and removes by asset version id', async () => {
  const source = await fs.readFile(new URL('../src/views/ProjectDetail.vue', import.meta.url), 'utf8')

  assert.match(source, /mergedRules\.orgCount/)
  assert.match(source, /mergedRules\.rules/)
  assert.match(source, /removeRule\(rule\.assetVersionId\)/)
  assert.doesNotMatch(source, /mergedRules\.(?:organizationCount|items)/)
  assert.doesNotMatch(source, /\bres\.data\b/)
})

test('project detail offers published rule-version ids from the asset API', async () => {
  const source = await fs.readFile(new URL('../src/views/ProjectDetail.vue', import.meta.url), 'utf8')

  assert.match(source, /import \{ assetApi[^\n]+from ['"]\.\.\/api\/asset['"]/)
  assert.match(source, /assetApi\.list\(\{\s*type:\s*['"]RULE['"],\s*size:\s*100\s*\}\)/)
  assert.match(source, /version[.]status === ['"]PUBLISHED['"]/)
  assert.match(source, /:value="option\.versionId"/)
  assert.doesNotMatch(source, /from ['"]\.\.\/api\/assets['"]/)
})

test('project rule bodies are rendered as escaped text', async () => {
  const source = await fs.readFile(new URL('../src/views/ProjectDetail.vue', import.meta.url), 'utf8')

  assert.doesNotMatch(source, /v-html=/)
  assert.match(source, /\{\{\s*rule\.body\s*\}\}/)
})

test('shared navigation only links to registered project routes', async () => {
  const layout = await fs.readFile(new URL('../src/components/MainLayout.vue', import.meta.url), 'utf8')
  const router = await fs.readFile(new URL('../src/router/index.ts', import.meta.url), 'utf8')

  assert.doesNotMatch(layout, /to=["']\/project-rules["']/)
  assert.match(layout, /to=["']\/projects["']/)
  assert.match(router, /beforeEach\(async \(to, _from, next\)/)
})

test('user administration uses the backend role, team and password contracts', async () => {
  const source = await fs.readFile(new URL('../src/views/Users.vue', import.meta.url), 'utf8')

  assert.match(source, /value:\s*['"]USER['"]/)
  assert.doesNotMatch(source, /value:\s*['"]MEMBER['"]/)
  assert.match(source, /getTeamName\(user\.teamId\)/)
  assert.match(source, /resetPassword\(user\.id,\s*\{\s*newPassword\s*\}\)/)
  assert.match(source, /email:\s*null/)
})
