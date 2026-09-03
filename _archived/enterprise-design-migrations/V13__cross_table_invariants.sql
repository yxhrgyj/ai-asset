-- V13: 跨表不变量
--
-- §16.1 非法组合三"正式版本不允许 PRIVATE"跨 assets 与 asset_versions 两表，
-- 无法用单表 CHECK 表达，需双向触发器：
--   方向一：版本转入 AVAILABLE 时，资产不得为 PRIVATE
--   方向二：资产改为 PRIVATE 时，不得已存在 AVAILABLE 版本
-- 只做一个方向会留下绕过路径。

CREATE OR REPLACE FUNCTION trg_version_available_forbids_private()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
  v_visibility TEXT;
BEGIN
  IF NEW.status = 'AVAILABLE'
     AND (TG_OP = 'INSERT' OR OLD.status IS DISTINCT FROM 'AVAILABLE') THEN
    SELECT visibility INTO v_visibility FROM assets WHERE id = NEW.asset_id;
    IF v_visibility = 'PRIVATE' THEN
      RAISE EXCEPTION
        '正式版本不允许 PRIVATE 可见性（§16.1）：请先将资产可见性调整为非 PRIVATE'
        USING ERRCODE = 'integrity_constraint_violation';
    END IF;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER version_available_forbids_private
  BEFORE INSERT OR UPDATE ON asset_versions
  FOR EACH ROW EXECUTE FUNCTION trg_version_available_forbids_private();

CREATE OR REPLACE FUNCTION trg_asset_private_forbids_available()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.visibility = 'PRIVATE' AND OLD.visibility IS DISTINCT FROM 'PRIVATE' THEN
    IF EXISTS (SELECT 1 FROM asset_versions
               WHERE asset_id = NEW.id AND status = 'AVAILABLE') THEN
      RAISE EXCEPTION
        '资产已存在 AVAILABLE 版本，不允许改为 PRIVATE（§16.1）'
        USING ERRCODE = 'integrity_constraint_violation';
    END IF;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER asset_private_forbids_available
  BEFORE UPDATE ON assets
  FOR EACH ROW EXECUTE FUNCTION trg_asset_private_forbids_available();

-- ── 审计日志只追加（§16.1）──
CREATE OR REPLACE FUNCTION trg_audit_logs_append_only()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION '审计日志只允许追加，禁止 % 操作', TG_OP
    USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER audit_logs_append_only
  BEFORE UPDATE OR DELETE ON audit_logs
  FOR EACH ROW EXECUTE FUNCTION trg_audit_logs_append_only();

-- ── 绑定只能指向正式可用版本 ──
-- 防止项目绑定到草稿或已撤回的版本。
CREATE OR REPLACE FUNCTION trg_binding_requires_usable_version()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
  v_status TEXT;
BEGIN
  SELECT status INTO v_status FROM asset_versions WHERE id = NEW.asset_version_id;
  IF v_status NOT IN ('AVAILABLE', 'DEPRECATED') THEN
    RAISE EXCEPTION
      '项目只能绑定 AVAILABLE 或 DEPRECATED 版本，当前为 %（§12.2 可下载性条件）', v_status
      USING ERRCODE = 'integrity_constraint_violation';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER binding_requires_usable_version
  BEFORE INSERT OR UPDATE ON project_bindings
  FOR EACH ROW EXECUTE FUNCTION trg_binding_requires_usable_version();
