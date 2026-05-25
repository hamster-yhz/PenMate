# Execution Message Layering Design

## Background

The current execution-stage prompt assembly mixes dynamic context and the real user request into the same current-turn `AgentLlmMessage.user(...)`.

Current shape:

1. `system`: execution base prompt
2. `history`: recent `user/assistant`
3. `user`: `style + story_bible + conflict + missing + rag + user_request`

This makes the final current-turn user message carry both instruction payload and context payload, which weakens the separation between:

- stable execution rules
- dynamic execution context
- real user intent for the current turn

The `preflight` stage already uses structured blocks correctly and is not part of this change.

## Goal

Change only the execution-stage final message assembly so that the current turn still ends with exactly one new `AgentLlmMessage.user(...)`, but that user message contains only the real current-turn user request.

## Non-Goals

- Do not change `preflight` message structure.
- Do not change provider transport shape such as `messages[{role, content}]`.
- Do not change history retrieval or history formatting.
- Do not change recovery or tool-resume storage contract.
- Do not redesign `PromptPlan` into a new multi-part prompt model.

## Final Message Contract

Execution-stage final messages must be assembled in this order:

1. `system#1`: existing execution base prompt
2. `system#2`: execution context blocks, optional
3. `history`: recent `user/assistant` messages from conversation window
4. `user`: current-turn real user request only

### `system#1`

`system#1` keeps the current source of truth unchanged:

- task/taskContext path: `SystemPromptBundle.assembledPrompt()`
- promptPlan/contextPackage path: `PromptPlan.assembledPromptPreview()`

This design does not change how the execution base prompt is loaded or composed.

### `system#2`

`system#2` is a new execution-context system message that contains only dynamic context blocks.

Allowed block order is fixed:

1. `style`
2. `story_bible`
3. `conflict`
4. `missing`
5. `rag`

Allowed block forms:

- `<context type="style">...</context>`
- `<context type="story_bible">...</context>`
- `<context type="conflict">...</context>`
- `<context type="missing">...</context>`
- `<context type="rag">...</context>`

If all five context categories are empty, `system#2` must be omitted entirely.

### Current-turn `user`

The current turn must still produce exactly one new `AgentLlmMessage.user(...)`.

Its content must contain only:

- `<user_request>...</user_request>`

It must not contain:

- `style`
- `story_bible`
- `conflict`
- `missing`
- `rag`

This is the key invariant of the change.

## Context Block Semantics

This change keeps existing semantics and only moves their placement.

### `conflict`

`conflict` means Story Bible conflict detected by the backend context builder, not model disagreement.

Current emitted form is derived from values such as:

- `story_bible_conflict:<entryKey>`

This block remains useful because it explicitly tells the model that some canon-related inputs are in conflict and should not be treated as a single clean fact set.

### `missing`

`missing` means required Story Bible context was expected for this task but no active matching Story Bible entries were found.

Current emitted form is derived from values such as:

- `story_bible_missing`

This block remains useful because it tells the model that missing canon context is a known absence rather than silent omission.

### `rag`

`rag` means retrieval evidence selected by the backend context routing and builder.

This block remains useful because it carries external or retrieved supporting context and already matches standard retrieval-augmented generation practice.

## Scope of Code Change

Only execution-stage `AgentPromptAssembler` behavior should change.

### In Scope

- Move `style / story_bible / conflict / missing / rag` out of current-turn user content.
- Build a new optional `system#2` from those blocks.
- Keep the final current-turn `user` message to `<user_request>` only.
- Preserve existing structured block escaping and formatting behavior.

### Out of Scope

- `DefaultAgentPreflightCoordinator`
- provider clients
- `ConversationWindowBuilder`
- context routing semantics
- persistence schema
- tool snapshot message codec

## Recommended Implementation Shape

Apply the smallest possible change inside `AgentPromptAssembler`.

Recommended shape:

1. Add a small internal helper that assembles execution context blocks into one optional string.
2. Reuse `StructuredPromptBlockFormatter` for every block.
3. Insert the helper result as `AgentLlmMessage.system(...)` only when non-blank.
4. Reduce current-turn user assembly to one block: `user_request`.

No new top-level component is required for this change.

## Affected Paths

Primary:

- `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java`

Tests likely affected:

- `penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssemblerTest.java`

Potentially touched only if assertions depend on exact message ordering or count:

- execution workflow tests that inspect assembled message lists indirectly

## Test Strategy

Add or update tests to lock down the new contract.

### Required Cases

1. When context exists:
   - first message is `system#1`
   - second message is `system#2`
   - last new message is a single `user`
   - that final `user` contains only `<user_request>`

2. When context is fully empty:
   - `system#2` is omitted
   - final current-turn `user` still contains only `<user_request>`

3. When history exists:
   - `system#1`
   - optional `system#2`
   - history messages
   - final current-turn `user`

4. `preflight` tests remain unchanged:
   - execution-stage layering change must not alter `preflight`

### Regression Conditions

The following must never reappear after this change:

- current-turn `user` containing `<context type="style">`
- current-turn `user` containing `<context type="story_bible">`
- current-turn `user` containing `<context type="conflict">`
- current-turn `user` containing `<context type="missing">`
- current-turn `user` containing `<context type="rag">`

## Acceptance Criteria

- Execution-stage current turn adds exactly one new `AgentLlmMessage.user(...)`.
- That user message contains only `<user_request>...</user_request>`.
- Dynamic execution context moves to an optional `system#2`.
- `system#2` ordering is fixed as `style -> story_bible -> conflict -> missing -> rag`.
- Empty execution context does not produce an empty `system#2`.
- `preflight` behavior remains unchanged.
- Provider payload shape remains unchanged.

## Risks

- Tests may implicitly assume old message indexes or message counts.
- Recovery or tool-loop tests may need assertion updates if they inspect exact execution message ordering.

These are contract-adjustment risks, not architecture risks.
