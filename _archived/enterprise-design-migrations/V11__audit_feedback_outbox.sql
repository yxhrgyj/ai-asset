-- V11: 审计日志、反馈、交付任务（预留）
--
-- 对应设计文档 §16.1 审批记录、§13.2 Outbox 发布任务。

-- 审计日志。§16.1 要求保存操作者、时间、意见、快照哈希和客户端请求 ID。
-- 只追加，不允许 UPDATE / DELETE（由 V12 的触发器强制）。
CREATE TABLE audit_logs (
  id                BIGSERIAL PRIMARY KEY,
  actor_user_id     UUID        REFERENCES users(id) ON DELETE SET NULL,
  action            TEXT        NOT NULL,
  target_type       TEXT        NOT NULL,
  target_id         TEXT        NOT NULL,
  snapshot_hash     TEXT,
  client_request_id TEXT,
  detail            JSONB,
  occurred_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_logs_target_idx ON audit_logs(target_type, target_id, occurred_at DESC);
CREATE INDEX audit_logs_actor_idx ON audit_logs(actor_user_id, occurred_at DESC);
-- 同一客户端请求 ID 幂等去重（同一动作不重复记账）。
CREATE UNIQUE INDEX audit_logs_client_request_uk
  ON audit_logs(action, client_request_id) WHERE client_request_id IS NOT NULL;

-- 资产反馈与评价。
CREATE TABLE asset_feedback (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  asset_id         UUID        NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
  asset_version_id UUID        REFERENCES asset_versions(id) ON DELETE SET NULL,
  user_id          UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  rating           SMALLINT    CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
  content          TEXT,
  status           TEXT        NOT NULL DEFAULT 'OPEN'
                   CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'WONT_FIX')),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT asset_feedback_has_payload
    CHECK (rating IS NOT NULL OR content IS NOT NULL)
);
CREATE INDEX asset_feedback_asset_idx ON asset_feedback(asset_id, status);

-- ── 以下为 MVP 之后使用，MVP 建表预留不写入 ──

-- generation 序列（§13.2）：manifest 的单调计数器必须由数据库生成，
-- 禁止应用层自增。MVP 不用，但序列先建，避免后续引入时与已有数据冲突。
CREATE SEQUENCE manifest_generation_seq AS BIGINT START 1;

-- 交付任务（§13.2 Outbox）。
-- 幂等靠 idempotency_key 唯一索引；串行靠 project_id 上的部分唯一索引
-- （同一项目同时只允许一个未完成任务）——幂等不等于串行，两者都要。
CREATE TABLE delivery_tasks (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id        UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  asset_version_id  UUID        REFERENCES asset_versions(id) ON DELETE RESTRICT,
  idempotency_key   TEXT        NOT NULL,
  status            TEXT        NOT NULL DEFAULT 'QUEUED'
                    CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'ABANDONED')),
  generation        BIGINT,
  base_commit_sha   TEXT,
  attempt_count     INTEGER     NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  last_error        TEXT,
  gitlab_mr_iid     INTEGER,
  gitlab_pipeline_id TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX delivery_tasks_idempotency_uk ON delivery_tasks(idempotency_key);
-- 同一项目串行：QUEUED / RUNNING 状态下每个项目最多一条。
CREATE UNIQUE INDEX delivery_tasks_project_serial_uk
  ON delivery_tasks(project_id) WHERE status IN ('QUEUED', 'RUNNING');
