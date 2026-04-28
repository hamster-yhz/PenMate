package com.penmate.backend.interfaces.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenApiConfig openApiConfig = new OpenApiConfig();

    @Test
    void UT_CONFIG_OPENAPI_CUSTOMIZER_SHOULD_CLASSIFY_MODEL_PREFIX_AS_MODEL_MODULE() {
        OpenAPI openAPI = new OpenAPI();
        Operation operation = new Operation();
        PathItem pathItem = new PathItem().get(operation);
        Paths paths = new Paths();
        paths.addPathItem("/api/v1/model/runtime-check", pathItem);
        openAPI.setPaths(paths);

        openApiConfig.defaultApiDocumentationCustomizer().customise(openAPI);

        assertThat(operation.getSummary()).contains("模型配置");
    }

    @Test
    void UT_CONFIG_OPENAPI_BASE_DOCUMENT_SHOULD_INCLUDE_BEARER_SCHEME() {
        OpenAPI api = openApiConfig.penmateOpenAPI();

        assertThat(api.getInfo().getTitle()).isEqualTo("PenMate Backend API");
        assertThat(api.getInfo().getVersion()).isEqualTo("v1");
        assertThat(api.getComponents()).isNotNull();
        assertThat(api.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(api.getComponents().getSecuritySchemes().get("bearerAuth").getScheme()).isEqualTo("bearer");
        assertThat(api.getSecurity()).isNotEmpty();
    }

    @Test
    void UT_CONFIG_OPENAPI_CUSTOMIZER_SHOULD_FILL_PARAMETER_DESCRIPTION_AND_EXAMPLE() {
        OpenAPI openAPI = new OpenAPI();
        Operation operation = new Operation();
        operation.addParametersItem(new Parameter().in("query").name("userId").schema(new Schema<>().type("integer")));
        operation.setResponses(new ApiResponses().addApiResponse("200", new ApiResponse()));
        PathItem pathItem = new PathItem().get(operation);
        openAPI.setPaths(new Paths().addPathItem("/api/v1/profile/menus", pathItem));

        openApiConfig.defaultApiDocumentationCustomizer().customise(openAPI);

        Parameter parameter = operation.getParameters().get(0);
        assertThat(parameter.getDescription()).contains("用户业务 ID");
        assertThat(parameter.getSchema().getExample()).isEqualTo(1);
        assertThat(operation.getSummary()).contains("RBAC");
    }

    @Test
    void UT_CONFIG_OPENAPI_CUSTOMIZER_SHOULD_FILL_REQUEST_AND_RESPONSE_EXAMPLES() {
        OpenAPI openAPI = new OpenAPI();
        Operation operation = new Operation();

        Schema<?> requestSchema = new ObjectSchema()
                .addProperty("name", new Schema<>().type("string"))
                .addProperty("status", new Schema<>().type("integer"));
        operation.setRequestBody(new RequestBody().content(new Content().addMediaType("application/json", new MediaType().schema(requestSchema))));

        Schema<?> responseSchema = new ObjectSchema()
                .addProperty("data", new ObjectSchema().addProperty("id", new Schema<>().type("integer")))
                .addProperty("meta", new ObjectSchema().addProperty("traceId", new Schema<>().type("string")));
        operation.setResponses(new ApiResponses().addApiResponse("200", new ApiResponse().content(new Content().addMediaType("application/json", new MediaType().schema(responseSchema)))));

        PathItem pathItem = new PathItem().post(operation);
        openAPI.setPaths(new Paths().addPathItem("/api/v1/users", pathItem));

        openApiConfig.defaultApiDocumentationCustomizer().customise(openAPI);

        Object reqExample = operation.getRequestBody().getContent().get("application/json").getExample();
        Object respExample = operation.getResponses().get("200").getContent().get("application/json").getExample();

        assertThat(reqExample).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) reqExample).containsKey("name")).isTrue();
        assertThat(((Map<?, ?>) reqExample).containsKey("status")).isTrue();
        assertThat(respExample).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) respExample).containsKey("data")).isTrue();
        assertThat(((Map<?, ?>) respExample).containsKey("meta")).isTrue();
    }

    @Test
    void UT_CONFIG_OPENAPI_CUSTOMIZER_SHOULD_NOT_THROW_WHEN_PATHS_NULL() {
        OpenAPI openAPI = new OpenAPI();
        openAPI.setPaths(null);

        openApiConfig.defaultApiDocumentationCustomizer().customise(openAPI);

        assertThat(openAPI.getPaths()).isNull();
    }

    @Test
    void UT_CONFIG_OPENAPI_PRIVATE_METHODS_SHOULD_RETURN_EXPECTED_ACTION_AND_FIELD_DESCRIPTION() throws Exception {
        String publishAction = (String) invokePrivate("inferAction",
                new Class[]{String.class, String.class},
                "/api/v1/novels/{projectId}/chapters/{chapterId}/publish", "post");
        String roleDescription = (String) invokePrivate("describeField",
                new Class[]{String.class, String.class, String.class, String.class},
                "roleId", "path参数", "/api/v1/users/{userId}/roles", "post");
        String modelField = (String) invokePrivate("describeField",
                new Class[]{String.class, String.class, String.class, String.class},
                "providerId", "字段", "/api/v1/model/keys", "post");

        assertThat(publishAction).isEqualTo("发布章节");
        assertThat(roleDescription).contains("角色业务 ID");
        assertThat(modelField).contains("模型服务商业务 ID");
    }

    @Test
    void UT_CONFIG_OPENAPI_CUSTOMIZER_SHOULD_DESCRIBE_RBAC_BINDING_QUERY_ENDPOINTS() {
        OpenAPI openAPI = new OpenAPI();
        Operation userRolesOperation = new Operation();
        userRolesOperation.setResponses(new ApiResponses().addApiResponse("200", new ApiResponse()));
        Operation rolePermissionsOperation = new Operation();
        rolePermissionsOperation.setResponses(new ApiResponses().addApiResponse("200", new ApiResponse()));
        PathItem userRolesPath = new PathItem();
        userRolesPath.setGet(userRolesOperation);
        PathItem rolePermissionsPath = new PathItem();
        rolePermissionsPath.setGet(rolePermissionsOperation);
        Paths paths = new Paths()
                .addPathItem("/api/v1/users/{userId}/roles", userRolesPath)
                .addPathItem("/api/v1/roles/{roleId}/permissions", rolePermissionsPath);
        openAPI.setPaths(paths);

        openApiConfig.defaultApiDocumentationCustomizer().customise(openAPI);

        assertThat(userRolesOperation.getSummary()).contains("RBAC").contains("已绑定角色");
        assertThat(userRolesOperation.getDescription()).contains("已绑定的角色");
        assertThat(rolePermissionsOperation.getSummary()).contains("RBAC").contains("已绑定权限");
        assertThat(rolePermissionsOperation.getDescription()).contains("已绑定的权限");
    }

    @Test
    void UT_CONFIG_OPENAPI_EXAMPLE_BY_SCHEMA_SHOULD_HANDLE_REF_AND_ARRAY() throws Exception {
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(new Components().addSchemas("RefObject", new ObjectSchema().addProperty("enabled", new Schema<>().type("boolean"))));

        Schema<?> refSchema = new Schema<>().$ref("#/components/schemas/RefObject");
        Object refExample = invokePrivate("exampleBySchema",
                new Class[]{Schema.class, OpenAPI.class},
                refSchema, openAPI);

        assertThat(refExample).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) refExample).get("enabled")).isEqualTo(true);
    }

    @Test
    void UT_CONFIG_OPENAPI_INFER_MODULE_SHOULD_RECOGNIZE_MODEL_ROOT_PATH() throws Exception {
        String module = (String) invokePrivate("inferModule",
                new Class[]{String.class},
                "/api/v1/model");

        assertThat(module).isEqualTo("模型配置");
    }

    @Test
    void UT_CONFIG_OPENAPI_INFER_ACTION_SHOULD_DESCRIBE_PROFILE_MENUS_AS_TARGET_USER_QUERY() throws Exception {
        String action = (String) invokePrivate("inferAction",
                new Class[]{String.class, String.class},
                "/api/v1/profile/menus", "get");

        assertThat(action).isEqualTo("查询指定用户可见菜单");
    }

    @Test
    void UT_CONFIG_OPENAPI_DESCRIBE_FIELD_SHOULD_DISTINGUISH_RBAC_ROLE_AND_PERMISSION_QUERY_SEMANTICS() throws Exception {
        String roleIdInRolePermissionQuery = (String) invokePrivate("describeField",
                new Class[]{String.class, String.class, String.class, String.class},
                "roleId", "path参数", "/api/v1/roles/{roleId}/permissions", "get");
        String permissionIdInRolePermissionDelete = (String) invokePrivate("describeField",
                new Class[]{String.class, String.class, String.class, String.class},
                "permissionId", "path参数", "/api/v1/roles/{roleId}/permissions/{permissionId}", "delete");

        assertThat(roleIdInRolePermissionQuery).contains("目标角色");
        assertThat(roleIdInRolePermissionQuery).doesNotContain("用户角色");
        assertThat(permissionIdInRolePermissionDelete).contains("权限业务 ID");
    }

    @Test
    void UT_CONFIG_OPENAPI_INFER_MODULE_SHOULD_CLASSIFY_PERMISSIONS_PATH_AS_RBAC() throws Exception {
        String module = (String) invokePrivate("inferModule",
                new Class[]{String.class},
                "/api/v1/permissions");

        assertThat(module).isEqualTo("RBAC 权限");
    }

    @Test
    void UT_CONFIG_OPENAPI_DESCRIBE_FIELD_SHOULD_RECOGNIZE_TRACE_HEADER_WITH_HYPHENS() throws Exception {
        String traceHeaderDescription = (String) invokePrivate("describeField",
                new Class[]{String.class, String.class, String.class, String.class},
                "X-Trace-Id", "header参数", "/api/v1/users", "get");

        assertThat(traceHeaderDescription).contains("链路追踪 ID");
    }

    @Test
    void UT_CONFIG_OPENAPI_CUSTOMIZER_SHOULD_OVERRIDE_INCORRECT_PREGENERATED_RBAC_SUMMARY_AND_PARAMETER_DESCRIPTIONS() {
        OpenAPI openAPI = new OpenAPI();
        Operation operation = new Operation();
        operation.setSummary("RBAC - 查询用户可见菜单");
        operation.setDescription("接口作用：查询当前用户可见菜单。");
        operation.addParametersItem(new Parameter()
                .in("header")
                .name("X-Trace-Id")
                .description("业务语义 ID")
                .schema(new Schema<>().type("string")));
        PathItem pathItem = new PathItem();
        pathItem.setGet(operation);
        openAPI.setPaths(new Paths().addPathItem("/api/v1/profile/menus", pathItem));

        openApiConfig.defaultApiDocumentationCustomizer().customise(openAPI);

        assertThat(operation.getSummary()).isEqualTo("RBAC - 查询指定用户可见菜单");
        assertThat(operation.getDescription()).contains("目标用户业务 ID");
        assertThat(operation.getParameters().get(0).getDescription()).contains("链路追踪 ID");
    }

    private Object invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = OpenApiConfig.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(openApiConfig, args);
    }
}
