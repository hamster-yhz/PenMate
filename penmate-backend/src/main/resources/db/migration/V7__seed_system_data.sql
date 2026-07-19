-- Required non-secret system metadata only. Accounts, credentials, model
-- configurations, projects, and demo cases are created outside Flyway.
INSERT INTO iam_roles (role_id, name, code, description, is_system)
VALUES (1, 'Administrator', 'ROLE_ADMIN', 'System administrator', TRUE)
ON CONFLICT (role_id) DO UPDATE SET
    name = EXCLUDED.name,
    code = EXCLUDED.code,
    description = EXCLUDED.description,
    is_system = EXCLUDED.is_system,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO model_providers (provider_id, code, name, base_url, auth_type, status) VALUES
    (1, 'openai', 'OpenAI', 'https://api.openai.com/v1', 'BEARER', 'ACTIVE'),
    (2, 'xai', 'xAI', 'https://api.x.ai/v1', 'BEARER', 'ACTIVE'),
    (3, 'longcat', 'LongCat', NULL, 'BEARER', 'ACTIVE'),
    (4, 'claude', 'Anthropic Claude', 'https://api.anthropic.com', 'BEARER', 'ACTIVE'),
    (5, 'gemini', 'Google Gemini OpenAI Compatibility', 'https://generativelanguage.googleapis.com/v1beta/openai', 'BEARER', 'ACTIVE'),
    (6, 'deepseek', 'DeepSeek', 'https://api.deepseek.com/v1', 'BEARER', 'ACTIVE'),
    (7, 'openai-compatible', 'OpenAI Compatible', NULL, 'BEARER', 'ACTIVE')
ON CONFLICT (provider_id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    base_url = EXCLUDED.base_url,
    auth_type = EXCLUDED.auth_type,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP(3),
    deleted_at = NULL;

-- Implemented protocols are seeded as active. Reserved future capabilities are
-- represented by the capability constraint and will be seeded when adapters exist.
INSERT INTO model_provider_capabilities (
    provider_capability_id, provider_id, capability_code, protocol_code, status
) VALUES
    (101, 1, 'CHAT', 'OPENAI_CHAT_COMPLETIONS', 'ACTIVE'),
    (102, 1, 'EMBEDDING', 'OPENAI_EMBEDDINGS', 'ACTIVE'),
    (201, 2, 'CHAT', 'OPENAI_CHAT_COMPLETIONS', 'ACTIVE'),
    (301, 3, 'CHAT', 'OPENAI_CHAT_COMPLETIONS', 'ACTIVE'),
    (401, 4, 'CHAT', 'ANTHROPIC_MESSAGES', 'ACTIVE'),
    (501, 5, 'CHAT', 'OPENAI_CHAT_COMPLETIONS', 'ACTIVE'),
    (502, 5, 'EMBEDDING', 'OPENAI_EMBEDDINGS', 'ACTIVE'),
    (601, 6, 'CHAT', 'OPENAI_CHAT_COMPLETIONS', 'ACTIVE'),
    (701, 7, 'CHAT', 'OPENAI_CHAT_COMPLETIONS', 'ACTIVE'),
    (702, 7, 'EMBEDDING', 'OPENAI_EMBEDDINGS', 'ACTIVE')
ON CONFLICT (provider_capability_id) DO UPDATE SET
    provider_id = EXCLUDED.provider_id,
    capability_code = EXCLUDED.capability_code,
    protocol_code = EXCLUDED.protocol_code,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP(3),
    deleted_at = NULL;

INSERT INTO iam_permissions (permission_id, name, code, module, description) VALUES
    (1, 'RBAC Admin Access', 'rbac:admin:access', 'rbac', 'Access RBAC administration'),
    (2, 'View Users', 'rbac:user:read', 'rbac', 'View users'),
    (3, 'Manage Users', 'rbac:user:write', 'rbac', 'Create or update users'),
    (4, 'Delete Users', 'rbac:user:delete', 'rbac', 'Delete users'),
    (5, 'View Roles', 'rbac:role:read', 'rbac', 'View roles'),
    (6, 'Manage Roles', 'rbac:role:write', 'rbac', 'Create or update roles'),
    (7, 'Delete Roles', 'rbac:role:delete', 'rbac', 'Delete roles'),
    (8, 'View Permissions', 'rbac:permission:read', 'rbac', 'View permissions'),
    (9, 'Bind User Roles', 'rbac:user:bind-role', 'rbac', 'Assign or remove user roles'),
    (10, 'Bind Role Permissions', 'rbac:role:bind-permission', 'rbac', 'Assign or remove role permissions'),
    (11, 'View Menus', 'rbac:menu:read', 'rbac', 'View menu tree'),
    (12, 'Manage System Models', 'model:system:write', 'model', 'Manage system model configurations'),
    (13, 'View Operations', 'ops:job:read', 'ops', 'View operational jobs'),
    (14, 'Manage Operations', 'ops:job:write', 'ops', 'Cancel or retry operational jobs')
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
    (5, NULL, 'Admin RBAC', '/admin/rbac', 5, 'rbac:admin:access', TRUE),
    (6, NULL, 'System Models', '/admin/models', 6, 'model:system:write', TRUE),
    (7, NULL, 'Operations', '/admin/ops', 7, 'ops:job:read', TRUE)
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
WHERE permission_id BETWEEN 1 AND 14
ON CONFLICT (role_id, permission_id) DO NOTHING;
