# 项目进度：项目管理模块复查与修复

最后更新：2026-09-05（Asia/Shanghai）

## 当前状态

- 项目管理桌面主流程已修复并通过自动化、HTTP 与浏览器验收。
- 修复已提交到 `main` 的本地提交；未重置或清理原有工作区文件，生成产物和 SQL 脚本仍保持本地未提交状态。
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
- 当前分支：`main`。本次实现已形成本地提交，待远端哈希核验后完成推送。

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
