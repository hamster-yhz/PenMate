-- V15: 种子管理员账户
-- 初始密码: Penmate@2024，首次登录后请立即修改

INSERT INTO iam_roles (role_id, name, code, description, is_system)
SELECT 1, 'Administrator', 'ROLE_ADMIN', 'System administrator with full access', 1
WHERE NOT EXISTS (SELECT 1 FROM iam_roles WHERE role_id = 1);

INSERT INTO iam_users (user_id, email, password_hash, display_name, status, auth_method)
SELECT 1, 'admin@penmate.you', 'Penmate@2024', 'Admin', 1, 'local'
WHERE NOT EXISTS (SELECT 1 FROM iam_users WHERE user_id = 1);

INSERT INTO iam_user_roles (user_id, role_id)
SELECT 1, 1
WHERE NOT EXISTS (SELECT 1 FROM iam_user_roles WHERE user_id = 1 AND role_id = 1);