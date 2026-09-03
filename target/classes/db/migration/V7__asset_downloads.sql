-- V7: 资产下载统计
--
-- 在 assets 表添加 download_count 字段，并创建下载记录表用于审计。

-- 1. 给 assets 表添加下载次数字段
ALTER TABLE assets ADD COLUMN download_count INTEGER NOT NULL DEFAULT 0;

-- 2. 创建下载记录表（审计用，可选但推荐保留）
CREATE TABLE asset_downloads (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  asset_id      UUID        NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
  version_id    UUID        REFERENCES asset_versions(id) ON DELETE SET NULL,
  user_id       UUID        REFERENCES users(id) ON DELETE SET NULL,
  downloaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX asset_downloads_asset_idx ON asset_downloads(asset_id, downloaded_at DESC);
CREATE INDEX asset_downloads_user_idx ON asset_downloads(user_id, downloaded_at DESC);
