# PenMate TDD 简明实操手册

> 适用对象：以后要在 PenMate 仓库里持续、正确地按 TDD 开发前后端功能的人。
>
> 本手册基于当前仓库已有约定与示例整理，包括 [`docs/project-specifications/plans/tdd-team-working-agreement.md`](docs/project-specifications/plans/tdd-team-working-agreement.md)、[`penmate-backend/pom.xml`](penmate-backend/pom.xml)、[`penmate-frontend/package.json`](penmate-frontend/package.json)、[`penmate-frontend/vitest.config.ts`](penmate-frontend/vitest.config.ts)、[`penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerTest.java)、[`penmate-frontend/src/api/modules/auth.api.spec.ts`](penmate-frontend/src/api/modules/auth.api.spec.ts)。

---

## 1. TDD 核心原则：RED → GREEN → REFACTOR

TDD 不是“写完代码再补测试”，而是“测试驱动开发”。顺序不能反。

### RED（先红）
先写一个**能表达需求的自动化测试**，然后马上运行它，确认它失败。

通俗理解：
- 先把“你想要系统做什么”写成检查题。
- 没实现前，它本来就该失败。
- 失败是好事，说明这个测试真能卡住问题，不是假测试。

常见失败形式：
- 编译失败
- 找不到方法/类
- 断言失败
- 返回状态码不对
- 请求路径不对

### GREEN（再绿）
只写**刚好让这个失败测试通过**的最小实现。

通俗理解：
- 不要一口气把“未来可能要的功能”全写了。
- 只修当前这一个失败点。
- 目标不是“代码写爽”，而是“测试变绿”。

### REFACTOR（最后重构）
在测试全绿的保护下，整理代码结构，但**不改变行为**。

通俗理解：
- 可以改命名、提取重复、拆方法、简化结构。
- 不可以顺手偷偷加新需求。
- 每次重构后要再跑测试，证明行为没变。

### 一句话记忆
- RED：证明“现在还没实现”
- GREEN：证明“已经最小实现”
- REFACTOR：证明“实现变干净但行为没变”

---

## 2. 后端日常开发流程

本项目后端以 Java + Spring Boot + JUnit5 + Mockito + MockMvc 为主，现有测试分层要求可参考 [`docs/plans/tdd-team-working-agreement.md`](docs/plans/tdd-team-working-agreement.md)。

### 2.1 从哪里开始挑接口
开发一个后端需求时，先确定它属于哪层：

1. **Controller/API 契约层**
   - 关心路径、方法、参数、状态码、响应结构。
   - 参考示例：[`AgentControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerTest.java)
2. **Application 业务层**
   - 关心业务规则、异常分支、边界、幂等。
3. **Architecture 层**
   - 关心依赖方向和分层规则。

实际开发里，新增接口至少要想两件事：
- Controller 契约要不要测？通常要。
- Application 规则要不要测？通常也要。

### 2.2 后端 TDD 日常步骤

#### Step 1：先写失败测试
先写最贴近需求的失败测试，不先写实现。

建议顺序：
1. 如果是新增接口，先写 Controller 测试。
2. 如果有明确业务规则，再补 Application 测试。
3. 用例命名遵循仓库约定：
   - `UT_<LAYER>_<DOMAIN>_<SCENARIO>_<EXPECTATION>`
   - 约定来源：[`tdd-team-working-agreement.md`](docs/project-specifications/plans/tdd-team-working-agreement.md)

例如：
- `UT_API_AGENT_CONVERSATION_CREATE_SUCCESS`
- `UT_APP_NOVEL_COMMIT_NULL_PROJECTID_BAD_REQUEST`

#### Step 2：立刻运行，确认它真失败
不要连续写多个测试再统一跑。写一个，跑一个。

你需要留存：
- 执行命令
- 失败日志关键片段

#### Step 3：写最小实现
只补足当前测试需要的：
- 新增 Controller 方法
- 新增 DTO 字段
- 新增参数校验
- 新增 ApplicationService 分支
- 新增异常映射

不要顺手做这些事：
- 提前抽象未来接口
- 提前加一堆通用能力
- 一次性重写整块逻辑

#### Step 4：再跑同一个测试，变绿
确认刚才那个红测已经绿了。

#### Step 5：补跑相关回归测试
至少覆盖：
- 当前测试类
- 当前模块相关测试
- 最终全量后端验证

#### Step 6：重构
只在“已经变绿”后做：
- 方法改名
- 提取重复构造
- 提取校验逻辑
- 消除魔法值

重构后必须再跑测试。

#### Step 7：提交
推荐小步提交：
- 一个失败测试 → 一个最小实现 → 一个小提交
- 不要堆成超大提交

### 2.3 后端开发时的落地判断标准
在你准备提交前，问自己：
- 我能拿出 RED 证据吗？
- 我能拿出 GREEN 证据吗？
- 我新增/修改接口时，测试是否同步更新了？
- 我最后有没有跑后端验证命令？

只要其中一个答不上来，就还不算完成 TDD。

---

## 3. 前端日常开发流程

本项目前端当前已具备 Vitest 测试入口与 API 模块契约测试示例：[`penmate-frontend/package.json`](penmate-frontend/package.json)、[`penmate-frontend/vitest.config.ts`](penmate-frontend/vitest.config.ts)、[`penmate-frontend/src/api/modules/auth.api.spec.ts`](penmate-frontend/src/api/modules/auth.api.spec.ts)。

### 3.1 前端优先测什么
按当前仓库现状，优先级建议如下：

1. **API 模块契约测试**
   - 测请求方法、路径、参数映射、错误处理。
   - 这是最容易稳定执行、最适合 TDD 的入口。
2. **关键页面集成测试**
   - 测登录、路由守卫、工作台关键交互。
3. **纯展示组件**
   - 只有在逻辑明显时再测，不要为了覆盖率乱测静态 UI。

### 3.2 前端 TDD 日常步骤

#### Step 1：先写失败测试
先在对应模块旁边写 `*.spec.ts`。

命名约定：
- 文件后缀：`*.spec.ts`
- 用例命名：`should_<result>_when_<condition>`
- 约定来源：[`tdd-team-working-agreement.md`](docs/project-specifications/plans/tdd-team-working-agreement.md)

参考现有风格：[`auth.api.spec.ts`](penmate-frontend/src/api/modules/auth.api.spec.ts)

典型断言包括：
- 是否调用了正确 endpoint
- 是否传了正确 payload
- 是否处理了错误 message
- 是否调用了正确 HTTP 方法

#### Step 2：立刻运行并确认失败
失败可能是：
- 请求路径断言不通过
- 方法不存在
- mock 不匹配
- 返回值映射不对

必须保留 RED 证据。

#### Step 3：写最小实现
只改当前 API 模块或当前页面需要的最小代码。

例如只改：
- [`src/api/modules/*.api.ts`](penmate-frontend/src/api/modules)
- 参数映射
- 错误解析函数
- 局部页面逻辑

#### Step 4：再跑测试，确认变绿
先跑单文件，再跑更大范围。

#### Step 5：重构
在绿测保护下：
- 提取重复请求构造
- 统一错误处理
- 优化类型定义
- 删除多余分支

#### Step 6：做前端回归验证
至少跑：
- 当前 spec
- 前端测试覆盖命令

### 3.3 前端开发时的落地判断标准
提交前检查：
- 测试是不是先写的？
- 我是不是亲眼看过它先失败？
- 这次改动有没有同步更新对应 spec？
- `test:coverage` 有没有跑？

---

## 4. 本项目可直接使用的命令清单

以下命令来自当前仓库已有配置。

### 4.1 Backend

在 [`penmate-backend/`](penmate-backend/) 目录执行：

```cmd
mvn -q test
```
用途：快速跑后端测试。

```cmd
mvn -q -Dtest=AgentControllerTest test
```
用途：只跑某一个测试类，适合 RED/GREEN 小步验证。

```cmd
mvn -q -Dtest=AgentControllerTest#UT_AGENT_MESSAGE_PARAM_INVALID test
```
用途：只跑单个测试方法，适合精确验证一个红测/绿测。

```cmd
mvn -q verify
```
用途：PR 前标准后端验证命令；团队约定与门禁文档中明确要求。

```cmd
mvn -B verify
```
用途：CI 使用的后端命令，来源于 [`tdd-team-working-agreement.md`](docs/project-specifications/plans/tdd-team-working-agreement.md)。

### 4.2 Frontend

在 [`penmate-frontend/`](penmate-frontend/) 目录执行：

```cmd
npm test
```
用途：启动 Vitest。

```cmd
npm run test:run
```
用途：单次执行测试，适合日常 RED/GREEN。

```cmd
npm run test:coverage
```
用途：PR 前标准前端验证命令；当前脚本定义见 [`package.json`](penmate-frontend/package.json)。

```cmd
npm run build
```
用途：构建验证；不是 TDD 主证据，但可作为补充验证。

### 4.3 推荐执行顺序

#### 后端开发时
1. 跑单个测试方法
2. 跑单个测试类
3. 跑相关模块测试
4. 跑 `mvn -q verify`

#### 前端开发时
1. 跑单个 spec
2. 跑 `npm run test:run`
3. 跑 `npm run test:coverage`
4. 如涉及构建风险，再跑 `npm run build`

---

## 5. PR 提交时必须粘贴的证据模板

根据 [`docs/project-specifications/plans/tdd-team-working-agreement.md`](docs/project-specifications/plans/tdd-team-working-agreement.md)，PR 必须提供 TDD 证据，至少包含 RED、GREEN、REFACTOR 说明。

可直接复制下面模板：

```md
## TDD Evidence

### RED
- Test first written before implementation: [测试文件路径]
- Command:
  ```bash
  [失败测试命令]
  ```
- Failure snippet:
  ```text
  [保留 5~20 行关键失败日志]
  ```
- Why it failed:
  [一句话说明失败原因，证明功能当时未实现/契约不满足]

### GREEN
- Minimal implementation files:
  - [修改文件 1]
  - [修改文件 2]
- Command:
  ```bash
  [通过测试命令]
  ```
- Success snippet:
  ```text
  [保留 5~20 行关键通过日志]
  ```

### REFACTOR
- [ ] No refactor in this PR
- [ ] Refactor done without behavior change
- Refactor notes:
  [如有，写提取重复/改名/结构整理]

### Regression Verification
- Backend:
  ```bash
  [例如 mvn -q verify]
  ```
- Frontend:
  ```bash
  [例如 npm run test:coverage]
  ```
- Result:
  [写通过结果或适用范围]
```

### 最低要求
PR 里至少要让 Reviewer 一眼看到：
- 你先写了哪个测试
- 它一开始为什么失败
- 你改了哪些文件使它变绿
- 你最后跑了什么验证

### 不合格证据示例
这些都不算：
- 只说“已测试通过”，没有命令和日志
- 贴历史日志，不是本次改动产生的
- 只有 GREEN，没有 RED
- 只贴全量通过，不贴失败证明

---

## 6. 常见反模式与纠正

### 反模式 1：先把功能写完，再补测试
**问题：** 这不是 TDD，只是“事后补票”。

**纠正：**
- 先写一个最小失败测试。
- 哪怕只是一条状态码断言，也必须先红一次。

### 反模式 2：一次写很多测试，但没逐个验证失败
**问题：** 你不知道到底哪个测试真正驱动了实现。

**纠正：**
- 一次只推进一个行为。
- 写一个测试，跑红；实现；跑绿；再写下一个。

### 反模式 3：RED 阶段失败原因不准确
**问题：** 例如测试因为环境坏了、mock 写错了、依赖没装，而不是因为功能没实现。

**纠正：**
- RED 必须失败在“需求相关点”上。
- 如果是环境问题，先修环境，不要把环境错误当 TDD 证据。

### 反模式 4：GREEN 阶段顺手实现很多未来需求
**问题：** 范围失控，增加回归风险，也削弱测试驱动作用。

**纠正：**
- 只写让当前测试通过的最小代码。
- 未来需求等未来测试来驱动。

### 反模式 5：重构和改需求混在一起
**问题：** 你无法证明“重构未改变行为”。

**纠正：**
- 先让行为稳定变绿。
- 重构只做结构整理，不改功能表现。
- 如果行为变了，就必须新写测试重新走 RED。

### 反模式 6：只跑单测，不跑回归
**问题：** 当前点变绿了，但可能打坏了别的模块。

**纠正：**
- 先跑目标测试，再跑相关范围，最后跑 PR 标准验证命令。

### 反模式 7：PR 不贴证据，只口头说自己按了 TDD
**问题：** 无法审计，也无法形成团队习惯。

**纠正：**
- 每个 PR 都粘贴 RED/GREEN 证据模板。
- 没证据就视为没执行。

### 反模式 8：把覆盖率当 TDD 本身
**问题：** 覆盖率高不等于测试先写，也不等于测试有价值。

**纠正：**
- 先看有没有 RED→GREEN 证据链。
- 覆盖率只是辅助门禁，不是 TDD 本体。

---

## 7. “新接口开发”的完整示例流程（含时间顺序）

下面给出一个贴近当前仓库结构的后端新接口示例：

**需求示例：**
新增接口：`GET /api/v1/novels/{novelId}/agent/conversations/{conversationId}`，用于查询单个会话详情。

目标：
- 正常存在时返回 `200`
- 不存在时返回 `404` 或约定业务错误
- 先从 Controller 契约测试开始，再补 Application 行为测试

### 00:00 - 00:05 理清需求与分层
你先确定：
- 这是一个新增 HTTP GET 接口
- 至少需要新增 Controller 测试
- 如果查询逻辑有“找不到”的业务规则，要补 Application 测试

产出：
- 决定先改 [`AgentControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerTest.java)

### 00:05 - 00:12 写第一个失败测试（RED）
在 [`AgentControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerTest.java) 新增测试，例如：
- `UT_AGENT_CONVERSATION_GET_SUCCESS`

测试意图：
- 请求 `GET /api/v1/novels/10001/agent/conversations/7001`
- 期望 `200`
- 返回体里有 `id=7001`

这时先不写实现。

### 00:12 - 00:14 运行单个测试并确认失败
示例命令：

```cmd
mvn -q -Dtest=AgentControllerTest#UT_AGENT_CONVERSATION_GET_SUCCESS test
```

你要保存：
- 命令
- 失败日志片段

可能失败原因：
- 没有对应 Controller 方法
- 路由不存在
- 返回状态码不是 200

这一步就是 RED 证据。

### 00:14 - 00:25 写最小实现（GREEN）
只补当前测试所需最小代码，例如：
- 在 Controller 中加查询详情接口
- 调用 `agentApplicationService.getConversation(...)`
- 返回当前测试断言需要的字段

不要此时顺手加：
- 列表过滤
- 分页
- 额外响应字段
- 其他会话接口

### 00:25 - 00:27 再跑刚才的单测，确认变绿
继续执行：

```cmd
mvn -q -Dtest=AgentControllerTest#UT_AGENT_CONVERSATION_GET_SUCCESS test
```

保存 GREEN 日志片段。

### 00:27 - 00:35 补第二个失败测试：找不到场景
新增测试，例如：
- `UT_AGENT_CONVERSATION_GET_NOT_FOUND`

断言：
- 查询不存在会话时返回约定状态码和错误码

然后立刻跑：

```cmd
mvn -q -Dtest=AgentControllerTest#UT_AGENT_CONVERSATION_GET_NOT_FOUND test
```

确认它红。

### 00:35 - 00:45 写最小实现使“找不到”变绿
只补：
- 异常映射
- 业务异常透传
- not found 返回结构

然后再跑这个单测，拿到 GREEN 证据。

### 00:45 - 00:55 补 Application 层测试
如果 `getConversation()` 有明确业务规则，再到应用层测试类补：
- 正常查询
- 查询不存在
- 权限/归属校验（如果有）

继续保持一条一条推进：
- 先写失败测试
- 跑红
- 最小实现
- 跑绿

### 00:55 - 01:05 小步重构
在所有新增测试已经通过后：
- 提取重复的 traceId 构造
- 提取重复 mock 数据构造
- 优化方法命名

然后复跑相关测试类。

### 01:05 - 01:15 回归验证
至少执行：

```cmd
mvn -q -Dtest=AgentControllerTest test
```

再执行：

```cmd
mvn -q verify
```

如果这个接口同时影响前端 API 对接，再补前端对应 spec 并执行：

```cmd
npm run test:coverage
```

### 01:15 - 01:20 整理 PR 证据
把下面内容贴到 PR：
- RED：哪个测试先失败、失败日志是什么
- GREEN：哪个命令通过、通过日志是什么
- REFACTOR：是否只做了无行为变化整理
- Regression：最终跑了哪些验证命令

### 01:20 - 01:25 提交
推荐提交信息风格：
- `test(agent): add failing contract test for get conversation detail`
- `feat(agent): implement get conversation detail endpoint`
- `refactor(agent): extract conversation response mapping`

这样 Reviewer 一眼能看出你的提交顺序是否符合 TDD。

---

## 8. 最后只记住这 6 条

1. 先写测试，不先写实现。
2. 先看它失败，再开始写代码。
3. 一次只驱动一个行为。
4. 只写最小实现，不超前发挥。
5. 重构必须在全绿之后做。
6. PR 必须贴 RED/GREEN 证据，不靠口头承诺。

如果以后拿不准，就直接照着这份手册执行，并以 [`docs/project-specifications/plans/tdd-team-working-agreement.md`](docs/project-specifications/plans/tdd-team-working-agreement.md) 作为最终门禁标准。