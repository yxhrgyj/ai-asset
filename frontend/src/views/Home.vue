<template>
  <MainLayout>
    <div class="home">
      <h1 class="page-title">欢迎使用数科院AI资产管理平台</h1>

      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon" style="background: var(--color-line-3)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
            </svg>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ assetCount }}</div>
            <div class="stat-label">资产总数</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon" style="background: var(--color-line-4)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ teamCount }}</div>
            <div class="stat-label">团队数量</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon" style="background: var(--color-line-1)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
            </svg>
          </div>
          <div class="stat-content">
            <div class="stat-value">0</div>
            <div class="stat-label">待审批</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon" style="background: var(--color-line-5)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
            </svg>
          </div>
          <div class="stat-content">
            <div class="stat-value">0</div>
            <div class="stat-label">项目绑定</div>
          </div>
        </div>
      </div>

      <div class="quick-actions">
        <h2 class="section-title">快速入口</h2>
        <div class="actions-grid">
          <router-link to="/assets" class="action-card">
            <div class="action-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
              </svg>
            </div>
            <div class="action-title">资产库</div>
            <div class="action-desc">浏览和管理 AI 资产</div>
          </router-link>

          <router-link to="/teams" class="action-card">
            <div class="action-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
            </div>
            <div class="action-title">团队管理</div>
            <div class="action-desc">创建和管理团队</div>
          </router-link>

          <router-link v-if="auth.user?.role === 'ADMIN'" to="/users" class="action-card">
            <div class="action-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="8.5" cy="7" r="4"/>
                <polyline points="17 11 19 13 23 9"/>
              </svg>
            </div>
            <div class="action-title">用户管理</div>
            <div class="action-desc">管理用户和权限</div>
          </router-link>
        </div>
      </div>
    </div>
  </MainLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth'
import MainLayout from '../components/MainLayout.vue'
import { teamApi } from '../api/team'
import { assetApi } from '../api/asset'

const auth = useAuthStore()
const assetCount = ref(0)
const teamCount = ref(0)

onMounted(async () => {
  try {
    const [assets, teams] = await Promise.all([
      assetApi.list({ page: 0, size: 1 }),
      teamApi.list()
    ])
    assetCount.value = assets.total
    teamCount.value = teams.length
  } catch (err) {
    console.error('Failed to load stats:', err)
  }
})
</script>

<style scoped>
.home {
  max-width: 1200px;
}

.page-title {
  font-size: 32px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--sp-8);
}

.page-subtitle {
  font-size: 16px;
  color: var(--color-text-secondary);
  margin-bottom: var(--sp-24);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--sp-20);
  margin-bottom: var(--sp-24);
}

.stat-card {
  background: var(--color-bg-1);
  border-radius: var(--radius-12);
  padding: var(--sp-20);
  display: flex;
  align-items: center;
  gap: var(--sp-16);
  box-shadow: var(--shadow-card);
  transition: all 0.2s;
}

.stat-card:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-2px);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-12);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon svg {
  width: 24px;
  height: 24px;
  color: white;
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 30px;
  font-weight: 700;
  color: var(--color-text-primary);
  font-family: 'Barlow', -apple-system, sans-serif;
  line-height: 1;
  margin-bottom: var(--sp-4);
}

.stat-label {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.quick-actions {
  margin-top: var(--sp-24);
}

.section-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--sp-16);
}

.actions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--sp-16);
}

.action-card {
  background: var(--color-bg-1);
  border-radius: var(--radius-12);
  padding: var(--sp-24);
  text-decoration: none;
  box-shadow: var(--shadow-card);
  transition: all 0.2s;
}

.action-card:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-2px);
}

.action-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-12);
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--sp-16);
}

.action-icon svg {
  width: 24px;
  height: 24px;
  color: white;
}

.action-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--sp-4);
}

.action-desc {
  font-size: 14px;
  color: var(--color-text-secondary);
}
</style>
