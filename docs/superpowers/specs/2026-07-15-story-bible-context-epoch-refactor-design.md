# Story Bible and Context Epoch Full Refactor Design

## Status

Approved for implementation on 2026-07-15.

Target worktree:

`D:\warehouse\project\PenMate\.worktrees\codex-agent-run-event-checkpoint-refactor`

Target branch:

`codex/agent-run-event-checkpoint-refactor`

This document is the source of truth for the Story Bible, context routing, prompt caching, and Agent Run integration refactor. If an older plan or implementation disagrees with this document, this document wins.

## Background

The current implementation has several overlapping models and lifecycle problems:

- `novel_cards` and `novel_card_relations` own character/world facts while the Story Bible domain also owns character, world, plot, item, faction, and rule entries.
- Story Bible entries are flat and globally versioned.
- chapter validity is evaluated by comparing business IDs, although a business ID does not represent manuscript order.
- every Agent Run executes a large LLM preflight decision before context routing.
- context is resolved again when an approval resumes because `AgentRunExecutor.resume()` restarts execution.
- Story Bible content is not exposed as a complete editable frontend workspace.
- the prompt does not have an explicit stable-prefix contract shared by execution prompts, tool schemas, the progressively disclosed skill catalog, and stable Story Bible core context.
- there is no durable session-level Context Epoch or bounded Working Set.

The goal is not to add another compatibility layer. The goal is to replace the overlapping models with one coherent design.

## Goals

1. Make Story Bible the only source of truth for characters, worldbuilding, narrative facts, relationships, and continuity knowledge.
2. Give users a complete Story Bible workspace with CRUD, custom structure, relations, story-state evolution, and change history.
3. Replace global Story Bible versions with one current state, a technical content revision, and an append-only change audit.
4. Initialize stable session context once per Context Epoch and reuse it across multiple Runs.
5. Resolve per-turn Story Bible context through configurable retrieval/LLM selector strategies.
6. Preserve exact Run context across approvals, process restarts, and later Story Bible edits.
7. Define prompt layers so stable prefixes are cacheable while Working Set and turn context remain dynamic.
8. Remove the existing full preflight model call and replace it with deterministic epoch initialization plus a focused context selector/router.
9. Keep the current progressive skill-disclosure work and place the Skill Catalog in the execution cache prefix.
10. Perform a destructive development-baseline cutover with no legacy tables, APIs, DTOs, dual writes, or compatibility fallbacks.

## Non-Goals

- Do not implement an embedding provider.
- Do not create or update vector indexes.
- Do not implement embedding outbox jobs, vector-index lag UI, or embedding configuration.
- Do not migrate current local development data from legacy card or Story Bible tables.
- Do not keep the legacy preflight contract.
- Do not keep `novel_cards`, `novel_card_relations`, or their frontend/API compatibility paths.
- Do not turn the Story Bible into an event-sourced aggregate. The current relational state remains authoritative.
- Do not put the Session Working Set into the Context Epoch fingerprint.
- Do not make Redis TTL a business invalidation rule.

## Terminology

### Story Bible

The project-scoped, user-editable source of truth for story knowledge. It contains current nodes, types, categories, aliases, tags, relations, and state progressions.

### Content Revision

A monotonically increasing technical revision on the Story Bible root. It is used for optimistic consistency, cache invalidation, and Context Epoch fingerprints. It is not a user-selectable Story Bible version.

### Changeset

An audit record describing who changed current Story Bible data, when, and which fields changed. Changesets support recent history, recovery, and cold archival. They do not describe in-story time.

### State Progression

A chapter-anchored JSON Patch attached to a Story Bible node. It describes a change that becomes true during the story, such as a character learning a secret or an item being destroyed. It is canonical Story Bible content, not an audit record.

### Manuscript Scope

The chapter-order range in which a fact or progression is effective for writing and context injection. It prevents future-state spoilers when generating earlier chapters.

### Story Timeline

The fictional chronology of events inside the story world. It is represented by event nodes and relations. Story Timeline and Manuscript Scope are separate axes.

### Context Epoch

An immutable session-scoped context snapshot identified by an explicit fingerprint. One Context Epoch can be reused by many Runs. A Run always binds exactly one epoch.

### Session Working Set

A bounded, mutable collection of recently relevant Story Bible node references for a session. It improves conversational continuity but is not part of the immutable Context Epoch.

### Selector Catalog

A compact representation of Story Bible nodes used only by `LLM_SELECTOR`: node ID, semantic family, node type, title, aliases, summary, key relations, and current chapter-state summary. It is not the context sent to the execution model.

## Core Invariants

1. A project has one current Story Bible.
2. Users never switch between global Story Bible versions.
3. Every Story Bible mutation increments `content_revision` exactly once per changeset.
4. Agent-originated Story Bible writes always require explicit approval.
5. User-originated Story Bible CRUD is applied immediately after validation.
6. A Run never observes two Context Epochs.
7. A resumed Run never reruns routing, selection, or prompt composition when a resolved context artifact exists.
8. A Run must not read mutable Story Bible content to reconstruct an already resolved prompt.
9. Working Set updates never invalidate a Context Epoch.
10. Business IDs must never be compared to infer chapter order.
11. Story Bible node IDs selected by an LLM must be validated against the bound epoch catalog.
12. Embedding is an optional port and is unavailable in the initial implementation.
13. Full skill instructions are loaded dynamically through `skill_load`; the stable Skill Catalog remains in the execution prefix.
14. Legacy Card and full-preflight code must not remain in production sources.

## Domain Ownership

### Story Bible owns

- story premise, themes, genre contracts, and core conflict
- characters and character arcs
- locations, organizations, factions, species, cultures, religions, languages, systems, and lore
- items, abilities, technologies, terms, and concepts
- plotlines, subplots, mysteries, foreshadowing, promises, and open threads
- events, established facts, continuity constraints, and conflicts
- aliases, tags, categories, relations, and state progressions

### Novel owns

- project metadata
- volumes
- chapters and manuscript content
- outline nodes
- chapter content versions
- manuscript ordering

Story Bible may reference Novel IDs but does not copy outline or chapter content as authoritative data.

### Style owns

- prose style profiles
- session style binding

Style is part of a Context Epoch fingerprint but is not stored as a Story Bible node.

### Agent Run owns

- Run input
- route decision
- resolved context artifact
- prompt plan snapshot
- tool loop state
- approval state
- events, checkpoints, projections, and result artifacts

## Story Bible Classification

Classification has three independent layers.

### Semantic Family

Stable system semantics used by retrieval, routing, and default frontend views:

| Code | Default display | Purpose |
|---|---|---|
| `CORE` | Story Core | premise, theme, genre contract, core conflict |
| `CHARACTER` | Characters | characters and character arcs |
| `WORLD` | World | locations, organizations, factions, species, cultures, religions, languages, systems |
| `THING` | Things | items, abilities, technologies, terms, concepts |
| `NARRATIVE` | Narrative | plotlines, subplots, mysteries, foreshadowing, promises, open threads |
| `TIMELINE` | Timeline | events, facts, continuity constraints, conflicts |

Semantic-family codes are stable. Frontend labels may be renamed, reordered, or hidden without changing their meaning.

### Node Type / Template

Node types provide detailed structure. They may be system-defined or project-defined. Every node type maps to exactly one Semantic Family and owns a JSON Schema for `attributes_json`.

Examples:

- `CHARACTER`
- `CHARACTER_ARC`
- `LOCATION`
- `ORGANIZATION`
- `FACTION`
- `SPECIES`
- `CULTURE`
- `RELIGION`
- `LANGUAGE`
- `MAGIC_SYSTEM`
- `ITEM`
- `ABILITY`
- `TECHNOLOGY`
- `TERM`
- `PLOTLINE`
- `MYSTERY`
- `FORESHADOWING`
- `EVENT`
- `FACT`
- `CONTINUITY_CONSTRAINT`

Custom node types must map to a Semantic Family. Renaming a type does not change its stable type code.

### Category / Tag

Categories and tags are fully user-managed organizational structures. They do not define core Agent semantics.

- Categories form a project-scoped tree.
- A node may belong to multiple categories.
- Tags are flat and reusable.
- Removing a category does not delete its nodes.

## Relational Storage Model

The schema uses common relational columns plus schema-validated `attributes_json`. It does not use EAV fields and does not create one table per node type.

### `story_bibles`

```text
story_bible_id
project_id
title
description
content_revision
created_at
updated_at
deleted_at
```

There is one active Story Bible per project.

### `story_bible_node_types`

```text
type_id
story_bible_id nullable for system templates
type_code
semantic_family
display_name
icon_code
field_schema_json
is_system
sort_order
created_at
updated_at
archived_at
```

### `story_bible_nodes`

```text
node_id
story_bible_id
type_id
title
summary
body_markdown
attributes_json
inclusion_policy
canon_status
revision
created_by
updated_by
created_at
updated_at
archived_at
deleted_at
```

`inclusion_policy` values:

- `ALWAYS_INCLUDE`
- `AUTO_RETRIEVE`
- `MANUAL_ONLY`

`canon_status` values:

- `DRAFT`
- `CANON`
- `ARCHIVED`

Agent proposals are not stored as `PROPOSED` nodes. They remain in Run/Approval until approved.

### `story_bible_aliases`

```text
alias_id
story_bible_id
node_id
alias
normalized_alias
created_at
deleted_at
```

Normalized aliases support exact entity resolution.

### `story_bible_categories`

```text
category_id
story_bible_id
parent_category_id
name
sort_order
created_at
updated_at
deleted_at
```

### `story_bible_node_categories`

Many-to-many membership between nodes and categories.

### `story_bible_tags` and `story_bible_node_tags`

Project-scoped reusable tags and node membership.

### `story_bible_relations`

```text
relation_id
story_bible_id
source_node_id
relation_type
target_node_id
description
attributes_json
revision
created_by
updated_by
created_at
updated_at
deleted_at
```

Relations are directed. Symmetric relation types may be rendered symmetrically by policy but remain one durable relation.

### `story_bible_progressions`

```text
progression_id
story_bible_id
node_id
anchor_chapter_id
end_chapter_id nullable
story_event_node_id nullable
patch_json
summary
revision
created_by
updated_by
created_at
updated_at
deleted_at
```

`patch_json` uses RFC 6902 operations `add`, `replace`, and `remove`. Paths must be validated against the node type schema and allowed base fields.

### `story_bible_view_preferences`

Project-level display overrides for the six stable Semantic Family views:

```text
story_bible_id
view_code
display_name
hidden
sort_order
updated_by
updated_at
```

### `story_bible_changesets`

```text
changeset_id
story_bible_id
content_revision
actor_type
actor_id
source_run_id nullable
change_summary
created_at
```

### `story_bible_change_items`

```text
change_item_id
changeset_id
entity_type
entity_id
operation
field_path
before_json
after_json
created_at
```

Change items store field-level differences, not complete Story Bible snapshots.

## Change History Retention

MySQL keeps the larger of:

- the most recent 180 days, or
- the most recent 5,000 changesets per project.

Older changesets are grouped by project and month, serialized as compressed JSONL, and archived to:

`story-bible-history/{projectId}/{yyyy-MM}.jsonl.gz`

MySQL rows are deleted only after object upload and checksum verification succeed. Cold archives live for the project lifetime and are removed with project deletion.

The current Story Bible read path never scans change-history tables.

## Manuscript Ordering

The legacy `novel_chapters.chapter_no` column is removed.

Canonical manuscript order is:

```text
novel_volumes.sort_order
novel_chapters.sort_order
novel_chapters.id as deterministic tie-breaker
```

The API computes a full-book continuous `displayNo` from the ordered result. `displayNo` is not stored and is never used as an identity or progression anchor.

`novel_projects.structure_revision` increments when volume/chapter structure or ordering changes.

Story Bible progressions store stable chapter IDs. A `ManuscriptPositionResolver` resolves order by joining project, volume, and chapter ordering. Deleted or unresolved anchors create an explicit conflict; they never silently apply.

## State Progression Semantics

A node's effective state at a target chapter is:

```text
effective state = base node + ordered applicable progression patches
```

Applicable progression ordering is based on resolved manuscript position, followed by progression creation identity as a deterministic tie-breaker.

Two applicable progressions that mutate the same JSON path at the same manuscript position create a conflict. The system must not silently choose a winner.

User editing has two explicit operations:

- edit base setting: correct or complete a fact that should always have been true
- add story change: create a chapter-anchored progression for a fact that changes during the story

The frontend labels progression as "State Evolution" or the corresponding Chinese product copy, not as a global Story Bible version.

## Story Bible Mutation Governance

### User writes

User CRUD is applied immediately with:

- project authorization
- JSON Schema validation
- optimistic `revision` validation
- one changeset
- one `content_revision` increment

### Agent writes

Every Agent-originated Story Bible mutation requires approval, including:

- create/update/archive/delete node
- create/update/delete relation
- create/update/delete progression
- category/type changes initiated by an Agent

Multiple operations from one Run may be approved as one batch. Approval payloads must contain before/after diffs and affected IDs. Approval execution invokes the same application commands as user CRUD and may not bypass validation or changeset creation.

Read-only Story Bible search never requires approval.

## Context Epoch

### Fingerprint

```text
hash(
  sessionId,
  storyBible.contentRevision,
  novelProject.structureRevision,
  activeChapterId,
  styleBindingRevision,
  routingMode,
  routerModelConfigRevision,
  promptBundleHash,
  skillCatalogHash,
  toolCatalogHash
)
```

Working Set state, user messages, and per-turn retrieval results are excluded.

### `agent_context_epochs`

```text
epoch_id
session_id
epoch_no
fingerprint
story_bible_revision
manuscript_revision
active_chapter_id
style_binding_revision
routing_mode
router_model_config_id
router_model_config_revision
prompt_bundle_hash
skill_catalog_hash
tool_catalog_hash
snapshot_object_key
snapshot_hash
snapshot_size_bytes
created_at
superseded_at
```

The epoch snapshot is immutable JSON stored in S3/MinIO. It contains:

- stable Story Bible core rendering
- compact Selector Catalog
- node/type/alias/relation manifest required by the selector
- source revisions and hashes

MySQL stores metadata and object references. Redis may cache snapshot content. Redis expiration reloads the same epoch snapshot and does not create a new epoch.

Old epochs remain available while any recoverable Run references them.

## Session Working Set

### `agent_session_working_set`

```text
session_id
node_id
activation_score
last_used_turn_id
use_count
pinned
updated_at
```

Defaults:

- retain nodes used in the most recent 8 turns
- retain at most 30 automatic nodes
- pinned nodes do not count toward automatic eviction

Selection and promotion are separate:

- a node may be used by one Run without being promoted
- only explicit entity hits, exact aliases, user-pinned nodes, high-confidence injected nodes, and nodes actually returned by `story_bible_search` are promoted
- low-score candidates, unused relation expansion, budget-rejected nodes, and invalid chapter states are not promoted

Working Set content is a ranking input. Only pinned nodes are guaranteed stable inclusion. Other entries are included according to current-turn relevance and token budget.

## Routing Preferences

Effective preference resolution:

```text
session override
-> user default
-> system default RETRIEVAL_THEN_LLM
```

User preference storage contains:

- `story_bible_routing_mode`
- `router_model_config_id`

Session storage contains nullable override fields.

The frontend retains the product copy already agreed with the user, including the existing Embedding wording. The initial backend implementation does not call an embedding provider.

Routing modes:

### `RETRIEVAL`

- exact alias resolution
- title/summary/body lexical matching
- MySQL full-text/BM25 candidate retrieval
- optional semantic-retriever port, unavailable initially
- relation expansion and chapter-state filtering
- no selector LLM

### `LLM_SELECTOR`

- one focused selector call
- receives the bound epoch's compact full Selector Catalog
- returns structured node selections
- never receives all node bodies

### `RETRIEVAL_THEN_LLM`

- lexical retrieval builds candidates
- selector LLM reads candidate summaries and reranks/selects them
- optional semantic candidates may be added in the future through the port

## Embedding Boundary

Only an application port and no-op implementation are in scope:

```java
public interface StoryBibleSemanticRetriever {
    StoryBibleSemanticSearchResult search(StoryBibleSemanticSearchQuery query);
    boolean isAvailable();
}
```

`NoopStoryBibleSemanticRetriever` returns unavailable and performs no external work.

No vector schema, provider, indexing pipeline, outbox, scheduler, or UI status is created.

## Selector Agent Contract

The selector has its own prompt bundle:

```text
prompts/agent/system/context-selector/default/
  00-role.md
  10-selection-policy.md
  20-story-bible-contract.md
  30-output-contract.md
```

The selector:

- selects the smallest sufficient context
- never modifies Story Bible data
- never invents IDs
- considers manuscript scope and state progressions
- returns missing/conflict flags
- uses a required structured-output tool or strict JSON Schema

Required output fields:

```text
intentTags
selectedNodeIds
relationExpansion
selectionReasons
missingContextFlags
confidence
```

All selected IDs are validated against the epoch catalog before content is loaded.

## Prompt Layering and Cache Boundaries

### Selector: `LLM_SELECTOR`

```text
Selector System Prompt
Epoch compact Selector Catalog
--- stable prefix boundary ---
conversation summary/history needed for routing
Session Working Set
current user request
```

### Selector: `RETRIEVAL_THEN_LLM`

```text
Selector System Prompt
--- stable prefix boundary ---
retrieval candidate summaries
conversation summary/history needed for routing
Session Working Set
current user request
```

### Execution Agent

```text
Execution System Prompt
stable Tool Schemas
Skill Catalog: name and short description
Context Epoch core Story Bible
--- stable prefix boundary ---
conversation history
Working Set dynamic context
turn-selected Story Bible content
current user request
```

Tool schemas and catalog entries must use deterministic ordering. The stable prefix manifest includes:

- `promptBundleHash`
- `toolCatalogHash`
- `skillCatalogHash`
- `storyBibleCoreHash`
- `contextEpochId`

Full skill instructions loaded through `skill_load` are dynamic tool results after the boundary.

The design guarantees reuse of stable prefixes. It does not promise that a full, ever-growing conversation prompt remains cache-identical when dynamic context changes.

## Run Integration and Recovery

`agent_runs` gains `context_epoch_id`.

The initial execution path is:

```text
load Run and immutable RunInput
resolve or create Context Epoch
resolve effective routing preference
route/select Story Bible nodes
materialize target-chapter effective states
persist context.resolved artifact
compose prompt and persist prompt.composed state
checkpoint
execute LLM/tool loop
```

The Run-level resolved context artifact contains:

```text
contextEpochId
routingMode
routeDecision
selectedNodeIds
progressionIds
renderedContext
contentHashes
retrievalTrace
workingSetSnapshot
```

It is stored as an immutable Run artifact in S3/MinIO and referenced from events/checkpoint state.

Required durable events include:

- `context.epoch.bound`
- `turn.route.completed`
- `context.resolved`
- `prompt.composed`

Resume behavior:

```text
load latest checkpoint
replay durable events after checkpoint
load referenced immutable artifacts
continue from saved phase/LLM turn/tool state
```

`resume(runId)` must not call the initial execution path. It must not recreate an epoch, rerun the selector, or rebuild the prompt after those phases completed.

## Story Bible Search Tool

Execution models have a read-only `story_bible_search` tool.

The tool:

- searches only the Run's bound epoch/catalog scope
- resolves target-chapter effective state
- returns canonical node IDs and citations
- records returned/used nodes in the Run trace
- may promote actually used nodes to the Working Set
- never mutates Story Bible data

Agent mutation tools always produce approval proposals and use approved application commands.

## API Contract

All business IDs remain strings at the HTTP boundary.

Primary Story Bible endpoints:

```text
GET    /api/v1/novels/{projectId}/story-bible
GET    /api/v1/novels/{projectId}/story-bible/views
PATCH  /api/v1/novels/{projectId}/story-bible/views/{viewCode}

GET    /api/v1/novels/{projectId}/story-bible/node-types
POST   /api/v1/novels/{projectId}/story-bible/node-types
PATCH  /api/v1/novels/{projectId}/story-bible/node-types/{typeId}
DELETE /api/v1/novels/{projectId}/story-bible/node-types/{typeId}

GET    /api/v1/novels/{projectId}/story-bible/nodes
POST   /api/v1/novels/{projectId}/story-bible/nodes
GET    /api/v1/novels/{projectId}/story-bible/nodes/{nodeId}
PATCH  /api/v1/novels/{projectId}/story-bible/nodes/{nodeId}
DELETE /api/v1/novels/{projectId}/story-bible/nodes/{nodeId}

POST   /api/v1/novels/{projectId}/story-bible/nodes/{nodeId}/progressions
PATCH  /api/v1/novels/{projectId}/story-bible/progressions/{progressionId}
DELETE /api/v1/novels/{projectId}/story-bible/progressions/{progressionId}

POST   /api/v1/novels/{projectId}/story-bible/relations
PATCH  /api/v1/novels/{projectId}/story-bible/relations/{relationId}
DELETE /api/v1/novels/{projectId}/story-bible/relations/{relationId}

POST   /api/v1/novels/{projectId}/story-bible/categories
PATCH  /api/v1/novels/{projectId}/story-bible/categories/{categoryId}
DELETE /api/v1/novels/{projectId}/story-bible/categories/{categoryId}

GET    /api/v1/novels/{projectId}/story-bible/changesets
GET    /api/v1/novels/{projectId}/story-bible/changesets/{changesetId}
POST   /api/v1/novels/{projectId}/story-bible/search
```

User preference and session override endpoints extend the existing model/profile and agent session contracts rather than introducing project-level routing preferences.

## Frontend Information Architecture

Workbench adds a top-level segmented mode control:

```text
Writing | Story Bible
```

### Writing mode

- left: volume/chapter outline
- center: chapter editor
- right: Agent conversation

### Story Bible mode

- left: Semantic Family views, custom categories, and tag filters
- center: node list and structured node editor
- right: Agent conversation

Node editor views:

- base setting
- relations
- state progression
- change history

Requirements:

- route/query state supports `mode=story-bible` and `nodeId`
- approval diffs can open the affected node
- existing character/world card tabs and components are removed
- system views may be renamed, reordered, and hidden
- all content nodes may be created, edited, moved, archived, and deleted
- desktop and mobile layouts must not overlap; mobile uses drawers for side navigation and chat
- frontend controls use the existing design language and icon library

## Destructive Baseline Cutover

The branch intentionally rebuilds the development baseline.

- `V4`: remove `chapter_no`, add chapter `sort_order`, add/maintain ordering indexes.
- `V8`: retain outline schema and remove card/relation schema.
- `V11`: add session routing overrides, Context Epoch metadata, Working Set, and Run epoch reference.
- `V14`: create the final Story Bible schema.

No local legacy data migration is written. Existing development databases must be recreated. Production code must not contain old-schema detection or fallback.

## Deletion Inventory

At minimum, remove:

- full preflight coordinator/request/decision/prompt assets and mappings
- legacy Story Bible version selector and version-based fields
- Novel Card models, mappers, gateway methods, commands, controller routes, DTOs, tests
- frontend card API, card composable, character/world card components and tests
- old context-provider signatures coupled to `AgentPreflightDecision`
- resume paths that call initial execution

The current uncommitted `skill_load` progressive-disclosure changes are explicitly preserved and integrated.

## Failure Semantics

- invalid node/type schema: reject mutation without incrementing content revision
- optimistic revision conflict: return conflict with current revision
- missing progression anchor: persist no silent fallback; return/flag conflict
- selector returns unknown ID: reject that selection and record a selector-contract failure
- selector model unavailable in `LLM_SELECTOR`: fail the route explicitly
- selector model unavailable in `RETRIEVAL_THEN_LLM`: fail explicitly; do not silently change the user-selected mode
- semantic retriever unavailable: treated as an unavailable optional source, not an error
- S3 epoch snapshot write fails: do not publish/bind the epoch
- Run context artifact write fails: do not enter execution
- Working Set update fails after a durable Run context artifact: log and retry without changing resolved Run context
- changeset cold archive fails: keep hot MySQL rows and retry

## Observability

Required structured fields:

- `projectId`, `sessionId`, `runId`, `epochId`, `contentRevision`
- `routingMode`, `selectorUsed`, `semanticRetrieverAvailable`
- exact alias count, lexical candidate count, selected node count
- working-set candidate/promoted/evicted counts
- selector token usage and latency
- execution cached-token fields when provided by the model provider
- artifact IDs and hashes, never full Story Bible bodies in logs

Provider usage parsing should retain cached-token metadata where the provider exposes it. Preflight usage accounting disappears with the full-preflight removal; selector usage is recorded explicitly.

## Acceptance Criteria

1. Fresh schema contains no `novel_cards`, `novel_card_relations`, `story_bible_versions`, or Story Bible `version_no`.
2. Production Java/TypeScript has no legacy card or full-preflight runtime path.
3. Story Bible CRUD, custom types, categories, aliases, tags, relations, and progressions work end to end.
4. User CRUD writes exactly one changeset and increments content revision once.
5. Agent Story Bible writes cannot execute without approval.
6. Chapter display numbers are continuous across the full manuscript and derived from volume/chapter sort order.
7. Progression evaluation uses resolved manuscript order, not business ID comparison.
8. Multiple Runs reuse the same epoch when the fingerprint is unchanged.
9. Fingerprint changes create a new epoch; Working Set changes do not.
10. Resume uses checkpoint/artifact state and does not rerun route/context/prompt phases.
11. The three user routing modes are persisted and frozen into each Run.
12. Embedding has only a port/no-op implementation and causes no external calls.
13. Skill Catalog appears in the stable execution prefix; full skill content is loaded through `skill_load`.
14. Story Bible Workbench mode is usable on desktop and mobile without overlap.
15. Backend and frontend focused tests, full builds, and contract scans pass.

## Research Basis

The classification and retrieval design was informed by:

- Novelcrafter Codex types, categories, relations, progressions, and prompt context selection
- Plottr Characters, Places, Notes, Tags, Timeline, custom attributes, and Series Bible organization
- Campfire's detailed worldbuilding modules
- Anthropic Contextual Retrieval and context-engineering guidance
- Azure AI Search chunking guidance
- OpenAI and Anthropic prompt-cache prefix requirements

The resulting design intentionally avoids copying any one product. It uses stable semantic families, extensible templates, user organization, chapter-state progressions, and small-to-big context selection suited to PenMate's existing Agent Run architecture.
