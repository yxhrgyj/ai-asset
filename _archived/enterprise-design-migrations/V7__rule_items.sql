-- V7: 规则条目
--
-- 对应设计文档 §8.3 Rule 元数据、§10.4 规则组合顺序。
--
-- 一个 RULE 类型的资产版本（如 vue3-typescript@1.2.0）包含多条规则条目。
-- 冲突检测、继承判定与互斥标记都作用在条目级，而非资产级，
-- 因此条目需独立成表并可按 rule_key 查询。

CREATE TABLE rule_items (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  asset_version_id  UUID        NOT NULL REFERENCES asset_versions(id) ON DELETE CASCADE,

  -- 稳定 ID（§8.3 的 `id`，如 frontend.vue.no-any）。
  -- 跨版本保持不变，是冲突判断、例外绑定与影响分析的锚点。
  rule_key          TEXT        NOT NULL,
  title             TEXT        NOT NULL,
  body              TEXT        NOT NULL,

  level             TEXT        NOT NULL
                    CHECK (level IN ('REQUIRED', 'RECOMMENDED')),

  -- 作用路径（§8.3 的 scope，如 frontend/**）。与 assets.scope 不同概念。
  path_globs        TEXT[]      NOT NULL DEFAULT '{}',

  -- 适用条件（§8.3 的 appliesWhen，如 {framework: vue3, language: typescript}）。
  applies_when      JSONB       NOT NULL DEFAULT '{}'::jsonb,

  -- 显式覆盖声明（§10.4 冲突检查第二类）。
  -- 声明覆盖某 rule_key 但无对应已批准例外时，组合阶段阻断。
  overrides_rule_key TEXT,

  -- 互斥标记（§8.3 的 mutuallyExclusiveWith）。
  -- 元数据层面的声明式约束，引入时机械比对并提示，不做语义分析。
  mutually_exclusive_with TEXT[] NOT NULL DEFAULT '{}',

  sort_order        INTEGER     NOT NULL DEFAULT 0,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- 同一版本内 rule_key 不得重复（§10.4 冲突检查第一类的数据库防线）。
  CONSTRAINT rule_items_no_self_override
    CHECK (overrides_rule_key IS DISTINCT FROM rule_key),
  CONSTRAINT rule_items_key_format
    CHECK (rule_key ~ '^[a-z0-9]+(\.[a-z0-9-]+)+$')
);

CREATE UNIQUE INDEX rule_items_key_uk ON rule_items(asset_version_id, rule_key);
CREATE INDEX rule_items_key_idx ON rule_items(rule_key);
CREATE INDEX rule_items_level_idx ON rule_items(asset_version_id, level);
CREATE INDEX rule_items_applies_when_idx ON rule_items USING gin(applies_when);
CREATE INDEX rule_items_overrides_idx
  ON rule_items(overrides_rule_key) WHERE overrides_rule_key IS NOT NULL;

COMMENT ON COLUMN rule_items.rule_key IS
  '稳定 Rule ID，跨版本不变。格式如 frontend.vue.no-any。命名约定由内容写作规范定义（§19.3）';
COMMENT ON COLUMN rule_items.mutually_exclusive_with IS
  '互斥的 rule_key 或资产 slug。不能替代审批人对语义冲突的判断（§10.4）';
