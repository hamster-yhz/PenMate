package com.penmate.backend.interfaces.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    private static final Map<String, String> OPERATION_SUMMARY_MAP = buildOperationSummaryMap();
    private static final Map<String, String> OPERATION_DESCRIPTION_MAP = buildOperationDescriptionMap();

    @Bean
    public OpenAPI penmateOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("PenMate Backend API")
                        .version("v1")
                        .description("PenMate 统一全量后台 API 文档")
                        .contact(new Contact().name("PenMate Team")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components().addSecuritySchemes(securitySchemeName,
                        new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    /**
     * 为所有接口补齐基础文档信息，方便直接导入 OpenAPI / Postman 使用。
     */
    @Bean
    public OpenApiCustomizer defaultApiDocumentationCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().forEach((path, pathItem) ->
                    pathItem.readOperationsMap().forEach((method, operation) -> {
                        fillOperationSummary(path, method.name(), operation);
                        fillParameterDocs(path, method.name(), operation, openApi);
                        fillRequestBodyExample(path, method.name(), operation, openApi);
                        fillResponseDocs(path, method.name(), operation, openApi);
                    })
            );
        };
    }

    private void fillOperationSummary(String path, String method, Operation operation) {
        String module = inferModule(path);
        String action = inferAction(path, method);
        String endpointKey = endpointKey(path, method);
        String summary = OPERATION_SUMMARY_MAP.get(endpointKey);
        String description = OPERATION_DESCRIPTION_MAP.get(endpointKey);

        if (operation.getSummary() == null || operation.getSummary().isBlank()) {
            operation.setSummary(summary == null ? module + " - " + action : summary);
        }
        if (operation.getDescription() == null || operation.getDescription().isBlank()) {
            operation.setDescription(description == null
                    ? "接口作用：用于" + module + "的" + action + "。\n"
                    + "调用方式：" + method.toUpperCase(Locale.ROOT) + " " + path + "。\n"
                    + "返回约定：统一返回 data/meta 结构（meta 含 traceId、timestamp）。"
                    : description);
        }
    }

    private void fillParameterDocs(String path, String method, Operation operation, OpenAPI openApi) {
        if (operation.getParameters() == null) {
            return;
        }
        for (Parameter parameter : operation.getParameters()) {
            if (parameter.getDescription() == null || parameter.getDescription().isBlank()) {
                String place = parameter.getIn() == null ? "参数" : parameter.getIn() + "参数";
                parameter.setDescription(describeField(parameter.getName(), place, path, method));
            }
            Schema<?> schema = parameter.getSchema();
            if (schema != null && schema.getExample() == null) {
                schema.setExample(exampleBySchema(schema, openApi));
            }
            if (schema != null) {
                fillSchemaDescriptions(schema, path, method, new HashSet<>(), openApi);
            }
        }
    }

    private void fillRequestBodyExample(String path, String method, Operation operation, OpenAPI openApi) {
        if (operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
            return;
        }
        operation.getRequestBody().getContent().forEach((k, mediaType) -> {
            if (mediaType.getSchema() != null) {
                fillSchemaDescriptions(mediaType.getSchema(), path, method, new HashSet<>(), openApi);
            }
            if (mediaType.getExample() == null) {
                Schema<?> schema = mediaType.getSchema();
                mediaType.setExample(schema == null ? Map.of("field", "示例值") : exampleBySchema(schema, openApi));
            }
        });
    }

    private void fillResponseDocs(String path, String method, Operation operation, OpenAPI openApi) {
        if (operation.getResponses() == null) {
            return;
        }

        operation.getResponses().forEach((code, response) -> {
            if (response.getDescription() == null || response.getDescription().isBlank()) {
                response.setDescription("HTTP " + code + " 响应，data 为" + inferModule(path) + "业务返回数据。");
            }
            ensureResponseExample(path, method, response, openApi);
        });
    }

    private void ensureResponseExample(String path, String method, ApiResponse response, OpenAPI openApi) {
        Content content = response.getContent();
        if (content == null) {
            return;
        }

        content.forEach((k, mediaType) -> {
            if (mediaType.getSchema() != null) {
                fillSchemaDescriptions(mediaType.getSchema(), path, method, new HashSet<>(), openApi);
            }
            if (mediaType.getExample() == null) {
                mediaType.setExample(buildResponseEnvelopeExample(mediaType, openApi));
            }
        });
    }

    private Object buildResponseEnvelopeExample(MediaType mediaType, OpenAPI openApi) {
        Schema<?> schema = mediaType.getSchema();
        if (schema == null) {
            return Map.of(
                    "data", Map.of("field", "示例值"),
                    "meta", Map.of("traceId", "trace-example-001", "timestamp", "2026-01-01T00:00:00Z")
            );
        }
        return exampleBySchema(schema, openApi);
    }

    private String inferModule(String path) {
        if (path.contains("/auth")) {
            return "认证与会话";
        }
        if (path.contains("/novels") && path.contains("/agent")) {
            return "Agent 写作";
        }
        if (path.contains("/approvals")) {
            return "审批流";
        }
        if (path.contains("/novels") && path.contains("/styles")) {
            return "风格管理";
        }
        if (path.contains("/novels") && path.contains("/rag")) {
            return "知识库（RAG）";
        }
        if (path.contains("/plugins")) {
            return "插件管理";
        }
        if (path.contains("/models") || path.contains("/model/") || path.endsWith("/model")) {
            return "模型配置";
        }
        if (path.contains("/novels")) {
            return "小说项目";
        }
        if (path.contains("/iam") || path.contains("/rbac") || path.contains("/roles") || path.contains("/users") || path.contains("/profile/menus")) {
            return "RBAC 权限";
        }
        if (path.contains("/ops")) {
            return "运维任务";
        }
        return "通用业务";
    }

    private String inferAction(String path, String method) {
        String httpMethod = method.toUpperCase(Locale.ROOT);
        // ---- RBAC 精确动作识别 ----
        if ("GET".equals(httpMethod) && "/api/v1/users".equals(path)) {
            return "查询用户列表";
        }
        if ("GET".equals(httpMethod) && "/api/v1/users/{userId}".equals(path)) {
            return "查询用户详情";
        }
        if ("POST".equals(httpMethod) && "/api/v1/users".equals(path)) {
            return "创建用户";
        }
        if ("PUT".equals(httpMethod) && "/api/v1/users/{userId}".equals(path)) {
            return "更新用户资料";
        }
        if ("DELETE".equals(httpMethod) && "/api/v1/users/{userId}".equals(path)) {
            return "删除用户";
        }
        if ("POST".equals(httpMethod) && "/api/v1/users/{userId}/roles".equals(path)) {
            return "为用户绑定角色";
        }
        if ("DELETE".equals(httpMethod) && "/api/v1/users/{userId}/roles/{roleId}".equals(path)) {
            return "解除用户角色绑定";
        }
        if ("GET".equals(httpMethod) && "/api/v1/roles".equals(path)) {
            return "查询角色列表";
        }
        if ("POST".equals(httpMethod) && "/api/v1/roles".equals(path)) {
            return "创建角色";
        }
        if ("PUT".equals(httpMethod) && "/api/v1/roles/{roleId}".equals(path)) {
            return "更新角色";
        }
        if ("DELETE".equals(httpMethod) && "/api/v1/roles/{roleId}".equals(path)) {
            return "删除角色";
        }
        if ("GET".equals(httpMethod) && "/api/v1/permissions".equals(path)) {
            return "查询权限列表";
        }
        if ("POST".equals(httpMethod) && "/api/v1/roles/{roleId}/permissions".equals(path)) {
            return "为角色绑定权限";
        }
        if ("DELETE".equals(httpMethod) && "/api/v1/roles/{roleId}/permissions/{permissionId}".equals(path)) {
            return "解除角色权限绑定";
        }
        if ("GET".equals(httpMethod) && "/api/v1/menus".equals(path)) {
            return "查询菜单列表";
        }
        if ("GET".equals(httpMethod) && "/api/v1/profile/menus".equals(path)) {
            return "查询当前用户可见菜单";
        }

        if (path.endsWith("/login")) {
            return "用户登录";
        }
        if (path.endsWith("/logout")) {
            return "用户登出";
        }
        if (path.endsWith("/refresh")) {
            return "刷新令牌";
        }
        if (path.endsWith("/me")) {
            return "获取当前登录用户信息";
        }
        if (path.contains("/publish")) {
            return "发布章节";
        }
        if (path.contains("/restore")) {
            return "恢复历史版本";
        }
        if (path.contains("/move")) {
            return "移动节点";
        }
        if (path.contains("/content-upload-url")) {
            return "获取章节内容上传地址";
        }
        if (path.contains("/content-url")) {
            return "获取章节内容下载地址";
        }
        if (path.contains("/content-commit")) {
            return "提交章节内容元数据";
        }
        if (path.contains("/versions") && "GET".equals(httpMethod)) {
            return "查询章节版本记录";
        }
        if (path.contains("/versions") && "POST".equals(httpMethod)) {
            return "创建章节版本快照";
        }

        String resource = guessResourceName(path);
        return switch (httpMethod) {
            case "GET" -> "查询" + resource;
            case "POST" -> "创建" + resource;
            case "PUT", "PATCH" -> "更新" + resource;
            case "DELETE" -> "删除" + resource;
            default -> "处理业务请求";
        };
    }

    private String guessResourceName(String path) {
        String[] segments = Arrays.stream(path.split("/"))
                .filter(s -> s != null && !s.isBlank())
                .filter(s -> !s.startsWith("{"))
                .filter(s -> !"api".equals(s) && !"v1".equals(s))
                .toArray(String[]::new);
        if (segments.length == 0) {
            return "数据";
        }

        String last = segments[segments.length - 1];
        String normalized = normalizeResourceToken(last);
        return switch (normalized) {
            case "users" -> "用户";
            case "roles" -> "角色";
            case "permissions" -> "权限";
            case "menus" -> "菜单";
            case "providers" -> "模型服务商";
            case "models" -> "模型";
            case "keys" -> "用户密钥";
            case "model-policies" -> "模型策略";
            case "plugins" -> "插件安装";
            case "catalog" -> "插件目录";
            case "call-logs" -> "插件调用日志";
            case "documents" -> "RAG 文档";
            case "retrieval-logs" -> "检索日志";
            case "conversations" -> "会话";
            case "messages" -> "消息";
            case "generations" -> "生成任务";
            case "approvals" -> "审批单";
            case "jobs" -> "异步任务";
            case "migrations" -> "迁移任务";
            case "novels" -> "小说项目";
            case "volumes" -> "分卷";
            case "chapters" -> "章节";
            case "members" -> "成员";
            case "versions" -> "版本";
            case "outlines" -> "大纲";
            case "nodes" -> "大纲节点";
            case "cards" -> "资料卡";
            case "card-relations" -> "资料卡关系";
            case "styles" -> "风格";
            default -> "数据";
        };
    }

    private String normalizeResourceToken(String token) {
        if (token == null) {
            return "";
        }
        return token.toLowerCase(Locale.ROOT);
    }

    private String describeField(String fieldName, String place, String path, String method) {
        String name = fieldName == null ? "field" : fieldName;
        String n = name.toLowerCase(Locale.ROOT);

        // RBAC 语义优先
        if ("id".equals(n) && path.contains("/users/")) {
            return place + "：用户业务 ID，用于定位目标用户。";
        }
        if ("id".equals(n) && path.contains("/roles/")) {
            return place + "：角色业务 ID，用于定位目标角色。";
        }
        if ("roleid".equals(n)) {
            return place + "：角色业务 ID，用于绑定/解绑用户角色。";
        }
        if ("permissionid".equals(n)) {
            return place + "：权限 ID，用于绑定/解绑角色权限。";
        }
        if ("userid".equals(n) && path.contains("/profile/menus")) {
            return place + "：用户业务 ID，用于查询该用户最终可见菜单（含角色聚合后权限）。";
        }
        if ("email".equals(n) && path.contains("/users")) {
            return place + "：用户邮箱，作为登录账号标识，通常要求唯一。";
        }
        if ("displayname".equals(n)) {
            return place + "：用户展示名，用于前端显示昵称。";
        }
        if ("authmethod".equals(n)) {
            return place + "：认证方式，如 password / oauth / sso。";
        }
        if ("code".equals(n) && path.contains("/roles")) {
            return place + "：角色编码，建议全局唯一，用于程序内权限判断。";
        }
        if ("issystem".equals(n)) {
            return place + "：是否系统内置角色（true=系统角色，不建议业务侧删除）。";
        }

        // 先按模型配置领域给出高精度说明（用户反馈该模块模糊）
        if (path.contains("/model/")) {
            switch (n) {
                case "providerid":
                    return place + "：模型服务商业务 ID（如 OpenAI、Anthropic），用于绑定密钥或策略。";
                case "providercode":
                    return place + "：模型服务商编码（唯一标识），用于查询该服务商支持的模型列表。";
                case "providername":
                    return place + "：模型服务商展示名称。";
                case "keyname":
                    return place + "：用户自定义密钥名称，便于在多个 API Key 间区分。";
                case "apikey":
                    return place + "：调用第三方模型服务的 API 密钥明文，仅在写入时传输。";
                case "isdefault":
                    return place + "：是否默认配置（1=默认，0=非默认）。";
                case "policyname":
                    return place + "：策略名称，用于标识某个场景下的模型路由策略。";
                case "scene":
                    return place + "：策略应用场景，如大纲生成、章节润色、角色对话。";
                case "providermodelid":
                    return place + "：服务商模型 ID，指向具体可调用模型。";
                case "userkeyid":
                    return place + "：用户 API Key 记录 ID，策略执行时用此密钥发起调用。";
                case "temperature":
                    return place + "：采样温度，越高随机性越强，越低结果越稳定。";
                case "topp":
                    return place + "：核采样阈值（Top-P），控制候选词概率截断范围。";
                case "maxtokens":
                    return place + "：模型单次生成的最大 token 数上限。";
                case "fallbackpolicyjson":
                    return place + "：降级策略 JSON，主模型失败时的备用策略配置。";
                default:
                    break;
            }
        }

        if ("projectid".equals(n)) {
            return place + "：小说项目业务 ID，用于定位具体项目。";
        }
        if ("chapterid".equals(n)) {
            return place + "：章节业务 ID，用于定位具体章节。";
        }
        if ("volumeid".equals(n)) {
            return place + "：分卷业务 ID，用于定位章节所在分卷。";
        }
        if ("userid".equals(n)) {
            return place + "：用户业务 ID，用于标识操作用户或目标用户。";
        }
        if ("operatorid".equals(n)) {
            return place + "：操作人业务 ID，用于审计与权限判断。";
        }
        if ("approvalid".equals(n)) {
            return place + "：审批单业务 ID，用于定位审批请求。";
        }
        if ("keyid".equals(n)) {
            return place + "：密钥业务 ID，用于定位用户密钥或官方密钥记录。";
        }
        if ("configid".equals(n)) {
            return place + "：模型配置业务 ID，用于定位项目模型配置。";
        }
        if ("traceid".equals(n)) {
            return place + "：链路追踪 ID，用于日志追踪与问题定位。";
        }
        if ("authorization".equals(n)) {
            return place + "：认证令牌，格式为 Bearer {token}。";
        }
        if (n.contains("status")) {
            return place + "：业务状态值，具体枚举见对应模块约定。";
        }
        if (n.contains("role")) {
            return place + "：角色标识字段，用于权限控制与职责划分。";
        }
        if (n.contains("type")) {
            return place + "：类型标识字段，用于区分业务类别。";
        }
        if (n.contains("reason")) {
            return place + "：原因说明字段，用于记录变更或审批原因。";
        }
        if (n.contains("description")) {
            return place + "：描述字段，用于补充业务上下文。";
        }
        if (n.contains("name")) {
            return place + "：名称字段，用于展示或唯一识别。";
        }
        if (n.contains("title")) {
            return place + "：标题字段，用于展示名称。";
        }
        if (n.contains("summary")) {
            return place + "：摘要说明，用于简述内容。";
        }
        if (n.contains("content")) {
            return place + "：正文或内容元数据字段。";
        }
        if (n.contains("token")) {
            return place + "：令牌字段，用于认证或续期。";
        }
        if (n.contains("email")) {
            return place + "：邮箱地址，用于登录或用户识别。";
        }
        if (n.contains("password")) {
            return place + "：密码字段，仅用于认证入参。";
        }
        if (n.contains("sortorder")) {
            return place + "：排序值，数值越小越靠前。";
        }
        if (n.contains("wordcount")) {
            return place + "：字数统计。";
        }
        if (n.contains("objectkey")) {
            return place + "：对象存储键，用于定位存储文件。";
        }
        if (n.contains("etag")) {
            return place + "：对象版本标记，用于校验文件一致性。";
        }
        if (n.contains("checksum")) {
            return place + "：内容校验和，用于完整性校验。";
        }
        if (n.contains("provider")) {
            return place + "：服务提供方标识，例如模型商或存储服务商。";
        }
        if (n.contains("size")) {
            return place + "：数据大小，单位按业务约定（通常为字节）。";
        }
        if (n.contains("version")) {
            return place + "：版本号字段，用于历史追踪与回滚。";
        }
        if (n.contains("id")) {
            return place + "：业务语义 ID，用于唯一定位业务对象。";
        }
        return place + "：字段「" + name + "」，用于" + inferModule(path) + inferAction(path, method) + "。";
    }

    private String endpointKey(String path, String method) {
        return method.toUpperCase(Locale.ROOT) + " " + path;
    }

    private static Map<String, String> buildOperationSummaryMap() {
        Map<String, String> map = new HashMap<>();

        // auth
        map.put("POST /api/v1/auth/login", "认证 - 用户登录");
        map.put("POST /api/v1/auth/logout", "认证 - 用户登出");
        map.put("POST /api/v1/auth/refresh", "认证 - 刷新访问令牌");
        map.put("GET /api/v1/auth/me", "认证 - 获取当前登录态用户");

        // model
        map.put("GET /api/v1/model/providers", "模型配置 - 查询模型服务商列表");
        map.put("GET /api/v1/model/providers/{providerCode}/models", "模型配置 - 查询服务商可用模型");
        map.put("GET /api/v1/model/keys", "模型配置 - 查询用户 API Key 列表");
        map.put("POST /api/v1/model/keys", "模型配置 - 新增用户 API Key");
        map.put("PATCH /api/v1/model/keys/{keyId}", "模型配置 - 更新用户 API Key");
        map.put("DELETE /api/v1/model/keys/{keyId}", "模型配置 - 删除用户 API Key");
        map.put("GET /api/v1/model/official-keys", "模型配置 - 查询官方 API Key 列表");
        map.put("POST /api/v1/model/official-keys", "模型配置 - 新增官方 API Key");
        map.put("PATCH /api/v1/model/official-keys/{keyId}", "模型配置 - 更新官方 API Key");
        map.put("DELETE /api/v1/model/official-keys/{keyId}", "模型配置 - 删除官方 API Key");
        map.put("GET /api/v1/novels/{projectId}/model-configs", "模型配置 - 查询项目模型配置列表");
        map.put("POST /api/v1/novels/{projectId}/model-configs", "模型配置 - 新增项目模型配置");
        map.put("PUT /api/v1/novels/{projectId}/model-configs/{configId}", "模型配置 - 更新项目模型配置");
        map.put("DELETE /api/v1/novels/{projectId}/model-configs/{configId}", "模型配置 - 删除项目模型配置");
        map.put("POST /api/v1/novels/{projectId}/model-configs/{configId}/set-default", "模型配置 - 设置项目默认模型配置");
        map.put("GET /api/v1/novels/{projectId}/model-policies", "模型策略(兼容) - 查询项目策略列表");
        map.put("POST /api/v1/novels/{projectId}/model-policies", "模型策略(兼容) - 新增项目策略");
        map.put("PUT /api/v1/novels/{projectId}/model-policies/{configId}", "模型策略(兼容) - 更新项目策略");
        map.put("DELETE /api/v1/novels/{projectId}/model-policies/{configId}", "模型策略(兼容) - 删除项目策略");
        map.put("POST /api/v1/novels/{projectId}/model-policies/{configId}/set-default", "模型策略(兼容) - 设置默认策略");

        // rbac
        map.put("GET /api/v1/users", "RBAC - 查询用户列表");
        map.put("GET /api/v1/users/{userId}", "RBAC - 查询用户详情");
        map.put("POST /api/v1/users", "RBAC - 创建用户");
        map.put("PUT /api/v1/users/{userId}", "RBAC - 更新用户");
        map.put("DELETE /api/v1/users/{userId}", "RBAC - 删除用户");
        map.put("POST /api/v1/users/{userId}/roles", "RBAC - 为用户绑定角色");
        map.put("DELETE /api/v1/users/{userId}/roles/{roleId}", "RBAC - 解除用户角色绑定");
        map.put("GET /api/v1/roles", "RBAC - 查询角色列表");
        map.put("POST /api/v1/roles", "RBAC - 创建角色");
        map.put("PUT /api/v1/roles/{roleId}", "RBAC - 更新角色");
        map.put("DELETE /api/v1/roles/{roleId}", "RBAC - 删除角色");
        map.put("GET /api/v1/permissions", "RBAC - 查询权限列表");
        map.put("POST /api/v1/roles/{roleId}/permissions", "RBAC - 为角色绑定权限");
        map.put("DELETE /api/v1/roles/{roleId}/permissions/{permissionId}", "RBAC - 解除角色权限绑定");
        map.put("GET /api/v1/menus", "RBAC - 查询菜单列表");
        map.put("GET /api/v1/profile/menus", "RBAC - 查询用户可见菜单");

        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> buildOperationDescriptionMap() {
        Map<String, String> map = new HashMap<>();

        map.put("GET /api/v1/model/providers", "接口作用：返回系统内可接入的模型服务商配置（不含用户密钥）。\n"
                + "典型场景：创建 API Key 前先拉取 provider 列表供前端下拉选择。\n"
                + "返回说明：data 为 ModelProvider 数组。\n"
                + "返回约定：统一返回 data/meta 结构（meta 含 traceId、timestamp）。");

        map.put("GET /api/v1/model/providers/{providerCode}/models", "接口作用：按服务商编码查询可用模型清单。\n"
                + "典型场景：用户选择 provider 后，联动加载 model 列表。\n"
                + "关键入参：providerCode（服务商唯一编码）。\n"
                + "返回说明：data 为 ModelProviderModel 数组。\n"
                + "返回约定：统一返回 data/meta 结构（meta 含 traceId、timestamp）。");

        map.put("POST /api/v1/model/keys", "接口作用：为指定用户新增模型 API Key。\n"
                + "业务规则：可标记默认 key，供策略执行时优先使用。\n"
                + "关键入参：userId（归属用户）、providerId（服务商）、apiKey（密钥明文）。\n"
                + "返回说明：data 为 created。\n"
                + "返回约定：统一返回 data/meta 结构（meta 含 traceId、timestamp）。");

        map.put("POST /api/v1/novels/{projectId}/model-policies", "接口作用：为项目新增模型调用策略。\n"
                + "业务规则：策略按 scene 绑定模型、密钥与采样参数；可设置默认策略。\n"
                + "关键入参：providerModelId、userKeyId、temperature、topP、maxTokens。\n"
                + "返回说明：data 为 created。\n"
                + "返回约定：统一返回 data/meta 结构（meta 含 traceId、timestamp）。");

        map.put("POST /api/v1/novels/{projectId}/model-policies/{configId}/set-default", "接口作用：将指定策略设为当前项目默认策略。\n"
                + "业务规则：同一项目仅允许一个默认策略。\n"
                + "关键入参：projectId、configId、operatorId（均为业务语义 ID）。\n"
                + "返回说明：data 为 updated。\n"
                + "返回约定：统一返回 data/meta 结构（meta 含 traceId、timestamp）。");

        // rbac（按正式项目写法给出差异化说明）
        map.put("GET /api/v1/users", "接口作用：分页/列表查询 IAM 用户（当前实现为全量列表）。\n"
                + "返回内容：脱敏后的用户信息，不返回密码等敏感字段。\n"
                + "适用场景：用户管理页初始化列表。\n"
                + "返回约定：统一返回 data/meta 结构（meta 含 traceId、timestamp）。");
        map.put("POST /api/v1/users", "接口作用：创建 IAM 用户账号。\n"
                + "关键字段：email（唯一账号）、displayName（展示名）、status（启用状态）、authMethod（认证方式）。\n"
                + "业务规则：创建后即可参与角色绑定。\n"
                + "返回约定：统一返回 data/meta 结构（meta 含 traceId、timestamp）。");
        map.put("POST /api/v1/users/{userId}/roles", "接口作用：为指定用户绑定角色。\n"
                + "关键字段：userId（用户业务 ID）、roleId（角色业务 ID）。\n"
                + "业务规则：绑定成功后用户继承该角色对应权限与菜单可见性。\n"
                + "返回约定：统一返回 data/meta 结构（meta 含 traceId、timestamp）。");
        map.put("POST /api/v1/roles/{roleId}/permissions", "接口作用：为指定角色绑定权限。\n"
                + "关键字段：roleId（角色业务 ID）、permissionId（权限业务 ID）。\n"
                + "业务规则：绑定后所有拥有该角色的用户获得对应权限。\n"
                + "返回约定：统一返回 data/meta 结构（meta 含 traceId、timestamp）。");
        map.put("GET /api/v1/profile/menus", "接口作用：按用户维度计算最终可见菜单。\n"
                + "关键字段：userId（目标用户业务 ID）。\n"
                + "业务规则：菜单由用户绑定角色后聚合出的权限结果决定。\n"
                + "返回约定：统一返回 data/meta 结构（meta 含 traceId、timestamp）。");

        return Collections.unmodifiableMap(map);
    }

    private void fillSchemaDescriptions(Schema<?> schema, String path, String method, Set<Integer> visited, OpenAPI openApi) {
        schema = resolveSchema(schema, openApi);
        if (schema == null) {
            return;
        }
        int key = System.identityHashCode(schema);
        if (visited.contains(key)) {
            return;
        }
        visited.add(key);

        if (schema.getDescription() == null || schema.getDescription().isBlank()) {
            if (schema.getName() != null && !schema.getName().isBlank()) {
                schema.setDescription("字段「" + schema.getName() + "」定义。");
            }
        }

        if (schema instanceof ArraySchema arraySchema) {
            fillSchemaDescriptions(arraySchema.getItems(), path, method, visited, openApi);
        }

        if (schema.getProperties() != null) {
            schema.getProperties().forEach((fieldName, rawProperty) -> {
                if (rawProperty instanceof Schema) {
                    @SuppressWarnings("unchecked")
                    Schema<?> propertySchema = (Schema<?>) rawProperty;
                    if (propertySchema.getDescription() == null || propertySchema.getDescription().isBlank()) {
                        propertySchema.setDescription(describeField(fieldName, "字段", path, method));
                    }
                    fillSchemaDescriptions(propertySchema, path, method, visited, openApi);
                }
            });
        }
    }

    private Object exampleBySchema(Schema<?> schema, OpenAPI openApi) {
        schema = resolveSchema(schema, openApi);
        if (schema == null) {
            return "示例值";
        }

        if (schema.getExample() != null) {
            return schema.getExample();
        }

        if (schema instanceof ArraySchema arraySchema) {
            Schema<?> itemSchema = arraySchema.getItems();
            List<Object> list = new ArrayList<>();
            list.add(exampleBySchema(itemSchema, openApi));
            return list;
        }

        if (schema instanceof ObjectSchema || schema.getProperties() != null) {
            Map<String, Object> obj = new LinkedHashMap<>();
            if (schema.getProperties() != null) {
                schema.getProperties().forEach((key, value) -> {
                    if (value instanceof Schema) {
                        @SuppressWarnings("unchecked")
                        Schema<?> childSchema = (Schema<?>) value;
                        obj.put(key, exampleBySchema(childSchema, openApi));
                    } else {
                        obj.put(key, "示例值");
                    }
                });
            }
            if (obj.isEmpty()) {
                obj.put("field", "示例值");
            }
            return obj;
        }

        String type = schema.getType();
        if ("integer".equals(type) || "number".equals(type)) {
            return 1;
        }
        if ("boolean".equals(type)) {
            return true;
        }
        if ("string".equals(type) && "date-time".equals(schema.getFormat())) {
            return "2026-01-01T08:00:00Z";
        }
        return "示例值";
    }

    private Schema<?> resolveSchema(Schema<?> schema, OpenAPI openApi) {
        if (schema == null || schema.get$ref() == null || openApi == null || openApi.getComponents() == null
                || openApi.getComponents().getSchemas() == null) {
            return schema;
        }
        String ref = schema.get$ref();
        int idx = ref.lastIndexOf('/');
        if (idx < 0 || idx + 1 >= ref.length()) {
            return schema;
        }
        Schema<?> refSchema = openApi.getComponents().getSchemas().get(ref.substring(idx + 1));
        return refSchema == null ? schema : refSchema;
    }
}

