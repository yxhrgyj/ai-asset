<template>
  <div class="portal-page portal-detail-page portal-container">
    <div class="portal-breadcrumbs">
      <RouterLink to="/assets-public">资产库</RouterLink><span aria-hidden="true">/</span><span>{{ detail?.summary.name || '资产详情' }}</span>
    </div>

    <div v-if="loading" class="portal-loading"><span class="portal-loading-dot"></span> 正在读取资产</div>
    <div v-else-if="error" class="portal-empty" role="alert">
      <span class="portal-empty-code">ASSET / NOT AVAILABLE</span>
      <h1>这项资产暂时不可用</h1>
      <p>{{ error }}</p>
      <RouterLink to="/assets-public" class="portal-button portal-button-quiet">返回资产库</RouterLink>
    </div>
    <template v-else-if="detail">
      <section class="portal-detail-heading">
        <div class="portal-detail-heading-copy">
          <div class="portal-detail-kicker">
            <span class="portal-type-mark" :class="`is-${detail.summary.type.toLowerCase()}`">{{ typeLabel(detail.summary.type) }}</span>
            <span>{{ scopeLabel(detail.summary.scope) }}</span>
            <span>VERSION {{ detail.versionNo }}</span>
          </div>
          <h1>{{ detail.summary.name }}</h1>
          <p>{{ detail.summary.summary || '这项资产没有附带摘要。' }}</p>
        </div>
        <div class="portal-detail-actions">
          <label v-if="detail.versions.length > 1" class="portal-version-select">
            <span>查看版本</span>
            <select :value="detail.versionNo" @change="selectVersion">
              <option v-for="version in detail.versions" :key="version.id" :value="version.versionNo">v{{ version.versionNo }}</option>
            </select>
          </label>
          <button type="button" class="portal-button portal-button-primary" :disabled="downloading" @click="downloadAsset">
            {{ downloading ? '准备下载...' : '下载资产' }} <span aria-hidden="true">↓</span>
          </button>
          <RouterLink v-if="detail.canEdit" :to="`/admin/assets/${detail.summary.id}`" class="portal-button portal-button-quiet">编辑</RouterLink>
        </div>
      </section>

      <div v-if="downloadError" class="portal-alert" role="alert">{{ downloadError }}</div>
      <div class="portal-detail-layout">
        <article class="portal-detail-body">
          <div class="portal-content-label">PUBLIC VERSION / v{{ detail.versionNo }}</div>
          <div v-if="renderedBody" class="portal-markdown" v-html="renderedBody"></div>
          <div v-else class="portal-detail-empty">该版本暂无正文内容。</div>
        </article>

        <aside class="portal-detail-aside">
          <section class="portal-side-block">
            <span class="portal-side-label">资产信息</span>
            <dl class="portal-meta-list">
              <div><dt>类型</dt><dd>{{ typeLabel(detail.summary.type) }}</dd></div>
              <div><dt>适用范围</dt><dd>{{ scopeLabel(detail.summary.scope) }}</dd></div>
              <div v-if="detail.summary.techStack"><dt>技术栈</dt><dd>{{ detail.summary.techStack }}</dd></div>
              <div><dt>累计取用</dt><dd>{{ detail.summary.downloadCount }} 次</dd></div>
              <div><dt>发布时间</dt><dd>{{ formatDate(detail.summary.publishedAt) }}</dd></div>
            </dl>
          </section>

          <section v-if="detail.summary.tags.length" class="portal-side-block">
            <span class="portal-side-label">标签</span>
            <div class="portal-tag-row portal-tag-row-detail">
              <span v-for="tag in detail.summary.tags" :key="tag">#{{ tag }}</span>
            </div>
          </section>

          <section class="portal-side-block">
            <span class="portal-side-label">版本记录</span>
            <ol class="portal-version-list">
              <li v-for="version in detail.versions" :key="version.id" :class="{ 'is-current': version.versionNo === detail.versionNo }">
                <button type="button" @click="loadVersion(version.versionNo)">
                  <span>v{{ version.versionNo }}</span>
                  <small>{{ formatDate(version.publishedAt) }}</small>
                </button>
                <p v-if="version.changelog">{{ version.changelog }}</p>
              </li>
            </ol>
          </section>

          <section v-if="detail.files.length" class="portal-side-block">
            <span class="portal-side-label">随附文件</span>
            <ul class="portal-file-list">
              <li v-for="file in detail.files" :key="file.id">
                <span>{{ file.relativePath }}</span><small>{{ formatBytes(file.sizeBytes) }}</small>
              </li>
            </ul>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { assetApi } from '../api/asset'
import { portalApi } from '../api/portal'
import { renderMarkdown } from '../lib/markdown.js'
import { useAuthStore } from '../stores/auth'
import type { AssetDetail, AssetSummary } from '../types/portal'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const detail = ref<AssetDetail | null>(null)
const loading = ref(true)
const error = ref('')
const downloadError = ref('')
const downloading = ref(false)
const renderedBody = computed(() => renderMarkdown(detail.value?.body || ''))

let requestId = 0
watch(() => [route.params.id, route.query.versionNo, auth.user?.id], loadDetail, { immediate: true })
onBeforeUnmount(() => { requestId++ })

async function loadDetail() {
  const currentRequest = ++requestId
  const id = String(route.params.id)
  const versionNo = Number(route.query.versionNo)
  loading.value = true
  error.value = ''
  downloadError.value = ''
  try {
    const result = await portalApi.getAssetDetail(id, Number.isInteger(versionNo) && versionNo > 0 ? versionNo : undefined)
    if (currentRequest === requestId) detail.value = result
  } catch (err) {
    if (currentRequest !== requestId) return
    detail.value = null
    error.value = err instanceof Error ? err.message : '资产不存在或尚未发布'
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}

async function loadVersion(versionNo: number) {
  await router.push({ query: { versionNo: String(versionNo) } })
}

async function selectVersion(event: Event) {
  const target = event.target as HTMLSelectElement
  await loadVersion(Number(target.value))
}

async function downloadAsset() {
  if (!detail.value || downloading.value) return
  if (!auth.user) {
    await router.push({ path: '/login', query: { returnUrl: route.fullPath } })
    return
  }

  downloading.value = true
  downloadError.value = ''
  const downloaded = detail.value
  try {
    const response = await fetch(assetApi.exportAsset(downloaded.summary.id, downloaded.versionNo), { credentials: 'include' })
    if (response.status === 401 || response.status === 403) {
      await auth.fetchMe()
      await router.push({ path: '/login', query: { returnUrl: route.fullPath } })
      return
    }
    if (!response.ok) throw new Error('导出暂时不可用，请稍后重试。')
    const blob = await response.blob()
    const version = downloaded.versions.find(item => item.versionNo === downloaded.versionNo)
    await assetApi.recordDownload(downloaded.summary.id, version?.id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${downloaded.summary.slug}-v${downloaded.versionNo}.zip`
    document.body.append(link)
    link.click()
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    if (detail.value?.summary.id === downloaded.summary.id) detail.value.summary.downloadCount++
  } catch (err) {
    downloadError.value = err instanceof Error ? err.message : '下载准备失败，请稍后重试'
  } finally {
    downloading.value = false
  }
}

function typeLabel(type: AssetSummary['type']) {
  return type === 'RULE' ? '编码规则' : type === 'SKILL' ? '技能包' : '规范文档'
}

function scopeLabel(scope: AssetSummary['scope']) {
  return scope === 'ORGANIZATION' ? '组织级' : scope === 'TECH_STACK' ? '技术栈' : '项目级'
}

function formatDate(value: string | null) {
  if (!value) return '尚未记录'
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' }).format(new Date(value))
}

function formatBytes(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}
</script>
