-- V4: 项目、项目选用规则、审批、审计

-- 项目。手工注册，技术栈手工声明（不扫描代码仓库）。
CREATE TABLE projects (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name           TEXT        NOT NULL,
  slug           TEXT        NOT NULL UNIQUE,
  description    TEXT,
  owner_user_id  UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  team_id        UUID        REFERENCES teams(id) ON DELETE SET NULL,
  -- 声明的技术栈，如 {"java","spring-boot","vue3","typescript"}。
  -- 合并引擎按此匹配技术栈层规则。
  tech_stacks    TEXT[]      NOT NULL DEFAULT '{}',
  archived       BOOLEAN     NOT NULL DEFAULT false,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX projects_owner_idx ON projects(owner_user_id);
CREATE INDEX projects_tech_stacks_idx ON projects USING gin(tech_stacks);

-- 项目选用的规则资产。钉到具体版本，否则"正式版本不可变"失去意义。
CREATE TABLE project_rules (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id       UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  asset_id         UUID        NOT NULL REFERENCES assets(id) ON DELETE RESTRICT,
  asset_version_id UUID        NOT NULL REFERENCES asset_versions(id) ON DELETE RESTRICT,
  enabled          BOOLEAN     NOT NULL DEFAULT true,
  added_by         UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX project_rules_uk ON project_rules(project_id, asset_id);
CREATE INDEX project_rules_version_idx ON project_rules(asset_version_id);

-- 审批记录。
--
-- submitted_by 冗余在本表，使"审批人 ≠ 提交人"能用 CHECK 表达，
-- 不必写触发器连表查询。
CREATE TABLE approvals (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  asset_version_id UUID        NOT NULL REFERENCES asset_versions(id) ON DELETE CASCADE,
  submitted_by     UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  submitted_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- 提交时冻结的内容哈希。审批期内容改动会导致哈希不符，需重新提交。
  frozen_hash      TEXT        NOT NULL,
  decided_by       UUID        REFERENCES users(id) ON DELETE RESTRICT,
  decided_at       TIMESTAMPTZ,
  decision         TEXT        CHECK (decision IS NULL OR
                               decision IN ('APPROVED', 'REJECTED')),
  comment          TEXT,

  -- 审批人不得为提交人。
  CONSTRAINT approvals_approver_not_submitter
    CHECK (decided_by IS NULL OR decided_by <> submitted_by),
  -- 决定三元组必须同时有或同时无。
  CONSTRAINT approvals_decision_triple
    CHECK ((decided_by IS NULL) = (decided_at IS NULL)
           AND (decided_by IS NULL) = (decision IS NULL))
);
CREATE INDEX approvals_pending_idx ON approvals(submitted_at) WHERE decided_at IS NULL;
CREATE INDEX approvals_version_idx ON approvals(asset_version_id);

-- 审计日志。只追加（V6 触发器强制）。
CREATE TABLE audit_logs (
  id            BIGSERIAL PRIMARY KEY,
  actor_user_id UUID        REFERENCES users(id) ON DELETE SET NULL,
  action        TEXT        NOT NULL,
  target_type   TEXT        NOT NULL,
  target_id     TEXT        NOT NULL,
  detail        JSONB,
  occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_logs_target_idx ON audit_logs(target_type, target_id, occurred_at DESC);
CREATE INDEX audit_logs_actor_idx ON audit_logs(actor_user_id, occurred_at DESC);
