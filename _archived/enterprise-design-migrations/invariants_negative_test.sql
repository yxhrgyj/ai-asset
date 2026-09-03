-- 不变量负例测试
--
-- 对应实施计划第 2.2 节与 S0 退出条件：
-- "每条不变量都有一个负例测试，证明违反时数据库拒绝写入"。
--
-- 运行方式（在已跑完全部迁移的空库上）：
--   psql -v ON_ERROR_STOP=1 -f invariants_negative_test.sql
-- 全部通过时打印 ALL INVARIANT TESTS PASSED 并回滚，不留数据。
--
-- 设计说明：每个用例用 assert_rejected() 包裹违规语句。
-- 该函数期望语句抛错；若语句意外成功，则测试失败——
-- 这正是负例测试的要点：不是验证操作能做，而是验证操作被拒绝。

BEGIN;

-- 断言辅助：执行 sql，期望其抛出异常。成功执行则视为测试失败。
CREATE OR REPLACE FUNCTION assert_rejected(label TEXT, sql TEXT)
RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
  BEGIN
    EXECUTE sql;
  EXCEPTION WHEN others THEN
    RAISE NOTICE 'PASS  %  (rejected: %)', label, replace(SQLERRM, E'\n', ' ');
    RETURN;
  END;
  RAISE EXCEPTION 'FAIL  %  —— 违规语句竟然执行成功，约束未生效', label;
END $$;

-- 断言辅助：执行 sql，期望其成功。用于确认约束没有过度收紧。
CREATE OR REPLACE FUNCTION assert_accepted(label TEXT, sql TEXT)
RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
  EXECUTE sql;
  RAISE NOTICE 'PASS  %  (accepted)', label;
END $$;

-- ── 测试夹具 ──
INSERT INTO organizations (id, name)
  VALUES ('00000000-0000-0000-0000-000000000001', 'TestOrg');
INSERT INTO teams (id, organization_id, name)
  VALUES ('00000000-0000-0000-0000-0000000000a1',
          '00000000-0000-0000-0000-000000000001', 'TeamA');
INSERT INTO users (id, organization_id, external_subject, username, display_name)
  VALUES ('00000000-0000-0000-0000-0000000000b1',
          '00000000-0000-0000-0000-000000000001', 'sub-author', 'author', '作者'),
         ('00000000-0000-0000-0000-0000000000b2',
          '00000000-0000-0000-0000-000000000001', 'sub-approver', 'approver', '审批人');

-- ══ 组一：权限判定矩阵（§16.1 非法组合）══

SELECT assert_rejected('16.1-a  scope=ORGANIZATION + TEAM_ONLY', $sql$
  INSERT INTO assets (organization_id, name, slug, type, visibility, scope, owner_team_id)
  VALUES ('00000000-0000-0000-0000-000000000001', '组织基线', 'org-base-1', 'RULE',
          'TEAM_ONLY', 'ORGANIZATION', '00000000-0000-0000-0000-0000000000a1')
$sql$);

SELECT assert_rejected('16.1-b  scope=ORGANIZATION + PROJECT_ONLY', $sql$
  INSERT INTO assets (organization_id, name, slug, type, visibility, scope)
  VALUES ('00000000-0000-0000-0000-000000000001', '组织基线', 'org-base-2', 'RULE',
          'PROJECT_ONLY', 'ORGANIZATION')
$sql$);

SELECT assert_rejected('16.1-c  scope=TEAM + PROJECT_ONLY', $sql$
  INSERT INTO assets (organization_id, name, slug, type, visibility, scope, owner_team_id)
  VALUES ('00000000-0000-0000-0000-000000000001', '团队规则', 'team-rule-1', 'RULE',
          'PROJECT_ONLY', 'TEAM', '00000000-0000-0000-0000-0000000000a1')
$sql$);

SELECT assert_rejected('16.1-d  TEAM_ONLY 缺 owner_team_id', $sql$
  INSERT INTO assets (organization_id, name, slug, type, visibility, scope)
  VALUES ('00000000-0000-0000-0000-000000000001', '无主团队资产', 'no-team-1', 'RULE',
          'TEAM_ONLY', 'TEAM')
$sql$);

SELECT assert_rejected('16.1-e  PRIVATE 缺 owner_user_id', $sql$
  INSERT INTO assets (organization_id, name, slug, type, visibility, scope)
  VALUES ('00000000-0000-0000-0000-000000000001', '无主私有资产', 'no-owner-1', 'DOCUMENT',
          'PRIVATE', 'PROJECT')
$sql$);

SELECT assert_accepted('16.1-f  合法：ORGANIZATION + PUBLIC', $sql$
  INSERT INTO assets (id, organization_id, name, slug, type, visibility, scope)
  VALUES ('00000000-0000-0000-0000-0000000000c1',
          '00000000-0000-0000-0000-000000000001', '组织安全基线', 'org-security', 'RULE',
          'PUBLIC', 'ORGANIZATION')
$sql$);

-- ══ 组二：正式版本不可变（§7.4、§12.1）══

-- 先造一个 DRAFT 版本并推进到 AVAILABLE。
INSERT INTO asset_versions (id, asset_id, version, version_ordinal, status,
                            content_hash, created_by)
VALUES ('00000000-0000-0000-0000-0000000000d1',
        '00000000-0000-0000-0000-0000000000c1', '1.0.0', 1, 'DRAFT',
        'hash-v1', '00000000-0000-0000-0000-0000000000b1');

INSERT INTO asset_files (id, asset_version_id, relative_path, file_type, mime_type,
                         size_bytes, content_hash, text_content)
VALUES ('00000000-0000-0000-0000-0000000000e1',
        '00000000-0000-0000-0000-0000000000d1', 'rule.md', 'MARKDOWN', 'text/markdown',
        42, 'hash-f1', '不得在代码、日志和配置中写入密钥。');

UPDATE asset_versions SET status = 'AVAILABLE', persisted_at = now()
WHERE id = '00000000-0000-0000-0000-0000000000d1';

SELECT assert_rejected('7.4-a  AVAILABLE 版本改 content_hash', $sql$
  UPDATE asset_versions SET content_hash = 'tampered'
  WHERE id = '00000000-0000-0000-0000-0000000000d1'
$sql$);

SELECT assert_rejected('7.4-b  AVAILABLE 版本改审批事实', $sql$
  UPDATE asset_versions SET approved_by = '00000000-0000-0000-0000-0000000000b1',
                            approved_at = now()
  WHERE id = '00000000-0000-0000-0000-0000000000d1'
$sql$);

SELECT assert_rejected('7.4-c  AVAILABLE 版本改文件内容', $sql$
  UPDATE asset_files SET text_content = '被篡改'
  WHERE id = '00000000-0000-0000-0000-0000000000e1'
$sql$);

SELECT assert_rejected('7.4-d  AVAILABLE 版本删文件', $sql$
  DELETE FROM asset_files WHERE id = '00000000-0000-0000-0000-0000000000e1'
$sql$);

SELECT assert_rejected('7.4-e  AVAILABLE 版本加文件', $sql$
  INSERT INTO asset_files (asset_version_id, relative_path, file_type, mime_type,
                           size_bytes, content_hash, text_content)
  VALUES ('00000000-0000-0000-0000-0000000000d1', 'extra.md', 'MARKDOWN', 'text/markdown',
          10, 'hash-f2', '事后追加')
$sql$);

SELECT assert_accepted('7.4-f  合法：AVAILABLE → DEPRECATED 状态流转', $sql$
  UPDATE asset_versions SET status = 'DEPRECATED', deprecated_at = now()
  WHERE id = '00000000-0000-0000-0000-0000000000d1'
$sql$);

-- ══ 组三：正式版本不允许 PRIVATE（§16.1，跨表双向）══

INSERT INTO assets (id, organization_id, name, slug, type, visibility, scope, owner_user_id)
VALUES ('00000000-0000-0000-0000-0000000000c2',
        '00000000-0000-0000-0000-000000000001', '私有草稿', 'private-draft', 'DOCUMENT',
        'PRIVATE', 'PROJECT', '00000000-0000-0000-0000-0000000000b1');

INSERT INTO asset_versions (id, asset_id, version, version_ordinal, status,
                            content_hash, created_by)
VALUES ('00000000-0000-0000-0000-0000000000d2',
        '00000000-0000-0000-0000-0000000000c2', '0.1.0', 1, 'DRAFT',
        'hash-p1', '00000000-0000-0000-0000-0000000000b1');

SELECT assert_rejected('16.1-g  方向一：PRIVATE 资产的版本转 AVAILABLE', $sql$
  UPDATE asset_versions SET status = 'AVAILABLE'
  WHERE id = '00000000-0000-0000-0000-0000000000d2'
$sql$);

SELECT assert_rejected('16.1-h  方向二：已有 AVAILABLE 版本的资产改 PRIVATE', $sql$
  UPDATE assets SET visibility = 'PRIVATE', owner_user_id = '00000000-0000-0000-0000-0000000000b1'
  WHERE id = '00000000-0000-0000-0000-0000000000c1'
$sql$);

-- ══ 组四：审批人不得为提交人（§5、§12.1）══

SELECT assert_rejected('12.1-a  审批人 = 提交人', $sql$
  INSERT INTO approval_requests (subject_type, asset_version_id, submitted_by,
                                 decided_by, decided_at, decision, frozen_hash)
  VALUES ('ASSET_VERSION', '00000000-0000-0000-0000-0000000000d2',
          '00000000-0000-0000-0000-0000000000b1',
          '00000000-0000-0000-0000-0000000000b1', now(), 'APPROVED', 'frozen-1')
$sql$);

SELECT assert_accepted('12.1-b  合法：审批人 ≠ 提交人', $sql$
  INSERT INTO approval_requests (subject_type, asset_version_id, submitted_by,
                                 decided_by, decided_at, decision, frozen_hash)
  VALUES ('ASSET_VERSION', '00000000-0000-0000-0000-0000000000d2',
          '00000000-0000-0000-0000-0000000000b1',
          '00000000-0000-0000-0000-0000000000b2', now(), 'APPROVED', 'frozen-2')
$sql$);

SELECT assert_rejected('12.1-c  决定三元组不完整（有 decided_by 无 decision）', $sql$
  INSERT INTO approval_requests (subject_type, asset_version_id, submitted_by,
                                 decided_by, frozen_hash)
  VALUES ('ASSET_VERSION', '00000000-0000-0000-0000-0000000000d2',
          '00000000-0000-0000-0000-0000000000b1',
          '00000000-0000-0000-0000-0000000000b2', 'frozen-3')
$sql$);

-- ══ 组五：审计日志只追加（§16.1）══

INSERT INTO audit_logs (action, target_type, target_id)
VALUES ('ASSET_PUBLISHED', 'ASSET_VERSION', '00000000-0000-0000-0000-0000000000d1');

SELECT assert_rejected('16.1-i  审计日志 UPDATE', $sql$
  UPDATE audit_logs SET action = 'TAMPERED' WHERE target_type = 'ASSET_VERSION'
$sql$);

SELECT assert_rejected('16.1-j  审计日志 DELETE', $sql$
  DELETE FROM audit_logs WHERE target_type = 'ASSET_VERSION'
$sql$);

-- ══ 组六：路径穿越防线（§16.3）══

SELECT assert_rejected('16.3-a  绝对路径', $sql$
  INSERT INTO asset_files (asset_version_id, relative_path, file_type, mime_type,
                           size_bytes, content_hash, text_content)
  VALUES ('00000000-0000-0000-0000-0000000000d2', '/etc/passwd', 'TEXT', 'text/plain',
          1, 'h', 'x')
$sql$);

SELECT assert_rejected('16.3-b  父目录穿越', $sql$
  INSERT INTO asset_files (asset_version_id, relative_path, file_type, mime_type,
                           size_bytes, content_hash, text_content)
  VALUES ('00000000-0000-0000-0000-0000000000d2', '../../secret.md', 'MARKDOWN',
          'text/markdown', 1, 'h', 'x')
$sql$);

SELECT assert_rejected('16.3-c  Windows 盘符', $sql$
  INSERT INTO asset_files (asset_version_id, relative_path, file_type, mime_type,
                           size_bytes, content_hash, text_content)
  VALUES ('00000000-0000-0000-0000-0000000000d2', 'C:\temp\x.md', 'MARKDOWN',
          'text/markdown', 1, 'h', 'x')
$sql$);

-- ══ 组七：内容存放位置互斥（§7.5）══

SELECT assert_rejected('7.5-a  text_content 与 storage_key 同时存在', $sql$
  INSERT INTO asset_files (asset_version_id, relative_path, file_type, mime_type,
                           size_bytes, content_hash, text_content, storage_key)
  VALUES ('00000000-0000-0000-0000-0000000000d2', 'both.md', 'MARKDOWN', 'text/markdown',
          1, 'h', '正文', 'key/both.md')
$sql$);

SELECT assert_rejected('7.5-b  两者都缺', $sql$
  INSERT INTO asset_files (asset_version_id, relative_path, file_type, mime_type,
                           size_bytes, content_hash)
  VALUES ('00000000-0000-0000-0000-0000000000d2', 'neither.md', 'MARKDOWN',
          'text/markdown', 1, 'h')
$sql$);

-- ══ 组八：规则条目（§8.3、§10.4）══

SELECT assert_rejected('10.4-a  同版本内重复 rule_key', $sql$
  INSERT INTO rule_items (asset_version_id, rule_key, title, body, level)
  VALUES ('00000000-0000-0000-0000-0000000000d2', 'organization.security.no-secrets',
          '禁止硬编码密钥', '不得写入密钥。', 'REQUIRED'),
         ('00000000-0000-0000-0000-0000000000d2', 'organization.security.no-secrets',
          '重复条目', '重复。', 'REQUIRED')
$sql$);

SELECT assert_rejected('8.3-a  rule_key 格式非法（无点分层级）', $sql$
  INSERT INTO rule_items (asset_version_id, rule_key, title, body, level)
  VALUES ('00000000-0000-0000-0000-0000000000d2', 'nodots', '标题', '正文', 'REQUIRED')
$sql$);

SELECT assert_rejected('8.3-b  rule_key 含大写', $sql$
  INSERT INTO rule_items (asset_version_id, rule_key, title, body, level)
  VALUES ('00000000-0000-0000-0000-0000000000d2', 'Frontend.Vue.NoAny',
          '标题', '正文', 'REQUIRED')
$sql$);

SELECT assert_rejected('10.4-b  规则自我覆盖', $sql$
  INSERT INTO rule_items (asset_version_id, rule_key, title, body, level, overrides_rule_key)
  VALUES ('00000000-0000-0000-0000-0000000000d2', 'frontend.vue.no-any',
          '标题', '正文', 'REQUIRED', 'frontend.vue.no-any')
$sql$);

-- ══ 组九：交付任务串行化（§13.2，MVP 预留但约束已生效）══

INSERT INTO projects (id, organization_id, name, slug, owner_user_id)
VALUES ('00000000-0000-0000-0000-0000000000f1',
        '00000000-0000-0000-0000-000000000001', '测试项目', 'test-proj',
        '00000000-0000-0000-0000-0000000000b1');

INSERT INTO delivery_tasks (project_id, idempotency_key, status)
VALUES ('00000000-0000-0000-0000-0000000000f1', 'idem-1', 'RUNNING');

SELECT assert_rejected('13.2-a  同项目并发第二个未完成任务', $sql$
  INSERT INTO delivery_tasks (project_id, idempotency_key, status)
  VALUES ('00000000-0000-0000-0000-0000000000f1', 'idem-2', 'QUEUED')
$sql$);

SELECT assert_rejected('13.2-b  幂等键重复', $sql$
  INSERT INTO delivery_tasks (project_id, idempotency_key, status)
  VALUES ('00000000-0000-0000-0000-0000000000f1', 'idem-1', 'SUCCEEDED')
$sql$);

-- 前任务先落地为终态，再排新任务。
-- 刻意分两句：数据修改 CTE 内 UPDATE 与 INSERT 的执行顺序无保证，
-- 而唯一索引在物理层检查，合并成一句会让本用例的结果依赖实现细节。
UPDATE delivery_tasks SET status = 'SUCCEEDED' WHERE idempotency_key = 'idem-1';

SELECT assert_accepted('13.2-c  合法：前任务完成后可排新任务', $sql$
  INSERT INTO delivery_tasks (project_id, idempotency_key, status)
  VALUES ('00000000-0000-0000-0000-0000000000f1', 'idem-3', 'QUEUED')
$sql$);

-- ══ 组十：绑定只能指向可用版本 ══

SELECT assert_rejected('12.2-a  绑定 DRAFT 版本', $sql$
  INSERT INTO project_bindings (project_id, asset_id, asset_version_id, source_scope,
                                approved_by)
  VALUES ('00000000-0000-0000-0000-0000000000f1',
          '00000000-0000-0000-0000-0000000000c2',
          '00000000-0000-0000-0000-0000000000d2', 'PROJECT',
          '00000000-0000-0000-0000-0000000000b2')
$sql$);

-- ══ 组十一：例外有效期与审批约束（§11）══

SELECT assert_rejected('11-a  失效时间早于生效时间', $sql$
  INSERT INTO rule_exceptions (project_id, base_asset_id, reason, impact_scope,
                               migration_plan, requested_by, starts_at, expires_at)
  VALUES ('00000000-0000-0000-0000-0000000000f1',
          '00000000-0000-0000-0000-0000000000c1', '原因', '范围', '迁移计划',
          '00000000-0000-0000-0000-0000000000b1', now(), now() - interval '1 day')
$sql$);

SELECT assert_rejected('11-b  例外审批人 = 申请人', $sql$
  INSERT INTO rule_exceptions (project_id, base_asset_id, reason, impact_scope,
                               migration_plan, requested_by, approved_by, approved_at,
                               status, starts_at, expires_at)
  VALUES ('00000000-0000-0000-0000-0000000000f1',
          '00000000-0000-0000-0000-0000000000c1', '原因', '范围', '迁移计划',
          '00000000-0000-0000-0000-0000000000b1',
          '00000000-0000-0000-0000-0000000000b1', now(), 'APPROVED',
          now(), now() + interval '30 days')
$sql$);

SELECT assert_rejected('11-c  APPROVED 状态缺审批人', $sql$
  INSERT INTO rule_exceptions (project_id, base_asset_id, reason, impact_scope,
                               migration_plan, requested_by, status, starts_at, expires_at)
  VALUES ('00000000-0000-0000-0000-0000000000f1',
          '00000000-0000-0000-0000-0000000000c1', '原因', '范围', '迁移计划',
          '00000000-0000-0000-0000-0000000000b1', 'APPROVED',
          now(), now() + interval '30 days')
$sql$);

-- ══ 组十二：包引用必须钉版本（§7.3）══

SELECT assert_rejected('7.3-a  PACKAGE_ITEM 未指定版本', $sql$
  INSERT INTO asset_relations (source_asset_id, target_asset_id, kind)
  VALUES ('00000000-0000-0000-0000-0000000000c1',
          '00000000-0000-0000-0000-0000000000c2', 'PACKAGE_ITEM')
$sql$);

SELECT assert_rejected('7.3-b  自引用', $sql$
  INSERT INTO asset_relations (source_asset_id, target_asset_id, kind)
  VALUES ('00000000-0000-0000-0000-0000000000c1',
          '00000000-0000-0000-0000-0000000000c1', 'RELATED_TO')
$sql$);

DO $$ BEGIN RAISE NOTICE '═══ ALL INVARIANT TESTS PASSED ═══'; END $$;

ROLLBACK;
