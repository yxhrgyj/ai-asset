<template>
  <section class="portal-container portal-ranking-section" :aria-busy="loading" aria-labelledby="download-ranking-title">
    <div class="portal-section-heading">
      <div class="portal-section-title"><h2 id="download-ranking-title">下载排行</h2></div>
      <span class="ranking-description">按累计下载次数 · 前 5 名</span>
    </div>
    <div v-if="error" class="portal-alert" role="alert">
      {{ error }} <button type="button" @click="loadRanking">重试</button>
    </div>
    <div v-else-if="loading" class="portal-inline-state" role="status">正在读取下载排行...</div>
    <div v-else-if="!items.length" class="portal-empty portal-empty-compact" role="status">
      <Download :size="28" aria-hidden="true" />
      <h3>{{ type ? '该分类暂无下载记录' : '暂无下载记录' }}</h3>
    </div>
    <ol v-else class="ranking-list">
      <li v-for="(asset, index) in items" :key="asset.id">
        <RouterLink :to="`/assets-public/${asset.id}`" class="ranking-link">
          <span class="ranking-number" :class="{ 'ranking-leading': index < 3 }">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="ranking-copy"><strong>{{ asset.name }}</strong><small>{{ typeLabels[asset.type] }}<span v-if="asset.category"> · {{ asset.category }}</span></small></span>
          <span class="ranking-count"><Download :size="15" aria-hidden="true" />{{ asset.downloadCount.toLocaleString('zh-CN') }}<span class="sr-only"> 次下载</span></span>
          <ArrowRight :size="17" class="ranking-arrow" aria-hidden="true" />
        </RouterLink>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight, Download } from '@lucide/vue'
import { portalApi } from '../api/portal'
import type { AssetSummary, PortalAssetType } from '../types/portal'

const props = defineProps<{ type?: PortalAssetType }>()
const typeLabels = { RULE: '规则', SKILL: '技能', DOCUMENT: '文档' }
const items = ref<AssetSummary[]>([])
const loading = ref(true)
const error = ref('')
let requestId = 0

watch(() => props.type, loadRanking, { immediate: true })
onBeforeUnmount(() => { requestId++ })

async function loadRanking() {
  const current = ++requestId
  loading.value = true
  error.value = ''
  try {
    const result = await portalApi.getDownloadRanking(props.type)
    if (current === requestId) items.value = result
  } catch {
    if (current === requestId) error.value = '下载排行暂时无法读取，请稍后重试。'
  } finally {
    if (current === requestId) loading.value = false
  }
}
</script>

<style scoped>
.portal-ranking-section { padding-top: 4px; padding-bottom: 64px; }
.ranking-description { color: var(--portal-muted); font-size: 13px; }
.ranking-list { margin: 0; padding: 0; list-style: none; border-top: 1px solid var(--portal-line); border-bottom: 1px solid var(--portal-line); background: var(--portal-surface); }
.ranking-list li + li { border-top: 1px solid var(--portal-line); }
.ranking-link { display: flex; align-items: center; gap: 20px; padding: 20px 24px; color: var(--portal-text); text-decoration: none; transition: background 150ms ease; }
.ranking-link:hover { background: var(--portal-hover); }
.ranking-link:focus-visible { outline: 2px solid var(--portal-signal); outline-offset: -3px; }
.ranking-number { width: 28px; flex-shrink: 0; color: var(--portal-muted); font-size: 20px; font-variant-numeric: tabular-nums; }
.ranking-leading { color: var(--portal-signal); font-weight: 600; }
.ranking-copy { display: grid; gap: 5px; min-width: 0; flex: 1; }
.ranking-copy strong { font-size: 16px; font-weight: 500; overflow-wrap: anywhere; }
.ranking-copy small { color: var(--portal-muted); font-size: 12px; overflow-wrap: anywhere; }
.ranking-count { display: inline-flex; align-items: center; gap: 7px; color: var(--portal-signal); font-size: 14px; white-space: nowrap; font-variant-numeric: tabular-nums; }
.ranking-arrow { flex-shrink: 0; color: var(--portal-muted); }
@media (max-width: 680px) {
  .portal-ranking-section { padding-bottom: 40px; }
  .portal-section-heading { align-items: flex-start; gap: 10px; flex-wrap: wrap; }
  .ranking-description { font-size: 12px; }
  .ranking-link { gap: 12px; padding: 16px 14px; }
  .ranking-number { width: 23px; font-size: 18px; }
  .ranking-copy strong { font-size: 14px; }
  .ranking-count { font-size: 12px; gap: 5px; }
  .ranking-arrow { display: none; }
}
</style>
