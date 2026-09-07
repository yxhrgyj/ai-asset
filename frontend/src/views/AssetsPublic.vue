<template>
  <div class="portal-page portal-list-page portal-container">
    <section class="portal-page-intro">
      <div>
        <p class="portal-eyebrow"><span class="portal-eyebrow-line"></span> PUBLIC CATALOGUE</p>
        <h1>资产库</h1>
        <p>浏览已发布的规则、技能与规范文档，找到下一次工作可以直接复用的部分。</p>
      </div>
      <span class="portal-page-index">INDEX / {{ String(total).padStart(2, '0') }}</span>
    </section>

    <form class="portal-filter-bar" @submit.prevent="submitFilters">
      <label class="portal-search-field">
        <span class="portal-search-icon" aria-hidden="true">⌕</span>
        <span class="sr-only">搜索资产</span>
        <input v-model="search" type="search" placeholder="搜索名称、简介或标签" />
      </label>
      <label class="portal-select-field">
        <span class="sr-only">资产类型</span>
        <select v-model="type">
          <option value="">全部类型</option>
          <option value="RULE">编码规则</option>
          <option value="SKILL">技能包</option>
          <option value="DOCUMENT">规范文档</option>
        </select>
      </label>
      <label class="portal-select-field">
        <span class="sr-only">适用范围</span>
        <select v-model="scope">
          <option value="">全部范围</option>
          <option value="ORGANIZATION">组织级</option>
          <option value="TECH_STACK">技术栈</option>
          <option value="PROJECT">项目级</option>
        </select>
      </label>
      <button class="portal-button portal-button-primary portal-filter-submit" type="submit">搜索 <span aria-hidden="true">→</span></button>
    </form>

    <div v-if="error" class="portal-alert" role="alert">{{ error }} <button type="button" @click="loadAssets">重试</button></div>
    <div v-else-if="loading" class="portal-loading"><span class="portal-loading-dot"></span> 正在读取公开目录</div>
    <div v-else-if="items.length === 0" class="portal-empty">
      <span class="portal-empty-code">NO MATCH / {{ search || 'EMPTY QUERY' }}</span>
      <h2>没有找到对应资产</h2>
      <p>换一个关键词，或清除筛选条件后再试。</p>
      <button type="button" class="portal-button portal-button-quiet" @click="clearFilters">清除筛选</button>
    </div>
    <div v-else class="portal-asset-grid portal-asset-grid-library">
      <RouterLink v-for="asset in items" :key="asset.id" :to="`/assets-public/${asset.id}`" class="portal-asset-card">
        <div class="portal-asset-card-top">
          <span class="portal-type-mark" :class="`is-${asset.type.toLowerCase()}`">{{ typeLabel(asset.type) }}</span>
          <span class="portal-download-count" aria-label="下载次数">↓ {{ asset.downloadCount }}</span>
        </div>
        <h2>{{ asset.name }}</h2>
        <p>{{ asset.summary || '这项资产没有附带摘要，请进入详情查看公开内容。' }}</p>
        <div v-if="asset.tags.length" class="portal-tag-row">
          <span v-for="tag in asset.tags.slice(0, 3)" :key="tag">#{{ tag }}</span>
        </div>
        <div class="portal-asset-card-foot">
          <span>{{ scopeLabel(asset.scope) }}</span>
          <span>{{ formatDate(asset.publishedAt) }}</span>
        </div>
      </RouterLink>
    </div>

    <nav v-if="total > size" class="portal-pagination" aria-label="资产分页">
      <button type="button" class="portal-pagination-button" aria-label="上一页" :disabled="page === 0 || loading" @click="changePage(page - 1)">←</button>
      <span>{{ page + 1 }} / {{ totalPages }}</span>
      <button type="button" class="portal-pagination-button" aria-label="下一页" :disabled="page >= totalPages - 1 || loading" @click="changePage(page + 1)">→</button>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { portalApi } from '../api/portal'
import { normalizePortalFilters } from '../lib/portal-navigation'
import type { AssetSummary, PortalAssetScope, PortalAssetType } from '../types/portal'

const router = useRouter()
const route = useRoute()
const items = ref<AssetSummary[]>([])
const search = ref('')
const type = ref<PortalAssetType | ''>('')
const scope = ref<PortalAssetScope | ''>('')
const page = ref(0)
const total = ref(0)
const size = 12
const loading = ref(true)
const error = ref('')
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
let requestId = 0

watch(() => route.fullPath, () => {
  const filters = normalizePortalFilters(route.query)
  search.value = filters.q
  type.value = filters.type
  scope.value = filters.scope
  page.value = filters.page
  void loadAssets()
}, { immediate: true })
onBeforeUnmount(() => { requestId++ })

async function loadAssets() {
  const currentRequest = ++requestId
  loading.value = true
  error.value = ''
  try {
    const result = await portalApi.listAssets({
      q: search.value.trim() || undefined,
      type: type.value || undefined,
      scope: scope.value || undefined,
      page: page.value,
      size
    })
    if (currentRequest !== requestId) return
    items.value = result.items
    total.value = result.total
  } catch (err) {
    if (currentRequest === requestId) error.value = err instanceof Error ? err.message : '公开目录暂时无法读取'
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}

async function submitFilters() {
  page.value = 0
  await syncQuery()
}

async function changePage(nextPage: number) {
  page.value = nextPage
  await syncQuery()
}

async function syncQuery() {
  const destination = { query: {
    ...(search.value.trim() ? { q: search.value.trim() } : {}),
    ...(type.value ? { type: type.value } : {}),
    ...(scope.value ? { scope: scope.value } : {}),
    ...(page.value ? { page: String(page.value) } : {})
  } }
  if (router.resolve(destination).fullPath === route.fullPath) {
    await loadAssets()
  } else {
    await router.push(destination)
  }
}

async function clearFilters() {
  search.value = ''
  type.value = ''
  scope.value = ''
  page.value = 0
  await syncQuery()
}

function typeLabel(value: AssetSummary['type']) {
  return value === 'RULE' ? 'RULE' : value === 'SKILL' ? 'SKILL' : 'DOC'
}

function scopeLabel(value: AssetSummary['scope']) {
  return value === 'ORGANIZATION' ? '组织级' : value === 'TECH_STACK' ? '技术栈' : '项目级'
}

function formatDate(value: string | null) {
  if (!value) return '尚未记录'
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(value))
}
</script>
