# PenMate TDD Gap Remediation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use [executing-plans] mode to implement this plan task-by-task.

**Goal:** 建立并落地 PenMate 前后端可执行、可度量、可门禁的 TDD 流程，使新增功能按“先失败测试→实现→重构”稳定运行。

**Architecture:** 本方案以“测试策略与工程门禁先行”为核心：先补齐测试基础设施（前端测试框架、后端覆盖率插件、CI 门禁），再按 OpenAPI 风险优先级分批补测接口。后端采用 Controller（契约）+ Application（业务）+ Architecture（依赖规则）三层测试，前端采用 API 模块契约测试 + 关键页面集成测试，最终用 PR 规则强制 TDD 证据。

**Tech Stack:** Java 21, Spring Boot 3, JUnit5, Mockito, MockMvc, ArchUnit, Maven Surefire/Failsafe, JaCoCo, Vue3 + Vite + TypeScript, Vitest, @vue/test-utils, GitHub Actions

---

## 0. 是否遵循 TDD：判定标准与证据清单

## 0.1 判定标准（必须同时满足）

1. **流程证据**：每个功能提交必须可追溯到“先有失败测试（RED）→最小实现（GREEN）→重构（REFACTOR）”。
2. **自动化证据**：本地与 CI 都能执行测试；PR 被测试门禁阻止“无测试代码”的变更合入。
3. **分层证据**：至少具备接口契约测试、业务行为测试；高风险路径要有异常/边界测试。
4. **覆盖证据**：覆盖率有阈值，且阈值写入自动化门禁（不是口头约定）。
5. **命名证据**：测试命名可表达场景-输入-预期，不是仅“test1/test2”。

## 0.2 当前证据（来自仓库现状）

### 正向证据（说明“有测试意识”）

- 后端存在较完整测试目录（应用层、接口层、架构层），如 [`NovelApplicationServiceTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/novel/NovelApplicationServiceTest.java) 与 [`NovelControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/novel/NovelControllerTest.java)。
- 存在架构约束测试 [`DependencyRulesTest.java`](penmate-backend/src/test/java/com/penmate/backend/architecture/DependencyRulesTest.java)。
- 后端已配置 JUnit5/Mockito/ArchUnit/Testcontainers 依赖（见 [`pom.xml`](penmate-backend/pom.xml)）。

### 反向证据（说明“未形成完整 TDD 体系”）

- 前端无测试命令与测试依赖（[`package.json`](penmate-frontend/package.json) 仅 `dev/build/preview`）。
- 前端源码目录未发现 `*.test.*` / `*.spec.*` 测试文件。
- 后端构建未配置 JaCoCo 覆盖率插件和阈值门禁（[`pom.xml`](penmate-backend/pom.xml) `build/plugins` 仅 `spring-boot-maven-plugin`）。
- 仓库无 CI 工作流目录（未发现 `.github/workflows`），PR 层缺失自动测试门禁。
- OpenAPI 规模较大（`82 paths / 120 operations`），但缺少“接口-测试映射矩阵”与风险优先级治理。

## 0.3 判定结论

**结论：当前项目“部分具备测试实践”，但“不满足严格 TDD”**。主要原因是：

1) 缺失强制执行 RED→GREEN→REFACTOR 的流程与门禁；
2) 前端测试体系缺位；
3) 覆盖率阈值与 PR 校验缺位；
4) 尚未建立 OpenAPI 驱动的测试优先级闭环。

---

## 1. 现状扫描与偏差分析（对照 TDD 流程）

## 1.1 后端测试组织（现状）

- 应用层：`src/test/java/.../application/*ApplicationServiceTest.java`
- 接口层：`src/test/java/.../interfaces/api/*ControllerTest.java`
- 架构层：`src/test/java/.../architecture/DependencyRulesTest.java`

**偏差：**

1. 用例虽多，但缺少“RED 证据记录”（PR 模板/提交规范中未强制失败测试截图或日志）。
2. 覆盖率没有工程化门槛，无法防止测试债务回流。
3. 接口测试与 OpenAPI 没有自动对齐校验（容易产生“文档变更但测试漏补”）。

## 1.2 前端测试组织（现状）

- 缺少测试脚手架（Vitest/Jest 均未在项目脚本与依赖中落地）。
- 缺少 `unit/component/integration` 分层目录。

**偏差：**

1. 无法执行 RED（没有测试命令就无法“先失败”）。
2. API 适配层（`src/api/modules/*.api.ts`）无契约回归，接口改动风险高。
3. 关键页面（登录、工作台）缺少最小集成测试，回归主要靠人工。

## 1.3 OpenAPI 优先级分析（用于补测排序）

OpenAPI 统计：`120 operations`，主要 tag 体量：

- `novel-controller`：40
- `model-controller`：19
- `rbac-query-controller`：16
- `rag-controller`：9
- `agent-controller`：8
- `style-controller`：7
- `plugin-controller`：7
- `approval-controller`：5
- `ops-controller`：5
- `auth-controller`：4

**优先级策略：**

P0（短期）= 高体量 + 高业务风险 + 高频变更：`novel/model/rbac/auth`  
P1（中期）= 业务连续性相关：`rag/agent/style/plugin/approval/ops`

---

## 2. 短期/中期补测与重构路线图

## 2.1 短期（1~2 周）

1. 补齐前端测试基础设施并跑通首批 API 契约测试。
2. 后端引入 JaCoCo + 阈值，建立覆盖率门禁。
3. 建立 CI 工作流，PR 强制执行：后端测试 + 前端测试 + 覆盖率检查。
4. 以 OpenAPI P0 tag 建立“接口-测试映射表”。
5. 引入 PR 模板，强制 RED/GREEN/REFACTOR 证据。

**短期目标：**

- 后端：P0 接口契约测试覆盖 ≥ 70%
- 前端：核心 API 模块测试覆盖 ≥ 60%
- 门禁：所有 PR 必须通过自动化测试

## 2.2 中期（3~6 周）

1. 按 P1 tag 扩展接口测试和异常边界测试。
2. 前端增加关键页面集成测试（登录/工作台/域控制台）。
3. 引入 OpenAPI 变更检测（文档变更触发缺口提示）。
4. 优化慢测拆分（单测快、集成测可并行）。

**中期目标：**

- 后端：整体行覆盖 ≥ 75%，分支覆盖 ≥ 60%
- 前端：行覆盖 ≥ 65%，关键 API 模块 ≥ 80%
- 新增功能 PR：100% 提供 RED→GREEN 证据

---

## 3. 团队执行规则（可直接纳入工程）

## 3.1 PR 门禁（必须）

1. 后端命令必须通过：`mvn -q test`
2. 前端命令必须通过：`npm run test:run`
3. 覆盖率门槛必须通过：后端 JaCoCo + 前端 Vitest coverage。
4. PR 描述必须包含：
   - RED 阶段失败日志片段
   - GREEN 阶段通过日志片段
   - 重构说明（如有）

## 3.2 命名规范

- 后端测试方法：`UT_<LAYER>_<DOMAIN>_<SCENARIO>_<EXPECTATION>`
  - 例如：`UT_API_AUTH_LOGIN_INVALID_PASSWORD_UNAUTHORIZED`
- 前端测试文件：`*.spec.ts`
- 前端测试用例：`should_<result>_when_<condition>`

## 3.3 测试分层

1. **Contract/API 测试**：Controller + OpenAPI path/status/schema
2. **Application 行为测试**：业务规则、异常、边界、幂等
3. **Architecture 测试**：依赖方向、分层约束
4. **Frontend API 契约测试**：请求参数、响应映射、错误处理
5. **Frontend 关键集成测试**：路由守卫、登录、核心页面渲染

## 3.4 失败测试示例流程（模板）

1. 新增测试（RED）：断言目标行为，目前应失败。
2. 执行单测并保留失败日志。
3. 最小代码实现（GREEN）。
4. 再次执行测试并通过。
5. 小步重构（命名、提取、删除重复）。
6. 复跑测试确保行为不回退。

---

## 4. 可直接执行的分步计划（TDD + 小步提交）

### Task 1: 建立前端测试基础设施（Vitest）

Use [test-driven-development] mode for this task.

**Files:**
- Modify: `penmate-frontend/package.json`
- Create: `penmate-frontend/vitest.config.ts`
- Create: `penmate-frontend/src/test/setup.ts`
- Create: `penmate-frontend/src/api/modules/auth.api.spec.ts`

**Step 1: Write the failing test**

在 `penmate-frontend/src/api/modules/auth.api.spec.ts` 写入：

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'

const postMock = vi.fn()

vi.mock('@/utils/request', () => ({
  request: {
    post: postMock,
  },
}))

import { login } from './auth.api'

describe('auth.api', () => {
  beforeEach(() => {
    postMock.mockReset()
  })

  it('should_call_login_endpoint_when_login_invoked', async () => {
    postMock.mockResolvedValue({ data: { accessToken: 't' } })

    await login({ username: 'u', password: 'p' })

    expect(postMock).toHaveBeenCalledWith('/api/v1/auth/login', {
      username: 'u',
      password: 'p',
    })
  })
})
```

**Step 2: Run test to verify it fails**

Run: `cd penmate-frontend && npm run test:run`

Expected: `Missing script: "test:run"`（RED 证明基础设施缺失）

**Step 3: Write minimal implementation**

修改 `penmate-frontend/package.json`（完整 scripts/devDependencies 示例）：

```json
{
  "name": "penmate-frontend",
  "private": true,
  "version": "0.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc -b && vite build",
    "preview": "vite preview",
    "test": "vitest",
    "test:run": "vitest run",
    "test:coverage": "vitest run --coverage"
  },
  "dependencies": {
    "@ant-design/icons-vue": "^7.0.1",
    "ant-design-vue": "^4.2.6",
    "axios": "^1.15.0",
    "vue": "^3.5.32",
    "vue-router": "^4.6.4"
  },
  "devDependencies": {
    "@types/node": "^24.12.2",
    "@vitejs/plugin-vue": "^6.0.5",
    "@vitest/coverage-v8": "^3.2.4",
    "@vue/test-utils": "^2.4.6",
    "@vue/tsconfig": "^0.9.1",
    "jsdom": "^26.1.0",
    "less": "^4.6.4",
    "typescript": "~6.0.2",
    "vite": "^8.0.4",
    "vitest": "^3.2.4",
    "vue-tsc": "^3.2.6"
  }
}
```

创建 `penmate-frontend/vitest.config.ts`：

```ts
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.spec.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json-summary'],
      lines: 65,
      functions: 65,
      branches: 55,
      statements: 65,
    },
  },
})
```

创建 `penmate-frontend/src/test/setup.ts`：

```ts
import { vi } from 'vitest'

vi.stubGlobal('matchMedia', () => ({
  matches: false,
  media: '',
  onchange: null,
  addListener: () => {},
  removeListener: () => {},
  addEventListener: () => {},
  removeEventListener: () => {},
  dispatchEvent: () => false,
}))
```

**Step 4: Run test to verify it passes**

Run: `cd penmate-frontend && npm install && npm run test:run`

Expected:

- `Test Files  1 passed`
- `Tests       1 passed`

**Step 5: Commit**

```bash
git add penmate-frontend/package.json penmate-frontend/vitest.config.ts penmate-frontend/src/test/setup.ts penmate-frontend/src/api/modules/auth.api.spec.ts
git commit -m "test(frontend): bootstrap vitest and add auth api red-green test"
```

---

### Task 2: 后端覆盖率门禁（JaCoCo）

Use [test-driven-development] mode for this task.

**Files:**
- Modify: `penmate-backend/pom.xml`

**Step 1: Write the failing test**

先以“门禁失败”为 RED：执行覆盖率检查命令（当前无 JaCoCo 阶段）。

**Step 2: Run test to verify it fails**

Run: `cd penmate-backend && mvn -q verify`

Expected: 无覆盖率阈值校验输出（即 RED：门禁尚未生效）。

**Step 3: Write minimal implementation**

在 `penmate-backend/pom.xml` 的 `<build><plugins>` 增加：

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
  <executions>
    <execution>
      <id>prepare-agent</id>
      <goals>
        <goal>prepare-agent</goal>
      </goals>
    </execution>
    <execution>
      <id>report</id>
      <phase>verify</phase>
      <goals>
        <goal>report</goal>
      </goals>
    </execution>
    <execution>
      <id>check</id>
      <phase>verify</phase>
      <goals>
        <goal>check</goal>
      </goals>
      <configuration>
        <rules>
          <rule>
            <element>BUNDLE</element>
            <limits>
              <limit>
                <counter>LINE</counter>
                <value>COVEREDRATIO</value>
                <minimum>0.70</minimum>
              </limit>
              <limit>
                <counter>BRANCH</counter>
                <value>COVEREDRATIO</value>
                <minimum>0.55</minimum>
              </limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

**Step 4: Run test to verify it passes**

Run: `cd penmate-backend && mvn -q verify`

Expected:

- `BUILD SUCCESS`
- 生成 `target/site/jacoco/index.html`

**Step 5: Commit**

```bash
git add penmate-backend/pom.xml
git commit -m "build(backend): add jacoco coverage report and quality gate"
```

---

### Task 3: 建立 PR 自动化门禁（后端+前端）

Use [executing-plans] mode for this task.

**Files:**
- Create: `.github/workflows/tdd-quality-gate.yml`
- Create: `.github/pull_request_template.md`

**Step 1: Write the failing test**

RED = 当前仓库无 workflow，PR 无自动阻断。

**Step 2: Run test to verify it fails**

Run: 在 PR 页面观察（或本地仅验证 yaml 存在前状态）。

Expected: 无 CI 检查项。

**Step 3: Write minimal implementation**

创建 `.github/workflows/tdd-quality-gate.yml`：

```yaml
name: tdd-quality-gate

on:
  pull_request:
    branches: ["main", "master"]
  push:
    branches: ["main", "master"]

jobs:
  backend-test:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: penmate-backend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - name: Run backend tests + coverage gate
        run: mvn -B verify

  frontend-test:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: penmate-frontend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: 'npm'
          cache-dependency-path: penmate-frontend/package-lock.json
      - name: Install
        run: npm ci
      - name: Run frontend coverage gate
        run: npm run test:coverage
```

创建 `.github/pull_request_template.md`：

```md
## 变更类型
- [ ] Feature
- [ ] Fix
- [ ] Refactor

## TDD 证据（必填）

### RED（先失败）
执行命令：
```bash
<粘贴命令>
```
失败输出（关键片段）：
```text
<粘贴失败日志>
```

### GREEN（最小通过）
执行命令：
```bash
<粘贴命令>
```
通过输出（关键片段）：
```text
<粘贴通过日志>
```

### REFACTOR（可选但推荐）
- [ ] 已重构且复跑测试通过
- [ ] 本次无需重构（说明原因）

## 测试清单
- [ ] 后端 `mvn -q verify` 通过
- [ ] 前端 `npm run test:coverage` 通过
- [ ] 新增/修改接口已更新对应测试
```

**Step 4: Run test to verify it passes**

Run:

- `cd penmate-backend && mvn -q verify`
- `cd penmate-frontend && npm run test:coverage`

Expected:

- 本地通过
- PR 上显示 `backend-test` 与 `frontend-test` 两个检查项

**Step 5: Commit**

```bash
git add .github/workflows/tdd-quality-gate.yml .github/pull_request_template.md
git commit -m "ci: enforce tdd quality gate for backend and frontend"
```

---

### Task 4: OpenAPI P0 接口补测矩阵（novel/model/rbac/auth）

Use [test-driven-development] mode for this task.

**Files:**
- Create: `docs/project-specifications/plans/openapi-p0-test-matrix.md`
- Modify: `penmate-backend/src/test/java/com/penmate/backend/interfaces/api/novel/NovelControllerTest.java`
- Modify: `penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java`
- Modify: `penmate-backend/src/test/java/com/penmate/backend/interfaces/api/rbac/RbacQueryControllerTest.java`
- Modify: `penmate-backend/src/test/java/com/penmate/backend/interfaces/api/auth/AuthControllerTest.java`

**Step 1: Write the failing test**

以 `auth` 为示例，先新增失败用例（例如刷新 token 缺少字段返回 400）：

```java
@Test
void UT_API_AUTH_REFRESH_MISSING_REFRESH_TOKEN_BAD_REQUEST() throws Exception {
    mockMvc().perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
}
```

**Step 2: Run test to verify it fails**

Run: `cd penmate-backend && mvn -q -Dtest=AuthControllerTest test`

Expected: 新增测试失败（状态码/错误码不符合预期）。

**Step 3: Write minimal implementation**

按失败信息最小修正控制器参数校验、DTO 注解或异常映射，确保仅满足该测试。

**Step 4: Run test to verify it passes**

Run: `cd penmate-backend && mvn -q -Dtest=AuthControllerTest,ModelControllerTest,RbacQueryControllerTest,NovelControllerTest test`

Expected: 4 个 ControllerTest 全绿。

**Step 5: Commit**

```bash
git add docs/project-specifications/plans/openapi-p0-test-matrix.md penmate-backend/src/test/java/com/penmate/backend/interfaces/api/auth/AuthControllerTest.java penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java penmate-backend/src/test/java/com/penmate/backend/interfaces/api/rbac/RbacQueryControllerTest.java penmate-backend/src/test/java/com/penmate/backend/interfaces/api/novel/NovelControllerTest.java
git commit -m "test(api): add OpenAPI P0 contract tests for auth/model/rbac/novel"
```

---

### Task 5: 前端 API 模块补测（P0 对应模块）

Use [test-driven-development] mode for this task.

**Files:**
- Create: `penmate-frontend/src/api/modules/model.api.spec.ts`
- Create: `penmate-frontend/src/api/modules/rbac.api.spec.ts`
- Create: `penmate-frontend/src/api/modules/novel.api.spec.ts`

**Step 1: Write the failing test**

每个模块先写“请求路径+方法+参数”断言，确保当前行为可被锁定。

**Step 2: Run test to verify it fails**

Run: `cd penmate-frontend && npm run test:run`

Expected: 至少 1~N 个新用例失败。

**Step 3: Write minimal implementation**

在对应 `*.api.ts` 中修正 endpoint 常量、请求参数映射或错误处理。

**Step 4: Run test to verify it passes**

Run: `cd penmate-frontend && npm run test:coverage`

Expected:

- 新增 spec 全部通过
- 覆盖率达到阈值（lines/functions/statements 65%、branches 55%）

**Step 5: Commit**

```bash
git add penmate-frontend/src/api/modules/model.api.spec.ts penmate-frontend/src/api/modules/rbac.api.spec.ts penmate-frontend/src/api/modules/novel.api.spec.ts
git commit -m "test(frontend-api): add P0 module contract tests"
```

---

### Task 6: 团队制度落地文档（常态化执行）

Use [finishing-a-development-branch] mode for this task.

**Files:**
- Create: `docs/project-specifications/plans/tdd-team-working-agreement.md`

**Step 1: Write the failing test**

RED = 当前无统一团队执行约定文档，导致执行漂移。

**Step 2: Run test to verify it fails**

Run: 评审时无法统一依据（流程失败）。

**Step 3: Write minimal implementation**

文档必须包含：

1. PR 门禁规则
2. 命名规范
3. 测试分层
4. 失败测试示例流程
5. 例外审批机制（紧急修复 24h 补测）

**Step 4: Run test to verify it passes**

Run: 团队评审通过并在每个 PR 使用模板执行。

Expected: 任意 PR 都能提供 TDD 证据链。

**Step 5: Commit**

```bash
git add docs/project-specifications/plans/tdd-team-working-agreement.md
git commit -m "docs(process): add team tdd working agreement and guardrails"
```

---

## 5. 验收标准（Definition of Done）

1. 前端具备可运行测试命令，且存在 `*.spec.ts` 用例。
2. 后端 JaCoCo 阈值在 `verify` 生效。
3. PR 自动化工作流存在并执行成功。
4. OpenAPI P0 接口建立测试映射矩阵并持续更新。
5. 任一新增接口改动都可提供 RED/GREEN 证据。

---

## 6. 风险与缓解

1. **风险：**覆盖率阈值初期过高导致交付阻塞  
   **缓解：**先以 70%/55% 起步，按月提升。

2. **风险：**前端历史代码可测试性差  
   **缓解：**先测 API 模块，再逐步分离页面副作用。

3. **风险：**团队对 RED 记录执行不稳定  
   **缓解：**PR 模板 + reviewer checklist + 合并门禁强制。

---

## 7. 估时

- Task 1: 0.5 天
- Task 2: 0.5 天
- Task 3: 0.5 天
- Task 4: 2~3 天
- Task 5: 1~2 天
- Task 6: 0.5 天

**总计：约 5~7 个工作日。**

