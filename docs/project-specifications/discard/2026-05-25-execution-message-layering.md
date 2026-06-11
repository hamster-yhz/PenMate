# Execution Message Layering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change execution-stage prompt assembly so the current turn still adds exactly one new `AgentLlmMessage.user(...)`, but that user message contains only `<user_request>...</user_request>` and all execution context moves into an optional `system#2`.

**Architecture:** Keep `preflight`, provider payloads, history retrieval, and recovery contracts unchanged. Limit the production code change to `AgentPromptAssembler`, introducing a small internal helper that assembles execution context blocks into an optional second system message with a fixed block order.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, AssertJ, Mockito, Maven

---

### Task 1: Lock The New Execution Message Contract In Tests

**Files:**
- Modify: `penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssemblerTest.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssemblerTest.java`

- [ ] **Step 1: Rewrite the existing execution-message assertions to expect an optional second system message and a user-only final current-turn message**

```java
assertThat(messages).hasSize(3);
assertThat(messages.get(0).role()).isEqualTo(AgentLlmMessageRole.SYSTEM);
assertThat(messages.get(0).content()).isEqualTo("你是执行代理");
assertThat(messages.get(1).role()).isEqualTo(AgentLlmMessageRole.SYSTEM);
assertThat(messages.get(1).content())
        .contains("<context type=\"style\">")
        .contains("<context type=\"story_bible\">")
        .contains("<context type=\"rag\">");
assertThat(messages.get(2).role()).isEqualTo(AgentLlmMessageRole.USER);
assertThat(messages.get(2).content()).isEqualTo("<user_request>\n请续写主角夜访城门后的场景\n</user_request>");
```

- [ ] **Step 2: Add regression assertions that the final current-turn user message never contains context blocks**

```java
assertThat(messages.get(2).content())
        .doesNotContain("<context type=\"style\">")
        .doesNotContain("<context type=\"story_bible\">")
        .doesNotContain("<context type=\"conflict\">")
        .doesNotContain("<context type=\"missing\">")
        .doesNotContain("<context type=\"rag\">");
```

- [ ] **Step 3: Add an explicit empty-context case that proves `system#2` is omitted**

```java
List<AgentLlmMessage> messages = agentPromptAssembler.buildExecutionMessages(
        task,
        null,
        List.of(),
        "default",
        null,
        List.of()
);

assertThat(messages).hasSize(2);
assertThat(messages.get(0).role()).isEqualTo(AgentLlmMessageRole.SYSTEM);
assertThat(messages.get(1).role()).isEqualTo(AgentLlmMessageRole.USER);
assertThat(messages.get(1).content()).isEqualTo("<user_request>\n仅保留结构化请求块\n</user_request>");
```

- [ ] **Step 4: Update the history-window test to expect `system#1 -> system#2 -> history -> final user`**

```java
assertThat(messages).hasSize(5);
assertThat(messages.get(0).role()).isEqualTo(AgentLlmMessageRole.SYSTEM);
assertThat(messages.get(1).role()).isEqualTo(AgentLlmMessageRole.SYSTEM);
assertThat(messages.get(2).content()).isEqualTo("上一轮提问");
assertThat(messages.get(3).content()).isEqualTo("上一轮回答");
assertThat(messages.get(4).content()).isEqualTo("<user_request>\n核对冲突后继续写作\n</user_request>");
```

- [ ] **Step 5: Run the focused test class and confirm it fails for the right reason before changing production code**

Run: `mvn -pl penmate-backend -Dtest=AgentPromptAssemblerTest test`

Expected: FAIL because the current implementation still writes `style / story_bible / conflict / missing / rag` into the current-turn `user` message and does not emit `system#2`.

### Task 2: Move Execution Context Into Optional `system#2`

**Files:**
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssemblerTest.java`

- [ ] **Step 1: Add a helper that assembles execution context blocks in the fixed order `style -> story_bible -> conflict -> missing -> rag`**

```java
private String buildExecutionContextSystemMessage(String style,
                                                  List<String> storyBibleEntries,
                                                  List<String> conflicts,
                                                  List<String> missingFlags,
                                                  List<String> ragRefs) {
    StringJoiner contextBuilder = new StringJoiner("\n\n");
    if (style != null && !style.isBlank()) {
        contextBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"style\"", style));
    }
    if (storyBibleEntries != null && !storyBibleEntries.isEmpty()) {
        contextBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"story_bible\"", String.join("\n", storyBibleEntries)));
    }
    if (conflicts != null && !conflicts.isEmpty()) {
        contextBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"conflict\"", String.join("\n", conflicts)));
    }
    if (missingFlags != null && !missingFlags.isEmpty()) {
        contextBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"missing\"", String.join("\n", missingFlags)));
    }
    if (ragRefs != null && !ragRefs.isEmpty()) {
        contextBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"rag\"", String.join("\n", ragRefs)));
    }
    return contextBuilder.toString();
}
```

- [ ] **Step 2: Change both execution-message assembly paths so the current-turn user builder contains only the `user_request` block**

```java
String userRequestBlock = structuredPromptBlockFormatter.wrapBlock("user_request", userRequest == null ? "" : userRequest.trim());

result.add(AgentLlmMessage.system(promptPlan == null ? "" : promptPlan.assembledPromptPreview()));
if (!contextSystemMessage.isBlank()) {
    result.add(AgentLlmMessage.system(contextSystemMessage));
}
if (conversationWindow != null && !conversationWindow.isEmpty()) {
    result.addAll(conversationWindow);
}
result.add(AgentLlmMessage.user(userRequestBlock));
```

- [ ] **Step 3: Keep the task/taskContext path aligned with the promptPlan/contextPackage path**

```java
String contextSystemMessage = buildExecutionContextSystemMessage(
        style,
        storyBible.isEmpty() ? List.of() : List.of(storyBible),
        List.of(),
        List.of(),
        ragRefs
);
```

- [ ] **Step 4: Run the focused test class again and confirm it passes**

Run: `mvn -pl penmate-backend -Dtest=AgentPromptAssemblerTest test`

Expected: PASS

### Task 3: Verify No Regression In Adjacent Execution Flow Tests

**Files:**
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java`

- [ ] **Step 1: Run the adjacent workflow test class that exercises the execution-stage assembler integration**

Run: `mvn -pl penmate-backend -Dtest=AgentGenerationWorkflowTest test`

Expected: PASS, or clearly scoped failures if any test asserts the old message order or count.

- [ ] **Step 2: If workflow assertions fail due to old message ordering assumptions, update only those assertions to match the new `system#1 -> optional system#2 -> history -> final user` contract**

```java
assertThat(messages.get(0).role()).isEqualTo(AgentLlmMessageRole.SYSTEM);
assertThat(messages.get(messages.size() - 1).role()).isEqualTo(AgentLlmMessageRole.USER);
assertThat(messages.get(messages.size() - 1).content()).contains("<user_request>");
```

- [ ] **Step 3: Run both relevant test classes together as the final feature verification**

Run: `mvn -pl penmate-backend -Dtest=AgentPromptAssemblerTest,AgentGenerationWorkflowTest test`

Expected: PASS with 0 failures.

- [ ] **Step 4: Commit the implementation branch changes after verification passes**

```bash
git add penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssemblerTest.java docs/superpowers/plans/2026-05-25-execution-message-layering.md
git commit -m "feat(agent): separate execution context from current user turn"
```
