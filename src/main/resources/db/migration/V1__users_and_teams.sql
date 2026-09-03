-- V1: 用户与团队
--
-- 约定（后续迁移遵守）：
--   主键 UUID，默认 gen_random_uuid()（PostgreSQL 13+ 内置）
--   时间 TIMESTAMPTZ
--   枚举用 TEXT + CHECK，不用原生 ENUM（原生 ENUM 增删值不能在事务中回滚）
--   凡"必须永远成立"的约束下沉到数据库，不靠应用层自觉

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 团队。仅用于展示资产归属，不参与权限判定。
CREATE TABLE teams (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name       TEXT        NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 用户。自建账号，口令用 bcrypt 哈希。
--
-- role 直接放在用户表上（不单设角色表）：v1 只有四种角色且一人一角色，
-- 单独建表属过度设计。将来需要一人多角色再拆。
CREATE TABLE users (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username             TEXT        NOT NULL,
  display_name         TEXT        NOT NULL,
  email                TEXT,
  -- bcrypt 哈希，60 字符。绝不存明文。
  password_hash        TEXT        NOT NULL,
  -- 管理员建号后首次登录强制改口令。
  must_change_password BOOLEAN     NOT NULL DEFAULT true,
  role                 TEXT        NOT NULL DEFAULT 'USER'
                       CHECK (role IN ('USER', 'AUTHOR', 'APPROVER', 'ADMIN')),
  team_id              UUID        REFERENCES teams(id) ON DELETE SET NULL,
  status               TEXT        NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE', 'DISABLED')),
  last_login_at        TIMESTAMPTZ,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- 口令哈希长度兜底：bcrypt 输出 60 字符，明文几乎不可能达到。
  -- 这条挡不住恶意写入，但能挡住"开发时图省事存明文"。
  CONSTRAINT users_password_looks_hashed CHECK (length(password_hash) >= 55)
);

CREATE UNIQUE INDEX users_username_uk ON users(lower(username));
CREATE INDEX users_team_idx ON users(team_id);
-- 支持"90 天未登录账号"清理列表（离职账号核对用）。
CREATE INDEX users_last_login_idx ON users(last_login_at NULLS FIRST)
  WHERE status = 'ACTIVE';

COMMENT ON COLUMN users.status IS
  '离职置 DISABLED，不删行——审批与审计记录引用了用户，删行会破坏追溯';
