-- 仅用于本地开发和接口验收的演示账号。
-- 生产环境不得使用这些账号或密码。

INSERT INTO sys_user
    (department_id, employee_no, username, password_hash, real_name, email, status)
SELECT
    d.id,
    'DEMO-ADMIN',
    'admin',
    '$2b$10$.lu2aY3Yu/uvJrixPfRyru/QPjsftH2O9rC8pRTvMgHDuTmsHYZe.',
    'Demo Administrator',
    'admin@example.local',
    1
FROM department d
WHERE d.code = 'HEAD_OFFICE'
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.username = 'admin');

INSERT INTO sys_user
    (department_id, employee_no, username, password_hash, real_name, email, status)
SELECT
    d.id,
    'DEMO-EMPLOYEE',
    'employee',
    '$2b$10$rO8aW.kMSntdBf7Jdy8v4.k.loleuBeoBOUsD4mZ1CJAia1pw6Mj.',
    'Demo Employee',
    'employee@example.local',
    1
FROM department d
WHERE d.code = 'R_AND_D'
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.username = 'employee');

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
INNER JOIN sys_role r ON r.code = 'ADMIN'
WHERE u.username = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_user_role ur
      WHERE ur.user_id = u.id
        AND ur.role_id = r.id
  );

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
INNER JOIN sys_role r ON r.code = 'EMPLOYEE'
WHERE u.username = 'employee'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_user_role ur
      WHERE ur.user_id = u.id
        AND ur.role_id = r.id
  );
