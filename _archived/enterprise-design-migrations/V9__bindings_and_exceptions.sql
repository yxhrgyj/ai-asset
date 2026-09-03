-- V9: 项目资产绑定与受控例外
--
-- 对应设计文档 §10.1 ProjectBinding、§11 受控例外。

-- 项目资产绑定。MVP 只写业务字段，交付字段建表预留。
CREATE TABLE project_bindings (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id       UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  asset_id         UUID        NOT NULL REFERENCES assets(id) ON DELETE RESTRICT,
  asset_version_id UUID        NOT NULL REFERENCES asset_versions(id) ON DELETE RESTRICT,
  source_scope     TEXT        NOT NULL
                   CHECK (source_scope IN ('ORGANIZATION', 'TEAM', 'PROJECT')),
  module_path      TEXT        NOT NULL DEFAULT '/',
  enabled          BOOLEAN     NOT NULL DEFAULT true,
  approved_by      UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  approved_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- 交付链字段：MVP 建表预留，不写入。见实施计划 S0 交付物。
  target_path         TEXT,
  deployed_commit_sha TEXT,
  deployed_at         TIMESTAMPTZ,
  current_state       TEXT
                      CHECK (current_state IS NULL OR current_state IN
                             ('DECLARED', 'DELIVERING', 'DELIVERED', 'DELIVER_FAILED')),
  drift_status        TEXT
                      CHECK (drift_status IS NULL OR drift_status IN
                             ('UNKNOWN', 'IN_SYNC', 'TARGET_MISSING', 'MODIFIED', 'CHECK_FAILED')),

  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 同一项目同一模块下不重复绑定同一资产。换版本是 UPDATE 而非新增行。
CREATE UNIQUE INDEX project_bindings_uk
  ON project_bindings(project_id, asset_id, module_path);
CREATE INDEX project_bindings_asset_idx ON project_bindings(asset_id);
CREATE INDEX project_bindings_version_idx ON project_bindings(asset_version_id);

-- 受控例外（§11）。
-- 到期后系统标记风险但不自动删除，故 status 需要 EXPIRED 而非直接删行。
CREATE TABLE rule_exceptions (
  id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id              UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  base_asset_id           UUID        NOT NULL REFERENCES assets(id) ON DELETE RESTRICT,
  base_asset_version_id   UUID        REFERENCES asset_versions(id) ON DELETE RESTRICT,
  -- 例外可精确到具体规则条目；为空表示覆盖整个资产。
  base_rule_key           TEXT,
  replacement_asset_id    UUID        REFERENCES assets(id) ON DELETE RESTRICT,
  reason                  TEXT        NOT NULL,
  impact_scope            TEXT        NOT NULL,
  migration_plan          TEXT        NOT NULL,
  requested_by            UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  approved_by             UUID        REFERENCES users(id) ON DELETE RESTRICT,
  approved_at             TIMESTAMPTZ,
  starts_at               TIMESTAMPTZ NOT NULL,
  expires_at              TIMESTAMPTZ NOT NULL,
  status                  TEXT        NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'REVOKED')),
  review_note             TEXT,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT rule_exceptions_period CHECK (expires_at > starts_at),
  -- §11 要求例外必须含审批人与有效期；已批准的必须有审批人。
  CONSTRAINT rule_exceptions_approved_pair
    CHECK (status <> 'APPROVED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)),
  -- §5、§12.1：审批人不得为申请人。
  CONSTRAINT rule_exceptions_approver_not_requester
    CHECK (approved_by IS NULL OR approved_by <> requested_by)
);
CREATE INDEX rule_exceptions_project_idx ON rule_exceptions(project_id, status);
CREATE INDEX rule_exceptions_expires_idx ON rule_exceptions(expires_at) WHERE status = 'APPROVED';
CREATE INDEX rule_exceptions_base_rule_idx ON rule_exceptions(base_rule_key)
  WHERE base_rule_key IS NOT NULL;
