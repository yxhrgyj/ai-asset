-- V5: 四条不变量（触发器部分）
--
-- 这四条是"可追溯"的地基。方案文档第 7 节说明了为什么不能只靠应用层校验：
-- 应用层校验改一次代码就可能失效，而数据库约束无法被业务代码绕过。
--
-- 其中"审批人 ≠ 提交人"和"版本号唯一"已在 V4 / V2 用 CHECK 与 UNIQUE 表达，
-- 本文件处理另两条需要触发器的：正式版本不可变、审计日志只追加。

-- ── 不变量一：正式版本内容不可变 ──
-- 版本进入 PUBLISHED 后正文与哈希冻结。
-- 只允许状态向 DEPRECATED / WITHDRAWN 流转。
CREATE OR REPLACE FUNCTION trg_asset_versions_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.status IN ('PUBLISHED', 'DEPRECATED', 'WITHDRAWN') THEN
    IF NEW.body         IS DISTINCT FROM OLD.body
    OR NEW.content_hash IS DISTINCT FROM OLD.content_hash
    OR NEW.version_no   IS DISTINCT FROM OLD.version_no
    OR NEW.asset_id     IS DISTINCT FROM OLD.asset_id
    OR NEW.created_by   IS DISTINCT FROM OLD.created_by
    OR NEW.published_at IS DISTINCT FROM OLD.published_at THEN
      RAISE EXCEPTION
        '正式版本不可变：版本 % 处于 % 状态，要改内容请发新版本',
        OLD.version_no, OLD.status
        USING ERRCODE = 'integrity_constraint_violation';
    END IF;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER asset_versions_immutable
  BEFORE UPDATE ON asset_versions
  FOR EACH ROW EXECUTE FUNCTION trg_asset_versions_immutable();

-- 已发布版本的文件同样不可变。
CREATE OR REPLACE FUNCTION trg_asset_files_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
  v_status TEXT;
BEGIN
  SELECT status INTO v_status FROM asset_versions
   WHERE id = COALESCE(NEW.asset_version_id, OLD.asset_version_id);

  -- 版本行已被级联删除时不阻断（整版本清理场景）。
  IF v_status IS NULL THEN
    RETURN COALESCE(NEW, OLD);
  END IF;

  IF v_status IN ('PUBLISHED', 'DEPRECATED', 'WITHDRAWN') THEN
    RAISE EXCEPTION
      '正式版本不可变：版本处于 % 状态，禁止增删改其文件', v_status
      USING ERRCODE = 'integrity_constraint_violation';
  END IF;
  RETURN COALESCE(NEW, OLD);
END $$;

CREATE TRIGGER asset_files_immutable
  BEFORE INSERT OR UPDATE OR DELETE ON asset_files
  FOR EACH ROW EXECUTE FUNCTION trg_asset_files_immutable();

-- 规则条目同理：已发布版本的规则不可改。
CREATE TRIGGER rule_items_immutable
  BEFORE INSERT OR UPDATE OR DELETE ON rule_items
  FOR EACH ROW EXECUTE FUNCTION trg_asset_files_immutable();

-- ── 不变量二：审计日志只追加 ──
CREATE OR REPLACE FUNCTION trg_append_only()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION '审计日志只允许追加，禁止 % 操作', TG_OP
    USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER audit_logs_append_only
  BEFORE UPDATE OR DELETE ON audit_logs
  FOR EACH ROW EXECUTE FUNCTION trg_append_only();

-- ── 附加：项目选用规则只能指向已发布版本 ──
CREATE OR REPLACE FUNCTION trg_project_rule_needs_published()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
  v_status TEXT;
BEGIN
  SELECT status INTO v_status FROM asset_versions WHERE id = NEW.asset_version_id;
  IF v_status NOT IN ('PUBLISHED', 'DEPRECATED') THEN
    RAISE EXCEPTION
      '项目只能选用已发布或已弃用的版本，当前为 %', v_status
      USING ERRCODE = 'integrity_constraint_violation';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER project_rule_needs_published
  BEFORE INSERT OR UPDATE ON project_rules
  FOR EACH ROW EXECUTE FUNCTION trg_project_rule_needs_published();
