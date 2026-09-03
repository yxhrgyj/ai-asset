-- V1: 扩展、约定与中文检索配置
--
-- 全局约定（后续所有迁移遵守）：
--   1. 主键统一 UUID，默认值 gen_random_uuid()（PostgreSQL 13+ 内置）。
--   2. 时间统一 TIMESTAMPTZ，不使用 epoch 整数。
--   3. 枚举用 TEXT + CHECK 约束表达，不用原生 ENUM 类型。
--      理由：原生 ENUM 增删值需要 ALTER TYPE 且不能在事务中回滚，
--      迁移演进成本高；TEXT + CHECK 对 JPA 映射也更直接。
--   4. 不变量一律下沉到数据库（CHECK / UNIQUE / 外键 / 触发器），
--      不依赖应用层自觉。见实施计划第 2.2 节。
--   5. 交付链（GitLab / Trae）相关字段在 MVP 建表即预留但不写入，
--      避免 MVP 之后被动改表。见实施计划 S0 交付物。
--
-- 部署前提：本迁移要求 PostgreSQL 镜像内已安装 zhparser 扩展。
--   默认 postgres 官方镜像不含 zhparser，需自定义镜像。
--   若镜像缺少该扩展，本迁移会失败——这是有意为之：
--   中文检索失准属功能缺陷而非性能问题，不应静默降级为默认分词器。
--   见设计文档 §6.4 中文全文检索。

-- pgcrypto 提供 gen_random_uuid()。PostgreSQL 13+ 已内置同名函数，
-- 此处显式创建以兼容更低版本，已内置时该语句无副作用。
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 中文分词。SCWS 词库由镜像提供。
CREATE EXTENSION IF NOT EXISTS zhparser;

-- 中文全文检索配置。
-- 词典映射说明（zhparser 的 token 类型）：
--   n 名词 / v 动词 / a 形容词 / i 成语 / e 感叹 / l 习用语 / j 简称
-- 只索引这些实义词类，忽略助词、标点等，降低索引体积并提高精度。
DROP TEXT SEARCH CONFIGURATION IF EXISTS chinese_zh;
CREATE TEXT SEARCH CONFIGURATION chinese_zh (PARSER = zhparser);
ALTER TEXT SEARCH CONFIGURATION chinese_zh
  ADD MAPPING FOR n, v, a, i, e, l, j WITH simple;

-- 复合词切分参数不在此处设置。
--
-- zhparser 的 multi_short / multi_duality / multi_zmain 控制多粒度切分，
-- 使"组件库"既能整体命中也能按"组件"命中。但它们必须在
-- postgresql.conf 中固化，原因有两条：
--   1. SET 是会话级的，迁移会话结束即失效，对后续连接无影响；
--   2. 分词参数影响 to_tsvector 的输出，而 V14 的 search_vector 是
--      生成列——若建索引时与查询时的参数不一致，索引与查询结果会
--      不匹配，且这种不一致不会报错，只表现为搜不准。
--
-- 因此这三项属于部署配置而非迁移内容，必须写入镜像的 postgresql.conf：
--   zhparser.multi_short = on
--   zhparser.multi_duality = on
--   zhparser.multi_zmain = on
--
-- 变更这些参数后必须 REINDEX 所有 search_vector 索引，否则新旧数据
-- 的分词粒度不一致。

COMMENT ON TEXT SEARCH CONFIGURATION chinese_zh IS
  '中文全文检索配置。自定义词典（技术栈名、框架名、公司术语）需在镜像层维护，见设计文档 §6.4';
