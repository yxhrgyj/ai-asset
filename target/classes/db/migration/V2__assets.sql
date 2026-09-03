-- V2: 资产与版本
--
-- 可见性设计（方案文档 5.1）：不设 visibility 字段。
--   草稿   = 存在但无 PUBLISHED 版本 → 仅作者与审批人可见
--   已发布 = 有 PUBLISHED 版本       → 全员可见可下载
-- 由版本状态直接派生。核心痛点是"资产散落找不到"，默认就该全员可见。

CREATE TABLE assets (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  type        TEXT        NOT NULL CHECK (type IN ('RULE', 'SKILL', 'DOCUMENT')),
  name        TEXT        NOT NULL,
  slug        TEXT        NOT NULL UNIQUE,
  summary     TEXT,
  category    TEXT,
  tags        TEXT[]      NOT NULL DEFAULT '{}',

  -- scope 只决定规则合并的层级顺序，与"谁能看"无关。
  -- 非 RULE 类型资产此字段无意义，统一填 ORGANIZATION。
  scope       TEXT        NOT NULL DEFAULT 'ORGANIZATION'
              CHECK (scope IN ('ORGANIZATION', 'TECH_STACK', 'PROJECT')),
  -- 技术栈标识（如 vue3、spring-boot），仅 scope=TECH_STACK 时有值。
  tech_stack  TEXT,

  owner_user_id UUID      NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  team_id       UUID      REFERENCES teams(id) ON DELETE SET NULL,
  archived      BOOLEAN   NOT NULL DEFAULT false,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT assets_tech_stack_pairing
    CHECK ((scope = 'TECH_STACK') = (tech_stack IS NOT NULL))
);

CREATE INDEX assets_type_idx ON assets(type) WHERE NOT archived;
CREATE INDEX assets_scope_idx ON assets(scope, tech_stack);
CREATE INDEX assets_tags_idx ON assets USING gin(tags);
CREATE INDEX assets_owner_idx ON assets(owner_user_id);

-- 资产版本。正式版本不可变（V6 触发器强制）。
CREATE TABLE asset_versions (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  asset_id      UUID        NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
  version_no    INTEGER     NOT NULL CHECK (version_no > 0),
  status        TEXT        NOT NULL DEFAULT 'DRAFT'
                CHECK (status IN ('DRAFT', 'PENDING', 'REJECTED',
                                  'PUBLISHED', 'DEPRECATED', 'WITHDRAWN')),
  body          TEXT,
  content_hash  TEXT,
  changelog     TEXT,
  created_by    UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  published_at  TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- 已发布及其后续状态必须有内容哈希，否则无法证明内容未被改动。
  CONSTRAINT asset_versions_published_needs_hash
    CHECK (status NOT IN ('PUBLISHED', 'DEPRECATED', 'WITHDRAWN')
           OR content_hash IS NOT NULL)
);

CREATE UNIQUE INDEX asset_versions_no_uk ON asset_versions(asset_id, version_no);
-- 同一资产同时只能有一个未发布的在编版本，避免并行草稿互相覆盖。
CREATE UNIQUE INDEX asset_versions_single_open_uk ON asset_versions(asset_id)
  WHERE status IN ('DRAFT', 'PENDING');
CREATE INDEX asset_versions_status_idx ON asset_versions(status);
