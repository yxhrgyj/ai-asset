<template>
  <div class="portal-page portal-home-page">
    <section class="portal-hero portal-container">
      <div class="portal-hero-copy">
        <p class="portal-eyebrow"><span class="portal-eyebrow-line"></span> 数科院 / AI ASSET LIBRARY</p>
        <h1>让好的方法，<em>随时可被复用</em></h1>
        <p class="portal-hero-lede">把经过验证的规则、技能与文档，沉淀成团队每个人都能取用的工作基础。</p>
        <div class="portal-hero-actions">
          <RouterLink to="/assets-public" class="portal-button portal-button-primary">浏览公开资产 <span aria-hidden="true">→</span></RouterLink>
          <RouterLink to="/statistics-public" class="portal-button portal-button-quiet">查看数据台 <span aria-hidden="true">↗</span></RouterLink>
        </div>
      </div>
      <div class="portal-hero-index" aria-label="平台概览">
        <div class="portal-index-mark">AI<span>/</span>LIB</div>
        <p>一套规则，一次沉淀<br />让每次交付都从更好的起点开始。</p>
        <div class="portal-hero-rule"></div>
        <span>PUBLIC CATALOGUE · 2026</span>
      </div>
    </section>

    <section class="portal-container portal-signal-row" aria-label="平台数据摘要">
      <div class="portal-signal">
        <span class="portal-signal-label">已发布资产</span>
        <strong>{{ stats?.totalAssets ?? '—' }}</strong>
      </div>
      <div class="portal-signal">
        <span class="portal-signal-label">累计取用</span>
        <strong>{{ stats ? formatNumber(stats.totalDownloads) : '—' }}</strong>
      </div>
      <div class="portal-signal portal-signal-note">
        <span class="portal-signal-label">公开目录</span>
        <strong>开放浏览</strong>
        <span>登录后即可下载可用版本</span>
      </div>
    </section>

    <section class="portal-container portal-featured-section">
      <div class="portal-section-heading">
        <div>
          <p class="portal-eyebrow">CURATED COLLECTION</p>
          <h2>最近加入目录的资产</h2>
        </div>
        <RouterLink to="/assets-public" class="portal-text-link">查看全部 <span aria-hidden="true">→</span></RouterLink>
      </div>

      <div v-if="error" class="portal-alert" role="alert">{{ error }} <button type="button" @click="loadHome">重试</button></div>
      <div v-else-if="loading" class="portal-inline-state">正在读取目录...</div>
      <div v-else-if="items.length === 0" class="portal-empty portal-empty-compact">
        <span class="portal-empty-code">CATALOGUE / 00</span>
        <p>公开目录正在积累第一批资产。</p>
      </div>
      <div v-else class="portal-asset-grid portal-asset-grid-featured">
        <RouterLink v-for="(asset, index) in items" :key="asset.id" :to="`/assets-public/${asset.id}`" class="portal-asset-card">
          <div class="portal-asset-card-top">
            <span class="portal-card-number">0{{ index + 1 }}</span>
            <span class="portal-type-mark" :class="`is-${asset.type.toLowerCase()}`">{{ typeLabel(asset.type) }}</span>
          </div>
          <h3>{{ asset.name }}</h3>
          <p>{{ asset.summary || '查看这项资产的公开版本与使用说明。' }}</p>
          <div class="portal-asset-card-foot">
            <span>{{ scopeLabel(asset.scope) }}</span>
            <span>{{ formatDate(asset.publishedAt) }}</span>
          </div>
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { portalApi } from '../api/portal'
import type { AssetSummary, Statistics } from '../types/portal'

const items = ref<AssetSummary[]>([])
const stats = ref<Statistics | null>(null)
const loading = ref(true)
const error = ref('')

onMounted(loadHome)

async function loadHome() {
  loading.value = true
  error.value = ''
  try {
    const [assets, statistics] = await Promise.all([
      portalApi.listAssets({ page: 0, size: 6 }),
      portalApi.getStatistics()
    ])
    items.value = assets.items
    stats.value = statistics
  } catch {
    error.value = '公开目录暂时无法读取，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function typeLabel(type: AssetSummary['type']) {
  return type === 'RULE' ? 'RULE' : type === 'SKILL' ? 'SKILL' : 'DOC'
}

function scopeLabel(scope: AssetSummary['scope']) {
  return scope === 'ORGANIZATION' ? '组织级' : scope === 'TECH_STACK' ? '技术栈' : '项目级'
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatDate(value: string | null) {
  if (!value) return '尚未记录'
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(value))
}
</script>
