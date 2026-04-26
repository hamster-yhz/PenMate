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

    private Object invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = OpenApiConfig.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(openApiConfig, args);
    }
}
