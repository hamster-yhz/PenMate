# Model Settings Contract And Key Entry Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use [executing-plans] mode to implement this plan task-by-task.

**Goal:** 统一模型配置接口的标识字段命名，并明确模型配置创建时“引用已有 Key”与“直接录入密钥”两种产品方案的推荐路径与落地范围。

**Architecture:** 当前后端数据库、仓储、应用服务已经以业务 ID `modelConfigId` 和 Key 引用关系 `keySourceType + userKeyId + officialKeyId` 为核心，但 [`ModelController.normalizeBusinessIdView()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:257) 在接口层将 `modelConfigId` 改写成 `id`，前端又在 [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue:361) 与 [`useProfileSettings.ts`](penmate-frontend/src/composables/profile/useProfileSettings.ts:168) 做了兼容回退，导致契约语义漂移。本次改造建议坚持“对外业务对象使用语义化业务 ID、模型配置继续采用引用式 Key 设计”，必要时仅在后端新增一个显式的快捷创建接口，而不是把现有引用式接口重新污染为既支持引用又支持明文密钥的混合契约。

**Tech Stack:** Spring Boot, MyBatis, JUnit 5, MockMvc, Vue 3, TypeScript, Vitest

---

## 一、现状结论与推荐方向

### 1.1 标识字段命名：推荐恢复 `modelConfigId`，不建议全链路统一成 `id`

**推荐结论：** 恢复 [`/model/configs`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:163) 列表接口返回 `modelConfigId`，并移除 `id` 兼容；路径参数仍继续使用 `modelConfigId`。不要把模型配置对象对外统一成 `id`。

**原因：**

1. **当前后端领域与仓储天然以 `modelConfigId` 为业务主键。** 见 [`ModelMapper.listUserModelConfigs()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelMapper.java:169)、[`ModelRepository.findUserModelConfig()`](penmate-backend/src/main/java/com/penmate/backend/domain/model/repository/ModelRepository.java:66)、[`ModelApplicationService.updateUserModelConfig()`](penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java:124)。
2. **用户指出的是接口契约问题，而不是内部实现问题。** 当前问题恰恰来自控制器层把稳定语义字段在出参时改名，见 [`normalizeBusinessIdView()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:257)。
3. **同一模块内已经存在多个业务 ID 字段。** 如 `mainAgentModelConfigId`、`dirtyWorkAgentModelConfigId`、`userKeyId`、`officialKeyId`，若仅把模型配置列表项改成 `id`，会形成“同一页面同时混用 `id` 和 `xxxId`”的不一致心智模型，见 [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue:54)。
4. **项目已有业务 ID 治理方向倾向语义化命名。** 见 [`2026-05-06-business-id-refactor-plan.md`](docs/plans/2026-05-06-business-id-refactor-plan.md:5)。把模型配置再收敛成 `id` 会与该方向冲突。

**不推荐“全链路统一成 `id`”的原因：**

1. 需要把前端类型、偏好对象、接口文档、OpenAPI 字段说明、测试样例一起改成通用 `id`，会削弱业务可读性。
2. `candidateConfigs`、角色偏好、模型卡片选择器都在表达“模型配置 ID”，继续使用 `modelConfigId` 更直观。
3. 若未来页面同时展示“模型配置”和“Key”对象，统一叫 `id` 反而更容易在表单提交和组件 state 中混淆。

### 1.2 Key 录入交互：推荐保持引用式设计，并优化交互

**推荐结论：** 保持“模型配置只引用已有 Key”的架构，不修改现有 [`POST /model/configs`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:173) / [`PUT /model/configs/{modelConfigId}`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:196) 的契约含义；通过前端增加引导、空状态跳转、同屏快捷创建 Key、按 provider 过滤选项和缺 Key 提示，解决用户对下拉框体验的质疑。

**原因：**

1. **当前数据库与应用层已经围绕“Key 是独立资源，模型配置只做引用”稳定落地。** 见 [`V15__restore_model_config_key_references.sql`](penmate-backend/src/main/resources/db/migration/V15__restore_model_config_key_references.sql)、[`ModelMapper.insertUserModelConfig()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/model/ModelMapper.java:223)、[`ModelApplicationService.resolveKeyBindingForCreate()`](penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java:104)。
2. **安全边界更清晰。** 明文密钥只在 Key 资源创建/更新时出现一次，模型配置编辑不会重复接触密钥明文。
3. **复用能力更强。** 一个用户 Key 可以挂多条模型配置；更换默认 Key、禁用 Key、审计 Key 使用状态时只需要维护一个资源。
4. **当前前端文案已经说明了引用式设计，但缺少“为什么这样设计”和“如何快速补 Key”的交互。** 见 [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue:19)、[`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue:72)、[`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue:122)。

---

## 二、方案对比

### 2.1 方案 A：保持引用式设计并优化交互（推荐）

**核心思路：**
- 模型配置接口继续只接受 `keySourceType + userKeyId + officialKeyId`。
- 在模型设置 UI 中补足“先建 Key，再选 Key”的引导与快捷操作。
- 如需减少步骤，可增加“在模型配置弹窗内快捷创建用户 Key”的子流程，但最终仍调用 [`createKey()`](penmate-frontend/src/api/modules/model.api.ts:58) 再回填到 `selectedKeyId`。

**用户体验改进建议：**
1. 在 [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue:107) 的“选择 Key”旁增加“新建 Key”按钮。
2. 当 `availableKeyOptions` 为空时，不只提示“请选择 Key”，而是显示“当前供应商下暂无可用用户密钥/官方密钥，请先新建”。逻辑位置见 [`availableKeyOptions`](penmate-frontend/src/components/workbench/ModelSettings.vue:208)。
3. 将“密钥来源”和“选择 Key”联动文案改得更明确，例如：
   - 用户密钥：仅显示当前 `providerId` 下用户自己的 Key。
   - 官方密钥：仅显示当前 `providerId` 下官方 Key。
4. 若用户从“新增模型”进入且尚未有任何用户 Key，可直接弹出 Key 创建子表单，而不是让下拉框为空。现有空状态位置见 [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue:24)。
5. 若业务强烈要求“一步完成”，新增**组合式前端流程**，不是修改旧接口：先提交 [`createKey()`](penmate-frontend/src/api/modules/model.api.ts:58)，成功后立即提交 [`createUserModelConfig()`](penmate-frontend/src/api/modules/model.api.ts:82)。

**改造范围：**

**后端：**
- 主要是接口契约收敛，不需要改数据库结构。
- 视需求可选新增“快捷创建并绑定”应用服务，但推荐先不做后端新接口。
- 必改文件：
  - [`ModelController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java)
  - [`ModelControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java)
  - 如 OpenAPI 有字段说明，则同步更新 [`OpenApiConfig.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/config/OpenApiConfig.java)

**前端：**
- 必改文件：
  - [`model.api.ts`](penmate-frontend/src/api/modules/model.api.ts)
  - [`model.api.spec.ts`](penmate-frontend/src/api/modules/model.api.spec.ts)
  - [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue)
  - [`ModelSettings.spec.ts`](penmate-frontend/src/components/workbench/ModelSettings.spec.ts)
  - [`useProfileSettings.ts`](penmate-frontend/src/composables/profile/useProfileSettings.ts)
  - [`useProfileSettings.spec.ts`](penmate-frontend/src/composables/profile/useProfileSettings.spec.ts)
- 可能新增：
  - `ModelKeyQuickCreate` 子组件，或在 [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue) 内增加局部表单区。

**优点：**
- 改动小、风险低、与当前 SQL/Service 架构一致。
- 不破坏已有审计、安全、复用、状态管理边界。
- 可分两阶段实施：先修契约，再修体验。

**缺点：**
- 用户仍需理解“Key 是独立资源”。
- 若不提供快捷创建，会继续被感知为“多一步”。

### 2.2 方案 B：改造成模型配置接口支持直接录入密钥（不推荐作为主线）

**核心思路：**
- 修改模型配置创建/更新接口，使其除了引用已有 Key 外，还能接收 `apiKey`（以及可能的 `keyName`）。
- 后端在创建模型配置时自动生成一条用户 Key 或直接把密钥嵌入配置。

**这里又分两种实现：**

1. **B1：接口接收 `apiKey`，服务层自动先创建用户 Key，再绑定配置。**
2. **B2：接口接收 `apiKey`，重新把模型配置表改回 embedded credential。**

其中 **B2 应直接排除**，因为它与 [`V15__restore_model_config_key_references.sql`](penmate-backend/src/main/resources/db/migration/V15__restore_model_config_key_references.sql) 和既有重构方向相反，会造成架构回滚。

即便是 **B1**，也不建议直接改现有接口为“引用/明文双模混合”。

**改造范围：**

**后端：**
- DTO/命令对象扩容：
  - [`CreateUserModelConfigDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/CreateUserModelConfigDto.java)
  - [`UpdateUserModelConfigDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/UpdateUserModelConfigDto.java)
  - [`ModelCommands.java`](penmate-backend/src/main/java/com/penmate/backend/application/model/command/ModelCommands.java)
- 应用服务重写创建/更新分支：
  - [`ModelApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java)
- 可能需要在一次事务内创建 Key 并创建配置，补齐“默认命名”“provider 一致性”“重复 Key 策略”“编辑时是否允许更新明文 Key”规则。
- 控制器和测试：
  - [`ModelController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java)
  - [`ModelControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java)
  - [`ModelApplicationServiceTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/model/ModelApplicationServiceTest.java)
- 如新增事务型组合接口，建议新建 DTO/endpoint，而不是污染现有接口。

**前端：**
- 需要在 [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue) 中恢复 `apiKey` 输入框、`keyName` 输入框、创建后提示等。
- 同步修改：
  - [`model.api.ts`](penmate-frontend/src/api/modules/model.api.ts)
  - [`model.api.spec.ts`](penmate-frontend/src/api/modules/model.api.spec.ts)
  - [`ModelSettings.spec.ts`](penmate-frontend/src/components/workbench/ModelSettings.spec.ts)

**额外风险：**
1. **接口语义混乱。** 同一接口既支持引用 key，又支持直接密钥，会出现冲突优先级：`userKeyId` 和 `apiKey` 同时传怎么办？
2. **编辑语义变复杂。** 更新模型配置时若传 `apiKey`，是更新已有 Key、创建新 Key、还是仅替换引用？
3. **安全与审计复杂度上升。** 明文密钥再次进入模型配置接口链路，需要新增更多日志脱敏与验证规则。
4. **测试矩阵膨胀。** 需要覆盖引用模式、明文模式、混合冲突模式、空值模式、provider 不一致模式。

**何时可以考虑 B1：**
- 只有在产品明确要求“新增模型配置必须一步完成，且不能要求用户先理解 Key 资源”时，才建议新增一个**独立快捷接口**，例如 `POST /model/configs/quick-create`，内部仍落地为“先建用户 Key，再建模型配置”的引用式存储。
- 这种做法比修改现有 [`POST /model/configs`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:173) 更可控。

---

## 三、推荐实施顺序

### Task 1: 收敛 `modelConfigId` 契约并删除 `id` 兼容

Use [test-driven-development] mode for this task.

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java)
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java)
- Modify: [`penmate-frontend/src/components/workbench/ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue)
- Modify: [`penmate-frontend/src/components/workbench/ModelSettings.spec.ts`](penmate-frontend/src/components/workbench/ModelSettings.spec.ts)
- Modify: [`penmate-frontend/src/composables/profile/useProfileSettings.ts`](penmate-frontend/src/composables/profile/useProfileSettings.ts)
- Modify: [`penmate-frontend/src/composables/profile/useProfileSettings.spec.ts`](penmate-frontend/src/composables/profile/useProfileSettings.spec.ts)
- Modify: [`penmate-frontend/src/api/modules/model.api.spec.ts`](penmate-frontend/src/api/modules/model.api.spec.ts)

**Step 1: Write the failing test**

1. 在 [`ModelControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java) 中把列表断言从：
   - `$.data[0].id = 9001`
   - `$.data[0].modelConfigId doesNotExist`
   改成：
   - `$.data[0].modelConfigId = 9001`
   - `$.data[0].id doesNotExist`
2. 在 [`ModelSettings.spec.ts`](penmate-frontend/src/components/workbench/ModelSettings.spec.ts) 中移除“当列表使用 id 字段时仍可工作”的兼容测试，改成“仅接受 modelConfigId”。
3. 在 [`useProfileSettings.spec.ts`](penmate-frontend/src/composables/profile/useProfileSettings.spec.ts) 中要求 `candidateConfigs` 映射时使用 `modelConfigId`，不再回退 `id`。

**Step 2: Run test to verify it fails**

Run: [`mvn -pl penmate-backend -Dtest=ModelControllerTest test`](penmate-backend/pom.xml)

Expected: 因 [`normalizeBusinessIdView()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:257) 仍在移除 `modelConfigId`，测试失败。

Run: [`npm run test -- src/components/workbench/ModelSettings.spec.ts src/composables/profile/useProfileSettings.spec.ts src/api/modules/model.api.spec.ts`](penmate-frontend/package.json)

Expected: 至少 1 个前端测试失败，因为当前仍兼容 `item.id`。

**Step 3: Write minimal implementation**

1. 删除 [`normalizeBusinessIdView()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:257) 在 [`listUserModelConfigs()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:164) 上的使用，直接返回仓储投影中的 `modelConfigId`。
2. 如果需要统一风格，新增显式 DTO 或 view mapper，但不要再把 `modelConfigId` 改名成 `id`。
3. 将 [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue:361) 从 `item.modelConfigId ?? item.id` 改为仅 `item.modelConfigId`。
4. 将 [`useProfileSettings.ts`](penmate-frontend/src/composables/profile/useProfileSettings.ts:168) 保持仅使用 `item.modelConfigId`，并在异常数据时清空而不是回退。

**Step 4: Run test to verify it passes**

Run: [`mvn -pl penmate-backend -Dtest=ModelControllerTest test`](penmate-backend/pom.xml)

Expected: `BUILD SUCCESS`。

Run: [`npm run test -- src/components/workbench/ModelSettings.spec.ts src/composables/profile/useProfileSettings.spec.ts src/api/modules/model.api.spec.ts`](penmate-frontend/package.json)

Expected: `All tests passed` 或 Vitest 成功输出。

**Step 5: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java penmate-frontend/src/components/workbench/ModelSettings.vue penmate-frontend/src/components/workbench/ModelSettings.spec.ts penmate-frontend/src/composables/profile/useProfileSettings.ts penmate-frontend/src/composables/profile/useProfileSettings.spec.ts penmate-frontend/src/api/modules/model.api.spec.ts && git commit -m "refactor: restore modelConfigId contract for model configs"`](.git)

---

### Task 2: 优化引用式 Key 交互而不修改存储模型

Use [test-driven-development] mode for this task.

**Files:**
- Modify: [`penmate-frontend/src/components/workbench/ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue)
- Modify: [`penmate-frontend/src/components/workbench/ModelSettings.spec.ts`](penmate-frontend/src/components/workbench/ModelSettings.spec.ts)
- Optional Modify: [`penmate-frontend/src/api/modules/model.api.ts`](penmate-frontend/src/api/modules/model.api.ts)
- Optional Create: [`penmate-frontend/src/components/workbench/ModelKeyQuickCreate.vue`](penmate-frontend/src/components/workbench/ModelKeyQuickCreate.vue)
- Optional Create: [`penmate-frontend/src/components/workbench/ModelKeyQuickCreate.spec.ts`](penmate-frontend/src/components/workbench/ModelKeyQuickCreate.spec.ts)

**Step 1: Write the failing test**

增加以下前端测试：

1. 选择某个 provider 后若当前来源下无 Key，应显示“暂无可用 Key，请先创建”。
2. 点击“新建 Key”后，能够打开快捷创建区域或触发父级动作。
3. 快捷创建成功后，Key 列表刷新并自动选中新建 Key。
4. 仍保证最终提交给 [`createUserModelConfig()`](penmate-frontend/src/api/modules/model.api.ts:82) 的 payload 只包含 `userKeyId` 或 `officialKeyId`，不包含 `apiKey`。

**Step 2: Run test to verify it fails**

Run: [`npm run test -- src/components/workbench/ModelSettings.spec.ts`](penmate-frontend/package.json)

Expected: 失败，因为当前表单只有空下拉，没有缺 Key 引导和快捷创建入口。

**Step 3: Write minimal implementation**

1. 在 [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue:106) 增加 Key 为空的辅助文案区。
2. 在“选择 Key”区域增加“新建 Key”按钮。
3. 若不拆组件，可在同一弹窗中增加简易 `keyName/apiKey` 子表单，点击保存时先调 [`modelApi.createKey()`](penmate-frontend/src/api/modules/model.api.ts:58)，再刷新 key 列表并回填 `selectedKeyId`。
4. 保证模型配置保存仍只走引用字段，不让 `apiKey` 穿过 [`normalizeUserModelConfigPayload()`](penmate-frontend/src/api/modules/model.api.ts:6)。

**Step 4: Run test to verify it passes**

Run: [`npm run test -- src/components/workbench/ModelSettings.spec.ts src/api/modules/model.api.spec.ts`](penmate-frontend/package.json)

Expected: Vitest 成功，且断言 payload 不含 `apiKey`。

**Step 5: Commit**

Run: [`git add penmate-frontend/src/components/workbench/ModelSettings.vue penmate-frontend/src/components/workbench/ModelSettings.spec.ts penmate-frontend/src/api/modules/model.api.ts penmate-frontend/src/api/modules/model.api.spec.ts && git commit -m "feat: improve referenced key workflow in model settings"`](.git)

---

### Task 3: 仅在产品强制要求一步录入时，新增独立快捷接口（备选，不默认执行）

Use [test-driven-development] mode for this task.

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/CreateUserModelConfigQuickDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/CreateUserModelConfigQuickDto.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/model/command/ModelCommands.java`](penmate-backend/src/main/java/com/penmate/backend/application/model/command/ModelCommands.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java)
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java)
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/application/model/ModelApplicationServiceTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/model/ModelApplicationServiceTest.java)
- Modify: [`penmate-frontend/src/components/workbench/ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue)
- Modify: [`penmate-frontend/src/components/workbench/ModelSettings.spec.ts`](penmate-frontend/src/components/workbench/ModelSettings.spec.ts)

**Step 1: Write the failing test**

1. 后端接口测试覆盖新接口请求体：`providerId + modelName + apiKey + keyName`。
2. 应用服务测试断言：先调用 [`insertUserKey()`](penmate-backend/src/main/java/com/penmate/backend/domain/model/repository/ModelRepository.java:16)，再调用 [`insertUserModelConfig()`](penmate-backend/src/main/java/com/penmate/backend/domain/model/repository/ModelRepository.java:68)。
3. 前端测试断言：快捷模式最终不直接把 `apiKey` 发送给旧的 [`/model/configs`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:173)，而是调新接口。

**Step 2: Run test to verify it fails**

Run: [`mvn -pl penmate-backend -Dtest=ModelControllerTest,ModelApplicationServiceTest test`](penmate-backend/pom.xml)

Expected: 失败，因为新 DTO、新 endpoint、新应用服务分支尚不存在。

Run: [`npm run test -- src/components/workbench/ModelSettings.spec.ts`](penmate-frontend/package.json)

Expected: 失败，因为前端没有快捷模式。

**Step 3: Write minimal implementation**

1. 新增 `POST /model/configs/quick-create`，不要篡改现有 [`POST /model/configs`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:173)。
2. 在应用服务中封装事务：
   - 生成 `userKeyId`
   - 加密并保存用户 Key
   - 生成 `modelConfigId`
   - 创建引用式模型配置
3. 明确业务规则：
   - 仅支持用户密钥，不支持官方密钥明文。
   - 若 `providerId` 不合法直接失败。
   - `keyName` 为空时自动按 `provider + modelName + timestamp` 生成。
4. 前端仅在用户点击“一步创建”入口时调用新接口。

**Step 4: Run test to verify it passes**

Run: [`mvn -pl penmate-backend -Dtest=ModelControllerTest,ModelApplicationServiceTest test`](penmate-backend/pom.xml)

Expected: `BUILD SUCCESS`。

Run: [`npm run test -- src/components/workbench/ModelSettings.spec.ts`](penmate-frontend/package.json)

Expected: Vitest 成功。

**Step 5: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/CreateUserModelConfigQuickDto.java penmate-backend/src/main/java/com/penmate/backend/application/model/command/ModelCommands.java penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java penmate-backend/src/test/java/com/penmate/backend/application/model/ModelApplicationServiceTest.java penmate-frontend/src/components/workbench/ModelSettings.vue penmate-frontend/src/components/workbench/ModelSettings.spec.ts && git commit -m "feat: add quick create flow for model config with key"`](.git)

---

## 四、测试与验证策略

### 4.1 后端验证

1. **接口契约测试**
   - [`ModelControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java)
   - 重点断言：
     - 列表返回 `modelConfigId`，不返回 `id`
     - 创建/更新模型配置仍只接受引用字段
     - 若启用快捷接口，则仅新接口接受 `apiKey`

2. **应用服务测试**
   - [`ModelApplicationServiceTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/model/ModelApplicationServiceTest.java)
   - 覆盖：
     - `USER_KEY` / `OFFICIAL_KEY` 引用校验
     - provider 不一致失败
     - 禁用 key 失败
     - 快捷接口场景下“先建 key 再建 config”

3. **仓储/迁移回归**
   - 现有 [`ModelConfigKeyReferenceMigrationTest.java`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/model/ModelConfigKeyReferenceMigrationTest.java)
   - 确认不会因交互需求再次回滚为 embedded credential

### 4.2 前端验证

1. **API 层测试**
   - [`model.api.spec.ts`](penmate-frontend/src/api/modules/model.api.spec.ts)
   - 断言模型配置 payload 不含 `apiKey` / `keyName` / `selectedKeyId`

2. **组件测试**
   - [`ModelSettings.spec.ts`](penmate-frontend/src/components/workbench/ModelSettings.spec.ts)
   - 覆盖：
     - 仅识别 `modelConfigId`
     - provider 切换后筛选 Key
     - 无 Key 提示
     - 快捷创建 Key 后自动回填
     - 创建/编辑/删除/角色分配不回归

3. **偏好加载测试**
   - [`useProfileSettings.spec.ts`](penmate-frontend/src/composables/profile/useProfileSettings.spec.ts)
   - 覆盖 `candidateConfigs` 仅使用 `modelConfigId`

### 4.3 手工验收

1. 打开 [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue) 所在模型设置弹窗。
2. 确认模型列表、编辑、删除、主 Agent/副 Agent 分配均基于 `modelConfigId`。
3. 新增模型时：
   - 已有 Key：可直接完成创建。
   - 无 Key：能看到清晰提示并能进入新建 Key 流程。
4. Network 检查：
   - `GET /model/configs` 返回 `modelConfigId`
   - `POST /model/configs` 请求体不含 `apiKey`
   - 若启用快捷方案，仅 `POST /model/configs/quick-create` 才出现 `apiKey`

---

## 五、最终建议

### 推荐方案

1. **字段命名：恢复 `modelConfigId`，不要全链路改成 `id`。**
2. **Key 录入：保持当前引用式设计，优先优化交互，不要直接把现有模型配置接口改成支持明文密钥。**
3. **若产品坚持一步创建，只新增独立快捷接口或前端组合流程，不污染旧接口。**

### 原因摘要

- `modelConfigId` 已是后端真实业务主键语义，当前 `id` 只是控制器层人为改名产生的契约漂移。
- 引用式 Key 已经贯穿数据库、仓储、应用服务和前端清洗逻辑，推翻它的收益小于成本。
- 用户真正不满的是“交互绕”，不是“引用式架构本身一定错误”。先补交互能以更低风险解决主要痛点。

### 执行优先级

1. **P0：** 修复 `modelConfigId` 契约，去掉 `id` 兼容。
2. **P1：** 优化模型设置中的 Key 空状态、新建入口、文案与快捷流。
3. **P2：** 仅在业务强制要求下增加独立快捷接口 `quick-create`。

---

Plan complete. Execute now?

Options:
1. Execute in this session ([executing-plans] mode)
2. Execute later (user will run `/execute-plan`)
3. Manual implementation (just use plan as guide)
