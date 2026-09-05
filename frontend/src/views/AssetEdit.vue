<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import {
  getAsset, createAsset, updateAssetMeta, saveDraft,
  uploadFile, deleteFile, fileUrl,
  ASSET_TYPES, SCOPES
} from '../stores/assets.js'

const route = useRoute()
const router = useRouter()

// 三种进入方式：新建、改元信息、编辑正文。同一个视图，字段显隐不同。
const isNew = computed(() => route.name === 'asset-new')
const metaOnly = computed(() => route.query.meta === '1')

const form = ref({
  type: 'PROMPT',
  name: '',
  slug: '',
  summary: '',
  category: '',
  tagsText: '',
  scope: 'ORGANIZATION',
  techStack: ''
})

const body = ref('')
const changelog = ref('')
const files = ref([])
const versionStatus = ref(null)

const loading = ref(!isNew.value)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const uploading = ref('')
const fileInput = ref(null)
const dirInput = ref(null)

const needsTechStack = computed(() => form.value.scope === 'TECH_STACK')
const editable = computed(() => isNew.value || versionStatus.value === 'DRAFT')

async function load() {
  const { data, error: err, unauthorized } = await getAsset(route.params.id)
  loading.value = false

  if (unauthorized) {
    router.push('/login')
    return
  }
  if (err) {
    error.value = err
    return
  }
  if (!data.canEdit) {
    error.value = '你没有修改这个资产的权限'
    return
  }

  const a = data.asset
  form.value = {
    type: a.type,
    name: a.name,
    slug: a.slug,
    summary: a.summary ?? '',
    category: a.category ?? '',
    tagsText: (a.tags ?? []).join('、'),
    scope: a.scope,
    techStack: a.techStack ?? ''
  }
  body.value = data.body ?? ''
  changelog.value = data.currentVersion?.changelog ?? ''
  versionStatus.value = data.currentVersion?.status ?? null
  files.value = data.files ?? []
}

// 标签允许用中文顿号或逗号分隔——让人按习惯输入，前端做归一化。
function parseTags(text) {
  return text.split(/[、,，\s]+/).map((s) => s.trim()).filter(Boolean)
}

function metaPayload() {
  return {
    type: form.value.type,
    name: form.value.name,
    slug: form.value.slug || undefined,
    summary: form.value.summary || null,
    category: form.value.category || null,
    tags: parseTags(form.value.tagsText),
    scope: form.value.scope,
    techStack: needsTechStack.value ? form.value.techStack : null
  }
}

async function submit() {
  error.value = ''
  notice.value = ''

  if (!form.value.name.trim()) {
    error.value = '请填写名称'
    return
  }
  if (needsTechStack.value && !form.value.techStack.trim()) {
    error.value = '范围为技术栈时必须填写技术栈'
    return
  }

  saving.value = true

  if (isNew.value) {
    const { data, error: err } = await createAsset(metaPayload())
    if (err) {
      saving.value = false
      error.value = err
      return
    }
    // 新建后立刻存一次正文，否则用户填的内容会丢。
    if (body.value.trim()) {
      const draft = await saveDraft(data.id, body.value, changelog.value || null)
      if (draft.error) {
        saving.value = false
        error.value = `资产已创建，但正文保存失败：${draft.error}`
        return
      }
    }
    saving.value = false
    router.push(`/assets/${data.id}`)
    return
  }

  const meta = await updateAssetMeta(route.params.id, metaPayload())
  if (meta.error) {
    saving.value = false
    error.value = meta.error
    return
  }

  if (!metaOnly.value && editable.value) {
    const draft = await saveDraft(route.params.id, body.value, changelog.value || null)
    if (draft.error) {
      saving.value = false
      error.value = draft.error
      return
    }
  }

  saving.value = false
  notice.value = '已保存'
}

/**
 * 逐个上传，不并发。
 *
 * 并发发 20 个请求在本地看着更快，但每个请求都要写库和落盘，一旦中间失败
 * 就不知道成了哪几个。串行的代价是慢一点，换来的是失败时能准确报出是哪个
 * 文件、且前面的已经稳稳入库。
 */
async function pickFiles(event) {
  const picked = Array.from(event.target.files ?? [])
  if (!picked.length) return

  error.value = ''
  const failed = []

  for (const [i, file] of picked.entries()) {
    uploading.value = picked.length > 1
      ? `上传中 ${i + 1}/${picked.length}…`
      : '上传中…'

    const { data, error: err } = await uploadFile(route.params.id, file)
    if (err) {
      failed.push(`${file.webkitRelativePath || file.name}：${err}`)
      continue
    }
    files.value = [...files.value, data]
  }

  uploading.value = ''
  // 清空 input，否则同一批文件再选一次不触发 change。
  if (fileInput.value) fileInput.value.value = ''
  if (dirInput.value) dirInput.value.value = ''

  if (failed.length) {
    error.value = failed.length === picked.length
      ? failed.join('；')
      : `${picked.length - failed.length} 个已上传，${failed.length} 个失败 —— ${failed.join('；')}`
  }
}

async function removeFile(f) {
  if (!confirm(`删除附件 ${f.relativePath}？`)) return
  const { error: err } = await deleteFile(route.params.id, f.id)
  if (err) {
    error.value = err
    return
  }
  files.value = files.value.filter((x) => x.id !== f.id)
}

onMounted(() => { if (!isNew.value) load() })
</script>

<template>
  <AppShell>
    <div class="bar">
      <h2>{{ isNew ? '新建资产' : metaOnly ? '修改元信息' : '编辑草稿' }}</h2>
      <RouterLink :to="isNew ? '/' : `/assets/${route.params.id}`" class="btn">
        取消
      </RouterLink>
    </div>

    <p v-if="loading" class="dim">加载中…</p>

    <form v-else class="form" @submit.prevent="submit">
      <div class="grid">
        <label class="field">
          <span>类型</span>
          <select v-model="form.type" class="control">
            <option v-for="t in ASSET_TYPES" :key="t.value" :value="t.value">
              {{ t.label }}
            </option>
          </select>
        </label>

        <label class="field">
          <span>范围</span>
          <select v-model="form.scope" class="control">
            <option v-for="s in SCOPES" :key="s.value" :value="s.value">
              {{ s.label }}
            </option>
          </select>
        </label>

        <label class="field span2">
          <span>名称</span>
          <input v-model="form.name" class="control" required maxlength="200">
        </label>

        <label v-if="isNew" class="field span2">
          <span>标识（slug）</span>
          <input
            v-model="form.slug"
            class="control"
            placeholder="留空则从名称生成；中文名称必须手填"
            maxlength="80"
          >
          <small class="dim">用于链接，创建后不可修改。</small>
        </label>

        <label v-if="needsTechStack" class="field span2">
          <span>技术栈</span>
          <input v-model="form.techStack" class="control" placeholder="如 Spring Boot、Vue 3">
        </label>

        <label class="field span2">
          <span>摘要</span>
          <input
            v-model="form.summary"
            class="control"
            placeholder="一句话说明这个资产解决什么问题"
            maxlength="500"
          >
        </label>

        <label class="field">
          <span>分类</span>
          <input v-model="form.category" class="control" maxlength="100">
        </label>

        <label class="field">
          <span>标签</span>
          <input v-model="form.tagsText" class="control" placeholder="用顿号或逗号分隔">
        </label>
      </div>

      <template v-if="!metaOnly">
        <div class="body-head">
          <span class="label">正文（Markdown）</span>
        </div>

        <p v-if="!editable" class="dim small">
          当前版本状态为 {{ versionStatus }}，正文不可编辑。改动元信息仍可保存。
        </p>

        <MdEditor
          v-model="body"
          language="zh-CN"
          :disabled="!editable"
          :toolbars="[
            'bold', 'underline', 'italic', 'strikeThrough',
            '-',
            'title', 'sub', 'sup', 'quote', 'unorderedList', 'orderedList',
            '-',
            'codeRow', 'code', 'link', 'image', 'table',
            '-',
            'revoke', 'next',
            '=',
            'pageFullscreen', 'fullscreen', 'preview', 'catalog'
          ]"
          :preview="editable"
          :style="{ height: '500px' }"
        />

        <label class="field">
          <span>本版变更说明</span>
          <input
            v-model="changelog"
            class="control"
            placeholder="这一版改了什么，便于他人判断是否需要更新本地副本"
            maxlength="500"
          >
        </label>
      </template>

      <section v-if="!isNew && !metaOnly" class="files">
        <div class="files-head">
          <h3>附件</h3>
          <button
            type="button"
            class="btn"
            :disabled="uploading !== '' || !editable"
            @click="fileInput?.click()"
          >{{ uploading || '添加文件' }}</button>
          <button
            type="button"
            class="btn"
            :disabled="uploading !== '' || !editable"
            @click="dirInput?.click()"
          >添加整个目录</button>
          <input
            ref="fileInput"
            type="file"
            multiple
            class="hidden-input"
            @change="pickFiles"
          >
          <!-- webkitdirectory 在 Chrome/Edge/Firefox/Safari 都可用，非标准但一致。 -->
          <input
            ref="dirInput"
            type="file"
            multiple
            webkitdirectory
            class="hidden-input"
            @change="pickFiles"
          >
        </div>
        <p class="dim small">
          支持 Markdown、脚本（py / sh / ps1 / js …）、源码、配置、图片与 PDF，单个不超过 10 MB。
          选目录会保留目录结构，最多 4 层。不收 exe / dll / jar 等编译产物。
        </p>
        <ul v-if="files.length" class="file-list">
          <li v-for="f in files" :key="f.id">
            <a :href="fileUrl(route.params.id, f.id)">{{ f.relativePath }}</a>
            <button
              type="button"
              class="btn btn-danger"
              :disabled="!editable"
              @click="removeFile(f)"
            >删除</button>
          </li>
        </ul>
      </section>

      <p v-if="error" class="error-text" role="alert">{{ error }}</p>
      <p v-if="notice" class="notice" role="status">{{ notice }}</p>

      <div class="submit">
        <button type="submit" class="btn btn-primary" :disabled="saving">
          {{ saving ? '保存中…' : isNew ? '创建' : '保存' }}
        </button>
        <span v-if="!isNew && !metaOnly" class="dim small">
          保存后回到详情页可发布。
        </span>
      </div>
    </form>
  </AppShell>
</template>

<style scoped>
.bar {
  display: flex;
  gap: var(--spacing-md);
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-lg);
}

h2 {
  margin: 0;
  font-size: 1.125rem;
}

h3 {
  margin: 0;
  font-size: 1rem;
}

.form {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-md);
}

.span2 {
  grid-column: 1 / -1;
}

@media (max-width: 40rem) {
  .grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.body-head {
  display: flex;
  gap: var(--spacing-md);
  align-items: center;
  justify-content: space-between;
  margin-top: var(--spacing-xs);
  margin-bottom: var(--spacing-md);
}

.label {
  font-size: 0.875rem;
  color: var(--text-dim);
}

.files {
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--surface-3);
}

.files-head {
  display: flex;
  gap: var(--spacing-md);
  align-items: center;
}

.hidden-input {
  display: none;
}

.files ul {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  margin: var(--spacing-sm) 0 0;
  padding: 0;
  list-style: none;
}

.files li {
  display: flex;
  gap: var(--spacing-sm);
  align-items: center;
}

.files a {
  color: var(--accent);
}

.dim {
  color: var(--text-dim);
}

.small {
  font-size: 0.8125rem;
}

.notice {
  margin: 0;
  font-size: 0.875rem;
  color: #6EE7B7;
}

.submit {
  display: flex;
  gap: var(--spacing-md);
  align-items: center;
  padding-top: var(--spacing-xs);
}
</style>

