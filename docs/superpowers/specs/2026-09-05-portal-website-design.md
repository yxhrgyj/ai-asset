# 数科院AI资产管理官网设计文档

**日期**: 2026-09-05  
**状态**: 待审核  
**作者**: Claude Code

## 1. 项目概述

### 1.1 目标

为数科院AI资产管理平台创建一个面向公众的官网（Portal Website），提供：
- 美观、大气的资产展示界面
- 便捷的资产查看和下载入口
- 直观的数据统计看板
- 无缝衔接到管理后台的入口

### 1.2 核心原则

- **开放性**: 未登录用户可浏览内容，降低访问门槛
- **引导性**: 通过下载需求引导用户登录，提高用户转化
- **一体化**: 官网与管理后台共享认证体系，无缝切换
- **视觉冲击**: 深色调、科技感设计，突出AI和数据属性

### 1.3 用户分层

| 用户类型 | 权限范围 |
|---------|---------|
| **未登录访客** | 浏览资产列表和详情、查看Markdown内容 |
| **已登录用户** | 上述权限 + 下载附件 + 记录统计 |
| **作者/管理员** | 上述权限 + 编辑资产 + 进入管理后台 |

---

## 2. 架构设计

### 2.1 路由结构

```
/                          → 官网首页 (PortalLayout)
/portal/assets             → 资产展示页 (PortalLayout)
/portal/assets/:id         → 资产详情页 (PortalLayout)
/portal/statistics         → 数据统计看板 (PortalLayout)
/portal/about              → 关于/帮助页 (PortalLayout)

/admin                     → 管理后台首页 (AdminLayout)
/admin/assets              → 资产管理 (AdminLayout)
/admin/teams               → 团队管理 (AdminLayout)
/admin/users               → 用户管理 (AdminLayout)
/admin/approvals           → 审批管理 (AdminLayout)
/admin/projects            → 项目管理 (AdminLayout)
/admin/statistics          → 后台统计 (AdminLayout)

/login                     → 登录页 (无布局)
/change-password           → 修改密码 (无布局)
```

### 2.2 布局系统

#### PortalLayout (官网布局)
```
┌────────────────────────────────────────┐
│  Header: Logo + 导航 + 搜索 + 管理入口    │
├────────────────────────────────────────┤
│                                        │
│          Page Content                  │
│                                        │
├────────────────────────────────────────┤
│  Footer: 版权 + 链接 + 联系方式         │
└────────────────────────────────────────┘
```

**Header 导航项**:
- 首页
- 资产库
- 数据统计
- 关于平台
- 搜索框
- 管理中心按钮（始终显示）
- 用户菜单（登录后显示）

#### AdminLayout (管理后台布局)
```
保持现有的 MainLayout 不变
左侧边栏 + 顶部栏 + 内容区
```

### 2.3 技术栈补充

| 类别 | 技术选型 | 说明 |
|-----|---------|------|
| 图表库 | **ECharts 5.x** | 功能强大、中文文档完善、视觉效果好 |
| 现有技术 | Vue 3 + TypeScript + Vite + Pinia | 保持不变 |

---

## 3. 页面设计

### 3.1 首页 (`/`)

#### 布局结构
```
[Hero Section]
  - 大标题: "数科院 AI 资产管理平台"
  - 副标题: "协作、治理、复用 —— 让AI编码规则和技能包触手可及"
  - CTA按钮: "探索资产" + "管理中心"
  - 背景: 深色渐变 + 几何纹理

[Quick Stats] (4个统计卡片横排)
  - 资产总数
  - 下载总量
  - 团队数量
  - 本周新增

[Hot Assets] (热门资产卡片 - 横向滚动或网格)
  - TOP 6 热门资产
  - 显示类型图标、名称、下载量、简介
  - 点击跳转详情

[Asset Categories] (分类快速入口)
  - 三列卡片: 编码规则 / 技能包 / 规范文档
  - 每个卡片显示该类型资产数量
  - 点击跳转到资产展示页并筛选

[Footer]
  - 关于平台 / 使用指南 / 联系我们
  - 版权信息
```

#### 视觉风格
- **背景**: 深色调 (#0A0D12 → #0F131C 渐变)
- **主色**: 青绿色 (#38BDF8) 作为强调色
- **卡片**: 半透明深色背景 + 微光边框
- **动画**: Hero区文字渐入、统计数字滚动、卡片悬停效果

### 3.2 资产展示页 (`/portal/assets`)

#### 功能需求
- 继承现有 Assets.vue 的核心功能
- 改用 PortalLayout
- 视觉风格适配官网
- 移除"创建资产"按钮（仅管理后台可创建）

#### 布局结构
```
[Page Header]
  - 标题: "资产库"
  - 副标题: "探索组织沉淀的AI编码规则与技能包"

[Search & Filter Bar]
  - 搜索框（全文搜索）
  - 类型筛选: 全部/编码规则/技能包/规范文档
  - 范围筛选: 全部/组织级/技术栈/项目级
  - 标签筛选（下拉多选）
  - 排序: 最新/最热/下载最多

[Asset Grid]
  - 卡片网格布局
  - 每个卡片显示: 类型图标、名称、简介、标签、下载量、版本号
  - 悬停效果: 卡片上浮 + 阴影加深

[Pagination]
  - 页码 + 上一页/下一页
```

#### 数据筛选逻辑
- 仅展示 `status=PUBLISHED` 且 `archived=false` 的资产
- 后端新增接口 `/api/portal/assets`

### 3.3 资产详情页 (`/portal/assets/:id`)

#### 功能需求
- 基于现有 AssetDetail.vue 改造
- 未登录用户: 可查看内容,下载按钮显示"登录后下载"
- 已登录用户: 可下载附件
- 作者/管理员: 显示"编辑"按钮,点击跳转到 `/admin/assets/:id`

#### 布局结构
```
[Asset Header]
  - 面包屑导航: 首页 > 资产库 > [资产名称]
  - 资产类型徽章
  - 资产名称
  - 简介
  - 元信息: 范围/分类/标签/作者/团队/创建时间
  - 操作按钮区:
    * 下载按钮 (根据登录状态变化)
    * 编辑按钮 (仅作者/管理员可见)

[Version Selector]
  - 当前查看版本号
  - 版本下拉选择器
  - 版本发布时间 + 变更说明

[Content Tabs]
  Tab 1: 内容预览
    - Markdown 渲染
    - 美化的代码高亮
  
  Tab 2: 附件列表
    - 文件名 + 大小 + 下载按钮
    - 未登录: 显示"登录后下载"提示
  
  Tab 3: 版本历史
    - 时间线展示所有已发布版本
    - 版本号 + 发布时间 + 变更说明
    - 点击切换版本

[Stat Bar]
  - 下载次数
  - 版本数量
  - 最后更新时间
```

#### 权限控制
```typescript
// 下载按钮逻辑
if (!auth.user) {
  显示 "登录后下载" + 点击跳转登录页
} else {
  显示 "下载" + 点击触发下载并记录统计
}

// 编辑按钮逻辑
if (auth.user && (isOwner || isAdmin)) {
  显示 "编辑" 按钮
  点击跳转到 /admin/assets/:id 编辑页
}
```

### 3.4 数据统计看板 (`/portal/statistics`)

#### 布局结构
```
[Page Header]
  - 标题: "数据统计"
  - 时间范围选择器: 近7天/近30天/全部

[Section 1: 基础统计卡片] (4列)
  - 资产总数 (大数字 + 趋势箭头)
  - 下载总量 (大数字 + 趋势箭头)
  - 团队数量
  - 用户数量

[Section 2: 资产分类统计] (饼图 + 柱状图)
  左侧: 按类型分布饼图 (RULE/SKILL/DOCUMENT)
  右侧: 按范围分布柱状图 (ORGANIZATION/TECH_STACK/PROJECT)

[Section 3: 热门资产排行] (表格 + 条形图)
  TOP 10 资产
  列: 排名 / 资产名称 / 类型 / 下载量 / 下载量可视化条

[Section 4: 趋势分析] (折线图)
  - 资产创建趋势 (按月统计)
  - 下载量趋势 (按日/周统计)
  - 双Y轴折线图

[Section 5: 活跃团队排行] (卡片列表)
  TOP 5 团队
  显示: 团队名称 / 资产数量 / 总下载量
```

#### ECharts 图表配置
- **主题**: 使用深色主题,配色与官网一致
- **交互**: 支持图例切换、tooltip悬停、缩放
- **响应式**: 图表随容器自适应

#### 后端API需求
```
GET /api/portal/statistics/overview
  → 基础统计数字

GET /api/portal/statistics/assets-by-type
  → 按类型分组统计

GET /api/portal/statistics/assets-by-scope
  → 按范围分组统计

GET /api/portal/statistics/hot-assets?limit=10
  → 热门资产排行

GET /api/portal/statistics/trends?range=30d
  → 趋势数据

GET /api/portal/statistics/active-teams?limit=5
  → 活跃团队排行
```

### 3.5 关于/帮助页 (`/portal/about`)

#### 布局结构
```
[Section 1: 平台介绍]
  - 什么是AI资产管理平台
  - 核心功能
  - 使用场景

[Section 2: 快速开始]
  - 如何浏览资产
  - 如何下载资产
  - 如何贡献资产 (链接到管理后台)

[Section 3: FAQ]
  - 常见问题手风琴列表
  Q: 谁可以查看资产？
  Q: 如何下载附件？
  Q: 如何贡献新资产？
  Q: 资产审批流程是什么？
  等等...

[Section 4: 联系我们]
  - 技术支持联系方式
  - 反馈渠道
```

---

## 4. 组件设计

### 4.1 新增组件列表

| 组件名 | 路径 | 用途 |
|-------|------|------|
| `PortalLayout.vue` | `components/` | 官网布局容器 |
| `PortalHeader.vue` | `components/portal/` | 官网顶部导航 |
| `PortalFooter.vue` | `components/portal/` | 官网页脚 |
| `HeroSection.vue` | `components/portal/` | 首页Hero区 |
| `StatCard.vue` | `components/portal/` | 统计卡片 |
| `AssetCard.vue` | `components/portal/` | 资产卡片(官网风格) |
| `CategoryCard.vue` | `components/portal/` | 分类入口卡片 |
| `ChartCard.vue` | `components/portal/` | 图表容器卡片 |
| `TimelineVersion.vue` | `components/portal/` | 版本历史时间线 |

### 4.2 组件复用策略

| 功能 | 管理后台组件 | 官网处理方式 |
|-----|-------------|-------------|
| 资产列表 | `Assets.vue` | 新建 `PortalAssets.vue`,复用逻辑但重写UI |
| 资产详情 | `AssetDetail.vue` | 新建 `PortalAssetDetail.vue`,复用API但添加权限控制 |
| 统计页 | `Statistics.vue` | 新建 `PortalStatistics.vue`,完全独立实现 |
| 登录 | `Login.vue` | 复用,但添加 `returnUrl` 参数支持登录后返回 |

---

## 5. API设计

### 5.1 新增后端接口

#### 公开资产列表
```java
GET /api/portal/assets
Query Params:
  - q: string (搜索关键词)
  - type: AssetType (RULE/SKILL/DOCUMENT)
  - scope: AssetScope
  - tag: string
  - sort: string (latest/popular/downloads)
  - page: int
  - size: int

Response:
{
  "items": [AssetDto.Summary],
  "total": number,
  "page": number,
  "size": number
}

说明: 仅返回 status=PUBLISHED && archived=false 的资产
```

#### 公开资产详情
```java
GET /api/portal/assets/{id}
Query Params:
  - versionNo: int (可选,默认最新已发布版本)

Response: AssetDto.Detail {
  asset: AssetDto.Summary,
  version: AssetDto.VersionInfo,
  body: string,  // Markdown内容
  files: AssetDto.FileInfo[],
  versions: AssetDto.VersionInfo[],  // 所有已发布版本
  canEdit: boolean  // 当前用户是否可编辑
}

说明: 
- 未登录用户也可访问
- body 始终返回
- files 返回列表,但下载需要认证
- canEdit 未登录时为 false
```

#### 统计接口 (6个)
```java
GET /api/portal/statistics/overview
Response: {
  totalAssets: number,
  totalDownloads: number,
  totalTeams: number,
  totalUsers: number,
  weeklyNewAssets: number,
  assetsTrend: "up" | "down" | "stable"
}

GET /api/portal/statistics/assets-by-type
Response: {
  RULE: number,
  SKILL: number,
  DOCUMENT: number
}

GET /api/portal/statistics/assets-by-scope
Response: {
  ORGANIZATION: number,
  TECH_STACK: number,
  PROJECT: number
}

GET /api/portal/statistics/hot-assets?limit=10
Response: Array<{
  id: UUID,
  name: string,
  type: AssetType,
  downloadCount: number,
  summary: string
}>

GET /api/portal/statistics/trends?range=7d|30d|all
Response: {
  assetCreation: Array<{date: string, count: number}>,
  downloads: Array<{date: string, count: number}>
}

GET /api/portal/statistics/active-teams?limit=5
Response: Array<{
  teamId: UUID,
  teamName: string,
  assetCount: number,
  totalDownloads: number
}>
```

### 5.2 现有接口调整

#### 下载记录接口
```java
POST /api/assets/{id}/download
Body: { versionId?: UUID }

调整:
- 添加 @AuthenticationRequired 注解
- 未登录返回 401
- 前端拦截器处理 401 跳转登录页
```

---

## 6. 前端实现细节

### 6.1 路由配置

```typescript
// frontend/src/router/index.ts
const router = createRouter({
  history: createWebHistory(),
  routes: [
    // 官网路由 (PortalLayout)
    {
      path: '/',
      name: 'portal-home',
      component: () => import('../views/portal/Home.vue'),
      meta: { layout: 'portal', public: true }
    },
    {
      path: '/portal/assets',
      name: 'portal-assets',
      component: () => import('../views/portal/Assets.vue'),
      meta: { layout: 'portal', public: true }
    },
    {
      path: '/portal/assets/:id',
      name: 'portal-asset-detail',
      component: () => import('../views/portal/AssetDetail.vue'),
      meta: { layout: 'portal', public: true }
    },
    {
      path: '/portal/statistics',
      name: 'portal-statistics',
      component: () => import('../views/portal/Statistics.vue'),
      meta: { layout: 'portal', public: true }
    },
    {
      path: '/portal/about',
      name: 'portal-about',
      component: () => import('../views/portal/About.vue'),
      meta: { layout: 'portal', public: true }
    },

    // 管理后台路由 (AdminLayout)
    {
      path: '/admin',
      redirect: '/admin/home'
    },
    {
      path: '/admin/home',
      name: 'admin-home',
      component: () => import('../views/Home.vue'),
      meta: { layout: 'admin' }
    },
    {
      path: '/admin/assets',
      name: 'admin-assets',
      component: () => import('../views/Assets.vue'),
      meta: { layout: 'admin' }
    },
    // ... 其他管理页面同样添加 /admin 前缀

    // 无布局路由
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/Login.vue'),
      meta: { public: true, layout: 'none' }
    }
  ]
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const auth = useAuthStore()
  
  // 公开页面直接放行
  if (to.meta.public) {
    next()
    return
  }

  // 非公开页面需要登录
  if (!auth.user) {
    await auth.fetchMe()
  }

  if (!auth.user) {
    // 保存目标URL,登录后返回
    next({ name: 'login', query: { returnUrl: to.fullPath } })
  } else {
    // 角色权限检查 (adminOnly, approverOnly等)
    next()
  }
})
```

### 6.2 App.vue 布局选择逻辑

```vue
<!-- frontend/src/App.vue -->
<template>
  <component :is="layoutComponent">
    <router-view />
  </component>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import PortalLayout from './components/PortalLayout.vue'
import AdminLayout from './components/MainLayout.vue'  // 重命名导入

const route = useRoute()

const layoutComponent = computed(() => {
  const layout = route.meta.layout as string | undefined
  
  if (layout === 'portal') return PortalLayout
  if (layout === 'admin') return AdminLayout
  return 'div'  // 无布局(登录页等)
})
</script>
```

### 6.3 API封装

```typescript
// frontend/src/api/portal.ts
export const portalApi = {
  // 资产列表
  async listAssets(params: {
    q?: string
    type?: AssetType
    scope?: AssetScope
    tag?: string
    sort?: 'latest' | 'popular' | 'downloads'
    page?: number
    size?: number
  }) {
    const query = new URLSearchParams()
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined) query.append(key, String(value))
    })
    const res = await fetch(`/api/portal/assets?${query}`, {
      credentials: 'include'
    })
    if (!res.ok) throw new Error('加载失败')
    return res.json()
  },

  // 资产详情
  async getAsset(id: string, versionNo?: number) {
    const url = versionNo 
      ? `/api/portal/assets/${id}?versionNo=${versionNo}`
      : `/api/portal/assets/${id}`
    const res = await fetch(url, { credentials: 'include' })
    if (!res.ok) throw new Error('加载失败')
    return res.json()
  },

  // 统计数据
  statistics: {
    async overview() {
      const res = await fetch('/api/portal/statistics/overview')
      if (!res.ok) throw new Error('加载失败')
      return res.json()
    },
    
    async assetsByType() {
      const res = await fetch('/api/portal/statistics/assets-by-type')
      if (!res.ok) throw new Error('加载失败')
      return res.json()
    },
    
    async assetsByScope() {
      const res = await fetch('/api/portal/statistics/assets-by-scope')
      if (!res.ok) throw new Error('加载失败')
      return res.json()
    },
    
    async hotAssets(limit = 10) {
      const res = await fetch(`/api/portal/statistics/hot-assets?limit=${limit}`)
      if (!res.ok) throw new Error('加载失败')
      return res.json()
    },
    
    async trends(range: '7d' | '30d' | 'all' = '30d') {
      const res = await fetch(`/api/portal/statistics/trends?range=${range}`)
      if (!res.ok) throw new Error('加载失败')
      return res.json()
    },
    
    async activeTeams(limit = 5) {
      const res = await fetch(`/api/portal/statistics/active-teams?limit=${limit}`)
      if (!res.ok) throw new Error('加载失败')
      return res.json()
    }
  }
}
```

### 6.4 登录跳转处理

```vue
<!-- frontend/src/views/Login.vue -->
<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const handleLogin = async () => {
  try {
    await auth.login(username.value, password.value)
    
    // 登录成功后跳转
    const returnUrl = route.query.returnUrl as string
    if (returnUrl) {
      router.push(returnUrl)
    } else {
      // 默认跳转到管理后台首页
      router.push('/admin/home')
    }
  } catch (err) {
    error.value = err.message
  }
}
</script>
```

---

## 7. 视觉设计规范

### 7.1 色彩系统

#### 背景梯度 (5层深度)
```css
--portal-bg-0: #05070C;  /* 最深 - 页面底色 */
--portal-bg-1: #0A0D12;  /* 深层 - 大区域背景 */
--portal-bg-2: #0F131C;  /* 中层 - 卡片背景 */
--portal-bg-3: #161D2B;  /* 浅层 - 悬停态 */
--portal-bg-4: #1E2636;  /* 最浅 - 输入框/选择器 */
```

#### 主题色
```css
--portal-primary: #38BDF8;      /* 青色 - 主按钮/链接 */
--portal-primary-hover: #7DD3FC; /* 悬停态 */
--portal-accent: #6EE7B7;        /* 绿色 - 成功/强调 */
--portal-warning: #FBBF24;       /* 黄色 - 警告 */
--portal-error: #F87171;         /* 红色 - 错误 */
```

#### 文本色
```css
--portal-text-primary: #F1F5F9;    /* 主文本 */
--portal-text-secondary: #CBD5E1;  /* 次要文本 */
--portal-text-tertiary: #94A3B8;   /* 辅助文本 */
--portal-text-disabled: #64748B;   /* 禁用文本 */
```

### 7.2 间距系统
```css
--sp-4: 4px;
--sp-8: 8px;
--sp-12: 12px;
--sp-16: 16px;
--sp-20: 20px;
--sp-24: 24px;
--sp-32: 32px;
--sp-48: 48px;
--sp-64: 64px;
```

### 7.3 圆角
```css
--radius-4: 4px;
--radius-8: 8px;
--radius-12: 12px;
--radius-16: 16px;
--radius-full: 999px;  /* 胶囊按钮 */
```

### 7.4 阴影
```css
--shadow-sm: 0 2px 8px rgba(0, 0, 0, 0.4);
--shadow-md: 0 4px 16px rgba(0, 0, 0, 0.5);
--shadow-lg: 0 8px 32px rgba(0, 0, 0, 0.6);
--shadow-glow: 0 0 20px rgba(56, 189, 248, 0.3);  /* 发光效果 */
```

### 7.5 字体
```css
--font-display: clamp(2rem, 5vw, 3.5rem);  /* Hero标题 */
--font-h1: clamp(1.75rem, 3.5vw, 2.5rem);
--font-h2: clamp(1.5rem, 3vw, 2rem);
--font-h3: clamp(1.25rem, 2.5vw, 1.75rem);
--font-body: 1rem;
--font-small: 0.875rem;
--font-xs: 0.75rem;
```

### 7.6 动画
```css
/* 过渡时长 */
--transition-fast: 150ms;
--transition-normal: 250ms;
--transition-slow: 350ms;

/* 缓动函数 */
--ease-out-cubic: cubic-bezier(0.33, 1, 0.68, 1);
--ease-in-out-cubic: cubic-bezier(0.65, 0, 0.35, 1);
```

---

## 8. 后端实现要点

### 8.1 Controller结构

```java
// 新建 PortalController.java
@RestController
@RequestMapping("/api/portal")
public class PortalController {
    
    private final AssetRepository assets;
    private final StatisticsService statsService;
    
    // 资产列表 (公开)
    @GetMapping("/assets")
    public Map<String, Object> listAssets(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Asset.Type type,
        @RequestParam(required = false) Asset.Scope scope,
        @RequestParam(required = false) String tag,
        @RequestParam(required = false) String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        // 硬编码筛选条件: archived=false, 仅已发布版本
        // 添加排序逻辑 (sort参数)
    }
    
    // 资产详情 (公开)
    @GetMapping("/assets/{id}")
    public AssetDto.Detail getAsset(
        @PathVariable UUID id,
        @RequestParam(required = false) Integer versionNo,
        CurrentUser current  // 可能为匿名用户
    ) {
        // 查询资产
        // 仅返回已发布版本
        // canEdit 根据 current.user 判断
    }
}

// 新建 PortalStatisticsController.java
@RestController
@RequestMapping("/api/portal/statistics")
public class PortalStatisticsController {
    
    @GetMapping("/overview")
    public Map<String, Object> overview() { ... }
    
    @GetMapping("/assets-by-type")
    public Map<Asset.Type, Long> assetsByType() { ... }
    
    @GetMapping("/assets-by-scope")
    public Map<Asset.Scope, Long> assetsByScope() { ... }
    
    @GetMapping("/hot-assets")
    public List<AssetDto.Summary> hotAssets(@RequestParam(defaultValue = "10") int limit) {
        // 按 downloadCount 降序
    }
    
    @GetMapping("/trends")
    public Map<String, Object> trends(@RequestParam String range) {
        // 按日期聚合统计
    }
    
    @GetMapping("/active-teams")
    public List<TeamStatsDto> activeTeams(@RequestParam(defaultValue = "5") int limit) {
        // JOIN assets + teams, 按团队聚合
    }
}
```

### 8.2 SecurityConfig 调整

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            // 公开路径
            .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
            .requestMatchers("/api/portal/**").permitAll()  // 新增
            
            // 需要认证的路径
            .requestMatchers("/api/assets/*/download").authenticated()  // 下载需要登录
            .anyRequest().authenticated()
        )
        // ...
    return http.build();
}
```

### 8.3 数据库查询优化

```java
// AssetRepository.java - 新增查询方法

@Query("""
    SELECT a FROM Asset a
    WHERE a.archived = false
    AND EXISTS (
        SELECT 1 FROM AssetVersion v 
        WHERE v.assetId = a.id 
        AND v.status = 'PUBLISHED'
    )
    AND (:q IS NULL OR 
         LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%')) OR
         LOWER(a.summary) LIKE LOWER(CONCAT('%', :q, '%'))
    )
    AND (:type IS NULL OR a.type = :type)
    AND (:scope IS NULL OR a.scope = :scope)
    ORDER BY
        CASE WHEN :sort = 'downloads' THEN a.downloadCount END DESC,
        CASE WHEN :sort = 'popular' THEN a.downloadCount END DESC,
        a.createdAt DESC
""")
Page<Asset> findPublishedAssets(
    @Param("q") String q,
    @Param("type") Asset.Type type,
    @Param("scope") Asset.Scope scope,
    @Param("sort") String sort,
    Pageable pageable
);
```

---

## 9. 实施计划

### 阶段一: 基础架构 (2-3天)

**任务列表**:
1. ✅ 路由重组
   - 所有管理页面路由添加 `/admin` 前缀
   - 新建官网路由
   - 更新路由守卫逻辑

2. ✅ 布局系统
   - MainLayout 重命名为 AdminLayout
   - 新建 PortalLayout 组件
   - 新建 PortalHeader 和 PortalFooter
   - App.vue 布局选择逻辑

3. ✅ 后端接口
   - 创建 PortalController 和 PortalStatisticsController
   - 实现 6个统计接口
   - 调整 SecurityConfig
   - 编写单元测试

4. ✅ API封装
   - frontend/src/api/portal.ts
   - 前端接口封装

### 阶段二: 核心页面 (4-5天)

**任务列表**:
1. ✅ 首页 `/`
   - HeroSection 组件
   - StatCard 组件
   - 热门资产区域
   - 分类入口卡片

2. ✅ 资产展示页 `/portal/assets`
   - PortalAssets.vue
   - AssetCard 组件(官网风格)
   - 搜索和筛选UI

3. ✅ 资产详情页 `/portal/assets/:id`
   - PortalAssetDetail.vue
   - 权限控制逻辑
   - 下载按钮状态处理
   - 版本历史时间线

4. ✅ 登录流程优化
   - returnUrl 参数支持
   - 401拦截跳转

### 阶段三: 增强功能 (3-4天)

**任务列表**:
1. ✅ 数据统计页 `/portal/statistics`
   - 集成 ECharts
   - 6个图表实现
   - 响应式布局

2. ✅ 关于/帮助页 `/portal/about`
   - 静态内容编写
   - FAQ手风琴组件

3. ✅ 视觉优化
   - 动画效果
   - 响应式适配
   - 加载状态
   - 空状态设计

4. ✅ 测试与修复
   - 端到端测试
   - Bug修复
   - 性能优化

### 验收标准

#### 功能完整性
- [ ] 未登录用户可以浏览所有官网页面
- [ ] 未登录用户点击下载时提示登录
- [ ] 已登录用户可以下载附件并记录统计
- [ ] 管理中心按钮始终显示,未登录点击跳转登录
- [ ] 官网和管理后台无缝切换,认证状态共享
- [ ] 所有统计数据正确展示
- [ ] 图表交互流畅,数据准确

#### 视觉质量
- [ ] 深色调设计美观大气
- [ ] 动画流畅自然
- [ ] 响应式布局完美适配移动端
- [ ] 加载状态友好
- [ ] 空状态有引导

#### 性能指标
- [ ] 首页加载 < 2s
- [ ] 资产列表加载 < 1s
- [ ] 图表渲染 < 500ms
- [ ] 无明显卡顿

---

## 10. 风险与注意事项

### 10.1 技术风险

| 风险项 | 影响 | 缓解措施 |
|-------|------|---------|
| 路由重构可能破坏现有功能 | 高 | 充分测试所有管理页面,保持URL向后兼容 |
| ECharts 打包体积大 | 中 | 按需引入,只导入使用的图表类型 |
| 统计查询性能问题 | 中 | 添加数据库索引,考虑缓存热点数据 |
| 未登录用户并发量大 | 低 | 公开接口添加限流,考虑CDN缓存 |

### 10.2 开发注意事项

1. **渐进式迁移**: 先完成官网,再逐步将管理页面路由改为 `/admin` 前缀,避免一次性大改
2. **组件复用**: 能复用的组件尽量复用,但不要为了复用而过度抽象
3. **测试先行**: 路由守卫逻辑复杂,需要编写完整的测试用例
4. **性能监控**: 上线后监控统计接口的查询性能,及时优化慢查询
5. **移动端优先**: 官网可能有移动端访问需求,响应式设计要到位

---

## 11. 后续迭代方向

v1.0 完成后,可以考虑的增强功能:

1. **搜索增强**
   - 搜索建议/自动完成
   - 高级搜索 (多条件组合)
   - 搜索历史

2. **个性化推荐**
   - 基于用户角色推荐资产
   - 基于下载历史推荐相似资产

3. **社交功能**
   - 资产评论
   - 资产评分
   - 收藏/点赞

4. **订阅通知**
   - 关注资产更新通知
   - 新资产发布通知

5. **多语言支持**
   - 中英文切换
   - i18n国际化

---

## 12. 附录

### 12.1 参考资料

- **设计灵感**: GitHub, GitLab, Stripe官网
- **图表库文档**: https://echarts.apache.org/zh/index.html
- **Vue Router文档**: https://router.vuejs.org/
- **Spring Security文档**: https://spring.io/projects/spring-security

### 12.2 术语表

| 术语 | 说明 |
|-----|------|
| Portal | 官网,面向公众的展示页面 |
| Admin | 管理后台,需要权限的管理界面 |
| PortalLayout | 官网布局组件 |
| AdminLayout | 管理后台布局组件(原MainLayout) |
| 已发布资产 | status=PUBLISHED 且 archived=false 的资产 |
| 混合模式 | 未登录可浏览,登录后可下载的访问模式 |

---

**文档版本**: v1.0  
**最后更新**: 2026-09-05  
**状态**: 待用户审核
