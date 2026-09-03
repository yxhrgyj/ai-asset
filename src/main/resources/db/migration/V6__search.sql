-- V6: 中文搜索
--
-- 方案文档 5.4：用 pg_trgm 三元组索引 + ILIKE 子串匹配，不装分词插件。
--
-- 为什么不用 zhparser：
--   1. 需要自定义 PostgreSQL 镜像（官方镜像不含），部署复杂度上一个台阶
--   2. 其许可未能确认
--   3. 几百到几千条资产的量级，子串匹配已经够快
--
-- 取舍：pg_trgm 做子串匹配而非语义分词，所以搜"状态管理"不会命中"管理状态"。
-- 但反过来，搜"组件库"一定命中含这三个字的文本，没有分词器不认识某个
-- 技术词汇导致的漏召问题。对"我记得有份文档提到过 Pinia"这类真实场景更可靠。
--
-- 将来资产上万且搜索体验成为问题，加 tsvector 列即可，不改现有表结构。

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 资产：名称、摘要、分类。
CREATE INDEX assets_name_trgm_idx    ON assets USING gin (name gin_trgm_ops);
CREATE INDEX assets_summary_trgm_idx ON assets USING gin (summary gin_trgm_ops)
  WHERE summary IS NOT NULL;

-- 标签用数组包含判断，不需要 trgm（V2 已建 gin 索引）。

-- 版本正文。资产正文通常较长，是搜索命中的主要来源。
CREATE INDEX asset_versions_body_trgm_idx
  ON asset_versions USING gin (body gin_trgm_ops)
  WHERE body IS NOT NULL;

-- 规则条目：标题与正文。
CREATE INDEX rule_items_title_trgm_idx ON rule_items USING gin (title gin_trgm_ops);
CREATE INDEX rule_items_body_trgm_idx  ON rule_items USING gin (body gin_trgm_ops);

-- 入库文本文件正文（Markdown 等）。
CREATE INDEX asset_files_text_trgm_idx
  ON asset_files USING gin (text_content gin_trgm_ops)
  WHERE text_content IS NOT NULL;

COMMENT ON INDEX assets_name_trgm_idx IS
  'pg_trgm 三元组索引，加速 ILIKE ''%关键词%''。中文两字词也能命中，但单字查询会退化为全表扫描——UI 层应要求至少两字';
