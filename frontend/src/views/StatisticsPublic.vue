<template>
  <div class="portal-page portal-stats-page portal-container">
    <section class="portal-page-intro">
      <div>
        <p class="portal-eyebrow"><span class="portal-eyebrow-line"></span> CATALOGUE SIGNALS</p>
        <h1>数据台</h1>
        <p>公开目录的当前切片：哪些类型正在积累，哪些方法正在被团队反复取用。</p>
      </div>
      <span class="portal-page-index">LIVE / PUBLIC</span>
    </section>

    <div v-if="error" class="portal-alert" role="alert">{{ error }} <button type="button" @click="loadStats">重试</button></div>
    <div v-if="loading" class="portal-loading"><span class="portal-loading-dot"></span> 正在汇总目录数据</div>
    <template v-else-if="stats">
      <section class="portal-stat-grid">
        <div class="portal-stat-card"><span>已发布资产</span><strong>{{ stats.totalAssets }}</strong><small>NON-ARCHIVED / PUBLISHED</small></div>
        <div class="portal-stat-card"><span>累计取用</span><strong>{{ stats.totalDownloads }}</strong><small>RECORDED DOWNLOADS</small></div>
        <div class="portal-stat-card portal-stat-card-accent"><span>覆盖类型</span><strong>{{ activeTypes }}</strong><small>RULE · SKILL · DOCUMENT</small></div>
      </section>
      <section class="portal-chart-grid">
        <article class="portal-chart-panel"><div class="portal-chart-heading"><span class="portal-side-label">TYPE MIX</span><strong>资产类型</strong></div><div ref="typeChartEl" class="portal-chart" aria-label="资产类型分布图"></div></article>
        <article class="portal-chart-panel"><div class="portal-chart-heading"><span class="portal-side-label">SCOPE MIX</span><strong>适用范围</strong></div><div ref="scopeChartEl" class="portal-chart" aria-label="资产范围分布图"></div></article>
      </section>
      <section class="portal-stats-note"><span class="portal-empty-code">READING THE SIGNAL</span><p>公开数据只统计已发布且未归档的资产。下载动作需要登录，因此取用数代表经过身份确认的实际导出。</p></section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { init, use, type EChartsType, type ComposeOption } from 'echarts/core'
import { PieChart, type PieSeriesOption } from 'echarts/charts'
import { LegendComponent, TooltipComponent, AriaComponent, type LegendComponentOption, type TooltipComponentOption } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { portalApi } from '../api/portal'
import type { Statistics } from '../types/portal'

use([PieChart, LegendComponent, TooltipComponent, AriaComponent, CanvasRenderer])

const stats = ref<Statistics | null>(null)
const loading = ref(true)
const error = ref('')
const typeChartEl = ref<HTMLElement | null>(null)
const scopeChartEl = ref<HTMLElement | null>(null)
let typeChart: EChartsType | null = null
let scopeChart: EChartsType | null = null
const activeTypes = computed(() => stats.value ? [stats.value.typeBreakdown.rule, stats.value.typeBreakdown.skill, stats.value.typeBreakdown.document].filter(value => value > 0).length : 0)

onMounted(() => {
  void loadStats()
  window.addEventListener('resize', resizeCharts)
})
onBeforeUnmount(() => {
  typeChart?.dispose()
  scopeChart?.dispose()
  window.removeEventListener('resize', resizeCharts)
})

async function loadStats() {
  loading.value = true
  error.value = ''
  try {
    stats.value = await portalApi.getStatistics()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '数据台暂时无法读取'
  } finally {
    loading.value = false
  }
  await nextTick()
  renderCharts()
}

function renderCharts() {
  if (!stats.value || !typeChartEl.value || !scopeChartEl.value) return
  typeChart?.dispose()
  scopeChart?.dispose()
  typeChart = init(typeChartEl.value)
  scopeChart = init(scopeChartEl.value)
  typeChart.setOption(chartOption([
    { name: '编码规则', value: stats.value.typeBreakdown.rule },
    { name: '技能包', value: stats.value.typeBreakdown.skill },
    { name: '规范文档', value: stats.value.typeBreakdown.document }
  ], ['#165DFF', '#00B42A', '#722ED1']))
  scopeChart.setOption(chartOption([
    { name: '组织级', value: stats.value.scopeBreakdown.organization },
    { name: '技术栈', value: stats.value.scopeBreakdown.techStack },
    { name: '项目级', value: stats.value.scopeBreakdown.project }
  ], ['#00B42A', '#165DFF', '#722ED1']))
}

function chartOption(data: Array<{ name: string; value: number }>, colors: string[]): ComposeOption<PieSeriesOption | LegendComponentOption | TooltipComponentOption> {
  return {
    color: colors,
    tooltip: { trigger: 'item', renderMode: 'richText' },
    legend: { bottom: 0, textStyle: { color: '#475B78' }, formatter: name => `${name}  ${data.find(item => item.name === name)?.value ?? 0}` },
    series: [{ type: 'pie', radius: ['44%', '70%'], center: ['50%', '43%'], stillShowZeroSum: false, itemStyle: { borderColor: '#FFFFFF', borderWidth: 4 }, label: { show: false }, data }]
  }
}

function resizeCharts() {
  typeChart?.resize()
  scopeChart?.resize()
}
</script>
