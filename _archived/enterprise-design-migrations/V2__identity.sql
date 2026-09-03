-- V2: 身份与组织结构
--
-- 对应设计文档 §5 用户与角色、§16.1 身份与授权。
-- 身份来源是公司统一身份系统（OIDC / LDAP / SSO），本地不存口令。

-- 组织。单组织部署时仍显式建表，避免 scope=organization 的语义悬空。
CREATE TABLE organizations (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        TEXT        NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 部门 / 团队树。从企业身份系统同步。
CREATE TABLE teams (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID        NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
  parent_team_id  UUID        REFERENCES teams(id) ON DELETE RESTRICT,
  name            TEXT        NOT NULL,
  external_key    TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT teams_no_self_parent CHECK (parent_team_id IS DISTINCT FROM id)
);
CREATE UNIQUE INDEX teams_external_key_uk
  ON teams(organization_id, external_key) WHERE external_key IS NOT NULL;
CREATE INDEX teams_parent_idx ON teams(parent_team_id);

-- 用户。password_hash 故意不存在：认证由统一身份系统完成。
CREATE TABLE users (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id   UUID        NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
  external_subject  TEXT        NOT NULL,
  username          TEXT        NOT NULL,
  display_name      TEXT        NOT NULL,
  email             TEXT,
  primary_team_id   UUID        REFERENCES teams(id) ON DELETE SET NULL,
  status            TEXT        NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'DISABLED')),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- external_subject 是 OIDC sub 或 LDAP DN，跨身份源唯一。
CREATE UNIQUE INDEX users_external_subject_uk ON users(organization_id, external_subject);
CREATE UNIQUE INDEX users_username_uk ON users(organization_id, lower(username));
CREATE INDEX users_primary_team_idx ON users(primary_team_id);

COMMENT ON COLUMN users.external_subject IS
  '统一身份系统的稳定标识（OIDC sub / LDAP DN）。离职置 status=DISABLED，不删行，以保留审批与审计的引用完整性';
