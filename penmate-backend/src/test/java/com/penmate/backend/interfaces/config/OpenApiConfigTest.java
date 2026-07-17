package com.penmate.backend.interfaces.config;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenApiConfig openApiConfig = new OpenApiConfig();

    @Test
    void exposes_bearer_authenticated_base_document() {
        OpenAPI api = openApiConfig.penmateOpenAPI();

        assertThat(api.getInfo().getTitle()).isEqualTo("PenMate Backend API");
        assertThat(api.getInfo().getVersion()).isEqualTo("v1");
        assertThat(api.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(api.getComponents().getSecuritySchemes().get("bearerAuth").getScheme()).isEqualTo("bearer");
        assertThat(api.getSecurity()).isNotEmpty();
    }

    @Test
    void customizer_classifies_model_operations_and_describes_business_ids() {
        OpenAPI openAPI = new OpenAPI();
        Operation operation = new Operation();
        operation.addParametersItem(new Parameter().in("path").name("projectId").schema(new Schema<>().type("integer")));
        operation.setResponses(new ApiResponses().addApiResponse("200", new ApiResponse()));
        openAPI.setPaths(new Paths().addPathItem("/api/v1/model/configs/{projectId}", new PathItem().get(operation)));

        openApiConfig.defaultApiDocumentationCustomizer().customise(openAPI);

        assertThat(operation.getSummary()).contains("模型配置");
        assertThat(operation.getParameters().get(0).getSchema().getType()).isEqualTo("string");
        assertThat(operation.getParameters().get(0).getDescription()).isNotBlank();
    }

    @Test
    void customizer_fills_parameter_examples_and_rbac_summary() {
        OpenAPI openAPI = new OpenAPI();
        Operation operation = new Operation();
        operation.addParametersItem(new Parameter().in("query").name("userId").schema(new Schema<>().type("integer")));
        operation.setResponses(new ApiResponses().addApiResponse("200", new ApiResponse()));
        openAPI.setPaths(new Paths().addPathItem("/api/v1/profile/menus", new PathItem().get(operation)));

        openApiConfig.defaultApiDocumentationCustomizer().customise(openAPI);

        assertThat(operation.getSummary()).contains("RBAC");
        assertThat(operation.getParameters().get(0).getSchema().getExample()).isEqualTo("1001");
        assertThat(operation.getParameters().get(0).getDescription()).isNotBlank();
    }

    @Test
    void customizer_converts_nested_business_ids_to_string_schema() {
        OpenAPI openAPI = new OpenAPI();
        Schema<?> requestSchema = new ObjectSchema()
                .addProperty("userId", new Schema<>().type("integer"))
                .addProperty("operatorId", new Schema<>().type("integer"))
                .addProperty("taskRequest", new ObjectSchema().addProperty("chapterId", new Schema<>().type("integer")));
        Operation operation = new Operation();
        operation.setRequestBody(new RequestBody().content(new Content()
                .addMediaType("application/json", new MediaType().schema(requestSchema))));
        operation.setResponses(new ApiResponses().addApiResponse("200", new ApiResponse()));
        openAPI.setPaths(new Paths().addPathItem("/api/v1/novels/{projectId}/agent/sessions/{sessionId}/turns",
                new PathItem().post(operation)));

        openApiConfig.defaultApiDocumentationCustomizer().customise(openAPI);

        Schema<?> customized = operation.getRequestBody().getContent().get("application/json").getSchema();
        assertThat(((Schema<?>) customized.getProperties().get("userId")).getType()).isEqualTo("string");
        assertThat(((Schema<?>) customized.getProperties().get("operatorId")).getType()).isEqualTo("string");
        Schema<?> taskRequest = (Schema<?>) customized.getProperties().get("taskRequest");
        assertThat(((Schema<?>) taskRequest.getProperties().get("chapterId")).getType()).isEqualTo("string");
    }

    @Test
    void customizer_generates_request_and_response_examples() {
        OpenAPI openAPI = new OpenAPI();
        Schema<?> requestSchema = new ObjectSchema().addProperty("name", new Schema<>().type("string"));
        Schema<?> responseSchema = new ObjectSchema().addProperty("data", new ObjectSchema().addProperty("id", new Schema<>().type("integer")));
        Operation operation = new Operation();
        operation.setRequestBody(new RequestBody().content(new Content()
                .addMediaType("application/json", new MediaType().schema(requestSchema))));
        operation.setResponses(new ApiResponses().addApiResponse("200", new ApiResponse().content(new Content()
                .addMediaType("application/json", new MediaType().schema(responseSchema)))));
        openAPI.setPaths(new Paths().addPathItem("/api/v1/users", new PathItem().post(operation)));

        openApiConfig.defaultApiDocumentationCustomizer().customise(openAPI);

        assertThat(operation.getRequestBody().getContent().get("application/json").getExample()).isInstanceOf(Map.class);
        assertThat(operation.getResponses().get("200").getContent().get("application/json").getExample()).isInstanceOf(Map.class);
    }

    @Test
    void customizer_is_safe_when_openapi_paths_are_absent() {
        OpenAPI openAPI = new OpenAPI();
        openAPI.setPaths(null);

        openApiConfig.defaultApiDocumentationCustomizer().customise(openAPI);

        assertThat(openAPI.getPaths()).isNull();
    }
}
