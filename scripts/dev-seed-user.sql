-- 本地开发用的测试账号。仅用于开发环境验证接口，不要在服务器上执行。
--
-- 为什么用 SQL 而不是走接口：创建用户的管理端点还没写（属于第 4 步），
-- 而验证资产读写需要一个 AUTHOR 角色的登录态。管理员口令已被使用者本人
-- 修改，不应为了测试把它重置掉。
--
-- 口令哈希用 pgcrypto 的 crypt() + gen_salt('bf') 生成，产出 $2a$ 前缀，
-- Spring Security 的 BCryptPasswordEncoder 可以直接校验。
-- must_change_password 置 false，省掉测试时的首登改密流程。

insert into users (username, display_name, email, password_hash, must_change_password, role, status)
values (
  'devauthor',
  '开发测试编写者',
  'devauthor@example.local',
  crypt('DevOnly_0901', gen_salt('bf')),
  false,
  'AUTHOR',
  'ACTIVE'
)
-- 唯一约束建在 lower(username) 表达式上（见 V1 的 users_username_uk），
-- 因此 ON CONFLICT 必须写成同一个表达式，写成 (username) 会报找不到约束。
on conflict (lower(username)) do update
  set password_hash = crypt('DevOnly_0901', gen_salt('bf')),
      must_change_password = false,
      role = 'AUTHOR',
      status = 'ACTIVE';

select username, role, status from users where username = 'devauthor';
