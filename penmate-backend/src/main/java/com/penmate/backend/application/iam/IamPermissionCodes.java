package com.penmate.backend.application.iam;

/** Stable permission codes shared by HTTP guards and application policies. */
public final class IamPermissionCodes {
    public static final String APP_ACCESS = "app:access";
    public static final String PROFILE_READ = "profile:author:read";
    public static final String PROFILE_WRITE = "profile:author:write";
    public static final String NOVEL_READ = "novel:read";
    public static final String NOVEL_WRITE = "novel:write";
    public static final String NOVEL_DELETE = "novel:delete";
    public static final String NOVEL_IMPORT = "novel:import";
    public static final String NOVEL_EXPORT = "novel:export";
    public static final String STORY_BIBLE_READ = "story-bible:read";
    public static final String STORY_BIBLE_WRITE = "story-bible:write";
    public static final String AGENT_USE = "agent:use";
    public static final String RAG_READ = "rag:read";
    public static final String RAG_WRITE = "rag:write";
    public static final String PLUGIN_READ = "plugin:read";
    public static final String PLUGIN_WRITE = "plugin:write";
    public static final String MODEL_USER_USE = "model:user:use";
    public static final String MODEL_USER_WRITE = "model:user:write";
    public static final String MODEL_OFFICIAL_USE = "model:official:use";
    public static final String MODEL_SYSTEM_WRITE = "model:system:write";

    public static final String RBAC_USER_READ = "rbac:user:read";
    public static final String RBAC_USER_WRITE = "rbac:user:write";
    public static final String RBAC_USER_DELETE = "rbac:user:delete";
    public static final String RBAC_USER_BIND_ROLE = "rbac:user:bind-role";
    public static final String RBAC_ROLE_READ = "rbac:role:read";
    public static final String RBAC_ROLE_WRITE = "rbac:role:write";
    public static final String RBAC_ROLE_DELETE = "rbac:role:delete";
    public static final String RBAC_ROLE_BIND_PERMISSION = "rbac:role:bind-permission";
    public static final String RBAC_PERMISSION_READ = "rbac:permission:read";
    public static final String RBAC_MENU_READ = "rbac:menu:read";

    public static final String OPS_JOB_READ = "ops:job:read";
    public static final String OPS_JOB_WRITE = "ops:job:write";
    public static final String OPS_MIGRATION_READ = "ops:migration:read";
    public static final String OPS_MIGRATION_WRITE = "ops:migration:write";

    private IamPermissionCodes() { }
}
