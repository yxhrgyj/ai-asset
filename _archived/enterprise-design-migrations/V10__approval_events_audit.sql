-- V10: 审批、事件、审计、反馈
--
-- 对应设计文档 §12.1 资产发布、§14 使用状态与统计口径、§16.1 审批记录。

-- 审批请求。submitted_by 冗余在本表，使"审批人不得为提交人"能用 CHECK 表达，
-- 而不必写触发器去连表查询。
CREATE TABLE approval_requests (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  subject_type     TEXT        NOT NULL
                   CHECK (subject_type IN ('ASSET_VERSION', 'PROJECT_BINDING', 'RULE_EXCEPTION')),
  asset_version_id UUID        REFERENCES asset_versions(id) ON DELETE CASCADE,
  project_id       UUID        REFERENCES projects(id) ON DELETE CASCADE,
  rule_exception_id UUID       REFERENCES rule_exceptions(id) ON DELETE CASCADE,
  submitted_by     UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  submitted_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  decided_by       UUID        REFERENCES users(id) ON DELETE RESTRICT,
  decided_at       TIMESTAMPTZ,
  decision         TEXT        CHECK (decision IS NULL OR decision IN
                               ('APPROVED', 'REJECTED', 'CHANGES_REQUESTED')),
  comment          TEXT,
  -- 冻结快照哈希（§12.1）：审批期内容不可改，撤回需重新冻结。
  frozen_hash      TEXT        NOT NULL,
  client_request_id TEXT,

  -- §5、§12.1：审批人不得为提交人。
  CONSTRAINT approval_requests_approver_not_submitter
    CHECK (decided_by IS NULL OR decided_by <> submitted_by),
  CONSTRAINT approval_requests_decision_triple
    CHECK ((decided_by IS NULL) = (decided_at IS NULL)
           AND (decided_by IS NULL) = (decision IS NULL)),
  -- 主体引用必须与 subject_type 一致，且恰好一个非空。
  CONSTRAINT approval_requests_subject_match CHECK (
    (subject_type = 'ASSET_VERSION'   AND asset_version_id IS NOT NULL
       AND rule_exception_id IS NULL) OR
    (subject_type = 'PROJECT_BINDING' AND project_id IS NOT NULL
       AND rule_exception_id IS NULL) OR
    (subject_type = 'RULE_EXCEPTION'  AND rule_exception_id IS NOT NULL)
  )
);
CREATE INDEX approval_requests_pending_idx
  ON approval_requests(subject_type, submitted_at) WHERE decided_at IS NULL;
CREATE INDEX approval_requests_version_idx ON approval_requests(asset_version_id);

-- 资产事件。§14 要求严格区分口径，故 event_type 不含任何"已加载"语义。
-- DECLARED 表示项目声明使用，不等于 Trae CN 实际加载——后者系统无法证明（§2.9）。
CREATE TABLE asset_events (
  id               BIGSERIAL PRIMARY KEY,
  asset_id         UUID        NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
  asset_version_id UUID        REFERENCES asset_versions(id) ON DELETE SET NULL,
  event_type       TEXT        NOT NULL
                   CHECK (event_type IN ('VIEWED', 'DOWNLOADED', 'DECLARED', 'UNDECLARED')),
  user_id          UUID        REFERENCES users(id) ON DELETE SET NULL,
  project_id       UUID        REFERENCES projects(id) ON DELETE SET NULL,
  occurred_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX asset_events_asset_type_idx ON asset_events(asset_id, event_type, occurred_at DESC);
CREATE INDEX asset_events_project_idx ON asset_events(project_id) WHERE project_id IS NOT NULL;

COMMENT ON COLUMN asset_events.event_type IS
  'VIEWED/DOWNLOADED/DECLARED 三者口径不得混用（§14）。系统不能证明 Trae CN 实际加载，故无 LOADED 事件';
