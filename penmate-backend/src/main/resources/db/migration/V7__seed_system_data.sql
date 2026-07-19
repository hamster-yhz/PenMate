-- Required non-secret system metadata. Administrator credentials and model
-- secrets are created by the application bootstrap after Flyway completes.

INSERT INTO iam_roles (role_id, name, code, description, is_system)
VALUES (1, 'Administrator', 'ROLE_ADMIN', 'System administrator with full access', TRUE)
ON CONFLICT (role_id) DO UPDATE SET
    name = EXCLUDED.name,
    code = EXCLUDED.code,
    description = EXCLUDED.description,
    is_system = EXCLUDED.is_system,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO model_providers (provider_id, code, name, base_url, auth_type, status) VALUES
    (1, 'openai', 'OpenAI', 'https://api.openai.com/v1', 'bearer', 'active'),
    (2, 'xai', 'xAI', 'https://api.x.ai/v1', 'bearer', 'active'),
    (3, 'longcat', 'Longcat', NULL, 'bearer', 'active'),
    (4, 'claude', 'Claude', 'https://api.anthropic.com', 'bearer', 'active'),
    (5, 'gemini', 'Gemini', 'https://generativelanguage.googleapis.com/v1beta/openai', 'bearer', 'active'),
    (6, 'deepseek', 'DeepSeek', 'https://api.deepseek.com/v1', 'bearer', 'active'),
    (7, 'openai-compatible', 'OpenAI Compatible', NULL, 'bearer', 'active')
ON CONFLICT (provider_id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    base_url = EXCLUDED.base_url,
    auth_type = EXCLUDED.auth_type,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO iam_permissions (permission_id, name, code, module, description) VALUES
    (1, 'RBAC Admin Access', 'rbac:admin:access', 'rbac', 'Access RBAC admin panel'),
    (2, 'View Users', 'rbac:user:read', 'rbac', 'View user list'),
    (3, 'Manage Users', 'rbac:user:write', 'rbac', 'Create or update users'),
    (4, 'Delete Users', 'rbac:user:delete', 'rbac', 'Delete users'),
    (5, 'View Roles', 'rbac:role:read', 'rbac', 'View role list'),
    (6, 'Manage Roles', 'rbac:role:write', 'rbac', 'Create or update roles'),
    (7, 'Delete Roles', 'rbac:role:delete', 'rbac', 'Delete roles'),
    (8, 'View Permissions', 'rbac:permission:read', 'rbac', 'View permission list'),
    (9, 'Bind User Roles', 'rbac:user:bind-role', 'rbac', 'Assign or remove user roles'),
    (10, 'Bind Role Permissions', 'rbac:role:bind-permission', 'rbac', 'Assign or remove role permissions'),
    (11, 'View Menus', 'rbac:menu:read', 'rbac', 'View menu tree')
ON CONFLICT (permission_id) DO UPDATE SET
    name = EXCLUDED.name,
    code = EXCLUDED.code,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO iam_menus (menu_id, parent_id, title, path, sort_order, permission_code, visible) VALUES
    (1, NULL, 'Dashboard', '/', 1, NULL, TRUE),
    (2, NULL, 'My Books', '/mybooks', 2, NULL, TRUE),
    (3, NULL, 'Workbench', '/workbench', 3, NULL, TRUE),
    (4, NULL, 'Profile', '/profile', 4, NULL, TRUE),
    (5, NULL, 'Admin RBAC', '/admin/rbac', 5, 'rbac:admin:access', TRUE)
ON CONFLICT (menu_id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    title = EXCLUDED.title,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    permission_code = EXCLUDED.permission_code,
    visible = EXCLUDED.visible,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT 1, permission_id
FROM iam_permissions
WHERE permission_id BETWEEN 1 AND 11
ON CONFLICT (role_id, permission_id) DO NOTHING;
