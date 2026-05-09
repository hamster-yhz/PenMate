# Model Config Key Reference Refactor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use [executing-plans] mode to implement this plan task-by-task.

**Goal:** 将 [`model_user_configurations`](penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql:85) 从 embedded credentials 模式彻底重构回“引用 [`model_user_api_keys`](penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql:50) / [`model_official_api_keys`](penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql:68)”的模式，并同步打通数据库、后端、Agent、前端与测试链路，且不保留任何历史兼容代码。

**Architecture:** 目标结构下，[`model_user_configurations`](penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql:85) 只保存模型元数据与 key 引用信息，即 `key_source_type + user_key_id + official_key_id`，不再保存 [`encrypted_api_key`](penmate-backend/src/main/resources/db/migration/V14__refactor_model_configs_to_embedded_credentials.sql:2) 或 [`masked_api_key`](penmate-backend/src/main/resources/db/migration/V14__refactor_model_configs_to_embedded_credentials.sql:3)。后端应用服务负责校验“配置引用的 key 是否存在、是否归属正确、provider 是否一致”，仓储层负责联表解析引用 key 的展示字段与运行时密钥，Agent 在 [`AgentModelRoutingService.resolveExecutionConfig()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java:26) 中显式按 `userId + modelConfigId` 读取并解密最终 key。前端模型设置改成围绕 key 引用工作：先有 key，再创建模型配置，界面不再接受内嵌 API Key。

**Tech Stack:** Flyway SQL, Spring Boot, MyBatis, JUnit 5, Mockito, Vue 3, TypeScript, Vitest

---

### Task 1: 数据库迁移恢复 Key 引用结构

Use [test-driven-development] mode for this task.

**Files:**
- Create: [`penmate-backend/src/main/resources/db/migration/V15__restore_model_config_key_references.sql`](penmate-backend/src/main/resources/db/migration/V15__restore_model_config_key_references.sql)
- Create: [`penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/model/ModelConfigKeyReferenceMigrationTest.java`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/model/ModelConfigKeyReferenceMigrationTest.java)
- Reference: [`penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql`](penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql)
- Reference: [`penmate-backend/src/main/resources/db/migration/V14__refactor_model_configs_to_embedded_credentials.sql`](penmate-backend/src/main/resources/db/migration/V14__refactor_model_configs_to_embedded_credentials.sql)

**Step 1: Write the failing test**

创建 [`ModelConfigKeyReferenceMigrationTest.java`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/model/ModelConfigKeyReferenceMigrationTest.java)，验证最终 schema 已回退为引用式：

```java
package com.penmate.backend.infrastructure.persistence.model;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ModelConfigKeyReferenceMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void IT_DB_MODEL_CONFIG_SCHEMA_SHOULD_USE_REFERENCED_KEYS_AFTER_V15() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'model_user_configurations'
                ORDER BY ordinal_position
                """);

        assertThat(columns)
                .extracting(row -> String.valueOf(row.get("column_name")))
                .contains("key_source_type", "user_key_id", "official_key_id")
                .doesNotContain("encrypted_api_key", "masked_api_key");
    }
}
```

**Step 2: Run test to verify it fails**

Run: [`mvn -pl penmate-backend -Dtest=ModelConfigKeyReferenceMigrationTest test`](penmate-backend/pom.xml)

Expected: 测试失败，因为当前最新结构仍来自 [`V14__refactor_model_configs_to_embedded_credentials.sql`](penmate-backend/src/main/resources/db/migration/V14__refactor_model_configs_to_embedded_credentials.sql:1)，配置表还带有 embedded credentials 列。

**Step 3: Write minimal implementation**

创建 [`V15__restore_model_config_key_references.sql`](penmate-backend/src/main/resources/db/migration/V15__restore_model_config_key_references.sql)：

```sql
ALTER TABLE model_user_configurations
    ADD COLUMN key_source_type VARCHAR(20) NULL AFTER base_url,
    ADD COLUMN user_key_id BIGINT UNSIGNED NULL AFTER key_source_type,
    ADD COLUMN official_key_id BIGINT UNSIGNED NULL AFTER user_key_id;

UPDATE model_user_configurations muc
LEFT JOIN model_user_api_keys muk
       ON muk.user_id = muc.user_id
      AND muk.provider_id = muc.provider_id
      AND muk.is_default = 1
      AND muk.deleted_at IS NULL
LEFT JOIN model_official_api_keys mok
       ON mok.provider_id = muc.provider_id
      AND mok.is_default = 1
      AND mok.deleted_at IS NULL
SET muc.key_source_type = CASE
        WHEN muk.user_api_key_id IS NOT NULL THEN 'USER_KEY'
        WHEN mok.official_api_key_id IS NOT NULL THEN 'OFFICIAL_KEY'
        ELSE NULL
    END,
    muc.user_key_id = CASE
        WHEN muk.user_api_key_id IS NOT NULL THEN muk.user_api_key_id
        ELSE NULL
    END,
    muc.official_key_id = CASE
        WHEN muk.user_api_key_id IS NULL AND mok.official_api_key_id IS NOT NULL THEN mok.official_api_key_id
        ELSE NULL
    END
WHERE muc.deleted_at IS NULL;

ALTER TABLE model_user_configurations
    MODIFY COLUMN key_source_type VARCHAR(20) NOT NULL,
    DROP COLUMN encrypted_api_key,
    DROP COLUMN masked_api_key;

CREATE INDEX idx_model_user_config_user_key ON model_user_configurations(user_key_id);
CREATE INDEX idx_model_user_config_official_key ON model_user_configurations(official_key_id);
```

说明：此迁移不做兜底兼容；若存在既无默认用户 key 又无默认官方 key 的历史脏数据，迁移应直接失败，由实施者先清理数据后再执行。

**Step 4: Run test to verify it passes**

Run: [`mvn -pl penmate-backend -Dtest=ModelConfigKeyReferenceMigrationTest test`](penmate-backend/pom.xml)

Expected: `BUILD SUCCESS`。

**Step 5: Commit**

Run: [`git add penmate-backend/src/main/resources/db/migration/V15__restore_model_config_key_references.sql penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/model/ModelConfigKeyReferenceMigrationTest.java && git commit -m "test: restore model config key reference schema"`](.git)

---

### Task 2: 后端 DTO 与命令对象改为 Key 引用入参

Use [test-driven-development] mode for this task.

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/CreateUserModelConfigDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/CreateUserModelConfigDto.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/UpdateUserModelConfigDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/UpdateUserModelConfigDto.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/model/command/ModelCommands.java`](penmate-backend/src/main/java/com/penmate/backend/application/model/command/ModelCommands.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java)

**Step 1: Write the failing test**

先改 [`ModelControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java) 中模型配置创建/更新用例，把请求体从 `apiKey` 切换为 key 引用字段，例如：

```java
.content(objectMapper.writeValueAsString(Map.of(
        "providerId", 1,
        "modelName", "gpt-4o-mini",
        "keySourceType", "USER_KEY",
        "userKeyId", 8001,
        "status", "active"
)))
```

并更新列表/偏好返回样例，要求出现以下字段：

```java
Map.of(
        "modelConfigId", 9001L,
        "modelName", "gpt-4o-mini",
        "providerId", 1L,
        "keySourceType", "USER_KEY",
        "userKeyId", 8001L,
        "keyName", "OpenAI User Key",
        "maskedApiKey", "****1234"
)
```

**Step 2: Run test to verify it fails**

Run: [`mvn -pl penmate-backend -Dtest=ModelControllerTest test`](penmate-backend/pom.xml)

Expected: 失败，因为 [`CreateUserModelConfigDto`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/CreateUserModelConfigDto.java:11) 仍要求 `apiKey`，且 Controller 仍向命令对象传旧字段。

**Step 3: Write minimal implementation**

将 [`CreateUserModelConfigDto`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/CreateUserModelConfigDto.java:11) 改为：

```java
@Data
public class CreateUserModelConfigDto {

    @NotNull
    private Long providerId;

    @NotBlank
    private String modelName;

    private String baseUrl;

    @NotBlank
    private String keySourceType;

    private Long userKeyId;

    private Long officialKeyId;

    private String status;
}
```

将 [`UpdateUserModelConfigDto`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/UpdateUserModelConfigDto.java:8) 改为：

```java
@Data
public class UpdateUserModelConfigDto {

    private Long providerId;

    private String modelName;

    private String baseUrl;

    private String keySourceType;

    private Long userKeyId;

    private Long officialKeyId;

    private String status;
}
```

将 [`ModelCommands.java`](penmate-backend/src/main/java/com/penmate/backend/application/model/command/ModelCommands.java:38) 中相关 record 改为：

```java
public record CreateUserModelConfigCommand(Long providerId,
                                           String modelName,
                                           String baseUrl,
                                           String keySourceType,
                                           Long userKeyId,
                                           Long officialKeyId,
                                           String status,
                                           Long operatorId) {
}

public record UpdateUserModelConfigCommand(Long providerId,
                                           String modelName,
                                           String baseUrl,
                                           String keySourceType,
                                           Long userKeyId,
                                           Long officialKeyId,
                                           String status,
                                           Long operatorId) {
}
```

更新 [`ModelController.createUserModelConfig()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:167) 与 [`ModelController.updateUserModelConfig()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:188) 的命令构造，把 `dto.getApiKey()` 替换为 `dto.getKeySourceType()/getUserKeyId()/getOfficialKeyId()`。

**Step 4: Run test to verify it passes**

Run: [`mvn -pl penmate-backend -Dtest=ModelControllerTest test`](penmate-backend/pom.xml)

Expected: `BUILD SUCCESS`。

**Step 5: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/CreateUserModelConfigDto.java penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/UpdateUserModelConfigDto.java penmate-backend/src/main/java/com/penmate/backend/application/model/command/ModelCommands.java penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java && git commit -m "refactor: switch model config dto to key references"`](.git)

---

### Task 3: 仓储与 Mapper 改为联表读写引用 Key

Use [test-driven-development] mode for this task.

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/domain/model/repository/ModelRepository.java`](penmate-backend/src/main/java/com/penmate/backend/domain/model/repository/ModelRepository.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelRepositoryImpl.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelRepositoryImpl.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelMapper.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelMapper.java)
- Create: [`penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/model/ModelMapperKeyReferenceTest.java`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/model/ModelMapperKeyReferenceTest.java)

**Step 1: Write the failing test**

编写 [`ModelMapperKeyReferenceTest.java`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/model/ModelMapperKeyReferenceTest.java)，覆盖以下断言：
1. [`listUserModelConfigs()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelMapper.java:169) 返回 `keySourceType/userKeyId/officialKeyId/keyName/maskedApiKey`。
2. [`findUserModelConfig()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelMapper.java:183) 根据 `key_source_type` 联表带回 `encryptedApiKey`。
3. [`countUsableModelConfig()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelMapper.java:252) 判断依据是“被引用 key 可用”，而不是配置表自身是否有 embedded credentials。

示例断言：

```java
assertThat(config)
        .containsEntry("keySourceType", "USER_KEY")
        .containsEntry("userKeyId", 8001L)
        .containsEntry("keyName", "OpenAI User Key")
        .containsEntry("maskedApiKey", "****1234")
        .containsEntry("encryptedApiKey", "cipher-user-key");
```

**Step 2: Run test to verify it fails**

Run: [`mvn -pl penmate-backend -Dtest=ModelMapperKeyReferenceTest test`](penmate-backend/pom.xml)

Expected: 失败，因为当前 [`ModelMapper.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelMapper.java) 仍直接从 [`model_user_configurations`](penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql:85) 读取 `encrypted_api_key/masked_api_key`。

**Step 3: Write minimal implementation**

调整 [`ModelRepository.java`](penmate-backend/src/main/java/com/penmate/backend/domain/model/repository/ModelRepository.java:64) 方法签名：

```java
int insertUserModelConfig(Long modelConfigId,
                          Long userId,
                          Long providerId,
                          String modelName,
                          String baseUrl,
                          String keySourceType,
                          Long userKeyId,
                          Long officialKeyId,
                          String status);

int updateUserModelConfig(Long userId,
                          Long modelConfigId,
                          Long providerId,
                          String modelName,
                          String baseUrl,
                          String keySourceType,
                          Long userKeyId,
                          Long officialKeyId,
                          String status);
```

将 [`ModelMapper.listUserModelConfigs()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelMapper.java:169) 改为：

```java
@Select("""
        SELECT muc.model_config_id AS modelConfigId,
               muc.user_id AS userId,
               muc.provider_id AS providerId,
               muc.model_name AS modelName,
               muc.base_url AS baseUrl,
               muc.key_source_type AS keySourceType,
               muc.user_key_id AS userKeyId,
               muc.official_key_id AS officialKeyId,
               CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.key_name ELSE mok.key_name END AS keyName,
               CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.masked_api_key ELSE mok.masked_api_key END AS maskedApiKey,
               muc.status AS status
        FROM model_user_configurations muc
        LEFT JOIN model_user_api_keys muk
               ON muc.user_key_id = muk.user_api_key_id
              AND muk.deleted_at IS NULL
        LEFT JOIN model_official_api_keys mok
               ON muc.official_key_id = mok.official_api_key_id
              AND mok.deleted_at IS NULL
        WHERE muc.user_id = #{userId}
          AND muc.deleted_at IS NULL
        ORDER BY muc.id DESC
        """)
```

将 [`ModelMapper.findUserModelConfig()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelMapper.java:183) 改为：

```java
@Select("""
        SELECT muc.model_config_id AS modelConfigId,
               muc.user_id AS userId,
               muc.provider_id AS providerId,
               muc.model_name AS modelName,
               muc.base_url AS baseUrl,
               muc.key_source_type AS keySourceType,
               muc.user_key_id AS userKeyId,
               muc.official_key_id AS officialKeyId,
               CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.key_name ELSE mok.key_name END AS keyName,
               CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.encrypted_api_key ELSE mok.encrypted_api_key END AS encryptedApiKey,
               CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.masked_api_key ELSE mok.masked_api_key END AS maskedApiKey,
               CASE WHEN muc.key_source_type = 'USER_KEY' THEN muk.status ELSE mok.status END AS keyStatus,
               muc.status AS status
        FROM model_user_configurations muc
        LEFT JOIN model_user_api_keys muk
               ON muc.user_key_id = muk.user_api_key_id
              AND muk.deleted_at IS NULL
        LEFT JOIN model_official_api_keys mok
               ON muc.official_key_id = mok.official_api_key_id
              AND mok.deleted_at IS NULL
        WHERE muc.user_id = #{userId}
          AND muc.model_config_id = #{modelConfigId}
          AND muc.deleted_at IS NULL
        LIMIT 1
        """)
```

将 `insert/update/countUsableModelConfig` 改为围绕引用字段：

```java
@Insert("""
        INSERT INTO model_user_configurations(model_config_id, user_id, provider_id, model_name, base_url, key_source_type, user_key_id, official_key_id, status)
        VALUES (#{modelConfigId}, #{userId}, #{providerId}, #{modelName}, #{baseUrl}, #{keySourceType}, #{userKeyId}, #{officialKeyId}, #{status})
        """)
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
            status = COALESCE(#{status}, status),
            updated_at = CURRENT_TIMESTAMP(3)
        WHERE user_id = #{userId}
          AND model_config_id = #{modelConfigId}
          AND deleted_at IS NULL
        """)
```

```java
@Select("""
        SELECT COUNT(1)
        FROM model_user_configurations muc
        LEFT JOIN model_user_api_keys muk
               ON muc.user_key_id = muk.user_api_key_id
              AND muk.deleted_at IS NULL
        LEFT JOIN model_official_api_keys mok
               ON muc.official_key_id = mok.official_api_key_id
              AND mok.deleted_at IS NULL
        WHERE muc.user_id = #{userId}
          AND muc.model_config_id = #{modelConfigId}
          AND muc.deleted_at IS NULL
          AND muc.status = 'active'
          AND (
              (muc.key_source_type = 'USER_KEY' AND muk.status = 'active' AND muk.encrypted_api_key <> '')
              OR (muc.key_source_type = 'OFFICIAL_KEY' AND mok.status = 'active' AND mok.encrypted_api_key <> '')
          )
        """)
```

同步修改 [`ModelRepositoryImpl.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelRepositoryImpl.java) 以适配新参数。

**Step 4: Run test to verify it passes**

Run: [`mvn -pl penmate-backend -Dtest=ModelMapperKeyReferenceTest test`](penmate-backend/pom.xml)

Expected: `BUILD SUCCESS`。

**Step 5: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/domain/model/repository/ModelRepository.java penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelRepositoryImpl.java penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelMapper.java penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/model/ModelMapperKeyReferenceTest.java && git commit -m "refactor: load model configs from referenced keys"`](.git)

---

### Task 4: 应用服务改为校验并保存 Key 引用

Use [test-driven-development] mode for this task.

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java)
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/application/model/ModelApplicationServiceTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/model/ModelApplicationServiceTest.java)

**Step 1: Write the failing test**

改造 [`ModelApplicationServiceTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/model/ModelApplicationServiceTest.java) 中以下场景：
1. 创建 `USER_KEY` 模型配置时，必须调用 [`findUserKey()`](penmate-backend/src/main/java/com/penmate/backend/domain/model/repository/ModelRepository.java:58) 校验归属和 provider。
2. 创建 `OFFICIAL_KEY` 模型配置时，必须调用 [`findOfficialKey()`](penmate-backend/src/main/java/com/penmate/backend/domain/model/repository/ModelRepository.java:60) 校验 provider。
3. 更新模型配置切换引用源时，必须重新校验。
4. `keySourceType` 非法、key 不存在、provider 不匹配时抛 [`BusinessException`](penmate-backend/src/main/java/com/penmate/backend/application/common/exception/BusinessException.java)。

示例：

```java
when(modelRepository.findUserKey(8001L)).thenReturn(userKey(8001L, 1001L, 1L));
when(modelRepository.insertUserModelConfig(9001L, 1001L, 1L, "gpt-4o-mini", null, "USER_KEY", 8001L, null, "active"))
        .thenReturn(1);
```

**Step 2: Run test to verify it fails**

Run: [`mvn -pl penmate-backend -Dtest=ModelApplicationServiceTest test`](penmate-backend/pom.xml)

Expected: 失败，因为 [`ModelApplicationService.createUserModelConfig()`](penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java:79) 仍依赖 `apiKey` 并自行加密。

**Step 3: Write minimal implementation**

删除 [`createUserModelConfig()`](penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java:79) / [`updateUserModelConfig()`](penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java:101) 中所有 `encryptApiKey()/mask()/mergedEncryptedApiKey/mergedMaskedApiKey` 相关逻辑，改为：

```java
public void createUserModelConfig(Long userId, CreateUserModelConfigCommand command, String traceId) {
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(command, "command must not be null");

    String keySourceType = normalizeKeySourceType(command.keySourceType());
    validateConfigCommand(command.providerId(), command.modelName(), keySourceType, command.userKeyId(), command.officialKeyId(), userId);

    Long modelConfigId = businessIdGenerator.nextId();
    int affected = modelRepository.insertUserModelConfig(
            modelConfigId,
            userId,
            command.providerId(),
            normalize(command.modelName()),
            normalizeNullable(command.baseUrl()),
            keySourceType,
            "USER_KEY".equals(keySourceType) ? command.userKeyId() : null,
            "OFFICIAL_KEY".equals(keySourceType) ? command.officialKeyId() : null,
            normalizeStatus(command.status())
    );
    if (affected != 1) {
        throw BusinessException.of("Failed to create user model config");
    }
}
```

新增校验方法：

```java
private void validateConfigCommand(Long providerId,
                                   String modelName,
                                   String keySourceType,
                                   Long userKeyId,
                                   Long officialKeyId,
                                   Long userId) {
    if (providerId == null) {
        throw BusinessException.of("Provider is required");
    }
    if (modelName == null || modelName.isBlank()) {
        throw BusinessException.of("Model name is required");
    }
    if ("USER_KEY".equals(keySourceType)) {
        ModelUserApiKey userKey = modelRepository.findUserKey(userKeyId);
        if (userKey == null || !Objects.equals(userKey.getUserId(), userId)) {
            throw BusinessException.of("User model key not found");
        }
        if (!Objects.equals(userKey.getProviderId(), providerId)) {
            throw BusinessException.of("User model key provider mismatch");
        }
        return;
    }
    if ("OFFICIAL_KEY".equals(keySourceType)) {
        ModelOfficialApiKey officialKey = modelRepository.findOfficialKey(officialKeyId);
        if (officialKey == null) {
            throw BusinessException.of("Official model key not found");
        }
        if (!Objects.equals(officialKey.getProviderId(), providerId)) {
            throw BusinessException.of("Official model key provider mismatch");
        }
        return;
    }
    throw BusinessException.of("Key source type is invalid");
}
```

更新 [`getUserModelPreferencesDetail()`](penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java:56) / [`listUserModelConfigs()`](penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java:73) 的测试样例，不再围绕旧的 embedded 字段。

**Step 4: Run test to verify it passes**

Run: [`mvn -pl penmate-backend -Dtest=ModelApplicationServiceTest test`](penmate-backend/pom.xml)

Expected: `BUILD SUCCESS`。

**Step 5: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java penmate-backend/src/test/java/com/penmate/backend/application/model/ModelApplicationServiceTest.java && git commit -m "refactor: validate model configs against referenced keys"`](.git)

---

### Task 5: Agent 路由显式按 userId 解析执行配置

Use [test-driven-development] mode for this task.

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java)
- Create: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/AgentModelRoutingServiceTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/AgentModelRoutingServiceTest.java)
- Search impact: 所有调用 [`resolveExecutionConfig()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java:26) 的 Agent 入口

**Step 1: Write the failing test**

创建 [`AgentModelRoutingServiceTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/AgentModelRoutingServiceTest.java)，至少覆盖：
1. 方法签名从 `(projectId, modelConfigId, traceId)` 改为 `(userId, modelConfigId, traceId)`。
2. 对 `USER_KEY` 配置可正确解密 `encryptedApiKey`。
3. 对 `OFFICIAL_KEY` 配置可正确解密 `encryptedApiKey`。
4. `keyStatus != active` 时抛异常。

测试样例：

```java
when(modelRepository.findUserModelConfig(1001L, 9001L)).thenReturn(Map.of(
        "modelConfigId", 9001L,
        "providerId", 1L,
        "modelName", "gpt-4o-mini",
        "baseUrl", "",
        "keySourceType", "USER_KEY",
        "encryptedApiKey", "cipher-user-key",
        "keyStatus", "active"
));
when(secretCryptoService.decrypt("cipher-user-key")).thenReturn("sk-live-user-key");
```

**Step 2: Run test to verify it fails**

Run: [`mvn -pl penmate-backend -Dtest=AgentModelRoutingServiceTest test`](penmate-backend/pom.xml)

Expected: 编译或运行失败，因为 [`AgentModelRoutingService.resolveExecutionConfig()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java:26) 还在用 `projectId` 做查询语义。

**Step 3: Write minimal implementation**

将 [`resolveExecutionConfig()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java:26) 改为：

```java
public AgentLlmExecutionConfig resolveExecutionConfig(Long userId, Long modelConfigId, String traceId) {
    Map<String, Object> config = modelRepository.findUserModelConfig(userId, modelConfigId);
    if (config == null) {
        throw BusinessException.of("Model config not found");
    }

    String keyStatus = stringValue(config.get("keyStatus"));
    if (keyStatus == null || !"active".equalsIgnoreCase(keyStatus.trim())) {
        throw BusinessException.of("Model config key is unavailable");
    }

    String encryptedApiKey = stringValue(config.get("encryptedApiKey"));
    if (encryptedApiKey == null || encryptedApiKey.isBlank()) {
        throw BusinessException.of("Model config key is required");
    }

    String plainApiKey = secretCryptoService.decrypt(encryptedApiKey);
    if (plainApiKey == null || plainApiKey.isBlank()) {
        throw BusinessException.of("Model config key decrypt failed");
    }

    // 其余 provider / baseUrl / modelName 逻辑保留，但日志字段全部改成 userId
}
```

然后全文检索所有 `resolveExecutionConfig(` 调用点，把传参改为真实 `userId`，不允许继续用 `projectId` 代替。

**Step 4: Run test to verify it passes**

Run: [`mvn -pl penmate-backend -Dtest=AgentModelRoutingServiceTest test`](penmate-backend/pom.xml)

Expected: `BUILD SUCCESS`。

**Step 5: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java penmate-backend/src/test/java/com/penmate/backend/application/agent/AgentModelRoutingServiceTest.java && git commit -m "refactor: resolve agent model config by user id"`](.git)

---

### Task 6: 前端 API 切换到 Key 引用字段

Use [test-driven-development] mode for this task.

**Files:**
- Modify: [`penmate-frontend/src/api/modules/model.api.ts`](penmate-frontend/src/api/modules/model.api.ts)
- Modify: [`penmate-frontend/src/api/modules/model.api.spec.ts`](penmate-frontend/src/api/modules/model.api.spec.ts)

**Step 1: Write the failing test**

更新 [`model.api.spec.ts`](penmate-frontend/src/api/modules/model.api.spec.ts:40) 创建/更新用例，改为：

```ts
await modelApi.createUserModelConfig(99, 7, {
  providerId: 1,
  modelName: '  gpt-4.1  ',
  baseUrl: '  https://api.example.com  ',
  keySourceType: '  USER_KEY  ',
  userKeyId: 8001,
  officialKeyId: null,
  status: '  active  ',
})

expect(postMock).toHaveBeenCalledWith('/v1/model/configs?userId=99&operatorId=7', {
  providerId: 1,
  modelName: 'gpt-4.1',
  baseUrl: 'https://api.example.com',
  keySourceType: 'USER_KEY',
  userKeyId: 8001,
  officialKeyId: null,
  status: 'active',
})
```

同时新增断言：`apiKey` 不得再被发送。

**Step 2: Run test to verify it fails**

Run: [`npm --prefix penmate-frontend run test -- src/api/modules/model.api.spec.ts`](penmate-frontend/package.json)

Expected: 失败，因为 [`normalizeUserModelConfigPayload()`](penmate-frontend/src/api/modules/model.api.ts:6) 仍在处理 `apiKey`。

**Step 3: Write minimal implementation**

将 [`normalizeUserModelConfigPayload()`](penmate-frontend/src/api/modules/model.api.ts:6) 改为：

```ts
const normalizeUserModelConfigPayload = (payload: AnyRecord) => {
  const next: AnyRecord = { ...payload }
  if (typeof next.modelName === 'string') {
    const trimmed = next.modelName.trim()
    next.modelName = trimmed || undefined
  }
  if (typeof next.baseUrl === 'string') {
    const trimmed = next.baseUrl.trim()
    next.baseUrl = trimmed || undefined
  }
  if (typeof next.keySourceType === 'string') {
    const trimmed = next.keySourceType.trim()
    next.keySourceType = trimmed || undefined
  }
  if (typeof next.providerId === 'number' && next.providerId <= 0) {
    next.providerId = undefined
  }
  if (typeof next.userKeyId === 'number' && next.userKeyId <= 0) {
    next.userKeyId = undefined
  }
  if (typeof next.officialKeyId === 'number' && next.officialKeyId <= 0) {
    next.officialKeyId = undefined
  }
  if (typeof next.status === 'string') {
    const trimmed = next.status.trim()
    next.status = trimmed || undefined
  }
  delete next.apiKey
  return next
}
```

**Step 4: Run test to verify it passes**

Run: [`npm --prefix penmate-frontend run test -- src/api/modules/model.api.spec.ts`](penmate-frontend/package.json)

Expected: Vitest 通过。

**Step 5: Commit**

Run: [`git add penmate-frontend/src/api/modules/model.api.ts penmate-frontend/src/api/modules/model.api.spec.ts && git commit -m "refactor: send model config key references from frontend api"`](.git)

---

### Task 7: 前端模型设置界面围绕 Key 引用重构

Use [test-driven-development] mode for this task.

**Files:**
- Modify: [`penmate-frontend/src/components/workbench/ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue)
- Modify: [`penmate-frontend/src/components/workbench/ModelSettings.spec.ts`](penmate-frontend/src/components/workbench/ModelSettings.spec.ts)
- Optional modify: [`penmate-frontend/src/views/Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue)

**Step 1: Write the failing test**

更新 [`ModelSettings.spec.ts`](penmate-frontend/src/components/workbench/ModelSettings.spec.ts) 为新交互：
1. 加载时除了 [`listProviders()`](penmate-frontend/src/api/modules/model.api.ts:31) / [`listUserModelConfigs()`](penmate-frontend/src/api/modules/model.api.ts:58) / [`getUserModelPreferences()`](penmate-frontend/src/api/modules/model.api.ts:76)，还要调用 [`listKeys()`](penmate-frontend/src/api/modules/model.api.ts:34) 与 [`listOfficialKeys()`](penmate-frontend/src/api/modules/model.api.ts:46)。
2. 创建模型配置时不再填写 API Key，而是先选择 `keySourceType`，再选择具体 key。
3. 配置卡片上展示 `keyName + maskedApiKey`，而不是“直接内嵌 key 已配置”。

示例断言：

```ts
expect(mocks.createUserModelConfig).toHaveBeenCalledWith(
  101,
  101,
  expect.objectContaining({
    providerId: 1,
    modelName: 'claude-3-7-sonnet',
    keySourceType: 'USER_KEY',
    userKeyId: 7001,
    officialKeyId: null,
  })
)
```

**Step 2: Run test to verify it fails**

Run: [`npm --prefix penmate-frontend run test -- src/components/workbench/ModelSettings.spec.ts`](penmate-frontend/package.json)

Expected: 失败，因为 [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue:47) 仍要求用户在模型配置表单中直接填写 `apiKey`。

**Step 3: Write minimal implementation**

重构 [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue) 的数据结构：

```ts
type UserModelConfig = {
  modelConfigId: number
  providerId: number
  providerName: string
  modelName: string
  baseUrl: string
  keySourceType: 'USER_KEY' | 'OFFICIAL_KEY'
  userKeyId: number | null
  officialKeyId: number | null
  keyName: string
  maskedApiKey: string
  status: string
}

type UserKeyOption = {
  keyId: number
  providerId: number
  keyName: string
  maskedApiKey: string
}

type OfficialKeyOption = {
  keyId: number
  providerId: number
  keyName: string
  maskedApiKey: string
}
```

将表单改为：
- `providerId`
- `modelName`
- `baseUrl`
- `keySourceType`
- `selectedKeyId`

模板中删掉 `API Key` 输入框，新增两个级联选择：

```vue
<div class="form-row">
  <label>密钥来源</label>
  <select v-model="form.keySourceType" class="f-input">
    <option value="USER_KEY">我的 Key</option>
    <option value="OFFICIAL_KEY">官方 Key</option>
  </select>
</div>

<div class="form-row form-row-full">
  <label>选择 Key</label>
  <select v-model="form.selectedKeyId" class="f-input">
    <option value="">请选择</option>
    <option v-for="item in availableKeys" :key="item.keyId" :value="String(item.keyId)">
      {{ item.keyName }} · {{ item.maskedApiKey }}
    </option>
  </select>
</div>
```

保存时组装 payload：

```ts
const payload: AnyRecord = {
  providerId: Number(form.providerId),
  modelName: form.modelName,
  baseUrl: form.baseUrl,
  keySourceType: form.keySourceType,
  userKeyId: form.keySourceType === 'USER_KEY' ? Number(form.selectedKeyId) : null,
  officialKeyId: form.keySourceType === 'OFFICIAL_KEY' ? Number(form.selectedKeyId) : null,
  status: 'active',
}
```

卡片描述改为：

```vue
<div class="config-card-meta">
  {{ item.keyName }} · {{ item.maskedApiKey || '未显示掩码' }}
  <span v-if="item.baseUrl"> · 自定义 Base URL</span>
</div>
```

**Step 4: Run test to verify it passes**

Run: [`npm --prefix penmate-frontend run test -- src/components/workbench/ModelSettings.spec.ts`](penmate-frontend/package.json)

Expected: Vitest 通过。

**Step 5: Commit**

Run: [`git add penmate-frontend/src/components/workbench/ModelSettings.vue penmate-frontend/src/components/workbench/ModelSettings.spec.ts && git commit -m "refactor: configure workbench models by key reference"`](.git)

---

### Task 8: 端到端回归测试与清理旧设计

Use [test-driven-development] mode for this task.

**Files:**
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java)
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/application/model/ModelApplicationServiceTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/model/ModelApplicationServiceTest.java)
- Modify: [`penmate-frontend/src/api/modules/model.api.spec.ts`](penmate-frontend/src/api/modules/model.api.spec.ts)
- Modify: [`penmate-frontend/src/components/workbench/ModelSettings.spec.ts`](penmate-frontend/src/components/workbench/ModelSettings.spec.ts)
- Search/Remove: 所有 embedded credentials 文案、字段名、注释、旧断言

**Step 1: Write the failing cleanup checks**

使用全文搜索查找以下旧设计残留，并将其视为必须清理项：
- `encrypted_api_key` in model config context
- `masked_api_key` in model config context
- `dto.getApiKey()` for user model config
- `form.apiKey`
- `Model config key is required` tied to config table columns
- 所有“每条模型配置都直接填写 apiKey”的文案

**Step 2: Run checks to verify failure**

Run: [`findstr /spin "apiKey keySourceType encrypted_api_key masked_api_key" penmate-backend\src\main\java\com\penmate\backend\application\model\* penmate-backend\src\main\java\com\penmate\backend\interfaces\api\model\* penmate-frontend\src\components\workbench\ModelSettings.vue penmate-frontend\src\api\modules\model.api.ts`](.)

Expected: 仍能看到旧字段残留，尤其是 [`ModelApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java) 与 [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue) 中的旧逻辑。

**Step 3: Write minimal cleanup implementation**

清理要求：
1. 删除所有 user-model-config 相关 `apiKey` 入参、注释、断言、映射字段。
2. 删除对 [`V14__refactor_model_configs_to_embedded_credentials.sql`](penmate-backend/src/main/resources/db/migration/V14__refactor_model_configs_to_embedded_credentials.sql) 结构的任何运行时代码依赖。
3. 更新界面文案，全部改成“选择 key 引用”，不能再说“模型配置内填写 key”。
4. 若有 Swagger/OpenAPI 示例，全部切换为新字段。

**Step 4: Run full verification to verify it passes**

Run backend targeted suite: [`mvn -pl penmate-backend -Dtest=ModelConfigKeyReferenceMigrationTest,ModelMapperKeyReferenceTest,ModelApplicationServiceTest,ModelControllerTest,AgentModelRoutingServiceTest test`](penmate-backend/pom.xml)

Expected: `BUILD SUCCESS`。

Run frontend targeted suite: [`npm --prefix penmate-frontend run test -- src/api/modules/model.api.spec.ts src/components/workbench/ModelSettings.spec.ts src/views/Workbench/index.chat-binding.spec.ts src/views/Workbench/index.refactor.spec.ts`](penmate-frontend/package.json)

Expected: Vitest 全部通过。

**Step 5: Commit**

Run: [`git add penmate-backend penmate-frontend docs/plans/2026-05-05-model-config-key-reference-refactor.md && git commit -m "refactor: remove embedded model credentials design"`](.git)

---

## Cross-Cutting Notes

- [`V14__refactor_model_configs_to_embedded_credentials.sql`](penmate-backend/src/main/resources/db/migration/V14__refactor_model_configs_to_embedded_credentials.sql) 是历史错误设计来源；本次改造不回滚历史迁移文件内容，而是通过 [`V15__restore_model_config_key_references.sql`](penmate-backend/src/main/resources/db/migration/V15__restore_model_config_key_references.sql) 明确覆盖到新终态。
- [`ModelApplicationService.listUserModelConfigs()`](penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java:73) 和 [`getUserModelPreferencesDetail()`](penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java:56) 返回的 `candidateConfigs` 必须统一为新结构，避免前端拼装兼容判断。
- [`AgentModelRoutingService.resolveExecutionConfig()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java:26) 的调用链如果拿不到 `userId`，必须继续向上追并补齐；不能继续依赖 `projectId` 推断。
- 对于历史数据迁移失败场景，不做代码兜底，只允许通过预清洗数据解决。

## Definition of Done

- [`model_user_configurations`](penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql:85) 最终只保留 key 引用字段，不再存 embedded credentials。
- 后端创建/更新/列表/偏好/Agent 运行全部基于 key 引用。
- 前端模型设置不再收集用户输入的模型配置 API Key。
- 测试覆盖数据库迁移、Mapper、应用服务、Controller、Agent 路由、前端 API、前端界面。
- 仓库中无任何 user-model-config embedded credentials 兼容代码残留。

## Estimated Effort

- Task 1: 15-25 分钟
- Task 2: 10-15 分钟
- Task 3: 20-30 分钟
- Task 4: 20-30 分钟
- Task 5: 15-25 分钟
- Task 6: 10-15 分钟
- Task 7: 20-35 分钟
- Task 8: 15-25 分钟
- Total: 2.0-3.3 小时

## Execution Options

1. Execute in this session using [executing-plans] mode.
2. Execute later via `/execute-plan` using [`docs/plans/2026-05-05-model-config-key-reference-refactor.md`](docs/plans/2026-05-05-model-config-key-reference-refactor.md).
3. Manual implementation using this document as the guide.
