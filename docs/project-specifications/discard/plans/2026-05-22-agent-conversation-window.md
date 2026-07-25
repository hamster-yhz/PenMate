# PenMate 后端对话滑动窗口 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use [executing-plans] mode to implement this plan task-by-task.

**Goal:** 为 PenMate 后端引入类型安全的 LLM 消息领域对象与基于最近 N 轮历史的对话滑动窗口，让执行阶段的 LLM 请求能稳定感知最近对话上下文，同时不把 LangChain4j 类型泄漏到应用层。

**Architecture:** 本次改造把当前 `List<Map<String, Object>>` 的 LLM 消息表示提升为领域对象 `AgentLlmMessage`，应用层新增 `ConversationWindowBuilder` 负责从 `AgentRepository.listMessages()` 拉取、排序、裁剪历史轮次，主工作流在进入 tool loop 前把 `[system] + history_window + [current_user_with_context]` 组装完毕。基础设施层保留 provider-specific 的协议转换职责：Claude 适配器把领域对象转成 LangChain4j `ChatMessage`，OpenAI-style HTTP 适配器把领域对象转回 provider 需要的 JSON `messages` 结构。

**Tech Stack:** Java, Spring Boot, MyBatis, Flyway SQL migration, JUnit 5, Mockito, AssertJ, LangChain4j（仅 infrastructure）

---

## 设计约束

1. **领域层只暴露 PenMate 自己的消息模型**，不出现 LangChain4j `ChatMessage`、`AiMessage`、`ToolExecutionRequest` 等类型。
2. **滑动窗口只拼接已持久化的历史消息**，tool loop 运行期产生的 assistant/tool 中间消息仍然只在内存和审批快照中流转，不回写会话历史。
3. **当前轮用户请求不重复发送**：`ConversationWindowBuilder` 需要识别“仓储里最后一条 user 消息就是当前 `promptSnapshot`”的情况，避免历史窗口和当前 prompt 同时包含同一条用户输入。
4. **历史窗口按轮裁剪而不是按消息条数裁剪**：从尾部取最近 N 个 user-started turns，保留每个 turn 内的 user + assistant 顺序。
5. **provider wire format 不变**：审批快照 JSON、OpenAI-style payload、Claude tool-call 结构继续兼容现有 `role/content/tool_calls/tool_call_id` 形状。

---

## 目标文件总览

### 新建文件
- `penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentLlmMessage.java`
- `penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentLlmMessageRole.java`
- `penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentLlmToolCallPayload.java`
- `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/ConversationWindowBuilder.java`
- `penmate-backend/src/test/java/com/penmate/backend/domain/agent/model/AgentLlmMessageTest.java`
- `penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/ConversationWindowBuilderTest.java`
- `penmate-backend/src/test/java/com/penmate/backend/application/agent/AgentModelRoutingServiceTest.java`
- `penmate-backend/src/test/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/AgentLlmMessagePayloadMapperTest.java`
- `penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/AgentLlmMessagePayloadMapper.java`

### 重点修改文件
- `penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql`
- `penmate-backend/src/test/resources/db/cases/seed_all_domain_base.sql`
- `penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmTurnRequest.java`
- `penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmExecutionConfig.java`
- `penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java`
- `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java`
- `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java`
- `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java`
- `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallSnapshotMapper.java`
- `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallResumeService.java`
- `penmate-backend/src/main/java/com/penmate/backend/domain/model/repository/ModelRepository.java`
- `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelRepositoryImpl.java`
- `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelMapper.java`
- `penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java`
- `penmate-backend/src/main/java/com/penmate/backend/application/model/command/ModelCommands.java`
- `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java`
- `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/CreateUserModelConfigDto.java`
- `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/UpdateUserModelConfigDto.java`
- `penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ClaudeProviderChatClient.java`
- `penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java`
- `penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssemblerTest.java`
- `penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunnerTest.java`
- `penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java`
- `penmate-backend/src/test/java/com/penmate/backend/application/model/ModelApplicationServiceTest.java`

---

## Task 1: 引入类型安全的 LLM 消息领域对象

Use [test-driven-development] mode for this task.

**Files:**
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentLlmMessageRole.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentLlmToolCallPayload.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentLlmMessage.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmTurnRequest.java:9-19`
- Test: `penmate-backend/src/test/java/com/penmate/backend/domain/agent/model/AgentLlmMessageTest.java`

### Step 1: Write the failing test

```java
package com.penmate.backend.domain.agent.model;

import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentLlmMessageTest {

    @Test
    void should_create_history_safe_user_and_assistant_messages() {
        AgentLlmMessage user = AgentLlmMessage.user("最近剧情是主角夜访城门");
        AgentLlmMessage assistant = AgentLlmMessage.assistant("守卫已经认出主角身份。", List.of());

        assertThat(user.role()).isEqualTo(AgentLlmMessageRole.USER);
        assertThat(user.content()).isEqualTo("最近剧情是主角夜访城门");
        assertThat(user.toolCalls()).isEmpty();
        assertThat(user.toolCallId()).isNull();

        assertThat(assistant.role()).isEqualTo(AgentLlmMessageRole.ASSISTANT);
        assertThat(assistant.content()).isEqualTo("守卫已经认出主角身份。");
        assertThat(assistant.toolCalls()).isEmpty();
    }

    @Test
    void should_create_assistant_tool_call_and_tool_result_messages() {
        AgentLlmToolCallPayload payload = new AgentLlmToolCallPayload(
                "call_1",
                "function",
                "context_enhancer",
                "{\"prompt\":\"补充上下文\"}"
        );

        AgentLlmMessage assistant = AgentLlmMessage.assistant("", List.of(payload));
        AgentLlmMessage tool = AgentLlmMessage.tool("call_1", "{\"context\":\"补充背景设定\"}");

        assertThat(assistant.toolCalls()).containsExactly(payload);
        assertThat(tool.role()).isEqualTo(AgentLlmMessageRole.TOOL);
        assertThat(tool.toolCallId()).isEqualTo("call_1");
        assertThat(tool.content()).isEqualTo("{\"context\":\"补充背景设定\"}");
    }

    @Test
    void should_reject_tool_message_without_tool_call_id() {
        assertThatThrownBy(() -> new AgentLlmMessage(
                AgentLlmMessageRole.TOOL,
                "{}",
                List.of(),
                "  "
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolCallId is required for tool message");
    }

    @Test
    void should_make_turn_request_messages_immutable_and_default_tool_choice() {
        AgentLlmMessage user = AgentLlmMessage.user("当前请求");
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(user),
                List.of(new AgentLlmToolSchema("tool_a", "desc", "{\"type\":\"object\"}")),
                null
        );

        assertThat(request.messages()).containsExactly(user);
        assertThat(request.toolChoice()).isEqualTo("auto");
        assertThatThrownBy(() -> request.messages().add(AgentLlmMessage.user("x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
```

### Step 2: Run test to verify it fails

Run:

```bash
mvn -q -Dtest=AgentLlmMessageTest test
```

Expected:

```text
[ERROR] COMPILATION ERROR
[ERROR] cannot find symbol: class AgentLlmMessage
[ERROR] cannot find symbol: class AgentLlmToolCallPayload
[ERROR] incompatible types: List<AgentLlmMessage> cannot be converted ...
```

### Step 3: Write minimal implementation

`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentLlmMessageRole.java`

```java
package com.penmate.backend.domain.agent.model;

public enum AgentLlmMessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL;

    public String wireValue() {
        return name().toLowerCase();
    }
}
```

`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentLlmToolCallPayload.java`

```java
package com.penmate.backend.domain.agent.model;

import java.util.Objects;

public record AgentLlmToolCallPayload(
        String id,
        String type,
        String functionName,
        String argumentsJson
) {

    public AgentLlmToolCallPayload {
        id = requireText(id, "id");
        type = normalizeType(type);
        functionName = requireText(functionName, "functionName");
        argumentsJson = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson.trim();
    }

    private static String normalizeType(String value) {
        return value == null || value.isBlank() ? "function" : value.trim();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
```

`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentLlmMessage.java`

```java
package com.penmate.backend.domain.agent.model;

import java.util.List;
import java.util.Objects;

public record AgentLlmMessage(
        AgentLlmMessageRole role,
        String content,
        List<AgentLlmToolCallPayload> toolCalls,
        String toolCallId
) {

    public AgentLlmMessage {
        role = Objects.requireNonNull(role, "role must not be null");
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        toolCallId = normalizeNullable(toolCallId);

        if (role == AgentLlmMessageRole.TOOL && toolCallId == null) {
            throw new IllegalArgumentException("toolCallId is required for tool message");
        }
        if (role != AgentLlmMessageRole.ASSISTANT && !toolCalls.isEmpty()) {
            throw new IllegalArgumentException("toolCalls are only allowed for assistant message");
        }
        if (role != AgentLlmMessageRole.TOOL && toolCallId != null) {
            throw new IllegalArgumentException("toolCallId is only allowed for tool message");
        }
    }

    public static AgentLlmMessage system(String content) {
        return new AgentLlmMessage(AgentLlmMessageRole.SYSTEM, content, List.of(), null);
    }

    public static AgentLlmMessage user(String content) {
        return new AgentLlmMessage(AgentLlmMessageRole.USER, content, List.of(), null);
    }

    public static AgentLlmMessage assistant(String content, List<AgentLlmToolCallPayload> toolCalls) {
        return new AgentLlmMessage(AgentLlmMessageRole.ASSISTANT, content, toolCalls, null);
    }

    public static AgentLlmMessage tool(String toolCallId, String content) {
        return new AgentLlmMessage(AgentLlmMessageRole.TOOL, content, List.of(), toolCallId);
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
```

Replace `penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmTurnRequest.java` with:

```java
package com.penmate.backend.application.agent.llm;

import com.penmate.backend.domain.agent.model.AgentLlmMessage;

import java.util.List;

/**
 * LLM 单轮对话请求。
 */
public record AgentLlmTurnRequest(
        List<AgentLlmMessage> messages,
        List<AgentLlmToolSchema> tools,
        String toolChoice
) {

    public AgentLlmTurnRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        toolChoice = (toolChoice == null || toolChoice.isBlank()) ? "auto" : toolChoice.trim();
    }
}
```

### Step 4: Run test to verify it passes

Run:

```bash
mvn -q -Dtest=AgentLlmMessageTest test
```

Expected:

```text
BUILD SUCCESS
```

### Step 5: Commit

```bash
git add penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentLlmMessageRole.java penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentLlmToolCallPayload.java penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentLlmMessage.java penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmTurnRequest.java penmate-backend/src/test/java/com/penmate/backend/domain/agent/model/AgentLlmMessageTest.java
git commit -m "feat(agent): introduce typed llm message domain model"
```

---

## Task 2: 让 `context_window_turns` 从模型配置落库并进入执行配置

Use [test-driven-development] mode for this task.

**Files:**
- Modify: `penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql:85-104`
- Modify: `penmate-backend/src/test/resources/db/cases/seed_all_domain_base.sql:200-203`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/CreateUserModelConfigDto.java:12-31`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/UpdateUserModelConfigDto.java:10-26`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/model/command/ModelCommands.java:38-53`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/domain/model/repository/ModelRepository.java:64-92`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelRepositoryImpl.java:118-166`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelMapper.java:169-257`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java:93-166`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:192-245`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmExecutionConfig.java:9-25`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java:26-79`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/model/ModelApplicationServiceTest.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/AgentModelRoutingServiceTest.java`

### Step 1: Write the failing tests

Append to `penmate-backend/src/test/java/com/penmate/backend/application/model/ModelApplicationServiceTest.java`:

```java
@Test
void UT_APP_MODEL_LIST_USER_MODEL_CONFIGS_SHOULD_EXPOSE_CONTEXT_WINDOW_TURNS() {
    Long userId = 1001L;
    List<Map<String, Object>> expected = List.of(Map.of(
            "modelConfigId", 9001L,
            "modelName", "gpt-4o-mini",
            "providerId", 1L,
            "keySourceType", "USER_KEY",
            "keyName", "OpenAI User Key",
            "maskedApiKey", "****1234",
            "contextWindowTurns", 6
    ));
    when(modelRepository.listUserModelConfigs(userId)).thenReturn(expected);

    List<Map<String, Object>> result = modelApplicationService.listUserModelConfigs(userId);

    assertThat(result).isEqualTo(expected);
    assertThat(result.get(0)).containsEntry("contextWindowTurns", 6);
}
```

Create `penmate-backend/src/test/java/com/penmate/backend/application/agent/AgentModelRoutingServiceTest.java`:

```java
package com.penmate.backend.application.agent;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.shared.service.SecretCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentModelRoutingServiceTest {

    @Mock
    private ModelRepository modelRepository;

    @Mock
    private SecretCryptoService secretCryptoService;

    @InjectMocks
    private AgentModelRoutingService agentModelRoutingService;

    @Test
    void should_carry_context_window_turns_into_execution_config() {
        when(modelRepository.findUserModelConfig(1001L, 9001L)).thenReturn(Map.of(
                "providerId", 1L,
                "modelName", "gpt-4o-mini",
                "baseUrl", "https://api.openai.com/v1",
                "encryptedApiKey", "cipher-key",
                "keyStatus", "active",
                "contextWindowTurns", 8
        ));
        when(secretCryptoService.decrypt("cipher-key")).thenReturn("sk-live");

        AgentLlmExecutionConfig config = agentModelRoutingService.resolveExecutionConfig(1001L, 9001L, "trace-ctx-window");

        assertThat(config.contextWindowTurns()).isEqualTo(8);
    }
}
```

### Step 2: Run tests to verify they fail

Run:

```bash
mvn -q -Dtest=ModelApplicationServiceTest,AgentModelRoutingServiceTest test
```

Expected:

```text
[ERROR] COMPILATION ERROR
[ERROR] cannot find symbol: method contextWindowTurns()
[ERROR] constructor CreateUserModelConfigCommand ... does not match
```

### Step 3: Write minimal implementation

Replace the `model_user_configurations` DDL block in `penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql` with:

```sql
CREATE TABLE IF NOT EXISTS model_user_configurations (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    model_config_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    provider_id BIGINT UNSIGNED NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    base_url VARCHAR(255) NULL,
    key_source_type VARCHAR(20) NOT NULL,
    user_key_id BIGINT UNSIGNED NULL,
    official_key_id BIGINT UNSIGNED NULL,
    context_window_turns INT UNSIGNED NOT NULL DEFAULT 6 COMMENT '发送给 LLM 的历史对话轮数，0 表示禁用历史窗口',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_model_user_configurations_model_config_id (model_config_id),
    KEY idx_model_user_config_user_deleted (user_id, deleted_at),
    KEY idx_model_user_config_provider_deleted (provider_id, deleted_at),
    KEY idx_model_user_config_user_key (user_key_id),
    KEY idx_model_user_config_official_key (official_key_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

Update the seed insert in `penmate-backend/src/test/resources/db/cases/seed_all_domain_base.sql` to:

```sql
INSERT INTO model_user_configurations (
    id, model_config_id, user_id, provider_id, model_name, base_url,
    key_source_type, user_key_id, official_key_id, context_window_turns,
    status, created_at, updated_at, deleted_at
) VALUES
(920001, 920021, 920002, 1, 'gpt-4o-mini',    NULL,                    'USER_KEY',     920011, NULL,   6, 'active', NOW(3), NOW(3), NULL),
(920002, 920022, 920003, 1, 'gpt-4.1-mini',   'https://api.openai.com/v1', 'USER_KEY', 920012, NULL,   4, 'active', NOW(3), NOW(3), NULL),
(920003, 920023, 920002, 2, 'claude-3-5-sonnet', NULL,                 'USER_KEY',     920013, NULL,   0, 'active', NOW(3), NOW(3), NULL),
(920004, 920024, 920001, 1, 'gpt-4o',         NULL,                    'OFFICIAL_KEY', NULL,   920001, 8, 'active', NOW(3), NOW(3), NULL);
```

Replace `penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmExecutionConfig.java` with:

```java
package com.penmate.backend.application.agent.llm;

import lombok.Builder;

/**
 * Agent 单次模型调用执行配置。
 */
@Builder
public record AgentLlmExecutionConfig(
        Long modelConfigId,
        String providerCode,
        String baseUrl,
        String apiKey,
        String modelName,
        String keySource,
        Integer contextWindowTurns) {
}
```

Replace the command records in `penmate-backend/src/main/java/com/penmate/backend/application/model/command/ModelCommands.java` with:

```java
public record CreateUserModelConfigCommand(Long providerId,
                                           String modelName,
                                           String baseUrl,
                                           String keySourceType,
                                           String apiKey,
                                           Integer contextWindowTurns,
                                           String status,
                                           Long operatorId) {
}

public record UpdateUserModelConfigCommand(Long providerId,
                                           String modelName,
                                           String baseUrl,
                                           String keySourceType,
                                           String apiKey,
                                           Integer contextWindowTurns,
                                           String status,
                                           Long operatorId) {
}
```

Update DTOs:

`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/CreateUserModelConfigDto.java`

```java
package com.penmate.backend.interfaces.api.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class CreateUserModelConfigDto {

    @NotBlank
    @Pattern(regexp = "^[1-9]\\d*$", message = "providerId must be greater than 0")
    @JsonDeserialize(using = StringIdOnlyDeserializer.class)
    private String providerId;

    @NotBlank
    private String modelName;

    private String baseUrl;

    @NotBlank
    private String modelCategory;

    @NotBlank
    private String apiKey;

    @PositiveOrZero
    private Integer contextWindowTurns;

    private String status;
}
```

`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/UpdateUserModelConfigDto.java`

```java
package com.penmate.backend.interfaces.api.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class UpdateUserModelConfigDto {

    @Pattern(regexp = "^[1-9]\\d*$", message = "providerId must be greater than 0")
    @JsonDeserialize(using = StringIdOnlyDeserializer.class)
    private String providerId;

    private String modelName;

    private String baseUrl;

    private String modelCategory;

    private String apiKey;

    @PositiveOrZero
    private Integer contextWindowTurns;

    private String status;
}
```

Update controller constructor calls in `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java`:

```java
new ModelCommands.CreateUserModelConfigCommand(
        requirePositiveLongId(dto.getProviderId(), "providerId"),
        dto.getModelName(),
        dto.getBaseUrl(),
        mapModelCategoryToKeySourceType(dto.getModelCategory()),
        dto.getApiKey(),
        dto.getContextWindowTurns(),
        dto.getStatus(),
        requirePositiveLongId(operatorId, "operatorId")
)
```

```java
new ModelCommands.UpdateUserModelConfigCommand(
        optionalPositiveLongId(dto.getProviderId(), "providerId"),
        dto.getModelName(),
        dto.getBaseUrl(),
        mapModelCategoryToKeySourceType(dto.getModelCategory()),
        dto.getApiKey(),
        dto.getContextWindowTurns(),
        dto.getStatus(),
        requirePositiveLongId(operatorId, "operatorId")
)
```

Update repository contract in `penmate-backend/src/main/java/com/penmate/backend/domain/model/repository/ModelRepository.java`:

```java
int insertUserModelConfig(Long modelConfigId,
                          Long userId,
                          Long providerId,
                          String modelName,
                          String baseUrl,
                          String keySourceType,
                          Long userKeyId,
                          Long officialKeyId,
                          Integer contextWindowTurns,
                          String status);

int updateUserModelConfig(Long userId,
                          Long modelConfigId,
                          Long providerId,
                          String modelName,
                          String baseUrl,
                          String keySourceType,
                          Long userKeyId,
                          Long officialKeyId,
                          Integer contextWindowTurns,
                          String status);
```

Update `ModelMapper` selects and writes so `context_window_turns` is always selected and persisted:

```java
SELECT muc.model_config_id AS modelConfigId,
       muc.user_id AS userId,
       muc.provider_id AS providerId,
       muc.model_name AS modelName,
       muc.base_url AS baseUrl,
       muc.key_source_type AS keySourceType,
       muc.user_key_id AS userKeyId,
       muc.official_key_id AS officialKeyId,
       muc.context_window_turns AS contextWindowTurns,
       CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.key_name ELSE mok.key_name END AS keyName,
       CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.masked_api_key ELSE mok.masked_api_key END AS maskedApiKey,
       muc.status AS status
```

```java
SELECT muc.model_config_id AS modelConfigId,
       muc.user_id AS userId,
       muc.provider_id AS providerId,
       muc.model_name AS modelName,
       muc.base_url AS baseUrl,
       muc.key_source_type AS keySourceType,
       muc.user_key_id AS userKeyId,
       muc.official_key_id AS officialKeyId,
       muc.context_window_turns AS contextWindowTurns,
       CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.key_name ELSE mok.key_name END AS keyName,
       CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.encrypted_api_key ELSE mok.encrypted_api_key END AS encryptedApiKey,
       CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.masked_api_key ELSE mok.masked_api_key END AS maskedApiKey,
       CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.status ELSE mok.status END AS keyStatus,
       muc.status AS status
```

```java
@Insert("""
        INSERT INTO model_user_configurations(
            model_config_id, user_id, provider_id, model_name, base_url,
            key_source_type, user_key_id, official_key_id, context_window_turns, status
        )
        VALUES (
            #{modelConfigId}, #{userId}, #{providerId}, #{modelName}, #{baseUrl},
            #{keySourceType}, #{userKeyId}, #{officialKeyId}, #{contextWindowTurns}, #{status}
        )
        """)
int insertUserModelConfig(..., @Param("contextWindowTurns") Integer contextWindowTurns, @Param("status") String status);
```

```java
@Update("""
        UPDATE model_user_configurations
        SET provider_id = COALESCE(#{providerId}, provider_id),
            model_name = COALESCE(#{modelName}, model_name),
            base_url = COALESCE(#{baseUrl}, base_url),
            key_source_type = COALESCE(#{keySourceType}, key_source_type),
            user_key_id = #{userKeyId},
            official_key_id = #{officialKeyId},
            context_window_turns = COALESCE(#{contextWindowTurns}, context_window_turns),
            status = COALESCE(#{status}, status),
            updated_at = CURRENT_TIMESTAMP(3)
        WHERE user_id = #{userId} AND model_config_id = #{modelConfigId} AND deleted_at IS NULL
        """)
int updateUserModelConfig(..., @Param("contextWindowTurns") Integer contextWindowTurns, @Param("status") String status);
```

Update `ModelApplicationService` methods:

```java
private static final int DEFAULT_CONTEXT_WINDOW_TURNS = 6;
```

```java
public void createUserModelConfig(Long userId, CreateUserModelConfigCommand command, String traceId) {
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(command, "command must not be null");

    Long providerId = validateProviderAndModelName(command.providerId(), command.modelName());
    Long modelConfigId = businessIdGenerator.nextId();
    KeyBinding keyBinding = resolveKeyBindingForCreate(
            userId,
            modelConfigId,
            providerId,
            command.modelName(),
            command.keySourceType(),
            command.apiKey(),
            command.status()
    );

    int affected = modelRepository.insertUserModelConfig(
            modelConfigId,
            userId,
            providerId,
            normalize(command.modelName()),
            normalizeNullable(command.baseUrl()),
            keyBinding.keySourceType(),
            keyBinding.userKeyId(),
            keyBinding.officialKeyId(),
            normalizeContextWindowTurns(command.contextWindowTurns()),
            normalizeStatus(command.status())
    );
    if (affected != 1) {
        throw BusinessException.of("Failed to create user model config");
    }
    writeAudit(traceId, command.operatorId(), "model", "create-user-model-config", "model_user_configurations", modelConfigId.toString(), null, 200);
}
```

```java
public void updateUserModelConfig(Long userId, Long modelConfigId, UpdateUserModelConfigCommand command, String traceId) {
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(modelConfigId, "modelConfigId must not be null");
    Objects.requireNonNull(command, "command must not be null");

    Map<String, Object> existing = modelRepository.findUserModelConfig(userId, modelConfigId);
    if (existing == null) {
        throw BusinessException.of("User model config not found");
    }

    Long mergedProviderId = command.providerId() != null ? command.providerId() : longValue(existing.get("providerId"));
    String mergedModelName = command.modelName() != null ? normalize(command.modelName()) : stringValue(existing.get("modelName"));
    String mergedBaseUrl = command.baseUrl() != null
            ? normalizeNullable(command.baseUrl())
            : normalizeNullable(stringValue(existing.get("baseUrl")));
    String mergedKeySourceType = command.keySourceType() != null
            ? normalizeKeySourceType(command.keySourceType())
            : stringValue(existing.get("keySourceType"));
    String mergedStatus = command.status() != null ? normalizeStatus(command.status()) : stringValue(existing.get("status"));
    Integer mergedContextWindowTurns = command.contextWindowTurns() != null
            ? normalizeContextWindowTurns(command.contextWindowTurns())
            : intValue(existing.get("contextWindowTurns"), DEFAULT_CONTEXT_WINDOW_TURNS);

    validateProviderAndModelName(mergedProviderId, mergedModelName);
    KeyBinding keyBinding = resolveKeyBindingForUpdate(
            userId,
            modelConfigId,
            mergedProviderId,
            mergedModelName,
            mergedKeySourceType,
            command.apiKey(),
            mergedStatus,
            existing
    );

    int affected = modelRepository.updateUserModelConfig(
            userId,
            modelConfigId,
            mergedProviderId,
            mergedModelName,
            mergedBaseUrl,
            keyBinding.keySourceType(),
            keyBinding.userKeyId(),
            keyBinding.officialKeyId(),
            mergedContextWindowTurns,
            mergedStatus
    );
    if (affected != 1) {
        throw BusinessException.of("Failed to update user model config");
    }
    writeAudit(traceId, command.operatorId(), "model", "update-user-model-config", "model_user_configurations", modelConfigId.toString(), null, 200);
}
```

```java
private Integer normalizeContextWindowTurns(Integer value) {
    if (value == null) {
        return DEFAULT_CONTEXT_WINDOW_TURNS;
    }
    if (value < 0) {
        throw BusinessException.of("contextWindowTurns must be greater than or equal to 0");
    }
    return value;
}

private Integer intValue(Object value, int defaultValue) {
    if (value == null) {
        return defaultValue;
    }
    if (value instanceof Number number) {
        return number.intValue();
    }
    return Integer.parseInt(String.valueOf(value));
}
```

Update `AgentModelRoutingService.resolveExecutionConfig()` to carry the field:

```java
Integer contextWindowTurns = intValue(config.get("contextWindowTurns"), 6);
return AgentLlmExecutionConfig.builder()
        .modelConfigId(modelConfigId)
        .providerCode(provider.getCode())
        .baseUrl(resolvedBaseUrl)
        .apiKey(plainApiKey)
        .modelName(resolvedModelName.trim())
        .keySource("MODEL_CONFIG")
        .contextWindowTurns(contextWindowTurns)
        .build();
```

with helper:

```java
private Integer intValue(Object value, int defaultValue) {
    if (value == null) {
        return defaultValue;
    }
    if (value instanceof Number number) {
        return number.intValue();
    }
    return Integer.parseInt(String.valueOf(value));
}
```

### Step 4: Run tests to verify they pass

Run:

```bash
mvn -q -Dtest=ModelApplicationServiceTest,AgentModelRoutingServiceTest test
```

Expected:

```text
BUILD SUCCESS
```

### Step 5: Commit

```bash
git add penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql penmate-backend/src/test/resources/db/cases/seed_all_domain_base.sql penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/CreateUserModelConfigDto.java penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/UpdateUserModelConfigDto.java penmate-backend/src/main/java/com/penmate/backend/application/model/command/ModelCommands.java penmate-backend/src/main/java/com/penmate/backend/domain/model/repository/ModelRepository.java penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelRepositoryImpl.java penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelMapper.java penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmExecutionConfig.java penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java penmate-backend/src/test/java/com/penmate/backend/application/model/ModelApplicationServiceTest.java penmate-backend/src/test/java/com/penmate/backend/application/agent/AgentModelRoutingServiceTest.java
git commit -m "feat(model): persist conversation window config"
```

---

## Task 3: 在应用层实现 `ConversationWindowBuilder`

Use [test-driven-development] mode for this task.

**Files:**
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/ConversationWindowBuilder.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/ConversationWindowBuilderTest.java`
- Read-only dependency: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentMessage.java`
- Read-only dependency: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/repository/AgentRepository.java:18`

### Step 1: Write the failing test

```java
package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationWindowBuilderTest {

    @Mock
    private AgentRepository agentRepository;

    @InjectMocks
    private ConversationWindowBuilder conversationWindowBuilder;

    @Test
    void should_sort_by_seq_and_return_last_two_completed_turns_without_current_prompt_duplication() {
        when(agentRepository.listMessages(9L)).thenReturn(List.of(
                message(1004L, "assistant", "第一轮回答", 4),
                message(1001L, "user", "第一轮提问", 1),
                message(1003L, "user", "第二轮提问", 3),
                message(1006L, "user", "当前提问", 6),
                message(1002L, "assistant", "第一轮补充", 2),
                message(1005L, "assistant", "第二轮回答", 5)
        ));

        List<AgentLlmMessage> result = conversationWindowBuilder.build(9L, "当前提问", 2);

        assertThat(result).extracting(AgentLlmMessage::content)
                .containsExactly("第一轮提问", "第一轮补充", "第一轮回答", "第二轮提问", "第二轮回答");
    }

    @Test
    void should_return_empty_when_window_is_disabled() {
        assertThat(conversationWindowBuilder.build(9L, "当前提问", 0)).isEmpty();
        assertThat(conversationWindowBuilder.build(9L, "当前提问", null)).isEmpty();
    }

    private AgentMessage message(Long messageId, String role, String content, int seqNo) {
        AgentMessage message = new AgentMessage();
        message.setMessageId(messageId);
        message.setConversationId(9L);
        message.setRole(role);
        message.setContentMd(content);
        message.setSeqNo(seqNo);
        return message;
    }
}
```

### Step 2: Run test to verify it fails

Run:

```bash
mvn -q -Dtest=ConversationWindowBuilderTest test
```

Expected:

```text
[ERROR] COMPILATION ERROR
[ERROR] cannot find symbol: class ConversationWindowBuilder
```

### Step 3: Write minimal implementation

`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/ConversationWindowBuilder.java`

```java
package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ConversationWindowBuilder {

    private final AgentRepository agentRepository;

    public List<AgentLlmMessage> build(Long conversationId, String currentPrompt, Integer contextWindowTurns) {
        if (conversationId == null || contextWindowTurns == null || contextWindowTurns <= 0) {
            return List.of();
        }

        List<AgentMessage> sortedMessages = agentRepository.listMessages(conversationId).stream()
                .filter(this::isUsableConversationMessage)
                .sorted(Comparator.comparing(AgentMessage::getSeqNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(AgentMessage::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        List<AgentMessage> historyOnly = dropCurrentPromptTail(sortedMessages, currentPrompt);
        List<List<AgentMessage>> turns = groupIntoTurns(historyOnly);
        if (turns.isEmpty()) {
            return List.of();
        }

        int fromIndex = Math.max(0, turns.size() - contextWindowTurns);
        List<AgentLlmMessage> result = new ArrayList<>();
        for (List<AgentMessage> turn : turns.subList(fromIndex, turns.size())) {
            for (AgentMessage message : turn) {
                result.add(toLlmMessage(message));
            }
        }
        return List.copyOf(result);
    }

    private boolean isUsableConversationMessage(AgentMessage message) {
        if (message == null || message.getRole() == null || message.getContentMd() == null) {
            return false;
        }
        String role = message.getRole().trim().toLowerCase(Locale.ROOT);
        return ("user".equals(role) || "assistant".equals(role)) && !message.getContentMd().isBlank();
    }

    private List<AgentMessage> dropCurrentPromptTail(List<AgentMessage> messages, String currentPrompt) {
        if (messages.isEmpty() || currentPrompt == null || currentPrompt.isBlank()) {
            return messages;
        }
        AgentMessage tail = messages.get(messages.size() - 1);
        if ("user".equalsIgnoreCase(tail.getRole())
                && currentPrompt.trim().equals(tail.getContentMd() == null ? null : tail.getContentMd().trim())) {
            return messages.subList(0, messages.size() - 1);
        }
        return messages;
    }

    private List<List<AgentMessage>> groupIntoTurns(List<AgentMessage> messages) {
        List<List<AgentMessage>> turns = new ArrayList<>();
        List<AgentMessage> currentTurn = new ArrayList<>();
        for (AgentMessage message : messages) {
            if ("user".equalsIgnoreCase(message.getRole())) {
                if (!currentTurn.isEmpty()) {
                    turns.add(List.copyOf(currentTurn));
                    currentTurn.clear();
                }
                currentTurn.add(message);
                continue;
            }
            if (!currentTurn.isEmpty()) {
                currentTurn.add(message);
            }
        }
        if (!currentTurn.isEmpty()) {
            turns.add(List.copyOf(currentTurn));
        }
        return turns;
    }

    private AgentLlmMessage toLlmMessage(AgentMessage message) {
        return "assistant".equalsIgnoreCase(message.getRole())
                ? AgentLlmMessage.assistant(message.getContentMd(), List.of())
                : AgentLlmMessage.user(message.getContentMd());
    }
}
```

### Step 4: Run test to verify it passes

Run:

```bash
mvn -q -Dtest=ConversationWindowBuilderTest test
```

Expected:

```text
BUILD SUCCESS
```

### Step 5: Commit

```bash
git add penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/ConversationWindowBuilder.java penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/ConversationWindowBuilderTest.java
git commit -m "feat(agent): build conversation history window"
```

---

## Task 4: 改造 `AgentPromptAssembler`，把历史窗口拼进执行消息

Use [test-driven-development] mode for this task.

**Files:**
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java:34-120`
- Modify: `penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssemblerTest.java:42-220`

### Step 1: Write the failing test

Add a new test to `AgentPromptAssemblerTest`:

```java
@Test
void should_insert_conversation_window_between_system_and_current_user_request() {
    PromptPlan promptPlan = new PromptPlan(
            List.of(new PromptModulePlan("execution:default", "prompts/agent/system/execution/default/00-base-role.md", true, "test")),
            List.of(),
            "default",
            "你是执行代理"
    );
    ContextPackage contextPackage = new ContextPackage(
            List.of("story-bible"),
            List.of(),
            List.of(),
            List.of("角色年龄：17（canon）"),
            List.of("设定集#2：王都夜禁"),
            "{\"styleId\":81,\"tone\":\"克制\"}",
            "chapter:21"
    );

    List<AgentLlmMessage> messages = agentPromptAssembler.buildExecutionMessages(
            promptPlan,
            contextPackage,
            "核对冲突后继续写作",
            List.of(
                    AgentLlmMessage.user("上一轮提问"),
                    AgentLlmMessage.assistant("上一轮回答", List.of())
            )
    );

    assertThat(messages).hasSize(4);
    assertThat(messages.get(0).role()).isEqualTo(AgentLlmMessageRole.SYSTEM);
    assertThat(messages.get(1).content()).isEqualTo("上一轮提问");
    assertThat(messages.get(2).content()).isEqualTo("上一轮回答");
    assertThat(messages.get(3).content())
            .contains("<context type=\"style\">\n{\"styleId\":81,\"tone\":\"克制\"}\n</context>")
            .contains("<context type=\"story_bible\">\n角色年龄：17（canon）\n</context>")
            .contains("<user_request>\n核对冲突后继续写作\n</user_request>");
}
```

### Step 2: Run test to verify it fails

Run:

```bash
mvn -q -Dtest=AgentPromptAssemblerTest test
```

Expected:

```text
[ERROR] COMPILATION ERROR
[ERROR] method buildExecutionMessages(...) cannot be applied to given types
```

### Step 3: Write minimal implementation

Replace `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java` with the following structure (imports omitted for brevity, but keep package and existing dependencies):

```java
@Component
public class AgentPromptAssembler {

    private final SystemPromptProvider systemPromptProvider;
    private final StructuredPromptBlockFormatter structuredPromptBlockFormatter;

    public AgentPromptAssembler(SystemPromptProvider systemPromptProvider,
                                StructuredPromptBlockFormatter structuredPromptBlockFormatter) {
        this.systemPromptProvider = systemPromptProvider;
        this.structuredPromptBlockFormatter = structuredPromptBlockFormatter;
    }

    public List<AgentLlmMessage> buildInitialMessages(AgentGenerationTask task,
                                                      AgentTaskContext taskContext,
                                                      List<RagRetrievedChunk> ragChunks) {
        return buildExecutionMessages(task, taskContext, ragChunks, resolveProfile(task), "", List.of());
    }

    public List<AgentLlmMessage> buildExecutionMessages(AgentGenerationTask task,
                                                        AgentTaskContext taskContext,
                                                        List<RagRetrievedChunk> ragChunks,
                                                        String executionProfile,
                                                        String storyBibleContent,
                                                        List<AgentLlmMessage> conversationWindow) {
        String prompt = task.getPromptSnapshot() == null ? "" : task.getPromptSnapshot().trim();
        String style = taskContext == null || taskContext.getStyleSnapshotJson() == null
                ? ""
                : taskContext.getStyleSnapshotJson().trim();
        String storyBible = storyBibleContent == null ? "" : storyBibleContent.trim();
        StringJoiner userBuilder = new StringJoiner("\n\n");

        if (!style.isEmpty()) {
            userBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"style\"", style));
        }
        if (ragChunks != null && !ragChunks.isEmpty()) {
            StringBuilder ragBuilder = new StringBuilder();
            for (RagRetrievedChunk chunk : ragChunks) {
                ragBuilder.append("- [")
                        .append(chunk.getDocumentTitle() == null ? "文档" : chunk.getDocumentTitle())
                        .append("#")
                        .append(chunk.getChunkNo() == null ? 0 : chunk.getChunkNo())
                        .append("] ")
                        .append(chunk.getContentText() == null ? "" : chunk.getContentText())
                        .append("\n");
            }
            userBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"rag\"", ragBuilder.toString()));
        }
        if (!storyBible.isEmpty()) {
            userBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"story_bible\"", storyBible));
        }
        userBuilder.add(structuredPromptBlockFormatter.wrapBlock("user_request", prompt));

        String profile = executionProfile == null || executionProfile.isBlank() ? resolveProfile(task) : executionProfile.trim();
        SystemPromptBundle promptBundle = systemPromptProvider.loadBundle("execution", profile);

        List<AgentLlmMessage> result = new java.util.ArrayList<>();
        result.add(AgentLlmMessage.system(promptBundle.assembledPrompt()));
        if (conversationWindow != null && !conversationWindow.isEmpty()) {
            result.addAll(conversationWindow);
        }
        result.add(AgentLlmMessage.user(userBuilder.toString()));
        return List.copyOf(result);
    }

    public List<AgentLlmMessage> buildExecutionMessages(PromptPlan promptPlan,
                                                        ContextPackage contextPackage,
                                                        String userRequest,
                                                        List<AgentLlmMessage> conversationWindow) {
        StringJoiner userBuilder = new StringJoiner("\n\n");
        ContextPackage resolvedContext = Objects.requireNonNull(contextPackage, "contextPackage");

        if (!resolvedContext.styleSnapshot().isBlank()) {
            userBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"style\"", resolvedContext.styleSnapshot()));
        }
        if (!resolvedContext.storyBibleEntries().isEmpty()) {
            userBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"story_bible\"", String.join("\n", resolvedContext.storyBibleEntries())));
        }
        if (!resolvedContext.conflicts().isEmpty()) {
            userBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"conflict\"", String.join("\n", resolvedContext.conflicts())));
        }
        if (!resolvedContext.missingContextFlags().isEmpty()) {
            userBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"missing\"", String.join("\n", resolvedContext.missingContextFlags())));
        }
        userBuilder.add(structuredPromptBlockFormatter.wrapBlock("user_request", userRequest == null ? "" : userRequest.trim()));

        List<AgentLlmMessage> result = new java.util.ArrayList<>();
        result.add(AgentLlmMessage.system(promptPlan == null ? "" : promptPlan.assembledPromptPreview()));
        if (conversationWindow != null && !conversationWindow.isEmpty()) {
            result.addAll(conversationWindow);
        }
        result.add(AgentLlmMessage.user(userBuilder.toString()));
        return List.copyOf(result);
    }

    private String resolveProfile(AgentGenerationTask task) {
        if (task == null || task.getTaskType() == null) {
            return "default";
        }
        return switch (task.getTaskType().trim().toUpperCase()) {
            case "WORLD_BUILD" -> "world-build";
            case "REWRITE" -> "rewrite";
            default -> "default";
        };
    }
}
```

Also refactor existing assertions in `AgentPromptAssemblerTest` from map access to typed access, e.g.:

```java
assertThat(messages.get(0).role()).isEqualTo(AgentLlmMessageRole.SYSTEM);
assertThat(messages.get(0).content()).isEqualTo("你是执行代理");
assertThat(messages.get(1).role()).isEqualTo(AgentLlmMessageRole.USER);
assertThat(messages.get(1).content())
        .contains("<context type=\"style\">\n{\"styleId\":81,\"tone\":\"克制\"}\n</context>")
        .contains("<user_request>\n请续写主角夜访城门后的场景\n</user_request>");
```

### Step 4: Run test to verify it passes

Run:

```bash
mvn -q -Dtest=AgentPromptAssemblerTest test
```

Expected:

```text
BUILD SUCCESS
```

### Step 5: Commit

```bash
git add penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssemblerTest.java
git commit -m "refactor(agent): assemble execution messages with typed history"
```

---

## Task 5: 适配 Tool Loop 与审批快照消息流转

Use [test-driven-development] mode for this task.

**Files:**
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:57-173`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallSnapshotMapper.java:23-128`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallResumeService.java:42-188`
- Modify: `penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunnerTest.java:69-580`

### Step 1: Write the failing test

Refactor the first test in `AgentToolLoopRunnerTest` to typed messages:

```java
@Test
void UT_APP_AGENT_TOOL_LOOP_RUNNER_SHOULD_COMPLETE_MINIMAL_TWO_TURN_TOOL_CALL_LOOP() {
    AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
            .providerCode("openai-compatible")
            .modelName("gpt-test")
            .contextWindowTurns(6)
            .build();
    List<AgentLlmMessage> initialMessages = List.of(AgentLlmMessage.user("请补充这个场景需要的上下文"));
    AgentLlmToolSchema contextEnhancerSchema = new AgentLlmToolSchema(
            "context_enhancer",
            "补充上下文",
            """
                    {
                      "type": "object",
                      "properties": {
                        "prompt": {
                          "type": "string"
                        }
                      },
                      "required": ["prompt"]
                    }
                    """
    );

    when(toolDefinitionSource.listLlmSchemas()).thenReturn(List.of(contextEnhancerSchema));
    when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
            .thenReturn(new AgentLlmTurnResponse(
                    "tool_calls",
                    "",
                    List.of(new AgentLlmToolCall("call_1", "context_enhancer", "{\"prompt\":\"请补充这个场景需要的上下文\"}")),
                    "{\"finish_reason\":\"tool_calls\"}"
            ))
            .thenReturn(new AgentLlmTurnResponse(
                    "stop",
                    "这是补充上下文后的最终答案",
                    List.of(),
                    "{\"finish_reason\":\"stop\"}"
            ));
    when(toolCallApplicationService.executeToolCall(any())).thenReturn(ToolCallResult.success("{\"context\":\"补充背景设定\"}"));

    AgentToolLoopIterationResult result = agentToolLoopRunner.execute(
            1L,
            11L,
            9L,
            0L,
            "trace-1",
            initialMessages,
            executionConfig
    );

    assertThat(result.finalAssistantText()).isEqualTo("这是补充上下文后的最终答案");

    ArgumentCaptor<AgentLlmTurnRequest> requestCaptor = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
    verify(agentLlmGateway, times(2)).generateTurn(requestCaptor.capture(), eq(executionConfig));

    List<AgentLlmTurnRequest> requests = requestCaptor.getAllValues();
    assertThat(requests.get(0).messages()).containsExactlyElementsOf(initialMessages);
    assertThat(requests.get(1).messages()).hasSize(3);
    assertThat(requests.get(1).messages().get(1).role()).isEqualTo(AgentLlmMessageRole.ASSISTANT);
    assertThat(requests.get(1).messages().get(1).toolCalls())
            .extracting(AgentLlmToolCallPayload::functionName)
            .containsExactly("context_enhancer");
    assertThat(requests.get(1).messages().get(2).role()).isEqualTo(AgentLlmMessageRole.TOOL);
    assertThat(requests.get(1).messages().get(2).toolCallId()).isEqualTo("call_1");
}
```

### Step 2: Run test to verify it fails

Run:

```bash
mvn -q -Dtest=AgentToolLoopRunnerTest test
```

Expected:

```text
[ERROR] COMPILATION ERROR
[ERROR] List<AgentLlmMessage> cannot be converted to List<Map<String,Object>>
```

### Step 3: Write minimal implementation

Change the signature and message handling in `AgentToolLoopRunner`:

```java
public AgentToolLoopIterationResult execute(Long projectId,
                                            Long taskId,
                                            Long conversationId,
                                            Long operatorId,
                                            String traceId,
                                            List<AgentLlmMessage> initialMessages,
                                            AgentLlmExecutionConfig executionConfig) {
    List<AgentLlmMessage> messages = new ArrayList<>(initialMessages == null ? List.of() : initialMessages);
    List<AgentLlmToolSchema> tools = toolDefinitionSource.listLlmSchemas();
    StringBuilder toolContextBuilder = new StringBuilder();
    int totalToolCalls = 0;
    AgentTaskContext taskContext = loadTaskContext(taskId);
    Long turnId = taskContext == null ? null : taskContext.getTurnId();

    for (int turnIndex = 0; turnIndex < MAX_TOOL_TURNS; turnIndex++) {
        AgentLlmTurnResponse response = agentLlmGateway.generateTurn(
                new AgentLlmTurnRequest(messages, tools, "auto"),
                executionConfig
        );
        if ("tool_calls".equalsIgnoreCase(response.finishReason()) && response.toolCalls().isEmpty()) {
            throw new IllegalStateException("LLM finishReason=tool_calls but toolCalls is empty");
        }
        if (!response.requestsToolCalls()) {
            return AgentToolLoopIterationResult.completed(
                    response.assistantText(),
                    totalToolCalls,
                    toolContextBuilder.toString()
            );
        }
        ensureToolCallsPerTurnWithinLimit(response.toolCalls());

        messages.add(toolCallSnapshotMapper.buildAssistantToolCallMessage(response));
        String assistantToolCallsJson = toolCallSnapshotMapper.toAssistantToolCallsJson(response.toolCalls());
        String loopRunId = buildLoopRunId(taskId, traceId);
        for (AgentLlmToolCall toolCall : response.toolCalls()) {
            totalToolCalls += 1;
            ToolCallResult toolResult = toolCallApplicationService.executeToolCall(new ToolCallRequest(
                    projectId,
                    taskId,
                    conversationId,
                    toolCall.toolCode(),
                    toolCall.argumentsJson(),
                    operatorId,
                    traceId,
                    "{}",
                    buildIdempotencyKey(taskId, toolCall),
                    loopRunId,
                    turnIndex,
                    toolCall.id(),
                    assistantToolCallsJson,
                    toolCallSnapshotMapper.toConversationMessagesJson(messages),
                    "RESUME_LOOP",
                    null
            ));
            publishToolCallStatus(projectId, taskId, conversationId, taskContext, turnId, toolCall, turnIndex, toolResult);
            if ("WAITING_APPROVAL".equals(toolResult.status())) {
                if (toolResult.approvalId() == null) {
                    throw new IllegalStateException("WAITING_APPROVAL result missing approvalId");
                }
                return AgentToolLoopIterationResult.waitingApproval(
                        toolResult.approvalId(),
                        totalToolCalls,
                        toolContextBuilder.toString()
                );
            }

            String toolOutput = extractToolOutput(toolResult, toolCall);
            appendToolContext(toolContextBuilder, toolOutput);
            messages.add(AgentLlmMessage.tool(toolCall.id(), toolOutput));
        }
    }

    throw new IllegalStateException("Agent tool loop exceeded max turns: " + MAX_TOOL_TURNS);
}
```

Replace `ToolCallSnapshotMapper` with typed message support while preserving old JSON shape:

```java
@Component
public class ToolCallSnapshotMapper {

    public AgentLlmMessage buildAssistantToolCallMessage(AgentLlmTurnResponse response) {
        return AgentLlmMessage.assistant(
                response.assistantText(),
                response.toolCalls().stream()
                        .map(toolCall -> new AgentLlmToolCallPayload(
                                toolCall.id(),
                                "function",
                                toolCall.toolCode(),
                                toolCall.argumentsJson()
                        ))
                        .toList()
        );
    }

    public String toAssistantToolCallsJson(List<AgentLlmToolCall> toolCalls) {
        return AgentJsonCodec.toJson(buildToolCallPayloads(toolCalls));
    }

    public String toConversationMessagesJson(List<AgentLlmMessage> messages) {
        return AgentJsonCodec.toJson(messages.stream().map(this::toMessagePayload).toList());
    }

    public List<AgentLlmMessage> parseMessages(String raw) {
        JSONArray array = AgentJsonCodec.parseArray(raw);
        List<AgentLlmMessage> messages = new ArrayList<>();
        for (Object item : array) {
            Map<String, Object> payload = mapValue(item);
            String role = stringValue(payload.get("role"));
            if ("assistant".equalsIgnoreCase(role)) {
                messages.add(AgentLlmMessage.assistant(
                        stringValue(payload.get("content")),
                        parseAssistantToolCalls(payload.get("tool_calls"))
                ));
            } else if ("tool".equalsIgnoreCase(role)) {
                messages.add(AgentLlmMessage.tool(
                        stringValue(payload.get("tool_call_id")),
                        stringValue(payload.get("content"))
                ));
            } else if ("system".equalsIgnoreCase(role)) {
                messages.add(AgentLlmMessage.system(stringValue(payload.get("content"))));
            } else {
                messages.add(AgentLlmMessage.user(stringValue(payload.get("content"))));
            }
        }
        return messages;
    }

    public ToolCallRequest buildLoopResumeRequest(PendingToolInvocationSnapshot snapshot,
                                                  Map<String, Object> toolCallPayload,
                                                  List<AgentLlmMessage> messages,
                                                  String idempotencyKey) {
        Map<String, Object> functionPayload = mapValue(toolCallPayload.get("function"));
        String toolCallId = stringValue(toolCallPayload.get("id"));
        String toolCode = stringValue(functionPayload.get("name"));
        String toolArgsJson = stringValue(functionPayload.get("arguments"));
        return new ToolCallRequest(
                snapshot.projectId(),
                snapshot.taskId(),
                snapshot.conversationId(),
                toolCode,
                toolArgsJson,
                snapshot.operatorId(),
                snapshot.traceId(),
                snapshot.contextJson(),
                idempotencyKey,
                snapshot.loopRunId(),
                snapshot.llmTurnIndex(),
                toolCallId,
                snapshot.assistantToolCallsJson(),
                toConversationMessagesJson(messages),
                "RESUME_LOOP",
                snapshot.approvalSummaryJson()
        );
    }

    private List<AgentLlmToolCallPayload> parseAssistantToolCalls(Object raw) {
        List<AgentLlmToolCallPayload> calls = new ArrayList<>();
        for (Object item : toList(raw)) {
            Map<String, Object> payload = mapValue(item);
            Map<String, Object> function = mapValue(payload.get("function"));
            calls.add(new AgentLlmToolCallPayload(
                    stringValue(payload.get("id")),
                    stringValue(payload.get("type")),
                    stringValue(function.get("name")),
                    stringValue(function.get("arguments"))
            ));
        }
        return calls;
    }

    private Map<String, Object> toMessagePayload(AgentLlmMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", message.role().wireValue());
        payload.put("content", message.content());
        if (!message.toolCalls().isEmpty()) {
            payload.put("tool_calls", message.toolCalls().stream().map(toolCall -> {
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", toolCall.functionName());
                function.put("arguments", toolCall.argumentsJson());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", toolCall.id());
                item.put("type", toolCall.type());
                item.put("function", function);
                return item;
            }).toList());
        }
        if (message.toolCallId() != null) {
            payload.put("tool_call_id", message.toolCallId());
        }
        return payload;
    }

    private List<?> toList(Object value) {
        if (value instanceof JSONArray array) {
            return array;
        }
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    // keep existing mapValue/stringValue/buildToolCallPayloads helpers
}
```

Update `ToolCallResumeService` to use `List<AgentLlmMessage>` instead of `List<Map<String,Object>>` for parsed messages and append tool results with `AgentLlmMessage.tool(...)`.

### Step 4: Run test to verify it passes

Run:

```bash
mvn -q -Dtest=AgentToolLoopRunnerTest test
```

Expected:

```text
BUILD SUCCESS
```

### Step 5: Commit

```bash
git add penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallSnapshotMapper.java penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallResumeService.java penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunnerTest.java
git commit -m "refactor(agent): use typed llm messages in tool loop"
```

---

## Task 6: 在主工作流中集成滑动窗口

Use [test-driven-development] mode for this task.

**Files:**
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:72-90,260-278`
- Modify: `penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java:141-216`

### Step 1: Write the failing test

Replace the message capture section in `AgentGenerationWorkflowTest` with a typed-history assertion:

```java
@Mock
private ConversationWindowBuilder conversationWindowBuilder;
```

and in the test body:

```java
when(conversationWindowBuilder.build(9L, "核对设定后继续写作", 6)).thenReturn(List.of(
        AgentLlmMessage.user("上一轮提问"),
        AgentLlmMessage.assistant("上一轮回答", List.of())
));
when(agentToolLoopRunner.execute(eq(1L), eq(10L), eq(9L), eq(0L), eq("trace-real-chain"), any(), any()))
        .thenReturn(AgentToolLoopIterationResult.waitingApproval(77L, 1, ""));

agentGenerationWorkflow.run(1L, 10L, "trace-real-chain");

ArgumentCaptor<List<AgentLlmMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
verify(agentToolLoopRunner).execute(eq(1L), eq(10L), eq(9L), eq(0L), eq("trace-real-chain"), messagesCaptor.capture(), any());
assertThat(messagesCaptor.getValue()).hasSize(4);
assertThat(messagesCaptor.getValue().get(0).role()).isEqualTo(AgentLlmMessageRole.SYSTEM);
assertThat(messagesCaptor.getValue().get(1).content()).isEqualTo("上一轮提问");
assertThat(messagesCaptor.getValue().get(2).content()).isEqualTo("上一轮回答");
assertThat(messagesCaptor.getValue().get(3).content())
        .contains("<context type=\"style\">\n{\"styleId\":81,\"tone\":\"克制\"}\n</context>")
        .contains("<context type=\"story_bible\">\n角色年龄：17（canon）\n不得违背既有设定\n</context>")
        .contains("<user_request>\n核对设定后继续写作\n</user_request>");
```

### Step 2: Run test to verify it fails

Run:

```bash
mvn -q -Dtest=AgentGenerationWorkflowTest test
```

Expected:

```text
[ERROR] COMPILATION ERROR
[ERROR] cannot infer type arguments for ArgumentCaptor
[ERROR] method buildExecutionMessages(...) cannot be applied ...
```

### Step 3: Write minimal implementation

Inject the builder into `AgentGenerationWorkflow` constructor field list:

```java
private final ConversationWindowBuilder conversationWindowBuilder;
```

Replace `executeToolLoopPhase()` with:

```java
private AgentToolLoopIterationResult executeToolLoopPhase(Long projectId,
                                                          Long taskId,
                                                          AgentGenerationTask task,
                                                          AgentTaskContext taskContext,
                                                          TaskProfile taskProfile,
                                                          PromptPlan promptPlan,
                                                          ContextPackage contextPackage,
                                                          String promptSnapshot,
                                                          String traceId) {
    AgentLlmExecutionConfig executionConfig = agentModelRoutingService.resolveExecutionConfig(task.getUserId(), task.getModelConfigId(), traceId);
    List<AgentLlmMessage> conversationWindow = conversationWindowBuilder.build(
            task.getConversationId(),
            promptSnapshot,
            executionConfig.contextWindowTurns()
    );
    syncRuntimeSnapshot(projectId, task, taskContext, taskProfile, promptPlan, contextPackage, "executing", "phase:executing");
    taskRuntimeStatusPublisher.publishStatus(projectId,
            buildRuntimeStatusView(task, taskContext, "executing", "正在生成正文", true, "run_tool_loop", null));
    long llmStartAt = System.currentTimeMillis();
    AgentToolLoopIterationResult loopResult = agentToolLoopRunner.execute(
            projectId,
            taskId,
            task.getConversationId(),
            0L,
            traceId,
            agentPromptAssembler.buildExecutionMessages(
                    promptPlan,
                    contextPackage,
                    promptSnapshot,
                    conversationWindow
            ),
            executionConfig
    );
    if (!loopResult.waitingApproval()) {
        long llmCostMs = System.currentTimeMillis() - llmStartAt;
        log.info("agent.llm.generate.finished: projectId={}, taskId={}, traceId={}, costMs={}, outputLength={}",
                projectId,
                taskId,
                traceId,
                llmCostMs,
                safeLength(loopResult.finalAssistantText()));
    }
    return loopResult;
}
```

### Step 4: Run test to verify it passes

Run:

```bash
mvn -q -Dtest=AgentGenerationWorkflowTest test
```

Expected:

```text
BUILD SUCCESS
```

### Step 5: Commit

```bash
git add penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java
git commit -m "feat(agent): integrate conversation window into workflow"
```

---

## Task 7: 基础设施层 provider 适配，把领域对象转回 provider 格式

Use [test-driven-development] mode for this task.

**Files:**
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/AgentLlmMessagePayloadMapper.java`
- Create: `penmate-backend/src/test/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/AgentLlmMessagePayloadMapperTest.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ClaudeProviderChatClient.java:57-214`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:153-183`

### Step 1: Write the failing test

`penmate-backend/src/test/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/AgentLlmMessagePayloadMapperTest.java`

```java
package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLlmMessagePayloadMapperTest {

    private final AgentLlmMessagePayloadMapper mapper = new AgentLlmMessagePayloadMapper();

    @Test
    void should_convert_typed_messages_to_openai_style_payload() {
        List<Map<String, Object>> payload = mapper.toProviderMessages(List.of(
                AgentLlmMessage.system("你是执行代理"),
                AgentLlmMessage.user("上一轮提问"),
                AgentLlmMessage.assistant("", List.of(new AgentLlmToolCallPayload(
                        "call_1",
                        "function",
                        "context_enhancer",
                        "{\"prompt\":\"补充\"}"
                ))),
                AgentLlmMessage.tool("call_1", "{\"context\":\"补充背景\"}")
        ));

        assertThat(payload).hasSize(4);
        assertThat(payload.get(0)).containsEntry("role", "system").containsEntry("content", "你是执行代理");
        assertThat(payload.get(2)).containsEntry("role", "assistant");
        assertThat((List<?>) payload.get(2).get("tool_calls")).hasSize(1);
        assertThat(payload.get(3)).containsEntry("role", "tool")
                .containsEntry("tool_call_id", "call_1")
                .containsEntry("content", "{\"context\":\"补充背景\"}");
    }
}
```

### Step 2: Run test to verify it fails

Run:

```bash
mvn -q -Dtest=AgentLlmMessagePayloadMapperTest test
```

Expected:

```text
[ERROR] COMPILATION ERROR
[ERROR] cannot find symbol: class AgentLlmMessagePayloadMapper
```

### Step 3: Write minimal implementation

`penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/AgentLlmMessagePayloadMapper.java`

```java
package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentLlmMessagePayloadMapper {

    public List<Map<String, Object>> toProviderMessages(List<AgentLlmMessage> messages) {
        return (messages == null ? List.<AgentLlmMessage>of() : messages).stream()
                .map(this::toProviderMessage)
                .toList();
    }

    private Map<String, Object> toProviderMessage(AgentLlmMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", message.role().wireValue());
        payload.put("content", message.content());
        if (!message.toolCalls().isEmpty()) {
            payload.put("tool_calls", message.toolCalls().stream().map(this::toToolCallPayload).toList());
        }
        if (message.toolCallId() != null) {
            payload.put("tool_call_id", message.toolCallId());
        }
        return payload;
    }

    private Map<String, Object> toToolCallPayload(AgentLlmToolCallPayload toolCall) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", toolCall.functionName());
        function.put("arguments", toolCall.argumentsJson());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", toolCall.id());
        payload.put("type", toolCall.type());
        payload.put("function", function);
        return payload;
    }
}
```

Update `NativeOpenAiStyleHttpProviderChatClient.buildTurnRequestBody()`:

```java
@Autowired
private AgentLlmMessagePayloadMapper agentLlmMessagePayloadMapper;
```

```java
body.put("messages", request == null ? List.of() : agentLlmMessagePayloadMapper.toProviderMessages(request.messages()));
```

Update `ClaudeProviderChatClient` to consume typed messages directly:

```java
private List<ChatMessage> toChatMessages(List<AgentLlmMessage> rawMessages) {
    List<ChatMessage> messages = new ArrayList<>();
    Map<String, String> toolNamesById = new LinkedHashMap<>();
    for (AgentLlmMessage rawMessage : rawMessages) {
        if (rawMessage == null) {
            continue;
        }
        if (rawMessage.role() == AgentLlmMessageRole.SYSTEM) {
            messages.add(SystemMessage.from(rawMessage.content()));
            continue;
        }
        if (rawMessage.role() == AgentLlmMessageRole.USER) {
            messages.add(UserMessage.from(rawMessage.content()));
            continue;
        }
        if (rawMessage.role() == AgentLlmMessageRole.ASSISTANT) {
            List<ToolExecutionRequest> requests = rawMessage.toolCalls().stream()
                    .map(toolCall -> ToolExecutionRequest.builder()
                            .id(toolCall.id())
                            .name(toolCall.functionName())
                            .arguments(toolCall.argumentsJson())
                            .build())
                    .toList();
            requests.forEach(request -> toolNamesById.put(request.id(), request.name()));
            messages.add(AiMessage.from(rawMessage.content(), requests));
            continue;
        }
        String toolName = toolNamesById.get(rawMessage.toolCallId());
        if (toolName == null || toolName.isBlank()) {
            throw BusinessException.of("Claude tool result message is missing matching assistant tool call");
        }
        messages.add(ToolExecutionResultMessage.from(
                rawMessage.toolCallId(),
                toolName,
                rawMessage.content()
        ));
    }
    return messages;
}
```

### Step 4: Run test to verify it passes

Run:

```bash
mvn -q -Dtest=AgentLlmMessagePayloadMapperTest test
```

Then run a focused integration-safe regression pack:

```bash
mvn -q -Dtest=AgentLlmMessagePayloadMapperTest,AgentPromptAssemblerTest,AgentToolLoopRunnerTest,AgentGenerationWorkflowTest,ConversationWindowBuilderTest,ModelApplicationServiceTest,AgentModelRoutingServiceTest test
```

Expected:

```text
BUILD SUCCESS
```

### Step 5: Commit

```bash
git add penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/AgentLlmMessagePayloadMapper.java penmate-backend/src/test/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/AgentLlmMessagePayloadMapperTest.java penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ClaudeProviderChatClient.java penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java
git commit -m "refactor(llm): adapt providers to typed message model"
```

---

## Final Verification Checklist

完成全部任务后，执行一轮最小回归：

```bash
mvn -q -Dtest=AgentLlmMessageTest,ConversationWindowBuilderTest,AgentPromptAssemblerTest,AgentToolLoopRunnerTest,AgentGenerationWorkflowTest,ModelApplicationServiceTest,AgentModelRoutingServiceTest,AgentLlmMessagePayloadMapperTest test
```

Expected:

```text
BUILD SUCCESS
```

如果数据库迁移测试是单独命令，再补跑：

```bash
mvn -q test
```

Expected:

```text
BUILD SUCCESS
```

---

## Implementation Notes

1. **不要在应用层引入 LangChain4j 类型**：`ConversationWindowBuilder`、`AgentPromptAssembler`、`AgentToolLoopRunner`、`AgentGenerationWorkflow`、`ToolCallSnapshotMapper` 都只认识 `AgentLlmMessage`。
2. **不要破坏 tool loop 中间消息仅内存流转的约束**：只有 `ConversationWindowBuilder` 读取 `agent_messages` 历史；assistant tool_calls / tool result 继续仅用于单次 loop 与审批恢复快照。
3. **审批快照 JSON shape 必须兼容旧格式**：这样待审批恢复逻辑和外部审计不会因字段名变化失效。
4. **当前 prompt 去重是关键保护**：如果最后一条持久化 user 消息与 `promptSnapshot` 相同，不应同时出现在 history window 和当前 user_request block。
5. **默认窗口值建议为 6**：这样新建模型配置即启用历史感知；明确设置 `0` 时保留旧行为（禁用历史窗口）。

---

## Execution Handoff

Plan file: `docs/plans/2026-05-22-agent-conversation-window.md`

Recommended execution mode: [executing-plans]

Execution options:
1. Execute in this session using [executing-plans]
2. Execute later via `/execute-plan`
3. Manual implementation using this document as the checklist

Estimated effort: 2.5 ~ 4 小时
Total tasks: 7
