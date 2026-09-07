# 项目进度：公开门户实现与验收

最后更新：2026-09-07（Asia/Shanghai）

## 本轮门户状态

- 实现位于隔离工作区 `E:/Objects/ai-asset-platform-portal`，分支 `codex/portal-website`。
- 已实现公开首页、资产目录与详情、数据台、关于和帮助页面；管理界面位于 `/admin/*`，旧地址保持兼容。
- 公开 API 在分页前排除未发布和归档资产；详情只提供已发布版本，编辑入口同时校验作者权限与所有权（或管理员身份）。
- 登录和修改密码保留经过校验的返回地址；目录筛选支持 URL 状态和浏览器后退；Markdown 继续使用 DOMPurify 清理。
- 下载复用已有认证导出端点，导出响应成功后记录所选版本下载；失败显示错误且不计数。
- 后台移动抽屉已实现，首页高亮使用 Vue Router 4 精确激活类；后台快捷入口和项目跳转使用 `/admin/*`。

## 本轮验证证据

- 前端契约与导航测试 17/17 通过；最终导航修复经历失败测试、修复、通过，并用真实浏览器断言只存在一个高亮导航项。
- 后端 `mvn.cmd test`：23/23 通过，失败、错误、跳过均为 0；MockMvc 使用真实 PostgreSQL。
- 前端生产构建与后端 `mvn.cmd package -DskipTests` 成功；`npm.cmd audit` 返回 0 vulnerabilities。
- Playwright + 本机 Chrome 浏览器回归 13/13 通过，覆盖搜索、分页、后退恢复、Markdown 清理、登录返回、下载成功和失败、图表像素、桌面与移动布局、后台兼容路由及重试。
- 浏览器 API 使用夹具拦截；这些结果不等同于当前 Java 服务的完整在线端到端验收。
- 截图、脚本与结果：`C:/Users/Administrator/.codex/visualizations/2026/09/05/01a07128-75c2-75b2-bd2b-349583f96ac1/portal-20260906/`。
- 当前预览：`http://127.0.0.1:5173/`。无夹具的正常浏览需要运行当前版本后端。
- 源码差异检查通过；全工作树差异仍有历史已跟踪生成文件 `frontend/dist/index.html` 的换行空白提示。`frontend/dist` 和 `frontend/tsconfig.tsbuildinfo` 保留本地且不纳入本轮源码提交。
- 统计页分块约 452 kB；现有后台 AssetDetail 分块约 906 kB，仍有 Vite 大分块提示。

## Cloudflare 部署评估

- 已按 `deploying-cloudflare-pages-d1` 技能评估，尚未执行云端部署。
- Vue 前端可部署到 Pages：构建目录 `frontend`，命令 `npm ci && npm run build`，产物目录 `dist`。
- Spring Boot 与 PostgreSQL 不能原样运行于 Pages Functions 和 D1。保留现有后端时，需要外部 Java 服务及同源 `/api` 代理，并验证会话 Cookie 和文件导出。
- 全量迁入 Pages + D1 需要另行迁移 Java API、PostgreSQL 查询及模式、认证会话和附件存储，不属于一次静态前端部署。

## 精确续作点

1. 源码完成后保留功能分支，集成前按文件检查主工作区现有未提交改动。
2. 在能够启动当前 Java 服务的环境完成真实登录、公开查询、下载与管理流程在线验收。当前机器先前启动 Tomcat 因 `Unable to establish loopback connection` / `UnixDomainSockets.connect0: Invalid argument` 失败。
3. 确定外部 Java 后端托管位置和 `/api` 代理后，再执行 Pages 部署与线上验证；如选择 D1，需要独立迁移计划。

---

以下保留上一轮项目管理修复记录，日期和状态属于历史证据。

# 历史记录：项目管理模块复查与修复

最后更新：2026-09-05（Asia/Shanghai）

## 当前状态

- 项目管理桌面主流程已修复并通过自动化、HTTP 与浏览器验收。
- 修复已提交并推送到 `origin/main`；未重置或清理原有工作区文件，生成产物和 SQL 脚本仍保持本地未提交状态。
- 工作区在本轮开始前已有大量业务改动和未跟踪文件，后续集成必须继续按文件审查，不能整体回退。
- 390px 移动视口仍有横向溢出，暂未擅自重做全局导航；见“已知风险”。

## 已完成修复

### 前端

- API 客户端统一返回类型化业务数据，并确保请求路径只带一个 `/api` 前缀。
- 项目与团队 API、项目列表、项目详情和规则管理统一使用同一响应契约。
- 项目列表和详情恢复共用的 `MainLayout`；移除不存在的 `/project-rules` 导航。
- 合并规则字段改为后端实际的 `rules`、`orgCount` 等字段；移除时使用 `assetVersionId`。
- 添加规则只提供规则资产的已发布版本；规则正文使用转义文本渲染，不使用 `v-html`。
- 修正用户角色、团队名称、重置密码及 email 请求契约。
- 修复严格 TypeScript 构建相关的未使用符号、DOM ref、模块声明和 CSS 声明问题。
- 页面级重试现在会把项目列表和团队列表作为一个整体重新加载，避免掩盖团队请求失败。
- 增加内联 favicon，消除浏览器默认请求 `/favicon.ico` 产生的 404 控制台错误。

### 后端与安全边界

- 项目列表合并当前用户拥有的项目和其 `users.team_id` 对应团队项目，并排除归档项、按 ID 去重。
- 创建/更新请求增加必填边界；归档项目拒绝更新、重复归档、添加规则和移除规则（409）。
- 添加规则时：缺失版本返回 404，草稿版本返回 400，同一规则资产的其他版本重复绑定返回 409。
- `/api/test-data/**` 要求管理员权限；`TestDataController` 仅在显式 `test-data` profile 下启用。
- 数据库诊断程序改为从环境变量读取连接参数，不再内置凭据。

## 设计与计划记录

- `abc5e43`：`docs/superpowers/specs/2026-09-04-project-management-repair-design.md`
- `3d24bd6`：`docs/superpowers/plans/2026-09-04-project-management-repair.md`
- 当前分支：`main`。本次实现提交已完成远端哈希核验：`origin/main` 与本地提交一致。

## 验证证据

### TDD 回归证据

- 原项目控制器回归检查在修复前为 8 项中 6 失败、2 错误；归档后的归档/移除请求错误返回 204。
- 测试数据安全检查在修复前证明匿名用户和普通用户均可收到 200。
- 2026-09-05 新增“项目与团队一起重试”检查，先得到 9 项中 1 个预期失败，最小修复后转绿。
- 2026-09-05 新增 favicon 检查，先得到 10 项中 1 个预期失败，补充图标声明后转绿。

### 2026-09-05 最终自动化

- `cd frontend && npm.cmd run test:contract`：10/10 通过，退出码 0。
- `cd frontend && npm.cmd run build`：`vue-tsc -b` 与 Vite 生产构建成功，退出码 0；保留一个大分块警告。
- `mvn.cmd test`：16/16 通过（ProjectController 10、TestDataSecurity 5、HashPassword 1），失败/错误/跳过均为 0，退出码 0。

### 浏览器验收

- 环境：`http://localhost:5173`，Playwright 1.62.1 + 本机 Chrome；当前会话无 Browser 插件。
- 桌面视口 1440×960：登录 → 项目列表 → 查看详情 → 项目级规则管理 → 添加规则对话框通过。
- 列表显示 1 个现有项目且共用顶栏/侧栏；详情显示 6 张合并规则卡片；管理页显示 1 条绑定和 4 个可选已发布版本。
- 最终复验中 console error/warn、page error、失败请求和 HTTP 4xx/5xx 均为 0。
- 截图保存在 `C:/Users/Administrator/.codex/visualizations/2026/09/04/01a06cb8-58b4-7ca1-ace1-01f84781bf19/`，文件名以 `project-*-final.png` 结尾。

### 只读 HTTP 冒烟

- 现有 8080 进程：`/actuator/health` 为 200/UP；登录、`/api/auth/me`、`/api/projects`、`/api/teams` 均为 200。
- 项目和团队响应均为数组；当前各返回 1 条。冒烟未创建、更新、归档或清理任何数据。

## 运行环境限制

- 现有 8080 是修复前已启动的旧 Java 进程，只能用于前端/API 形状的只读冒烟，不能证明当前后端实现已被该进程加载。
- 当前代码曾尝试在 18080 启动全新后端：编译、数据库连接和 7 个 Flyway 迁移均完成，但 JDK 21 在创建 Tomcat selector 时因 `Unable to establish loopback connection` / `UnixDomainSockets.connect0: Invalid argument` 失败。打包 JAR和更换 Unix-domain 临时目录仍相同，判定为本机运行时限制，不再无效重试。
- 当前后端行为以新鲜的编译、JUnit/MockMvc 16/16 结果为依据。
- 不要对现有 8080 调用 `/api/test-data/clean`；旧进程可能仍加载修复前的匿名破坏性端点。

## 已知风险

- 390×844 视口实测 `documentElement.scrollWidth = 548`，大于 390px；固定 180px 侧栏使内容区拥挤并产生横向溢出。需要单独确认移动导航方案后再改。
- 生产构建中 `AssetDetail` JavaScript 分块约 905.79 kB，超过 Vite 500 kB 提示线；不阻断本次项目管理修复，但后续可做编辑器/语法包按需加载。
- 工作区仍包含本轮开始前的生成产物变化、SQL 文件和其他业务模块改动；它们没有被删除或回退，也不能仅凭当前差异自动判定归属。

## 精确续作点

1. 若用户批准移动端改造，先确认“折叠侧栏/抽屉导航”方案，再针对 390px、768px 和桌面视口实施与截图回归。
2. 若移动端暂不处理，下一步是按文件审查仍保留在本地的生成产物和 SQL 脚本；它们未进入本次提交。
3. 集成前重新运行 `npm.cmd run test:contract`、`npm.cmd run build`、`mvn.cmd test` 和 `git diff --check`。
