# AI 资产管理功能说明

## 已实现的功能

### 1. 资产列表页面 (`/assets`)
- ✅ 搜索资产（按名称、摘要搜索）
- ✅ 按类型筛选（RULE/SKILL/DOCUMENT）
- ✅ 按范围筛选（组织级/技术栈/项目级）
- ✅ 卡片式展示，显示图标、名称、标签
- ✅ 分页功能
- ✅ 创建新资产

### 2. 资产详情页面 (`/assets/:id`)

#### 2.1 Markdown 内容编辑
- ✅ 在草稿状态下可编辑 Markdown 内容
- ✅ Markdown 实时渲染预览
- ✅ 添加变更说明（changelog）
- ✅ 保存草稿
- ✅ 发布版本（冻结内容）

#### 2.2 附件管理（新增）
- ✅ 上传附件到当前草稿版本
- ✅ 支持多文件上传
- ✅ 自定义文件相对路径（用于保留目录结构）
  - 示例：`scripts/check.py`、`templates/report.md`
- ✅ 附件列表展示
  - 文件名、文件大小
  - 下载按钮
  - 删除按钮（仅草稿版本可删除）
- ✅ 上传进度显示
- ✅ 已发布版本的附件只读，不可修改

#### 2.3 版本管理
- ✅ 查看版本历史
- ✅ 切换查看不同版本
- ✅ 基于已发布版本创建新草稿
- ✅ 版本状态标签（草稿/已发布/已弃用等）

### 3. 权限控制
- ✅ 只有作者及以上角色可创建资产
- ✅ 只有资产所有者或管理员可编辑
- ✅ 附件只能在草稿版本中添加/删除
- ✅ 已发布版本完全冻结（内容+附件）

## API 端点

### 资产基础操作
- `GET /api/assets` - 列表与搜索
- `GET /api/assets/:id` - 获取详情
- `POST /api/assets` - 创建资产
- `PATCH /api/assets/:id` - 更新元数据
- `PUT /api/assets/:id/draft` - 保存草稿内容
- `POST /api/assets/:id/versions` - 创建新版本
- `POST /api/assets/:id/publish` - 发布版本
- `POST /api/assets/:id/archive` - 归档资产

### 附件操作（新增）
- `POST /api/assets/:assetId/files` - 上传附件
  - 参数：`file` (multipart), `path` (可选，相对路径)
- `GET /api/assets/:assetId/files/:fileId` - 下载附件
- `DELETE /api/assets/:assetId/files/:fileId` - 删除附件

## 典型使用场景

### 场景 1：创建编码规则（RULE）
1. 创建资产，类型选择 "编码规则"
2. 编辑 Markdown 内容，描述规则
3. 上传示例代码文件（如 `examples/good.java`、`examples/bad.java`）
4. 发布版本

### 场景 2：创建技能包（SKILL）
1. 创建资产，类型选择 "技能包"
2. 编辑主文档 `SKILL.md`
3. 上传相关脚本和模板：
   - `scripts/check.py` - 检查脚本
   - `scripts/deploy.sh` - 部署脚本
   - `templates/report.md` - 报告模板
4. 发布版本

### 场景 3：创建规范文档（DOCUMENT）
1. 创建资产，类型选择 "规范文档"
2. 编辑文档内容
3. 上传补充材料（图表、配置示例等）
4. 发布版本

## 技术实现要点

### 前端
- Vue 3 + TypeScript + Vite
- Markdown 渲染：`marked` 库
- 文件上传：`FormData` + `multipart/form-data`
- 相对路径保留：通过 `path` 参数传递

### 后端
- Spring Boot 3.3.5 + Java 21
- 文件存储：抽象的 `FileStorage` 接口
- 内容哈希：SHA-256（用于去重和完整性校验）
- 附件安全：
  - 文件类型白名单验证
  - Content-Disposition: attachment（强制下载）
  - X-Content-Type-Options: nosniff（禁止嗅探）

### 数据库约束
- `asset_versions_single_open_uk` - 同一资产只能有一个开放版本
- `asset_versions_published_needs_hash` - 已发布版本必须有内容哈希
- 附件通过 `asset_version_id` 关联到版本，版本发布后不可修改

## 下一步建议

1. **增强 Markdown 编辑器**
   - 添加预览/编辑分屏模式
   - 代码高亮
   - 图片上传支持

2. **文件拖拽上传**
   - 支持拖拽文件到上传区域
   - 支持拖拽文件夹（保留目录结构）

3. **附件预览**
   - 文本文件在线预览
   - 图片预览
   - PDF 预览

4. **批量操作**
   - 批量下载附件（打包为 zip）
   - 批量删除附件

5. **版本对比**
   - Markdown 内容 diff
   - 附件变更列表

6. **审批流程**
   - PENDING 状态处理
   - 审批人指定
   - 审批意见记录
