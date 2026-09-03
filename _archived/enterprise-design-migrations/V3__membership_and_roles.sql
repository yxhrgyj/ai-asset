-- V3: 团队成员关系与角色
--
-- 对应设计文档 §5 用户与角色、§16.1 权限判定矩阵。
-- 团队成员关系与项目成员关系是权限判定的两个输入维度，
-- 判定规则见 §16.1，实现为单一策略函数（实施计划第 2.4 节）。

-- 团队成员关系。一人可属多团队，users.primary_team_id 只是主属团队。
CREATE TABLE team_members (
  team_id    UUID        NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
  user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (team_id, user_id)
);
CREATE INDEX team_members_user_idx ON team_members(user_id);

-- 角色授予。角色可以是全局的（team_id / project_id 均为空），
-- 也可以限定在某团队范围内。项目范围的角色见 V6 的 project_members。
--
-- 角色取值对应设计文档 §5：
--   VIEWER        普通使用者，全员默认拥有，不需显式授予
--   AUTHOR        资产作者
--   APPROVER      审批人
--   AUDITOR       审计员，可见范围最广（见 §16.1 矩阵）
--   PLATFORM_ADMIN 平台管理员
CREATE TABLE role_grants (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role       TEXT        NOT NULL
             CHECK (role IN ('AUTHOR', 'APPROVER', 'AUDITOR', 'PLATFORM_ADMIN')),
  team_id    UUID        REFERENCES teams(id) ON DELETE CASCADE,
  granted_by UUID        REFERENCES users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- 平台管理员与审计员是全局角色，不得限定团队范围。
  CONSTRAINT role_grants_global_roles_have_no_team
    CHECK (role NOT IN ('AUDITOR', 'PLATFORM_ADMIN') OR team_id IS NULL)
);
-- 同一用户在同一范围内不重复授予同一角色。
-- team_id 可空，故用两个部分唯一索引而非单一 UNIQUE。
CREATE UNIQUE INDEX role_grants_scoped_uk
  ON role_grants(user_id, role, team_id) WHERE team_id IS NOT NULL;
CREATE UNIQUE INDEX role_grants_global_uk
  ON role_grants(user_id, role) WHERE team_id IS NULL;

COMMENT ON TABLE role_grants IS
  'VIEWER 不在此表出现——全员默认可见 PUBLIC 资产，见 §16.1 矩阵';
