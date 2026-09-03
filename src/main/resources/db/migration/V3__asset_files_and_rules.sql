-- V3: 资产文件与规则条目

-- 资产文件（图片、附件、Skill 包内的脚本等）。
--
-- 小文本直接入库（text_content），二进制与大文件走存储抽象层（storage_key）。
-- storage_key 是后端无关的存储键：本地实现解析为数据目录下的相对路径。
-- 键的构造与业务标识解耦（版本ID/内容哈希），换存储后端时只搬文件不改库。
CREATE TABLE asset_files (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  asset_version_id UUID        NOT NULL REFERENCES asset_versions(id) ON DELETE CASCADE,
  relative_path    TEXT        NOT NULL,
  mime_type        TEXT        NOT NULL,
  size_bytes       BIGINT      NOT NULL CHECK (size_bytes >= 0),
  content_hash     TEXT        NOT NULL,
  text_content     TEXT,
  storage_key      TEXT,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- 内容必须恰好落在一处。
  CONSTRAINT asset_files_content_location
    CHECK ((text_content IS NOT NULL) <> (storage_key IS NOT NULL)),
  -- 路径穿越兜底。应用层仍须校验，此处是最后一道。
  CONSTRAINT asset_files_path_safe
    CHECK (relative_path !~ '(^/|^[A-Za-z]:|\.\.|\\)')
);

CREATE UNIQUE INDEX asset_files_path_uk ON asset_files(asset_version_id, relative_path);
CREATE INDEX asset_files_storage_key_idx ON asset_files(storage_key)
  WHERE storage_key IS NOT NULL;

-- 规则条目。
--
-- 一个 RULE 资产（如 vue3-typescript）含多条规则，
-- 合并与重复检查作用在条目级而非资产级，故独立成表。
CREATE TABLE rule_items (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  asset_version_id UUID        NOT NULL REFERENCES asset_versions(id) ON DELETE CASCADE,

  -- 稳定 ID，如 frontend.vue.no-any。跨版本不变，是重复检查的锚点。
  rule_key         TEXT        NOT NULL,
  title            TEXT        NOT NULL,
  body             TEXT        NOT NULL,
  level            TEXT        NOT NULL CHECK (level IN ('REQUIRED', 'RECOMMENDED')),
  -- 作用路径，如 {"src/**","!src/legacy/**"}。空表示全项目。
  path_globs       TEXT[]      NOT NULL DEFAULT '{}',
  sort_order       INTEGER     NOT NULL DEFAULT 0,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT rule_items_key_format
    CHECK (rule_key ~ '^[a-z0-9]+(\.[a-z0-9-]+)+$')
);

-- 同版本内 rule_key 不重复。跨资产的重复由合并引擎检查并提示。
CREATE UNIQUE INDEX rule_items_key_uk ON rule_items(asset_version_id, rule_key);
CREATE INDEX rule_items_key_idx ON rule_items(rule_key);

COMMENT ON COLUMN rule_items.rule_key IS
  '命名约定要先定下来再大量写规则，改起来很贵。建议 领域.技术.规则名';
COMMENT ON COLUMN rule_items.level IS
  'REQUIRED 级规则必须能回答"违反了会怎样"，答不上来的应降为 RECOMMENDED';
