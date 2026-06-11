# Agent Todo Redis Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Agent Todo from MySQL/API/planner output to a Redis-backed session-scoped runtime state updated only through Agent tools.

**Architecture:** Keep the DDD repository boundary, replace the MyBatis implementation with a `StringRedisTemplate` JSON document per `(projectId, sessionId)`, and refresh a 30 minute TTL on every read/write. Remove REST Todo API, SQL migration, `todo_planner`, and preflight Todo seeding; keep a single `todo_crud` Agent tool with task operations and state-machine validation.

**Tech Stack:** Spring Boot, Jackson, Spring Data Redis, JUnit 5, Mockito.

---

### Task 1: Redis Todo Repository

**Files:**
- Modify: `penmate-backend/src/main/java/com/penmate/backend/domain/todo/model/SessionTodo.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/domain/todo/repository/SessionTodoRepository.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/todo/SessionTodoMapper.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/todo/SessionTodoRepositoryImpl.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/todo/SessionTodoRedisRepositoryTest.java`

- [ ] Write failing tests proving one Redis key stores a session document and read/write refreshes 30 minute TTL.
- [ ] Implement repository JSON serialization with key `agent:session:{projectId}:{sessionId}:todo`.
- [ ] Run `mvn -Dtest=SessionTodoRedisRepositoryTest test`.

### Task 2: Todo Application State Machine

**Files:**
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoCrudApplicationService.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/todo/TodoCrudApplicationServiceTest.java`

- [ ] Write failing tests for allowed states, one `in_progress` item per session, `blockedReason`, `errorSummary`, and `completed` summary/completedAt.
- [ ] Implement create/update/list/get/reorder/delete against the Redis repository.
- [ ] Run `mvn -Dtest=TodoCrudApplicationServiceTest test`.

### Task 3: Agent Tool Contract

**Files:**
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/TodoCrudToolDefinition.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/TodoCrudToolHandler.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/TodoPlannerToolDefinition.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/TodoPlannerToolHandler.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/handler/TodoCrudToolHandlerTest.java`

- [ ] Write failing tests for `create`, `update`, `list`, `get`, `reorder`, `delete`.
- [ ] Update `todo_crud` schema and handler output to expose lightweight Todo task JSON.
- [ ] Run `mvn -Dtest=TodoCrudToolHandlerTest test`.

### Task 4: Remove Planner/SQL/API Wiring

**Files:**
- Delete: `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/todo/TodoController.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/todo/dto/CreateTodoDto.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/todo/dto/UpdateTodoDto.java`
- Delete: `penmate-backend/src/main/resources/db/migration/V13__init_agent_session_todos.sql`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java`
- Delete/update planner-specific tests.

- [ ] Write/adjust tests proving no preflight Todo snapshot is seeded and no planner tool is registered.
- [ ] Remove `todo_planner` extraction, persistence, display name, and phase seeding.
- [ ] Run orchestration/tool-definition focused tests.

### Task 5: Verification

- [ ] Run backend focused tests for todo, tool definition, orchestration, and OpenAI-compatible tool schema.
- [ ] Search for removed symbols: `todo_planner`, `TodoPlanner`, `TodoController`, `SessionTodoMapper`, `agent_session_todos`.
- [ ] Report remaining intentional frontend test fixtures if they are not production code.
