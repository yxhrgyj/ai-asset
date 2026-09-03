-- V8: 项目、成员、技术栈声明
--
-- 对应设计文档 §9 项目技术画像、§10.1 ProjectBinding、§16.1 项目成员关系。
--
-- MVP 无 GitLab 接入：项目手工注册，技术栈手工声明。
-- 自动扫描识别属 MVP 之后（§19.2），届时 tech_stack_declarations
-- 的 source 从 MANUAL 扩展为 SCAN_CONFIRMED。

CREATE TABLE projects (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id  UUID        NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
  name             TEXT        NOT NULL,
  slug             TEXT        NOT NULL,
  owner_team_id    UUID        REFERENCES teams(id) ON DELETE RESTRICT,
  owner_user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  status           TEXT        NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE', 'ARCHIVED')),

  -- 交付链字段：MVP 建表预留，不写入。
  gitlab_project_id   TEXT,
  gitlab_default_branch TEXT,

  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX projects_slug_uk ON projects(organization_id, slug);
CREATE INDEX projects_owner_team_idx ON projects(owner_team_id);

-- 项目成员关系。权限判定的第二个维度（PROJECT_ONLY 可见性依赖它）。
CREATE TABLE project_members (
  project_id UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role       TEXT        NOT NULL DEFAULT 'MEMBER'
             CHECK (role IN ('OWNER', 'MEMBER')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (project_id, user_id)
);
CREATE INDEX project_members_user_idx ON project_members(user_id);

-- 技术栈声明。MVP 为负责人手工声明，作为规则推荐依据。
-- §9.3 要求"扫描结论必须由项目负责人确认"，MVP 去掉自动识别但保留确认语义：
-- 每条声明都记录确认人与确认时间。
CREATE TABLE tech_stack_declarations (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id   UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  module_path  TEXT        NOT NULL DEFAULT '/',
  attributes   JSONB       NOT NULL,
  source       TEXT        NOT NULL DEFAULT 'MANUAL'
               CHECK (source IN ('MANUAL', 'SCAN_CONFIRMED')),
  confirmed_by UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  confirmed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX tech_stack_declarations_uk
  ON tech_stack_declarations(project_id, module_path);
CREATE INDEX tech_stack_declarations_attrs_idx
  ON tech_stack_declarations USING gin(attributes);

COMMENT ON COLUMN tech_stack_declarations.attributes IS
  '技术栈属性，与 rule_items.applies_when 对齐，如 {"framework":"vue3","language":"typescript"}';
