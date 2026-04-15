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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Configuration
public class OpenApiConfig {

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
                        fillParameterDocs(operation);
                        fillRequestBodyExample(operation);
                        fillResponseDocs(operation);
                    })
            );
        };
    }

    private void fillOperationSummary(String path, String method, Operation operation) {
        if (operation.getSummary() == null || operation.getSummary().isBlank()) {
            operation.setSummary(method.toUpperCase(Locale.ROOT) + " " + path);
        }
        if (operation.getDescription() == null || operation.getDescription().isBlank()) {
            operation.setDescription("接口说明：用于处理 " + path + " 资源请求。");
        }
    }

    private void fillParameterDocs(Operation operation) {
        if (operation.getParameters() == null) {
            return;
        }
        for (Parameter parameter : operation.getParameters()) {
            if (parameter.getDescription() == null || parameter.getDescription().isBlank()) {
                String place = parameter.getIn() == null ? "参数" : parameter.getIn() + " 参数";
                parameter.setDescription(place + "，名称：" + parameter.getName());
            }
            Schema<?> schema = parameter.getSchema();
            if (schema != null && schema.getExample() == null) {
                schema.setExample(exampleBySchema(schema));
            }
        }
    }

    private void fillRequestBodyExample(Operation operation) {
        if (operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
            return;
        }
        operation.getRequestBody().getContent().forEach((k, mediaType) -> {
            if (mediaType.getExample() == null) {
                Schema<?> schema = mediaType.getSchema();
                mediaType.setExample(schema == null ? Map.of("field", "示例值") : exampleBySchema(schema));
            }
        });
    }

    private void fillResponseDocs(Operation operation) {
        if (operation.getResponses() == null) {
            return;
        }

        operation.getResponses().forEach((code, response) -> {
            if (response.getDescription() == null || response.getDescription().isBlank()) {
                response.setDescription("HTTP " + code + " 响应");
            }
            ensureResponseExample(response);
        });
    }

    private void ensureResponseExample(ApiResponse response) {
        Content content = response.getContent();
        if (content == null) {
            return;
        }

        content.forEach((k, mediaType) -> {
            if (mediaType.getExample() == null) {
                Map<String, Object> example = new LinkedHashMap<>();
                example.put("code", 0);
                example.put("message", "ok");
                example.put("traceId", "trace-example-001");
                example.put("data", buildResponseDataExample(mediaType));
                mediaType.setExample(example);
            }
        });
    }

    private Object buildResponseDataExample(MediaType mediaType) {
        Schema<?> schema = mediaType.getSchema();
        if (schema == null) {
            return Map.of("field", "示例值");
        }
        return exampleBySchema(schema);
    }

    private Object exampleBySchema(Schema<?> schema) {
        if (schema == null) {
            return "示例值";
        }

        if (schema.getExample() != null) {
            return schema.getExample();
        }

        if (schema instanceof ArraySchema arraySchema) {
            Schema<?> itemSchema = arraySchema.getItems();
            List<Object> list = new ArrayList<>();
            list.add(exampleBySchema(itemSchema));
            return list;
        }

        if (schema instanceof ObjectSchema || schema.getProperties() != null) {
            Map<String, Object> obj = new LinkedHashMap<>();
            if (schema.getProperties() != null) {
                schema.getProperties().forEach((key, value) -> {
                    if (value instanceof Schema) {
                        @SuppressWarnings("unchecked")
                        Schema<?> childSchema = (Schema<?>) value;
                        obj.put(key, exampleBySchema(childSchema));
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
}

