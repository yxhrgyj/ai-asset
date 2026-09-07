import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import test from 'node:test'

const source = (name) => fs.readFile(new URL(`../src/${name}`, import.meta.url), 'utf8')

test('portal exposes public pages and nests management pages below /admin', async () => {
  const router = await source('router/index.ts')

  for (const path of ['assets-public', 'assets-public/:id', 'statistics-public', 'about', 'help']) {
    assert.match(router, new RegExp(`path:\\s*['"]${path.replace('/', '\\/')}['"]`), `missing route /${path}`)
  }

  assert.match(router, /path:\s*['"]\/['"]/)
  assert.match(router, /path:\s*['"]\/admin['"]/)
  assert.match(router, /component:\s*PortalLayout/)
  assert.match(router, /component:\s*AdminLayout/)
  assert.match(router, /query:\s*\{\s*returnUrl:/)
})

test('portal views are present and use safe markdown rendering', async () => {
  for (const view of ['views/PortalHome.vue', 'views/AssetsPublic.vue', 'views/AssetPublicDetail.vue', 'views/StatisticsPublic.vue', 'views/About.vue', 'views/Help.vue']) {
    const content = await source(view)
    assert.match(content, /<template>/, `${view} should be a Vue view`)
  }

  const detail = await source('views/AssetPublicDetail.vue')
  assert.match(detail, /renderMarkdown/)
  assert.doesNotMatch(detail, /v-html="detail\.body"/)
  assert.match(detail, /returnUrl/)
})

test('portal API keeps requests relative to the shared /api client', async () => {
  const api = await source('api/portal.ts')
  assert.match(api, /apiClient\.get<AssetListResponse>\(`\/public\/assets/)
  assert.match(api, /apiClient\.get<AssetDetail>\(`\/public\/assets\//)
  assert.doesNotMatch(api, /apiClient\.(?:get|post|put|delete)\([^\n]*['"]\/api\//)
})

test('admin navigation uses canonical routes and highlights only the exact item', async () => {
  const layout = await source('components/AdminLayout.vue')
  const home = await source('views/Home.vue')
  const projectDetail = await source('views/ProjectDetail.vue')
  const projects = await source('views/Projects.vue')

  assert.match(layout, /:active-class="item\.to === '\/admin' \? '' : 'is-active'"/)
  assert.match(layout, /exact-active-class="is-active"/)
  assert.doesNotMatch(home, /to="\/(?:assets|teams)"/)
  assert.doesNotMatch(projectDetail, /to="\/projects"/)
  assert.doesNotMatch(projects, /router\.push\(`\/projects\//)
})
