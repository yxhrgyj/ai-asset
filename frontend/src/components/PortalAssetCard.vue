<template>
  <RouterLink :to="`/assets-public/${asset.id}`" class="portal-asset-card">
    <div class="portal-asset-card-body">
      <span class="portal-asset-icon" :class="`is-${iconTone}`" aria-hidden="true">
        <component :is="assetIcon" :size="30" :stroke-width="1.8" />
      </span>
      <div class="portal-asset-copy">
        <component :is="headingTag">{{ asset.name }}</component>
        <p>{{ asset.summary || '查看这项资产的公开版本与使用说明。' }}</p>
        <div v-if="asset.tags.length" class="portal-tag-row">
          <span v-for="tag in asset.tags.slice(0, 3)" :key="tag">{{ tag }}</span>
        </div>
      </div>
    </div>
    <div class="portal-asset-card-foot">
      <span>{{ scopeLabel }}<span class="portal-meta-separator" aria-hidden="true">·</span>{{ typeLabel }}</span>
      <time v-if="updatedAt" :datetime="updatedAt">更新于 {{ formattedDate }}</time>
      <span v-else>更新时间未记录</span>
    </div>
  </RouterLink>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { CodeXml, FileText, Package } from '@lucide/vue'
import type { AssetSummary } from '../types/portal'

const props = withDefaults(defineProps<{ asset: AssetSummary; headingTag?: 'h2' | 'h3' }>(), { headingTag: 'h3' })
const scopeLabel = computed(() => ({ ORGANIZATION: '组织级', TECH_STACK: '技术栈', PROJECT: '项目级' })[props.asset.scope])
const typeLabel = computed(() => ({ RULE: '规则', SKILL: '技能', DOCUMENT: '文档' })[props.asset.type])
const assetIcon = computed(() => props.asset.type === 'SKILL' ? Package : props.asset.scope === 'TECH_STACK' ? CodeXml : FileText)
const iconTone = computed(() => {
  if (props.asset.scope === 'PROJECT') return 'violet'
  if (props.asset.scope === 'TECH_STACK') return /vue|typescript|javascript/i.test(props.asset.techStack || '') ? 'green' : 'orange'
  return props.asset.type === 'SKILL' ? 'green' : 'blue'
})
const updatedAt = computed(() => {
  const value = props.asset.updatedAt || props.asset.publishedAt
  return value && Number.isFinite(Date.parse(value)) ? value : null
})
const formattedDate = computed(() => updatedAt.value
  ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(updatedAt.value))
  : '')
</script>
