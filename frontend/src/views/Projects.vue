<template>
  <MainLayout>
    <div class="projects-page">
    <div class="page-header">
      <h1 class="page-title">项目管理</h1>
      <button class="btn-primary" @click="showCreateDialog = true">
        <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        创建项目
      </button>
    </div>

    <div v-if="loading" class="loading-state">正在加载项目...</div>

    <div v-else-if="error" class="error-message" role="alert">
      <span>{{ error }}</span>
      <button class="btn-text" @click="loadPage">重试</button>
    </div>

    <div v-else-if="projects.length === 0" class="empty-state">
      <p>还没有项目</p>
    </div>

    <div v-else class="projects-grid">
      <div v-for="project in projects" :key="project.id" class="project-card">
        <div class="card-header">
          <h3 class="project-name">{{ project.name }}</h3>
          <span class="project-slug">{{ project.slug }}</span>
        </div>

        <p v-if="project.description" class="project-description">
          {{ project.description }}
        </p>

        <div v-if="project.techStacks.length > 0" class="tech-stacks">
          <span v-for="tech in project.techStacks" :key="tech" class="tech-tag">
            {{ tech }}
          </span>
        </div>

        <div class="card-actions">
          <button class="btn-secondary btn-sm" @click="viewProject(project)">
            查看详情
          </button>
          <button class="btn-text btn-sm" @click="editProject(project)">
            编辑
          </button>
        </div>
      </div>
    </div>

    <!-- 创建项目对话框 -->
    <div v-if="showCreateDialog" class="dialog-overlay" @click.self="showCreateDialog = false">
      <div class="dialog">
        <div class="dialog-header">
          <h2>创建项目</h2>
          <button class="btn-close" @click="showCreateDialog = false">×</button>
        </div>

        <div class="dialog-body">
          <div class="form-group">
            <label>项目名称 *</label>
            <input v-model="createForm.name" type="text" class="form-control" />
          </div>

          <div class="form-group">
            <label>项目标识 *</label>
            <input v-model="createForm.slug" type="text" class="form-control" placeholder="英文标识,如 my-project" />
          </div>

          <div class="form-group">
            <label>项目描述</label>
            <textarea v-model="createForm.description" class="form-control" rows="3"></textarea>
          </div>

          <div class="form-group">
            <label>所属团队</label>
            <select v-model="createForm.teamId" class="form-control">
              <option :value="null">个人项目</option>
              <option v-for="team in teams" :key="team.id" :value="team.id">
                {{ team.name }}
              </option>
            </select>
          </div>

          <div class="form-group">
            <label>技术栈（用于匹配规则）</label>
            <input v-model="techStackInput" type="text" class="form-control"
                   placeholder="输入后按回车添加,如 Java, Spring Boot"
                   @keydown.enter.prevent="addTechStack" />
            <div v-if="createForm.techStacks.length > 0" class="tech-stacks-edit">
              <span v-for="(tech, idx) in createForm.techStacks" :key="idx" class="tech-tag">
                {{ tech }}
                <button @click="removeTechStack(idx)" class="tag-remove">×</button>
              </span>
            </div>
          </div>
        </div>

        <div class="dialog-footer">
          <button class="btn-secondary" @click="showCreateDialog = false">取消</button>
          <button class="btn-primary" @click="handleCreate" :disabled="!canCreate || saving">
            {{ saving ? '创建中...' : '创建' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 编辑项目对话框 -->
    <div v-if="showEditDialog" class="dialog-overlay" @click.self="showEditDialog = false">
      <div class="dialog">
        <div class="dialog-header">
          <h2>编辑项目</h2>
          <button class="btn-close" @click="showEditDialog = false">×</button>
        </div>

        <div class="dialog-body">
          <div class="form-group">
            <label>项目名称</label>
            <input v-model="editForm.name" type="text" class="form-control" />
          </div>

          <div class="form-group">
            <label>项目描述</label>
            <textarea v-model="editForm.description" class="form-control" rows="3"></textarea>
          </div>

          <div class="form-group">
            <label>技术栈</label>
            <input v-model="techStackInput" type="text" class="form-control"
                   placeholder="输入后按回车添加"
                   @keydown.enter.prevent="addTechStackEdit" />
            <div v-if="editForm.techStacks && editForm.techStacks.length > 0" class="tech-stacks-edit">
              <span v-for="(tech, idx) in editForm.techStacks" :key="idx" class="tech-tag">
                {{ tech }}
                <button @click="removeTechStackEdit(idx)" class="tag-remove">×</button>
              </span>
            </div>
          </div>
        </div>

        <div class="dialog-footer">
          <button class="btn-danger" @click="handleArchive">归档项目</button>
          <div class="dialog-footer-right">
            <button class="btn-secondary" @click="showEditDialog = false">取消</button>
            <button class="btn-primary" @click="handleUpdate" :disabled="saving">
              {{ saving ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </div>
    </div>
  </MainLayout>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { projectsApi, type Project, type CreateProjectRequest, type UpdateProjectRequest } from '../api/projects'
import { teamsApi, type Team } from '../api/teams'
import MainLayout from '../components/MainLayout.vue'
import { useDialog } from '../composables/useDialog'

const router = useRouter()
const { alert, confirm } = useDialog()

const projects = ref<Project[]>([])
const teams = ref<Team[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')

const showCreateDialog = ref(false)
const showEditDialog = ref(false)

const createForm = ref<CreateProjectRequest>({
  name: '',
  slug: '',
  description: '',
  teamId: null,
  techStacks: []
})

const editForm = ref<UpdateProjectRequest & { id?: string }>({
  name: '',
  description: '',
  techStacks: []
})

const techStackInput = ref('')
const canCreate = computed(() => Boolean(createForm.value.name.trim() && createForm.value.slug.trim()))

onMounted(loadPage)

async function loadPage() {
  loading.value = true
  error.value = ''
  try {
    const [nextProjects, nextTeams] = await Promise.all([
      projectsApi.list(),
      teamsApi.list()
    ])
    projects.value = nextProjects
    teams.value = nextTeams
  } catch (err) {
    error.value = getErrorMessage(err, '加载项目页面失败')
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  loading.value = true
  error.value = ''
  try {
    projects.value = await projectsApi.list()
  } catch (err) {
    error.value = getErrorMessage(err, '加载项目失败')
  } finally {
    loading.value = false
  }
}

function addTechStack() {
  const value = techStackInput.value.trim()
  if (value && !createForm.value.techStacks.includes(value)) {
    createForm.value.techStacks.push(value)
    techStackInput.value = ''
  }
}

function removeTechStack(idx: number) {
  createForm.value.techStacks.splice(idx, 1)
}

function addTechStackEdit() {
  const value = techStackInput.value.trim()
  if (value && editForm.value.techStacks && !editForm.value.techStacks.includes(value)) {
    editForm.value.techStacks.push(value)
    techStackInput.value = ''
  }
}

function removeTechStackEdit(idx: number) {
  editForm.value.techStacks!.splice(idx, 1)
}

async function handleCreate() {
  if (!canCreate.value || saving.value) return
  saving.value = true
  try {
    await projectsApi.create({
      ...createForm.value,
      name: createForm.value.name.trim(),
      slug: createForm.value.slug.trim()
    })
    showCreateDialog.value = false
    createForm.value = {
      name: '',
      slug: '',
      description: '',
      teamId: null,
      techStacks: []
    }
    await loadProjects()
    await alert({ message: '项目已创建', type: 'success' })
  } catch (err) {
    await alert({ message: getErrorMessage(err, '创建失败'), type: 'error' })
  } finally {
    saving.value = false
  }
}

function editProject(project: Project) {
  editForm.value = {
    id: project.id,
    name: project.name,
    description: project.description || '',
    techStacks: [...project.techStacks]
  }
  showEditDialog.value = true
}

async function handleUpdate() {
  if (!editForm.value.id || saving.value) return

  saving.value = true
  try {
    await projectsApi.update(editForm.value.id, {
      name: editForm.value.name,
      description: editForm.value.description,
      techStacks: editForm.value.techStacks
    })
    showEditDialog.value = false
    await loadProjects()
    await alert({ message: '项目已更新', type: 'success' })
  } catch (err) {
    await alert({ message: getErrorMessage(err, '更新失败'), type: 'error' })
  } finally {
    saving.value = false
  }
}

async function handleArchive() {
  if (!editForm.value.id) return

  const confirmed = await confirm({
    title: '归档项目',
    message: '确定要归档该项目吗？归档后它将不再出现在项目列表中。',
    type: 'warning'
  })
  if (!confirmed) return

  saving.value = true
  try {
    await projectsApi.archive(editForm.value.id)
    showEditDialog.value = false
    await loadProjects()
    await alert({ message: '项目已归档', type: 'success' })
  } catch (err) {
    await alert({ message: getErrorMessage(err, '归档失败'), type: 'error' })
  } finally {
    saving.value = false
  }
}

function viewProject(project: Project) {
  router.push(`/projects/${project.id}`)
}

function getErrorMessage(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback
}
</script>

<style scoped>
.projects-page {
  max-width: 1200px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sp-24);
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.btn-icon {
  width: 16px;
  height: 16px;
  margin-right: var(--sp-8);
}

.empty-state {
  text-align: center;
  padding: var(--sp-48);
  color: var(--color-text-secondary);
}

.loading-state {
  text-align: center;
  padding: var(--sp-48);
  color: var(--color-text-secondary);
}

.error-message {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-16);
  padding: var(--sp-16);
  color: var(--color-error);
  background: var(--color-error-bg);
  border-left: 3px solid var(--color-error);
  border-radius: var(--radius-8);
}

.projects-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--sp-16);
}

.project-card {
  background: var(--color-bg-1);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-12);
  padding: var(--sp-20);
  display: flex;
  flex-direction: column;
  gap: var(--sp-12);
}

.card-header {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.project-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.project-slug {
  font-size: 12px;
  color: var(--color-text-tertiary);
  font-family: 'Monaco', 'Consolas', monospace;
}

.project-description {
  font-size: 14px;
  color: var(--color-text-secondary);
  line-height: 1.5;
  margin: 0;
}

.tech-stacks {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-8);
}

.tech-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-4);
  padding: var(--sp-4) var(--sp-12);
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}

.card-actions {
  display: flex;
  gap: var(--sp-8);
  margin-top: auto;
  padding-top: var(--sp-12);
  border-top: 1px solid var(--color-border);
}

.btn-sm {
  padding: var(--sp-6) var(--sp-12);
  font-size: 14px;
}

.tech-stacks-edit {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-8);
  margin-top: var(--sp-8);
}

.tech-stacks-edit .tech-tag {
  padding-right: var(--sp-4);
}

.tag-remove {
  background: none;
  border: none;
  color: inherit;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  padding: 0 var(--sp-4);
}

.tag-remove:hover {
  opacity: 0.7;
}

.dialog-footer-right {
  display: flex;
  gap: var(--sp-12);
  margin-left: auto;
}
</style>
