<template>
  <div class="portal-page portal-home-page">
    <section class="portal-search-band">
      <div class="portal-container portal-hero">
        <img class="portal-hero-art" src="/brand/portal-documents.png" alt="" width="560" height="430" />
        <div class="portal-hero-copy">
          <h1><span>让好的方法，</span><span>随时可被复用</span></h1>
          <p class="portal-hero-lede">把经过验证的规则、技能与文档，沉淀成团队每个人都能取用的工作基础。</p>
          <form class="portal-home-search" role="search" @submit.prevent="searchAssets">
            <Search :size="21" aria-hidden="true" />
            <label class="sr-only" for="portal-home-query">搜索资产</label>
            <input id="portal-home-query" v-model="search" type="search" placeholder="搜索规则、技能、文档" />
            <button type="submit">搜索</button>
          </form>
          <div class="portal-category-filter" role="group" aria-label="资产分类">
            <button v-for="category in categories" :key="category.value" type="button"
              :aria-pressed="selectedType === category.value" @click="selectType(category.value)">
              {{ category.label }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <section class="portal-container portal-featured-section" :aria-busy="loading" aria-labelledby="recent-assets-title">
      <div class="portal-section-heading">
        <div class="portal-section-title">
          <h2 id="recent-assets-title">最近发布</h2>
          <span class="portal-result-count" aria-live="polite">{{ loading || error ? '—' : total }} 项资产</span>
        </div>
        <RouterLink :to="catalogueLocation" class="portal-text-link">查看全部 <ArrowRight :size="17" aria-hidden="true" /></RouterLink>
      </div>

      <div v-if="error" class="portal-alert" role="alert">{{ error }} <button type="button" @click="loadHome">重试</button></div>
      <div v-else-if="loading" class="portal-inline-state" role="status"><span class="portal-loading-dot"></span>正在读取目录...</div>
      <div v-else-if="items.length === 0" class="portal-empty portal-empty-compact" role="status">
        <FileSearch :size="32" aria-hidden="true" />
        <h3>{{ selectedType ? `暂无已发布的${selectedCategoryLabel}` : '暂无已发布资产' }}</h3>
        <button v-if="selectedType" type="button" class="portal-button portal-button-quiet" @click="selectType('')">查看全部资产</button>
      </div>
      <div v-else class="portal-asset-grid portal-asset-grid-featured">
        <PortalAssetCard v-for="asset in items" :key="asset.id" :asset="asset" />
      </div>
    </section>
    <PortalDownloadRanking :type="selectedType || undefined" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ArrowRight, FileSearch, Search } from '@lucide/vue'
import PortalAssetCard from '../components/PortalAssetCard.vue'
import PortalDownloadRanking from '../components/PortalDownloadRanking.vue'
import { portalApi } from '../api/portal'
import { normalizePortalFilters } from '../lib/portal-navigation'
import type { AssetSummary, PortalAssetType } from '../types/portal'

const router = useRouter()
const route = useRoute()
const categories: Array<{ value: PortalAssetType | ''; label: string }> = [
  { value: '', label: '全部' }, { value: 'RULE', label: '规则' },
  { value: 'SKILL', label: '技能' }, { value: 'DOCUMENT', label: '文档' }
]
const selectedType = computed(() => normalizePortalFilters(route.query).type)
const selectedCategoryLabel = computed(() => categories.find(category => category.value === selectedType.value)?.label)
const catalogueLocation = computed(() => ({
  path: '/assets-public', query: selectedType.value ? { type: selectedType.value } : {}
}))
const search = ref('')
const items = ref<AssetSummary[]>([])
const total = ref(0)
const loading = ref(true)
const error = ref('')
let requestId = 0

watch(selectedType, loadHome, { immediate: true })
onBeforeUnmount(() => { requestId++ })

async function loadHome() {
  const currentRequest = ++requestId
  loading.value = true
  error.value = ''
  try {
    const result = await portalApi.listAssets({ type: selectedType.value || undefined, page: 0, size: 4 })
    if (currentRequest !== requestId) return
    items.value = result.items
    total.value = result.total
  } catch {
    if (currentRequest === requestId) error.value = '公开目录暂时无法读取，请稍后重试。'
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}

function selectType(type: PortalAssetType | '') {
  return router.push({ path: '/', query: type ? { type } : {} })
}

function searchAssets() {
  return router.push({
    ...catalogueLocation.value,
    query: { ...catalogueLocation.value.query, ...(search.value.trim() ? { q: search.value.trim() } : {}) }
  })
}
</script>
