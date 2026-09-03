-- V4: 资产（逻辑资产）
--
-- 对应设计文档 §7.2 逻辑资产与版本、§7.3 Asset、§16.1 权限判定矩阵。
--
-- scope 与 visibility 是两个独立维度，不互相推导（§7.3）：
--   scope      = 适用范围与继承层级，影响规则组合顺序
--   visibility = 谁能看见和下载，影响鉴权
-- 两者相交的合法组合由本表 CHECK 约束强制，非法组合在数据库层被拒绝，
-- 不依赖应用层校验（实施计划第 2.2 节）。

CREATE TABLE assets (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id  UUID        NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
  name             TEXT        NOT NULL,
  slug             TEXT        NOT NULL,
  type             TEXT        NOT NULL
                   CHECK (type IN ('RULE', 'SKILL', 'DOCUMENT', 'PACKAGE')),
  description      TEXT,
  category         TEXT,
  owner_team_id    UUID        REFERENCES teams(id) ON DELETE RESTRICT,
  owner_user_id    UUID        REFERENCES users(id) ON DELETE RESTRICT,
  visibility       TEXT        NOT NULL
                   CHECK (visibility IN ('PUBLIC', 'TEAM_ONLY', 'PROJECT_ONLY', 'PRIVATE')),
  scope            TEXT        NOT NULL
                   CHECK (scope IN ('ORGANIZATION', 'TEAM', 'PROJECT')),
  lifecycle_status TEXT        NOT NULL DEFAULT 'ACTIVE'
                   CHECK (lifecycle_status IN ('ACTIVE', 'DEPRECATED', 'ARCHIVED')),
  applies_when     JSONB       NOT NULL DEFAULT '{}'::jsonb,
  tags             TEXT[]      NOT NULL DEFAULT '{}',
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- §16.1 非法组合一：组织基线按定义应全员可见。
  -- 若确有内部基线需求，应新增 scope 值而不是靠 visibility 收窄。
  CONSTRAINT assets_org_scope_must_be_visible
    CHECK (scope <> 'ORGANIZATION' OR visibility = 'PUBLIC'),

  -- §16.1 非法组合二：PROJECT_ONLY 仅允许 scope=PROJECT。
  CONSTRAINT assets_project_only_requires_project_scope
    CHECK (visibility <> 'PROJECT_ONLY' OR scope = 'PROJECT'),

  -- §16.1：TEAM_ONLY 需要归属团队才有判定依据。
  CONSTRAINT assets_team_only_requires_owner_team
    CHECK (visibility <> 'TEAM_ONLY' OR owner_team_id IS NOT NULL),

  -- PRIVATE 需要归属人才有判定依据。
  CONSTRAINT assets_private_requires_owner_user
    CHECK (visibility <> 'PRIVATE' OR owner_user_id IS NOT NULL)
);

CREATE UNIQUE INDEX assets_slug_uk ON assets(organization_id, slug);
CREATE INDEX assets_type_idx ON assets(organization_id, type);
CREATE INDEX assets_owner_team_idx ON assets(owner_team_id);
CREATE INDEX assets_owner_user_idx ON assets(owner_user_id);
CREATE INDEX assets_tags_idx ON assets USING gin(tags);
CREATE INDEX assets_applies_when_idx ON assets USING gin(applies_when);
