# Story Bible and Context Epoch Full Refactor Implementation Plan

> Source of truth: `docs/superpowers/specs/2026-07-15-story-bible-context-epoch-refactor-design.md`

**Goal:** Replace the legacy Card, versioned Story Bible, full-preflight, and restart-on-resume paths with one editable Story Bible domain, immutable Context Epochs, bounded Session Working Sets, configurable selector routing, stable prompt prefixes, and exact Run recovery.

**Target worktree:** `D:\warehouse\project\PenMate\.worktrees\codex-agent-run-event-checkpoint-refactor`

**Target branch:** `codex/agent-run-event-checkpoint-refactor`

**Tech stack:** Java 21, Spring Boot 3.3, MyBatis, MySQL, Redis, S3/MinIO, Jackson, JSON Schema/JSON Patch, JUnit 5, Mockito, Vue 3, TypeScript, Vitest.

---

## Ground Rules

- Work only in the target worktree.
- Preserve and integrate all current uncommitted `skill_load` progressive-disclosure changes.
- Do not reset, revert, or overwrite user-owned worktree changes.
- Use tests before implementation for each contract boundary.
- This is a destructive development-baseline cutover. Do not create compatibility code or dual writes.
- Do not implement embeddings, vector storage, embedding jobs, or embedding UI.
- Keep the user-approved frontend routing-mode wording unchanged.
- Business IDs remain strings at the HTTP/frontend boundary.
- Story Bible current state is MySQL-authoritative.
- Epoch and Run context snapshots are immutable S3/MinIO objects referenced by MySQL and cached by Redis.
- Run resume may not call initial execution after route/context/prompt state has been persisted.
- Agent Story Bible mutations always require approval.
- Do not commit unless the user separately asks for a commit.

## Existing Dirty-Worktree Protection

Before implementation, capture and continuously preserve these current edits:

```text
application/agent/context/DefaultAgentContextRoutingFacade.java
application/agent/prompt/PromptComposer.java
application/agent/prompt/SkillPromptRegistry.java
application/agent/prompt/SkillCatalogItem.java
application/agent/tool/definition/SkillLoadToolDefinition.java
application/agent/tool/handler/SkillLoadToolHandler.java
infrastructure/agent/prompt/ClasspathSkillPromptRegistry.java
application.yml
their focused tests
legacy SkillPromptRead files currently deleted by the user change
```

Run before every large phase:

```powershell
git status --short
git diff --check
```

---

## Task 1: Freeze Baseline and Add Architecture Contract Tests

**Files:**

- Create: `penmate-backend/src/test/java/com/penmate/backend/architecture/StoryBibleContextEpochArchitectureTest.java`
- Create: `penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/storybible/StoryBibleFinalSchemaContractTest.java`
- Modify: existing migration/schema contract tests that still expect Cards or `chapter_no`
- Modify: `penmate-backend/src/test/resources/db/cases/seed_all_domain_base.sql`
- Modify/Create: focused frontend contract tests for removed Card API and new Workbench mode

### Steps

- [ ] Record `git status --short --branch` and dirty-file inventory.
- [ ] Add failing schema assertions for final tables and columns.
- [ ] Add failing forbidden-symbol assertions for:

```text
novel_cards
novel_card_relations
StoryBibleVersion
StoryBibleVersionSelector
active_version_no
version_no inside Story Bible schema/model
AgentPreflightCoordinator
AgentPreflightDecision
DefaultAgentPreflightCoordinator
skill_prompt_read
```

- [ ] Add dependency rules preventing Story Bible domain code from importing Agent Run/application types.
- [ ] Add a frontend test that fails while `card.api.ts`, `useWorkbenchCards`, and legacy card tabs are still wired.
- [ ] Run focused tests and confirm expected failure.

Verification:

```powershell
cd penmate-backend
mvn -Dtest=StoryBibleContextEpochArchitectureTest,StoryBibleFinalSchemaContractTest test

cd ../penmate-frontend
npm test -- --run src/views/Workbench/index.refactor.spec.ts
```

---

## Task 2: Rewrite the Development Database Baseline

**Files:**

- Modify: `penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql`
- Modify: `penmate-backend/src/main/resources/db/migration/V4__init_novel_volume_and_chapter.sql`
- Modify: `penmate-backend/src/main/resources/db/migration/V8__init_novel_outlines_and_cards.sql`
- Modify: `penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql`
- Create: `penmate-backend/src/main/resources/db/migration/V14__init_story_bible_domain.sql`
- Modify: all database-case seed SQL that mirrors these tables

### Schema changes

- [ ] Add `structure_revision` to `novel_projects`.
- [ ] Remove `novel_chapters.chapter_no`.
- [ ] Add `novel_chapters.sort_order` and ordering index.
- [ ] Keep `novel_volumes.sort_order`.
- [ ] Remove `novel_cards` and `novel_card_relations` from V8.
- [ ] Add nullable session routing-mode/model overrides to `agent_sessions`.
- [ ] Add `context_epoch_id` to `agent_runs`.
- [ ] Add `agent_context_epochs`.
- [ ] Add `agent_session_working_set`.
- [ ] Add user-level Agent routing preferences in the existing appropriate user/model preference baseline.
- [ ] Create V14 current-state Story Bible tables from the design spec.
- [ ] Add indexes for project/type/status, normalized aliases, category tree, relations, progression anchors, changeset retention, epoch fingerprint, and working-set eviction.
- [ ] Do not create Story Bible global-version tables.
- [ ] Do not create embedding/vector/outbox tables.
- [ ] Update test seeds and schema assertions.

Required V14 tables:

```text
story_bibles
story_bible_node_types
story_bible_nodes
story_bible_aliases
story_bible_categories
story_bible_node_categories
story_bible_tags
story_bible_node_tags
story_bible_relations
story_bible_progressions
story_bible_view_preferences
story_bible_changesets
story_bible_change_items
```

Verification:

```powershell
cd penmate-backend
mvn -Dtest=StoryBibleFinalSchemaContractTest,AgentSessionSchemaMysqlContractTest,AgentBaseSeedSqlContractTest test
```

Run forbidden DDL scan:

```powershell
rg -n "CREATE TABLE IF NOT EXISTS novel_cards|CREATE TABLE IF NOT EXISTS novel_card_relations|story_bible_versions|active_version_no|version_no" src/main/resources/db/migration src/test/resources
```

Expected: no legacy Story Bible/Card schema matches.

---

## Task 3: Refactor Manuscript Ordering and Continuous Display Numbers

**Files:**

- Modify: `domain/novel/model/NovelProject.java`
- Modify: `domain/novel/model/NovelChapter.java`
- Modify: `domain/novel/repository/NovelGateway.java`
- Modify: `application/novel/NovelApplicationService.java`
- Modify: `application/novel/command/NovelCommands.java`
- Modify: `infrastructure/persistence/novel/NovelProjectMapper.java`
- Modify: `infrastructure/persistence/novel/NovelChapterMapper.java`
- Modify: `infrastructure/persistence/novel/NovelGatewayImpl.java`
- Modify: chapter create/update DTOs and controller mapping
- Modify: frontend chapter/outline API types and composables
- Create: `application/novel/ManuscriptPositionResolver.java`
- Create: focused resolver/order tests

### Steps

- [ ] Replace all production `chapterNo` fields with `sortOrder`.
- [ ] Query chapters by volume sort, chapter sort, and stable row ID.
- [ ] Return a derived full-book continuous `displayNo`.
- [ ] Increment `structure_revision` on volume/chapter create, update-order, move, archive, or delete.
- [ ] Ensure content edits do not increment structure revision.
- [ ] Implement `ManuscriptPositionResolver` using actual ordering, never ID comparison.
- [ ] Return an explicit unresolved/deleted-anchor result for Story Bible consumers.
- [ ] Update Workbench outline drag/drop to persist chapter `sortOrder`.
- [ ] Test duplicate/gapped sort values and deterministic tie-breaking.
- [ ] Test numbering across multiple volumes and ungrouped chapters.

Verification:

```powershell
cd penmate-backend
mvn -Dtest=ManuscriptPositionResolverTest,NovelApplicationServiceTest,NovelChapterMapperDbCaseTest test

cd ../penmate-frontend
npm test -- --run src/composables/workbench/__tests__/useWorkbenchOutline.spec.ts
```

---

## Task 4: Replace the Story Bible Domain Model

**Files:**

- Replace current classes under `domain/storybible/model`
- Replace `domain/storybible/repository/StoryBibleRepository.java`
- Replace `infrastructure/persistence/storybible/StoryBibleMapper.java`
- Replace `infrastructure/persistence/storybible/StoryBibleRepositoryImpl.java`
- Delete: `StoryBibleVersion.java`
- Delete later after consumers move: `StoryBibleVersionSelector.java`
- Create focused domain/repository tests

### Domain types

- [ ] Add `StoryBible` current root with content revision.
- [ ] Add `StoryBibleSemanticFamily`.
- [ ] Add `StoryBibleNodeType`.
- [ ] Add `StoryBibleNode`.
- [ ] Add alias, category, tag, relation, progression, view-preference, changeset, and change-item models.
- [ ] Add stable enums for inclusion policy, canon status, actor type, operation, and relation semantics.
- [ ] Validate node type JSON Schema and progression JSON Patch paths.
- [ ] Keep Agent proposal types outside the Story Bible current-state model.

### Repository behavior

- [ ] Load one active Story Bible per project.
- [ ] CRUD all current-state entities with project scoping and soft-delete/archive behavior.
- [ ] Implement exact normalized-alias lookup.
- [ ] Implement lexical/full-text node search.
- [ ] Load relations in one-hop batches without N+1 queries.
- [ ] Load progressions for a set of nodes and target chapter.
- [ ] Persist one changeset and increment the root revision transactionally.
- [ ] Enforce optimistic node/relation/progression revision checks.

Verification:

```powershell
cd penmate-backend
mvn -Dtest='*StoryBible*DomainTest,*StoryBible*Repository*Test' test
```

---

## Task 5: Implement State Progression Evaluation

**Files:**

- Create: `application/storybible/StoryBibleEffectiveStateResolver.java`
- Create: `application/storybible/StoryBiblePatchValidator.java`
- Create: `application/storybible/StoryBibleConflictDetector.java`
- Create: focused tests

### Steps

- [ ] Parse RFC 6902 `add`, `replace`, and `remove` patches with a structured library/API.
- [ ] Reject unsupported operations and invalid schema paths.
- [ ] Resolve progression anchors using `ManuscriptPositionResolver`.
- [ ] Apply only progressions effective at the target chapter.
- [ ] Support optional end anchors.
- [ ] Detect same-position/same-path collisions.
- [ ] Return effective node state, applied progression IDs, unresolved-anchor flags, and conflict details.
- [ ] Keep Story Timeline event relation separate from manuscript applicability.
- [ ] Test corrections versus story changes, chapter moves, deleted anchors, and conflicts.

Verification:

```powershell
cd penmate-backend
mvn -Dtest=StoryBibleEffectiveStateResolverTest,StoryBiblePatchValidatorTest,StoryBibleConflictDetectorTest test
```

---

## Task 6: Implement Story Bible Application CRUD and Changesets

**Files:**

- Replace: `application/storybible/StoryBibleApplicationService.java`
- Replace/remove legacy initialization/proposal helpers as their responsibilities move
- Create command/query records under `application/storybible/command` and `application/storybible/query`
- Create: `StoryBibleChangesetService.java`
- Create: `StoryBibleHistoryArchiveService.java`
- Extend: `domain/shared/service/ObjectStorageService.java`
- Extend: `infrastructure/storage/S3ObjectStorageServiceImpl.java`
- Create focused service tests

### Steps

- [ ] Implement Story Bible bootstrap for a project and seed system node types/view preferences.
- [ ] Implement node type/template CRUD.
- [ ] Implement node CRUD with aliases, categories, and tags.
- [ ] Implement relation CRUD.
- [ ] Implement progression CRUD.
- [ ] Implement view preference CRUD.
- [ ] Ensure each command writes one changeset and increments content revision once.
- [ ] Record field-level before/after items.
- [ ] Extend object storage with byte upload/read so gzip archives remain binary rather than base64 text.
- [ ] Implement 180-day/5,000-changeset hot retention selection.
- [ ] Upload monthly gzip JSONL archives and verify checksums before deletion.
- [ ] Do not put history tables in current Story Bible read queries.

Verification:

```powershell
cd penmate-backend
mvn -Dtest=StoryBibleApplicationServiceTest,StoryBibleChangesetServiceTest,StoryBibleHistoryArchiveServiceTest,S3ObjectStorageServiceImplTest test
```

---

## Task 7: Add Story Bible HTTP APIs

**Files:**

- Create: `interfaces/api/storybible/StoryBibleController.java`
- Create request/response DTOs under `interfaces/api/storybible/dto`
- Modify OpenAPI/controller contract tests
- Delete legacy Card routes and DTOs from `NovelController`

### Steps

- [ ] Implement root/view endpoints.
- [ ] Implement node-type/template endpoints.
- [ ] Implement node list/detail/create/update/delete endpoints.
- [ ] Implement category/tag endpoints.
- [ ] Implement relation endpoints.
- [ ] Implement progression endpoints.
- [ ] Implement recent changeset endpoints.
- [ ] Implement lexical search endpoint.
- [ ] Validate string business IDs and project authorization.
- [ ] Return conflict responses for optimistic revisions.
- [ ] Remove Card endpoints from NovelController and delete Card DTOs.

Verification:

```powershell
cd penmate-backend
mvn -Dtest=StoryBibleControllerTest,NovelControllerTest,BusinessIdJsonPrecisionContractTest test
```

---

## Task 8: Add User Routing Preferences and Session Overrides

**Files:**

- Modify user/model preference model, repository, mapper, service, DTOs, and controller
- Modify `domain/agent/model/AgentSession.java`
- Modify `domain/agent/repository/AgentSessionRepository.java`
- Modify Agent session mapper/repository/controller DTOs
- Create: `application/agent/context/StoryBibleRoutingMode.java`
- Create: `application/agent/context/StoryBibleRoutingPreferenceResolver.java`
- Add tests

### Steps

- [ ] Persist user default `story_bible_routing_mode` and router model configuration.
- [ ] Persist nullable session overrides.
- [ ] Resolve session override -> user default -> `RETRIEVAL_THEN_LLM`.
- [ ] Validate router model ownership and availability.
- [ ] Freeze effective mode/model revision into each Run context-resolution artifact.
- [ ] Preserve approved frontend option wording.

Verification:

```powershell
cd penmate-backend
mvn -Dtest=StoryBibleRoutingPreferenceResolverTest,ModelApplicationServiceTest,AgentSessionRepositoryImplTest test
```

---

## Task 9: Implement Context Epoch Persistence and Immutable Snapshot Storage

**Files:**

- Create domain models/repositories for Context Epoch and Working Set
- Create MyBatis mappers/repository implementations
- Create: `application/agent/context/ContextEpochFingerprintService.java`
- Create: `application/agent/context/ContextEpochService.java`
- Create: `application/agent/context/ContextEpochSnapshotCodec.java`
- Create: `application/agent/context/SessionWorkingSetService.java`
- Extend Redis cache support only as a read-through optimization
- Modify `AgentRun`, Run mapper/repository, and Run input contracts
- Add tests

### Steps

- [ ] Compute fingerprint from the exact approved fields.
- [ ] Reuse current session epoch on exact fingerprint match.
- [ ] Create an immutable epoch on mismatch.
- [ ] Render core context and compact Selector Catalog.
- [ ] Store epoch snapshot JSON with `ObjectStorageService.putText`.
- [ ] Store object key, SHA-256 hash, and byte size in MySQL.
- [ ] Verify object content/hash on load.
- [ ] Add Redis read-through cache keyed by immutable epoch ID; TTL never changes business identity.
- [ ] Bind `context_epoch_id` to each Run once.
- [ ] Keep superseded epochs while recoverable Runs reference them.
- [ ] Implement Working Set promotion, pinning, scoring, 8-turn retention, and 30-node automatic cap.
- [ ] Ensure Working Set changes do not create epochs.

Verification:

```powershell
cd penmate-backend
mvn -Dtest=ContextEpochFingerprintServiceTest,ContextEpochServiceTest,ContextEpochSnapshotCodecTest,SessionWorkingSetServiceTest test
```

---

## Task 10: Replace Full Preflight with Story Bible Routing

**Files:**

- Delete package: `application/agent/orchestration/preflight`
- Delete preflight prompt assets
- Remove/replace `TaskProfileMapper` dependency on preflight
- Replace `AgentContextRoutingRequest` and current context-provider contracts
- Create: `StoryBibleRouteRequest.java`
- Create: `StoryBibleRouteDecision.java`
- Create: `StoryBibleCandidateRetriever.java`
- Create: `StoryBibleLexicalRetriever.java`
- Create: semantic retriever port/query/result plus Noop implementation
- Create: `StoryBibleSelectorGateway.java`
- Create: `DefaultStoryBibleSelectorGateway.java`
- Create context-selector prompt assets
- Create: `StoryBibleContextResolver.java`
- Create tests

### Steps

- [ ] Remove all full-preflight execution and schema fields.
- [ ] Derive execution profile from explicit task type and focused route decision.
- [ ] Implement exact alias and lexical/full-text candidates.
- [ ] Implement optional no-op semantic port with zero external calls.
- [ ] Implement `RETRIEVAL` without selector LLM.
- [ ] Implement `LLM_SELECTOR` with the epoch compact full catalog.
- [ ] Implement `RETRIEVAL_THEN_LLM` with dynamic candidate summaries.
- [ ] Force structured selector output and validate selected IDs.
- [ ] Apply relation expansion, effective-state resolution, conflict handling, and context budget.
- [ ] Separate current-Run selection from Working Set promotion.
- [ ] Record selector usage, latency, selection reasons, and missing flags.

Verification:

```powershell
cd penmate-backend
mvn -Dtest=StoryBibleLexicalRetrieverTest,NoopStoryBibleSemanticRetrieverTest,DefaultStoryBibleSelectorGatewayTest,StoryBibleContextResolverTest test
```

Forbidden production scan:

```powershell
rg -n "AgentPreflight|DefaultAgentPreflightCoordinator|submit_preflight_decision|includeStoryBibleContext" src/main
```

Expected: no matches.

---

## Task 11: Rebuild Prompt Plans and Stable Cache Prefixes

**Files:**

- Modify current dirty `PromptComposer.java` without losing Skill Catalog work
- Modify `AgentPromptAssembler.java` or replace it with an explicit message-plan assembler
- Modify `SkillPromptRegistry`, `SkillCatalogItem`, and Classpath registry only as required
- Modify tool catalog ordering
- Add prompt manifest/hash types
- Add tests

### Steps

- [ ] Preserve stable, sorted Skill Catalog names and descriptions.
- [ ] Preserve `skill_load` dynamic full-instruction behavior.
- [ ] Sort Tool Schemas deterministically.
- [ ] Build execution stable prefix in approved order.
- [ ] Put history, Working Set, selected Story Bible content, and user request after the stable boundary.
- [ ] Build separate selector prompts for `LLM_SELECTOR` and `RETRIEVAL_THEN_LLM`.
- [ ] Persist prompt manifest hashes and assembled prompt plan in the Run artifact/checkpoint path.
- [ ] Ensure the execution model receives selected node bodies, never the Selector Catalog.
- [ ] Add provider usage parsing for cached-token metadata where present.

Verification:

```powershell
cd penmate-backend
mvn -Dtest=PromptComposerTest,AgentPromptAssemblerTest,ClasspathSkillPromptRegistryTest,SkillLoadToolHandlerTest,SkillLoadToolDefinitionTest test
```

---

## Task 12: Integrate Context Epochs into Agent Run and Fix Resume

**Files:**

- Refactor: `application/agent/run/AgentRunExecutor.java`
- Modify: `AgentRuntimeState.java`
- Modify: `AgentRuntimeStateReducer.java`
- Modify: `AgentCheckpointService.java`
- Modify Run loop request/result as needed
- Modify Run repository/artifact/event/projection code
- Add context artifact codec and repository service
- Add tests

### Steps

- [ ] Split initial execution from resume execution.
- [ ] Add explicit phases: epoch binding, routing, context resolution, prompt composition, execution.
- [ ] Publish durable events:

```text
context.epoch.bound
turn.route.completed
context.resolved
prompt.composed
```

- [ ] Store resolved context as an immutable Run artifact in S3/MinIO.
- [ ] Store selected IDs, progression IDs, rendered content, hashes, trace, and Working Set snapshot.
- [ ] Extend checkpoint state with context/prompt artifact references and phase completion.
- [ ] Resume from the latest checkpoint and replay later events.
- [ ] Resume tool approval from saved LLM messages/tool-call state.
- [ ] Do not rebuild mutable context on resume.
- [ ] Promote Working Set entries idempotently after durable context resolution.
- [ ] Test Story Bible edits between wait and resume.
- [ ] Test process restart, Redis cache miss, MySQL checkpoint fallback, and S3 artifact load.

Verification:

```powershell
cd penmate-backend
mvn -Dtest=AgentRunExecutorTest,AgentCheckpointServiceTest,AgentRuntimeStateReducerContractTest,AgentRunRecoveryAppServiceTest test
```

---

## Task 13: Replace Story Bible Agent Tools and Approval Payloads

**Files:**

- Replace `StoryBibleUpdateToolDefinition.java`
- Replace `StoryBibleUpdateToolHandler.java`
- Replace `StoryBibleUpdateApplicationService` and default implementation
- Create: `StoryBibleSearchToolDefinition.java`
- Create: `StoryBibleSearchToolHandler.java`
- Modify approval view/payload code
- Add tests

### Steps

- [ ] Expose read-only `story_bible_search` without approval.
- [ ] Scope search to the Run's bound epoch/target chapter.
- [ ] Return node IDs, titles, effective states, source progression IDs, and citations.
- [ ] Add used search nodes to Run trace and eligible Working Set promotion.
- [ ] Define mutation operations for node, relation, and progression commands.
- [ ] Require approval for every mutation operation.
- [ ] Support one batch approval with ordered operations and before/after diffs.
- [ ] Revalidate optimistic revisions when approval executes.
- [ ] Invoke the same Story Bible application commands as direct user CRUD.
- [ ] Emit durable approval/tool terminal events and checkpoint before waiting.

Verification:

```powershell
cd penmate-backend
mvn -Dtest=StoryBibleSearchToolHandlerTest,StoryBibleUpdateToolHandlerTest,ToolCallApplicationServiceTest,ApprovalApplicationServiceRunFlowTest test
```

---

## Task 14: Add Frontend Story Bible API and State Layer

**Files:**

- Create: `penmate-frontend/src/api/modules/storyBible.api.ts`
- Create: `penmate-frontend/src/api/modules/storyBible.api.spec.ts`
- Extend shared API types
- Create: `composables/workbench/useStoryBible.ts`
- Create focused composable tests
- Delete: `api/modules/card.api.ts`
- Delete: `composables/workbench/useWorkbenchCards.ts` and its tests

### Steps

- [ ] Add typed root/view/type/node/category/tag/relation/progression/history/search APIs.
- [ ] Keep all business IDs as strings.
- [ ] Add optimistic revision fields to update commands.
- [ ] Add Story Bible view state, selection, filters, drafts, save/delete, relations, progressions, and history loading.
- [ ] Reflect API conflicts without silently replacing local drafts.
- [ ] Update state after approved Agent mutations and Run events.
- [ ] Delete Card API/state wiring.

Verification:

```powershell
cd penmate-frontend
npm test -- --run src/api/modules/storyBible.api.spec.ts src/composables/workbench/__tests__/useStoryBible.spec.ts
```

---

## Task 15: Build the Story Bible Workbench Mode

**Files:**

- Modify: `views/Workbench/index.vue`
- Modify: `WorkbenchHeader.vue`
- Refactor: `WorkbenchLeftPanel.vue`
- Create components under `components/workbench/story-bible/`
- Modify Story Bible approval card/navigation integration
- Modify routing/session settings UI
- Delete legacy components under `components/workbench/cards/`
- Update visual and component tests

### Required components

```text
StoryBibleWorkspace.vue
StoryBibleNavigator.vue
StoryBibleNodeList.vue
StoryBibleNodeEditor.vue
StoryBibleBaseTab.vue
StoryBibleRelationsTab.vue
StoryBibleProgressionsTab.vue
StoryBibleHistoryTab.vue
StoryBibleTypeEditor.vue
StoryBibleCategoryTree.vue
StoryBibleSearchToolbar.vue
StoryBibleRoutingSettings.vue
```

### Steps

- [ ] Add a top-level Writing/Story Bible segmented mode control.
- [ ] Keep Agent chat in the right panel for both modes.
- [ ] Replace left Card tabs with Story Bible family/category/tag navigation in Story Bible mode.
- [ ] Implement system-view rename/reorder/hide controls.
- [ ] Implement custom type/template and category CRUD.
- [ ] Implement structured node editor using field schema.
- [ ] Implement relation management.
- [ ] Implement base-setting versus state-progression edit actions.
- [ ] Implement chapter-scoped effective-state preview.
- [ ] Implement recent change history and recovery action.
- [ ] Preserve the user-approved routing option wording while persisting the new enums.
- [ ] Add Session override with inheritance from user preferences.
- [ ] Allow approval diffs to navigate to `mode=story-bible&nodeId=...`.
- [ ] Remove all character/world card components and imports.
- [ ] Add responsive constraints and mobile drawers; verify no overlap.

Verification:

```powershell
cd penmate-frontend
npm test -- --run src/components/workbench/story-bible src/views/Workbench/index.refactor.spec.ts src/components/workbench/workbench-visual-contract.spec.ts
npm run build
```

---

## Task 16: Remove Legacy Production Paths and Tests

### Delete backend legacy paths

- [ ] Novel Card model, mapper, gateway methods, commands, DTOs, controller routes, and tests.
- [ ] Legacy Story Bible version models/selectors/fields and old provider contracts.
- [ ] Full preflight classes, prompt assets, tests, and TaskProfile mapping.
- [ ] Any restart-on-resume helper that recreates route/context/prompt state.

### Delete frontend legacy paths

- [ ] Card API.
- [ ] Workbench Card composable.
- [ ] CharacterCard and WorldCard components/tests.
- [ ] CardRelationPanel and tests.
- [ ] stale Card types, fixtures, imports, handlers, and CSS.

### Production scans

```powershell
rg -n "novel_cards|novel_card_relations|NovelCard|CardRelationPanel|CharacterCard|WorldCard|useWorkbenchCards|cardApi" penmate-backend/src/main penmate-frontend/src penmate-backend/src/main/resources/db/migration

rg -n "StoryBibleVersion|StoryBibleVersionSelector|activeVersionNo|versionNo.*StoryBible|story_bible_versions" penmate-backend/src/main penmate-frontend/src penmate-backend/src/main/resources/db/migration

rg -n "AgentPreflight|DefaultAgentPreflightCoordinator|submit_preflight_decision|run_preflight" penmate-backend/src/main penmate-frontend/src
```

Expected: no legacy production matches. Test fixtures may mention forbidden names only when the test explicitly asserts absence.

---

## Task 17: Full Verification and Documentation Sync

**Files:**

- Modify: `docs/plans/agent-checkpoint-tech-design.md`
- Create: `docs/analysis/2026-07-15-story-bible-context-epoch-refactor-result.md`
- Update API/schema documentation generated by the repository's normal workflow if applicable

### Backend verification

- [ ] Run focused suites after each task.
- [ ] Run full backend tests.
- [ ] Run package/architecture tests.
- [ ] Run migration/seed database-case tests.
- [ ] Run formatting/checkstyle if configured.

```powershell
cd penmate-backend
mvn test
```

### Frontend verification

- [ ] Run all Vitest tests.
- [ ] Run production build.
- [ ] Start a dev server on an available port.
- [ ] Exercise Writing and Story Bible modes.
- [ ] Exercise node CRUD, relation CRUD, progression preview, routing settings, approval navigation, and mobile drawers.
- [ ] Capture desktop and mobile screenshots with Playwright if available.
- [ ] Confirm no text/button overflow and no panel overlap.

```powershell
cd penmate-frontend
npm test -- --run
npm run build
npm run dev -- --host 127.0.0.1
```

### End-to-end acceptance scenarios

- [ ] Create Story Bible, custom type, category, character node, alias, relation, and progression.
- [ ] Verify full-book continuous chapter display numbers after cross-volume movement.
- [ ] Create Session and first Run; verify epoch creation.
- [ ] Create second Run without fingerprint changes; verify epoch reuse.
- [ ] Change Working Set; verify epoch reuse.
- [ ] Edit Story Bible; verify next Run creates a new epoch.
- [ ] Pause a Run for Story Bible mutation approval; edit current Story Bible separately; approve/resume; verify the old Run resumes from its immutable artifact.
- [ ] Verify all Agent Story Bible writes require approval.
- [ ] Verify direct user CRUD does not require approval.
- [ ] Verify `RETRIEVAL`, `LLM_SELECTOR`, and `RETRIEVAL_THEN_LLM` snapshots.
- [ ] Verify semantic retriever reports unavailable and makes no external call.
- [ ] Verify Skill Catalog remains in prompt prefix and `skill_load` still works.
- [ ] Verify S3/Redis cache miss reloads the same immutable epoch.

### Final worktree checks

```powershell
git status --short
git diff --check
git diff --stat
```

Review every changed dirty-overlap file against the pre-implementation diff to confirm the progressive skill-disclosure work remains intact.

---

## Completion Definition

The refactor is complete only when:

- all design-spec acceptance criteria pass
- all legacy production paths are removed
- fresh baseline migrations and seeds pass
- backend full tests pass
- frontend full tests and production build pass
- desktop/mobile visual checks pass
- Run approval recovery never reruns route/context/prompt phases
- no embedding implementation or hidden external call exists
- the user can open the provided dev-server URL and use the completed Story Bible workflow

## Implementation Order Rationale

The order is deliberate:

1. failing architecture/schema tests prevent accidental compatibility drift
2. final DDL establishes ownership boundaries
3. manuscript ordering is fixed before progression evaluation
4. Story Bible current state exists before APIs or Agent routing consume it
5. Context Epoch and routing are built before Run integration
6. Run recovery is fixed before frontend workflows rely on it
7. frontend replaces Cards only after new APIs exist
8. legacy deletion and full scans prove that the cutover is complete

Do not reorder these phases in a way that leaves both legacy Cards and Story Bible writable at the same time.
