# PenMate 团队 TDD 工作约定（Team Working Agreement）

> 适用范围：PenMate 前端与后端仓库内所有功能开发、缺陷修复与重构 PR。

## 1. PR 门禁规则

### 1.1 强制检查项（必须全部通过）

1. CI 工作流 [`tdd-quality-gate.yml`](.github/workflows/tdd-quality-gate.yml) 的以下 Job 必须为绿色：
   - `backend-test`：执行 `mvn -B verify`
   - `frontend-test`：执行 `npm ci` + `npm run test:coverage`
2. PR 模板 [`pull_request_template.md`](.github/pull_request_template.md) 的 **TDD 证据** 必填：
   - RED（先失败）命令与失败日志片段
   - GREEN（最小通过）命令与通过日志片段
   - REFACTOR 勾选与说明（可选但推荐）
3. PR 模板测试清单必须全部勾选：
   - 后端 `mvn -q verify` 通过
   - 前端 `npm run test:coverage` 通过
   - 新增/修改接口已更新对应测试

### 1.2 合入判定

- 任何缺失 TDD 证据链、任何 CI Job 失败、任何测试清单未完成，均不得合入 `main/master`。
- Reviewer 在审批前必须核对 RED→GREEN 证据是否与本次变更直接相关，禁止粘贴历史日志替代。

## 2. 命名规范

### 2.1 后端测试命名

- 测试方法统一采用：`UT_<LAYER>_<DOMAIN>_<SCENARIO>_<EXPECTATION>`
- 示例：`UT_API_AUTH_LOGIN_INVALID_PASSWORD_UNAUTHORIZED`
- 约束：
  - `<LAYER>` 取值建议：`API` / `APP` / `ARCH`
  - `<SCENARIO>` 必须体现输入条件或触发动作
  - `<EXPECTATION>` 必须体现断言结果（状态码/错误码/业务效果）

### 2.2 前端测试命名

- 测试文件统一后缀：`*.spec.ts`
- 测试用例统一采用：`should_<result>_when_<condition>`
- 示例：`should_return_401_when_token_expired`

## 3. 测试分层

为避免重复测试和测试盲区，按以下层次组织：

1. **Contract/API 测试（后端 Controller）**
   - 目标：校验 OpenAPI 路径、状态码、关键响应结构、基础校验错误。
2. **Application 行为测试（后端应用层）**
   - 目标：校验业务规则、边界、异常分支、幂等行为。
3. **Architecture 测试（后端架构层）**
   - 目标：校验依赖方向与分层规则，防止架构腐化。
4. **Frontend API 契约测试（前端 API 模块）**
   - 目标：校验请求方法/路径/参数映射、响应映射、错误处理。
5. **Frontend 关键集成测试（前端关键页面）**
   - 目标：覆盖登录、路由守卫、工作台关键渲染与核心交互路径。

## 4. 失败测试示例流程（RED→GREEN→REFACTOR）

以下流程必须在每个功能分支至少执行一轮，并在 PR 中提供证据：

1. **RED：先写失败测试**
   - 新增一个能表达需求的测试，断言目标行为。
   - 立即执行测试，确认失败（编译失败/断言失败均可）。
2. **记录 RED 证据**
   - 记录执行命令与关键失败日志片段。
3. **GREEN：最小实现**
   - 仅实现使当前失败测试通过的最小代码，不提前做扩展优化。
4. **验证 GREEN**
   - 再次执行测试，确认新增测试与相关回归测试通过。
5. **REFACTOR：小步重构**
   - 做命名优化、重复提取、结构整理等无行为变更重构。
6. **回归确认**
   - 重构后复跑测试，确保行为不回退。

## 5. 例外审批机制（紧急修复 24h 补测）

### 5.1 适用条件

仅允许用于以下“生产紧急修复”场景之一：

- P0 故障（系统不可用/核心链路中断）
- 安全漏洞紧急封堵
- 法规或合规要求的时效性修复

### 5.2 例外审批规则

1. 发起人必须在 PR 标题或描述标注：`[Emergency Exception]`。
2. 必须获得至少 **2 名审批人** 同意：
   - 1 名代码负责人（模块 Owner）
   - 1 名技术管理者（Tech Lead/架构负责人）
3. 即便走例外流程，仍需提供：
   - 最小可用验证（如手工复现与修复后验证步骤）
   - 风险说明与回滚方案

### 5.3 24h 补测闭环（强制）

1. 合入后 **24 小时内** 必须补齐自动化测试（至少覆盖本次修复主路径）。
2. 必须提交补测 PR，并在描述中回链紧急修复 PR。
3. 若超时未补测：
   - 默认阻断该模块后续非紧急 PR 合入；
   - 由 Tech Lead 在周会通报并跟踪到关闭。

### 5.4 审计与度量

- 每月统计紧急例外次数、24h 按时补测率、重复故障率。
- 连续两月超阈值（例外次数 > 3 或补测按时率 < 95%）时，触发专项复盘与流程整改。

