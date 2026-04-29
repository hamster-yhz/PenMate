# OpenAPI P0 接口补测矩阵（novel/model/rbac/auth）

## 1. 目标与范围

- 目标：为 OpenAPI P0 域建立“接口 -> 测试 -> 状态码/错误码”可追溯矩阵。
- 范围：`auth-controller`、`model-controller`、`rbac-query-controller`、`novel-controller`。
- 原则：优先覆盖高风险契约（参数缺失、校验失败、业务冲突）。

## 2. Controller 测试映射（Task 4）

| 域 | 接口 | 场景 | 预期 | 对应测试 |
|---|---|---|---|---|
| auth | `POST /api/v1/auth/refresh` | `refreshToken` 缺字段 | `400 + VALIDATION_ERROR` | `UT_API_AUTH_REFRESH_MISSING_REFRESH_TOKEN_BAD_REQUEST` |
| model | `POST /api/v1/model/keys` | 缺少 `operatorId` query 参数 | `400 + VALIDATION_ERROR` | `UT_MODEL_KEY_CREATE_MISSING_OPERATOR_ID_BAD_REQUEST` |
| rbac | `POST /api/v1/users/{userId}/roles` | 缺少 `roleId` query 参数 | `400 + VALIDATION_ERROR` | `UT_RBAC_ASSIGN_ROLE_MISSING_ROLE_ID_BAD_REQUEST` |
| novel | `DELETE /api/v1/novels/{projectId}` | 缺少 `operatorId` query 参数 | `400 + VALIDATION_ERROR` | `UT_NOVEL_PROJECT_DELETE_MISSING_OPERATOR_ID_BAD_REQUEST` |

## 3. 执行记录（TDD）

1. RED：先新增上述 4 条用例并运行定向测试，记录失败日志。
2. GREEN：仅做最小修复（参数缺失异常统一映射到 `VALIDATION_ERROR`）。
3. REFACTOR：保持行为不变，仅做必要命名/注释整理。

### 3.1 RED 关键日志（摘录）

命令：

```bash
mvn -q -Dtest=AuthControllerTest,ModelControllerTest,RbacQueryControllerTest,NovelControllerTest test
```

失败关键信息：

```text
Status expected:<400> but was:<500>
MissingServletRequestParameterException: Required request parameter 'operatorId' ... is not present
MissingServletRequestParameterException: Required request parameter 'roleId' ... is not present
```

### 3.2 GREEN 关键日志（摘录）

命令：

```bash
mvn -q -Dtest=AuthControllerTest,ModelControllerTest,RbacQueryControllerTest,NovelControllerTest test
```

通过关键信息：

```text
Exit code: 0
```

## 4. 覆盖说明

- 本轮聚焦 P0 的“请求参数完整性”契约。
- 后续可在同矩阵追加：类型错误、边界值、鉴权缺失、幂等行为等场景。
