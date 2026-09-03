-- V12: 不变量触发器
--
-- 本文件覆盖 CHECK 约束表达不了的不变量：跨表条件与不可变性。
-- 对应实施计划第 2.2 节。每条都必须有负例测试（S0 退出条件）。

-- ── 不变量一：正式版本内容不可变（§7.4、§12.1）──
-- 版本进入 AVAILABLE 后，内容相关字段冻结。
-- 只允许状态向 DEPRECATED / YANKED / ARCHIVED 流转及其时间戳、原因、supersede 更新。
CREATE OR REPLACE FUNCTION trg_asset_versions_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.status IN ('AVAILABLE', 'DEPRECATED', 'YANKED', 'ARCHIVED') THEN
    IF NEW.content_hash    IS DISTINCT FROM OLD.content_hash
    OR NEW.version         IS DISTINCT FROM OLD.version
    OR NEW.version_ordinal IS DISTINCT FROM OLD.version_ordinal
    OR NEW.asset_id        IS DISTINCT FROM OLD.asset_id
    OR NEW.created_by      IS DISTINCT FROM OLD.created_by
    OR NEW.approved_by     IS DISTINCT FROM OLD.approved_by
    OR NEW.approved_at     IS DISTINCT FROM OLD.approved_at
    OR NEW.metadata_snapshot IS DISTINCT FROM OLD.metadata_snapshot THEN
      RAISE EXCEPTION
        '正式版本不可变：版本 % 处于 % 状态，禁止修改内容或审批事实',
        OLD.version, OLD.status
        USING ERRCODE = 'integrity_constraint_violation';
    END IF;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER asset_versions_immutable
  BEFORE UPDATE ON asset_versions
  FOR EACH ROW EXECUTE FUNCTION trg_asset_versions_immutable();

-- 正式版本的文件同样不可变。
CREATE OR REPLACE FUNCTION trg_asset_files_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
  v_status TEXT;
  v_version_id UUID;
BEGIN
  v_version_id := COALESCE(NEW.asset_version_id, OLD.asset_version_id);
  SELECT status INTO v_status FROM asset_versions WHERE id = v_version_id;

  -- 版本行已被级联删除时（整版本清理），不阻断文件删除。
  IF v_status IS NULL THEN
    RETURN COALESCE(NEW, OLD);
  END IF;

  IF v_status IN ('AVAILABLE', 'DEPRECATED', 'YANKED', 'ARCHIVED') THEN
    RAISE EXCEPTION
      '正式版本不可变：版本处于 % 状态，禁止增删改其文件（替换文件必须创建新版本）',
      v_status
      USING ERRCODE = 'integrity_constraint_violation';
  END IF;
  RETURN COALESCE(NEW, OLD);
END $$;

CREATE TRIGGER asset_files_immutable
  BEFORE INSERT OR UPDATE OR DELETE ON asset_files
  FOR EACH ROW EXECUTE FUNCTION trg_asset_files_immutable();
