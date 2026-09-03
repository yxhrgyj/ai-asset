-- V5: 资产版本
--
-- 对应设计文档 §7.4 AssetVersion、§12.2 资产状态与工程发布状态。
--
-- 关键设计：status 与 publish_status 是两条独立状态机，必须分开存储。
-- 合并会让 GitLab 故障波及全员使用，§12.2 明确禁止。
-- MVP 无交付链，publish_status 恒为 NOT_REQUIRED。

CREATE TABLE asset_versions (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  asset_id           UUID        NOT NULL REFERENCES assets(id) ON DELETE RESTRICT,
  version            TEXT        NOT NULL,
  version_ordinal    BIGINT      NOT NULL,

  -- 业务资产状态（§12.2 权威定义）
  status             TEXT        NOT NULL DEFAULT 'DRAFT'
                     CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'REJECTED',
                                       'CHANGES_REQUESTED', 'APPROVED', 'PERSISTED',
                                       'AVAILABLE', 'DEPRECATED', 'YANKED', 'ARCHIVED')),

  -- GitLab 工程发布状态（§12.2），与 status 无关。MVP 恒为 NOT_REQUIRED。
  publish_status     TEXT        NOT NULL DEFAULT 'NOT_REQUIRED'
                     CHECK (publish_status IN ('NOT_REQUIRED', 'PENDING', 'PUBLISHING',
                                               'PUBLISHED', 'PUBLISH_FAILED', 'RETRYING')),

  content_hash       TEXT,
  metadata_snapshot  JSONB,
  changelog          TEXT,
  created_by         UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  approved_by        UUID        REFERENCES users(id) ON DELETE RESTRICT,
  approved_at        TIMESTAMPTZ,
  persisted_at       TIMESTAMPTZ,
  published_at       TIMESTAMPTZ,
  deprecated_at      TIMESTAMPTZ,
  yanked_at          TIMESTAMPTZ,
  yank_reason        TEXT,
  superseded_by      UUID        REFERENCES asset_versions(id) ON DELETE SET NULL,

  -- 交付链字段：MVP 建表预留，不写入。见实施计划 S0 交付物。
  gitlab_project_id  TEXT,
  gitlab_commit_sha  TEXT,
  gitlab_tag         TEXT,
  gitlab_mr_iid      INTEGER,
  gitlab_pipeline_id TEXT,

  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT asset_versions_no_self_supersede CHECK (superseded_by IS DISTINCT FROM id),
  -- 正式版本必须有内容哈希，否则无法做完整性校验与对账。
  CONSTRAINT asset_versions_available_needs_hash
    CHECK (status NOT IN ('AVAILABLE', 'DEPRECATED', 'YANKED', 'ARCHIVED')
           OR content_hash IS NOT NULL),
  -- 审批人存在时审批时间必须存在，反之亦然。
  CONSTRAINT asset_versions_approval_pair
    CHECK ((approved_by IS NULL) = (approved_at IS NULL)),
  CONSTRAINT asset_versions_yank_reason_required
    CHECK (status <> 'YANKED' OR yank_reason IS NOT NULL)
);

CREATE UNIQUE INDEX asset_versions_version_uk ON asset_versions(asset_id, version);
CREATE UNIQUE INDEX asset_versions_ordinal_uk ON asset_versions(asset_id, version_ordinal);
CREATE INDEX asset_versions_status_idx ON asset_versions(asset_id, status);
CREATE INDEX asset_versions_created_by_idx ON asset_versions(created_by);
