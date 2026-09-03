-- V14: 中文全文检索
--
-- 对应设计文档 §6.4 中文全文检索。使用 V1 建立的 chinese_zh 配置。
--
-- 设计取舍：用生成列 + GIN 索引，而非独立的 FTS 表 + 触发器同步。
-- 生成列由数据库维护，不存在同步遗漏；上游 PromptHub 用触发器同步 FTS5 表，
-- 每处写入都要记得维护，是 AI Coding 下容易漏的一类。

-- 资产检索向量：标题、描述、分类、标签。
-- 权重：A 标题 / B 分类与标签 / C 描述。
ALTER TABLE assets ADD COLUMN search_vector tsvector
  GENERATED ALWAYS AS (
    setweight(to_tsvector('chinese_zh', coalesce(name, '')), 'A') ||
    setweight(to_tsvector('chinese_zh', coalesce(category, '')), 'B') ||
    setweight(to_tsvector('chinese_zh', array_to_string(tags, ' ')), 'B') ||
    setweight(to_tsvector('chinese_zh', coalesce(description, '')), 'C')
  ) STORED;

CREATE INDEX assets_search_idx ON assets USING gin(search_vector);

-- 资产文件正文检索。仅对入库的文本内容建索引；
-- 走存储抽象层的二进制文件不参与全文检索。
ALTER TABLE asset_files ADD COLUMN search_vector tsvector
  GENERATED ALWAYS AS (
    to_tsvector('chinese_zh', coalesce(text_content, ''))
  ) STORED;

CREATE INDEX asset_files_search_idx ON asset_files USING gin(search_vector)
  WHERE text_content IS NOT NULL;

-- 规则条目检索：标题权重高于正文。
ALTER TABLE rule_items ADD COLUMN search_vector tsvector
  GENERATED ALWAYS AS (
    setweight(to_tsvector('chinese_zh', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('chinese_zh', coalesce(body, '')), 'C')
  ) STORED;

CREATE INDEX rule_items_search_idx ON rule_items USING gin(search_vector);

COMMENT ON COLUMN assets.search_vector IS
  '生成列，由数据库维护。检索效果需在 S1 用真实中文资产验证（含"组件库""状态管理""依赖注入"这类复合词），见实施计划 S1 退出条件';
