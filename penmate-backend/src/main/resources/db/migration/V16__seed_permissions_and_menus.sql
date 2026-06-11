-- V16: Seed permissions, menus, and bind all to ROLE_ADMIN (role_id = 1)

-- ============================================================
-- Permissions
-- ============================================================
INSERT INTO iam_permissions (permission_id, name, code, module, description)
SELECT 1, 'RBAC Admin Access', 'rbac:admin:access', 'rbac', 'Access RBAC admin panel'
WHERE NOT EXISTS (SELECT 1 FROM iam_permissions WHERE permission_id = 1);

INSERT INTO iam_permissions (permission_id, name, code, module, description)
SELECT 2, 'View Users', 'rbac:user:read', 'rbac', 'View user list'
WHERE NOT EXISTS (SELECT 1 FROM iam_permissions WHERE permission_id = 2);

INSERT INTO iam_permissions (permission_id, name, code, module, description)
SELECT 3, 'Manage Users', 'rbac:user:write', 'rbac', 'Create or update users'
WHERE NOT EXISTS (SELECT 1 FROM iam_permissions WHERE permission_id = 3);

INSERT INTO iam_permissions (permission_id, name, code, module, description)
SELECT 4, 'Delete Users', 'rbac:user:delete', 'rbac', 'Delete users'
WHERE NOT EXISTS (SELECT 1 FROM iam_permissions WHERE permission_id = 4);

INSERT INTO iam_permissions (permission_id, name, code, module, description)
SELECT 5, 'View Roles', 'rbac:role:read', 'rbac', 'View role list'
WHERE NOT EXISTS (SELECT 1 FROM iam_permissions WHERE permission_id = 5);

INSERT INTO iam_permissions (permission_id, name, code, module, description)
SELECT 6, 'Manage Roles', 'rbac:role:write', 'rbac', 'Create or update roles'
WHERE NOT EXISTS (SELECT 1 FROM iam_permissions WHERE permission_id = 6);

INSERT INTO iam_permissions (permission_id, name, code, module, description)
SELECT 7, 'Delete Roles', 'rbac:role:delete', 'rbac', 'Delete roles'
WHERE NOT EXISTS (SELECT 1 FROM iam_permissions WHERE permission_id = 7);

INSERT INTO iam_permissions (permission_id, name, code, module, description)
SELECT 8, 'View Permissions', 'rbac:permission:read', 'rbac', 'View permission list'
WHERE NOT EXISTS (SELECT 1 FROM iam_permissions WHERE permission_id = 8);

INSERT INTO iam_permissions (permission_id, name, code, module, description)
SELECT 9, 'Bind User Roles', 'rbac:user:bind-role', 'rbac', 'Assign or remove user roles'
WHERE NOT EXISTS (SELECT 1 FROM iam_permissions WHERE permission_id = 9);

INSERT INTO iam_permissions (permission_id, name, code, module, description)
SELECT 10, 'Bind Role Permissions', 'rbac:role:bind-permission', 'rbac', 'Assign or remove role permissions'
WHERE NOT EXISTS (SELECT 1 FROM iam_permissions WHERE permission_id = 10);

INSERT INTO iam_permissions (permission_id, name, code, module, description)
SELECT 11, 'View Menus', 'rbac:menu:read', 'rbac', 'View menu tree'
WHERE NOT EXISTS (SELECT 1 FROM iam_permissions WHERE permission_id = 11);

-- ============================================================
-- Menus (visible in sidebar/nav)
-- ============================================================
INSERT INTO iam_menus (menu_id, parent_id, title, path, sort_order, permission_code, visible)
SELECT 1, NULL, 'Dashboard', '/', 1, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM iam_menus WHERE menu_id = 1);

INSERT INTO iam_menus (menu_id, parent_id, title, path, sort_order, permission_code, visible)
SELECT 2, NULL, 'My Books', '/mybooks', 2, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM iam_menus WHERE menu_id = 2);

INSERT INTO iam_menus (menu_id, parent_id, title, path, sort_order, permission_code, visible)
SELECT 3, NULL, 'Workbench', '/workbench', 3, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM iam_menus WHERE menu_id = 3);

INSERT INTO iam_menus (menu_id, parent_id, title, path, sort_order, permission_code, visible)
SELECT 4, NULL, 'Profile', '/profile', 4, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM iam_menus WHERE menu_id = 4);

INSERT INTO iam_menus (menu_id, parent_id, title, path, sort_order, permission_code, visible)
SELECT 5, NULL, 'Admin RBAC', '/admin/rbac', 5, 'rbac:admin:access', 1
WHERE NOT EXISTS (SELECT 1 FROM iam_menus WHERE menu_id = 5);

-- ============================================================
-- Bind all 11 permissions to ROLE_ADMIN (role_id = 1)
-- ============================================================
INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT 1, 1
WHERE NOT EXISTS (SELECT 1 FROM iam_role_permissions WHERE role_id = 1 AND permission_id = 1);

INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT 1, 2
WHERE NOT EXISTS (SELECT 1 FROM iam_role_permissions WHERE role_id = 1 AND permission_id = 2);

INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT 1, 3
WHERE NOT EXISTS (SELECT 1 FROM iam_role_permissions WHERE role_id = 1 AND permission_id = 3);

INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT 1, 4
WHERE NOT EXISTS (SELECT 1 FROM iam_role_permissions WHERE role_id = 1 AND permission_id = 4);

INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT 1, 5
WHERE NOT EXISTS (SELECT 1 FROM iam_role_permissions WHERE role_id = 1 AND permission_id = 5);

INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT 1, 6
WHERE NOT EXISTS (SELECT 1 FROM iam_role_permissions WHERE role_id = 1 AND permission_id = 6);

INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT 1, 7
WHERE NOT EXISTS (SELECT 1 FROM iam_role_permissions WHERE role_id = 1 AND permission_id = 7);

INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT 1, 8
WHERE NOT EXISTS (SELECT 1 FROM iam_role_permissions WHERE role_id = 1 AND permission_id = 8);

INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT 1, 9
WHERE NOT EXISTS (SELECT 1 FROM iam_role_permissions WHERE role_id = 1 AND permission_id = 9);

INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT 1, 10
WHERE NOT EXISTS (SELECT 1 FROM iam_role_permissions WHERE role_id = 1 AND permission_id = 10);

INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT 1, 11
WHERE NOT EXISTS (SELECT 1 FROM iam_role_permissions WHERE role_id = 1 AND permission_id = 11);
