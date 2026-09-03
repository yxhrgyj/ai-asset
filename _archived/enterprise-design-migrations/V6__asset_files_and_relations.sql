-- V6: 资产文件、关系与包引用
--
-- 对应设计文档 §7.5 AssetFile、§7.6 文档与派生 Rule、§7.3 Asset Package。

-- 资产文件。
-- text_content 与 storage_key 二选一：小文本入库，二进制与大文件走存储抽象层。
-- storage_key 是后端无关的存储键（§7.5），本地文件系统实现解析为相对路径，
-- 对象存储实现作为 Object Key，故切换后端只搬文件不改库。
CREATE TABLE asset_files (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  asset_version_id UUID        NOT NULL REFERENCES asset_versions(id) ON DELETE CASCADE,
  relative_path    TEXT        NOT NULL,
  file_type        TEXT        NOT NULL
                   CHECK (file_type IN ('MARKDOWN', 'YAML', 'JSON', 'TEXT',
                                        'IMAGE', 'ARCHIVE', 'SCRIPT', 'OTHER')),
  mime_type        TEXT        NOT NULL,
  size_bytes       BIGINT      NOT NULL CHECK (size_bytes >= 0),
  content_hash     TEXT        NOT NULL,
  text_content     TEXT,
  storage_key      TEXT,
  is_entry_file    BOOLEAN     NOT NULL DEFAULT false,
  preview_status   TEXT        NOT NULL DEFAULT 'NONE'
                   CHECK (preview_status IN ('NONE', 'READY', 'UNSUPPORTED', 'FAILED')),
  security_status  TEXT        NOT NULL DEFAULT 'PENDING'
                   CHECK (security_status IN ('PENDING', 'PASSED', 'FLAGGED', 'REJECTED')),
  security_report  JSONB,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- 内容必须恰好落在一处，不允许两处都有或两处都无。
  CONSTRAINT asset_files_content_location
    CHECK ((text_content IS NOT NULL) <> (storage_key IS NOT NULL)),
  -- 路径穿越防线之一（§16.3）。应用层仍须做完整校验，此处兜底。
  CONSTRAINT asset_files_relative_path_safe
    CHECK (relative_path !~ '(^/|^[A-Za-z]:|\.\.|\\)')
);
CREATE UNIQUE INDEX asset_files_path_uk ON asset_files(asset_version_id, relative_path);
CREATE UNIQUE INDEX asset_files_entry_uk
  ON asset_files(asset_version_id) WHERE is_entry_file;
CREATE INDEX asset_files_storage_key_idx ON asset_files(storage_key) WHERE storage_key IS NOT NULL;

-- 资产间关系：文档派生 Rule（§7.6）、包引用（§7.3）等。
-- 包只存引用不复制内容，故 target 指向具体版本。
CREATE TABLE asset_relations (
  id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source_asset_id         UUID        NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
  target_asset_id         UUID        NOT NULL REFERENCES assets(id) ON DELETE RESTRICT,
  target_asset_version_id UUID        REFERENCES asset_versions(id) ON DELETE RESTRICT,
  kind                    TEXT        NOT NULL
                          CHECK (kind IN ('DERIVED_FROM', 'PACKAGE_ITEM', 'RELATED_TO', 'SUPERSEDES')),
  sort_order              INTEGER     NOT NULL DEFAULT 0,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT asset_relations_no_self CHECK (source_asset_id <> target_asset_id),
  -- 包引用必须钉到具体版本，否则"正式版本不可变"失效。
  CONSTRAINT asset_relations_package_needs_version
    CHECK (kind <> 'PACKAGE_ITEM' OR target_asset_version_id IS NOT NULL)
);
CREATE UNIQUE INDEX asset_relations_uk
  ON asset_relations(source_asset_id, target_asset_id, kind);
CREATE INDEX asset_relations_target_idx ON asset_relations(target_asset_id);
