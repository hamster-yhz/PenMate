-- Required non-secret system metadata only. Accounts, credentials, model
-- configurations, projects, and demo cases are created outside Flyway.
INSERT INTO iam_roles (role_id, name, code, description, is_system)
VALUES
    (1, '系统管理员', 'ROLE_ADMIN', '受保护的系统管理员角色', TRUE),
    (2, '普通用户', 'ROLE_USER', '使用个人创作工作区与自有模型', TRUE),
    (3, '官方模型用户', 'ROLE_OFFICIAL_MODEL_USER', '允许使用平台提供的官方模型', TRUE)
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
    (101, 1, 'CHAT', 'OPENAI_RESPONSES', 'ACTIVE'),
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
    (1, '进入业务工作区', 'app:access', '应用访问', '进入作品、工作台与个人设置'),
    (2, '查看作者资料', 'profile:author:read', '作者资料', '查看自己的作者资料'),
    (3, '编辑作者资料', 'profile:author:write', '作者资料', '编辑自己的作者资料'),
    (4, '查看作品', 'novel:read', '作品管理', '查看自己的作品、卷、章节与风格'),
    (5, '编辑作品', 'novel:write', '作品管理', '创建和编辑作品、卷、章节、封面与风格'),
    (6, '删除作品', 'novel:delete', '作品管理', '删除作品以及清理回收站'),
    (7, '导入作品', 'novel:import', '作品管理', '导入外部作品文件'),
    (8, '导出作品', 'novel:export', '作品管理', '导出自己的作品'),
    (9, '查看故事圣经', 'story-bible:read', '故事圣经', '查看故事圣经、关系与变更历史'),
    (10, '编辑故事圣经', 'story-bible:write', '故事圣经', '维护故事圣经节点、关系、分类与标签'),
    (11, '使用 Agent', 'agent:use', 'AI 创作', '运行 Agent、管理会话、待办与审批'),
    (12, '查看知识库', 'rag:read', '知识库', '查看项目知识库、索引状态与检索日志'),
    (13, '管理知识库', 'rag:write', '知识库', '上传文档、配置、重建或删除索引'),
    (14, '查看插件', 'plugin:read', '插件', '查看插件目录、安装状态与调用日志'),
    (15, '管理插件', 'plugin:write', '插件', '安装、启停和卸载项目插件'),
    (16, '使用用户模型', 'model:user:use', '模型', '在创作与知识库中使用自己的模型'),
    (17, '管理用户模型', 'model:user:write', '模型', '维护自己的模型配置、密钥和默认模型'),
    (18, '使用官方模型', 'model:official:use', '模型', '使用平台提供的官方 Chat 与 Embedding 模型'),
    (19, '管理官方模型', 'model:system:write', '模型', '创建、测试、编辑和删除官方模型'),
    (20, '查看用户', 'rbac:user:read', '用户管理', '查看用户目录与账号详情'),
    (21, '管理用户', 'rbac:user:write', '用户管理', '创建用户并编辑账号状态'),
    (22, '删除用户', 'rbac:user:delete', '用户管理', '删除或恢复用户账号'),
    (23, '分配用户角色', 'rbac:user:bind-role', '用户管理', '替换用户的完整角色集合'),
    (24, '查看角色', 'rbac:role:read', '角色权限', '查看角色目录与角色定义'),
    (25, '管理角色', 'rbac:role:write', '角色权限', '创建和编辑自定义角色'),
    (26, '删除角色', 'rbac:role:delete', '角色权限', '删除自定义角色'),
    (27, '分配角色权限', 'rbac:role:bind-permission', '角色权限', '替换自定义角色的完整权限集合'),
    (28, '查看权限目录', 'rbac:permission:read', '角色权限', '查看系统权限目录'),
    (29, '查看菜单定义', 'rbac:menu:read', '角色权限', '查看菜单与权限映射'),
    (30, '查看运维任务', 'ops:job:read', '运维', '查看后台任务及运行状态'),
    (31, '管理运维任务', 'ops:job:write', '运维', '重试、取消后台任务'),
    (32, '查看数据迁移', 'ops:migration:read', '运维', '查看数据迁移进度与结果'),
    (33, '执行数据迁移', 'ops:migration:write', '运维', '启动高风险数据迁移')
ON CONFLICT (permission_id) DO UPDATE SET
    name = EXCLUDED.name,
    code = EXCLUDED.code,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO iam_menus (menu_id, parent_id, title, path, sort_order, permission_code, visible) VALUES
    (1, NULL, 'Dashboard', '/', 1, NULL, TRUE),
    (2, NULL, 'My Books', '/mybooks', 2, 'novel:read', TRUE),
    (3, NULL, 'Workbench', '/workbench', 3, 'app:access', TRUE),
    (4, NULL, 'Profile', '/profile', 4, 'app:access', TRUE),
    (5, NULL, 'User Administration', '/admin/users', 5, 'rbac:user:read', TRUE),
    (6, NULL, 'Roles and Permissions', '/admin/rbac', 6, 'rbac:role:read', TRUE),
    (7, NULL, 'System Models', '/admin/models', 7, 'model:system:write', TRUE),
    (8, NULL, 'Operations', '/admin/tasks', 8, 'ops:job:read', TRUE)
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
WHERE permission_id BETWEEN 1 AND 33
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT 2, permission_id
FROM iam_permissions
WHERE permission_id BETWEEN 1 AND 17
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT 3, permission_id
FROM iam_permissions
WHERE permission_id = 18
ON CONFLICT (role_id, permission_id) DO NOTHING;
