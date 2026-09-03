# AI 资产平台

企业 AI 资产管理系统 —— Prompt 模板、模型配置规则、数据集的协作与治理平台。

## 快速开始

### 前置要求

- **JDK 21**（Temurin 或 Zulu）
- **Maven 3.8+**
- **Node.js 20+**
- **Docker Desktop**（或 PostgreSQL 16）

### 1. 启动数据库

```bash
cd E:\Objects\ai-asset-platform
docker-compose up -d
```

PostgreSQL 将在 `localhost:5432` 运行，数据持久化在 `./data/postgres`。

### 2. 启动后端

```bash
mvn spring-boot:run
```

首次启动会：
- 自动执行 Flyway 迁移（6 个脚本，建立 9 张表）
- 创建初始管理员账号 `admin` / `ChangeMe_0901`

后端运行在 `http://localhost:8080`。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端运行在 `http://localhost:5173`。

### 4. 登录

浏览器打开 `http://localhost:5173`，使用初始账号登录：

- 用户名：`admin`
- 密码：`ChangeMe_0901`

首次登录后会强制修改密码。

## 项目结构

```
ai-asset-platform/
├── src/main/
│   ├── java/com/company/aiasset/
│   │   ├── security/          # 认证过滤器、bcrypt 配置
│   │   ├── user/              # 用户实体与仓库
│   │   └── config/            # CORS、参数解析器
│   └── resources/
│       ├── db/migration/      # Flyway 迁移脚本
│       └── application.yml    # 数据库、会话配置
├── frontend/
│   ├── src/
│   │   ├── api/               # 后端接口封装
│   │   ├── stores/            # Pinia 状态管理
│   │   ├── views/             # 登录、改密、首页
│   │   └── router/            # 路由守卫
│   └── package.json
├── docker-compose.yml         # PostgreSQL 容器
└── pom.xml                    # Spring Boot 3.3.5 + Java 21
```

## 技术栈

**后端**
- Spring Boot 3.3.5 + Spring Security
- PostgreSQL 16 + Flyway
- 会话认证（HttpSession + bcrypt）
- pg_trgm 中文全文搜索

**前端**
- Vue 3 + TypeScript
- Vite 6
- Pinia
- Vue Router

## 配置

### 修改数据库连接

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ai_asset
    username: ai_asset
    password: localdev
```

### 修改初始管理员账号

编辑 `application.yml`：

```yaml
app:
  bootstrap:
    admin-username: admin
    admin-password: YourStrongPassword
```

删除现有管理员后重启生效：

```bash
docker exec ai-asset-db psql -U ai_asset -d ai_asset -c "DELETE FROM users WHERE username = 'admin';"
mvn spring-boot:run
```

### 会话超时

默认 8 小时，在 `application.yml` 修改：

```yaml
server:
  servlet:
    session:
      timeout: 8h
```

## API 端点

### 认证

- `POST /api/auth/login` —— 登录
- `POST /api/auth/logout` —— 登出
- `GET /api/auth/me` —— 获取当前用户
- `POST /api/auth/change-password` —— 修改密码

所有 `/api/**` 端点（除认证接口外）需要登录后访问。

## 开发说明

### 数据库迁移

Flyway 脚本在 `src/main/resources/db/migration/`：

- `V1__users_and_teams.sql` —— 用户与团队
- `V2__assets.sql` —— 资产与版本
- `V3__asset_files_and_rules.sql` —— 附件与规则条目
- `V4__projects_and_approvals.sql` —— 项目与审批事件
- `V5__invariants.sql` —— 四条不变量触发器
- `V6__search.sql` —— pg_trgm 搜索索引

**不要手工修改已应用的迁移脚本**。新建 `V7__` 开头的文件添加变更。

### 添加新端点

1. 在控制器方法参数中注入 `CurrentUser currentUser`
2. 使用 `currentUser.user()` 获取当前登录用户
3. 使用 `currentUser.hasRole(Role.ADMIN)` 检查角色
4. 在 `SecurityConfig` 中配置路径是否需要认证

### 前端状态管理

登录状态由 `useAuthStore()` 管理：

```typescript
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
console.log(auth.user?.username)
```

## 故障排查

### 后端无法启动

检查 PostgreSQL 是否运行：

```bash
docker ps | grep ai-asset-db
```

查看后端日志中的 Flyway 迁移是否成功。

### 前端登录失败

1. 检查后端是否在 `http://localhost:8080` 运行
2. 浏览器开发者工具查看网络请求是否返回 CORS 错误
3. 确认 `WebConfig.java` 中的 CORS 配置包含前端地址

### 会话丢失

前端请求必须带 `credentials: 'include'`，已在 `api/auth.ts` 中配置。

浏览器开发者工具 → Application → Cookies 检查 `JSESSIONID` 是否存在。

## 下一步

- [ ] 资产列表与详情页
- [ ] 版本管理与审批流
- [ ] 文件上传
- [ ] 全文搜索
- [ ] 用户与团队管理

## 许可

内部项目，不对外发布。
