# PenMate pgvector, Model, Security, And DDD Full Refactor Plan

**Goal:** Replace Milvus/etcd and placeholder SQL `LIKE` retrieval with a production-grade PostgreSQL/pgvector knowledge-index pipeline, while completing model configuration, project ownership, JWT security, asynchronous jobs, document upload, frontend operations, and a clean V1-V7 baseline.

**Architecture:** PostgreSQL 18.4 remains the only database and also hosts pgvector 0.8.5. Novel, Story Bible, Model, Agent, IAM, and RAG remain separate bounded contexts. Source domains own business truth; the RAG bounded context owns only derived searchable projections. Application use cases coordinate domain ports. MyBatis, pgvector DDL/SQL, S3, Redis, HTTP model clients, Spring Security filters, and workers stay in infrastructure/interfaces adapters.

**Tech Stack:** Java 21, Spring Boot 3.3.4, Spring Security, MyBatis, Flyway, PostgreSQL 18.4, pgvector 0.8.5, Redis 7.4, S3-compatible object storage, Vue 3, TypeScript, Vitest, Playwright, Testcontainers, Docker Compose, GitHub Actions.

**Data Policy:** Existing development/production database contents may be discarded. V1-V7 have not been executed in a retained environment and may be rewritten directly. No MariaDB, Milvus, or etcd data migration is required. No physical foreign keys are added.

---

## Delivery Order

1. Commit the current pre-existing dirty worktree by coherent functional area before pgvector implementation begins.
2. Commit this plan independently or with the documentation-only functional group.
3. Rewrite the fresh V1-V7 baseline and runtime configuration.
4. Implement security, ownership, model, project AI configuration, indexing, jobs, upload, retrieval, and frontend workflows in bounded commits.
5. Run requirement-level verification and create the final local commits. Do not push or trigger GitHub CI/CD until the user explicitly requests it.

## DDD Dependency Rules

```text
interfaces -> application -> domain
                         ^
                         |
                  infrastructure
```

- `domain` contains aggregates, value objects, policies, state transitions, and repository/gateway ports. It must not import Spring Web, WebClient, MyBatis, Redis, S3, pgvector, or interface DTOs.
- `application` contains transactional use cases and cross-bounded-context orchestration. It depends on ports, not adapters.
- `infrastructure` implements persistence, object storage, model HTTP, Redis, pgvector provisioning/search, and queue persistence ports.
- `interfaces` contains REST DTO/controllers, Spring Security filter/configuration, worker entrypoints, and presentation mapping.
- Controllers, Agent tools, and asynchronous workers call application use cases only. They never call Mapper, S3, WebClient, or vector clients directly.
- RAG owns derived index projections. `novel_chapters`, `story_bible_nodes`, and `novel_outline_nodes` remain the authoritative business records and receive no vector columns.
- `ops_async_jobs` is a generic durable scheduler. RAG job steps and validation remain in RAG application/domain handlers.

---

## Confirmed Product And Technical Decisions

### Authentication And Authorization

- Keep JWT plus Redis-backed sessions.
- Verify JWT signature, expiry, issuer/token type, then read the verified `jti`.
- Load `auth:access:{jti}` from Redis and build the Spring Security Principal from cached `userId`, roles, and permissions.
- Keep `jti` as the Redis key; never use the full JWT as a Redis key.
- Reject missing/revoked sessions with 401. Fail closed when Redis is unavailable; do not trust JWT alone.
- Remove business trust in client-provided `operatorId` and `ownerUserId`; derive actor identity from Principal.
- RBAC/Ops/system-model endpoints require admin authority.
- Admin may manage system models/jobs and alter project AI/index bindings, but may not read or edit user project content.

### Project Ownership

- A novel project has exactly one owner.
- `novel_projects.owner_user_id` is the only ownership source.
- Remove `novel_members`, member roles, collaboration APIs/DTOs/repositories/tests, and active UI/docs remnants.
- Project content APIs always verify Principal ownership in application use cases.

### Model Catalog And Credentials

- Rename `model_user_configurations` to `model_configurations`.
- Configuration scopes are `SYSTEM` and `USER`; model types are `CHAT` and `EMBEDDING`.
- System configurations are admin-created and globally visible/usable. User configurations are private to their owner.
- Providers are admin-managed protocol metadata. Ordinary users select a provider and create model configurations; arbitrary OpenAI-compatible services use the generic provider plus a custom Base URL.
- Add `model_provider_capabilities` with reserved capabilities `CHAT`, `EMBEDDING`, `RERANK`, `VISION`, `OCR`, and `SPEECH`; only CHAT and EMBEDDING are implemented now.
- First implementation supports `OPENAI_CHAT_COMPLETIONS` and `OPENAI_EMBEDDINGS` adapters.
- From the UI/API consumer perspective, API Key is part of a model configuration. The backend stores it separately and encrypted.
- Each model configuration owns exactly one credential record. SYSTEM uses `model_official_api_keys`; USER uses its owner's `model_user_api_keys`.
- Read APIs return only masked credential state. Blank key during update means keep the existing key; a supplied key replaces it.
- `auth_type=NONE` permits no credential only for an allowed provider/address.
- Provider, Base URL, model name, distance metric, or project chunk settings invalidate embeddings. API Key/display-name changes do not.
- Model type is immutable after creation.
- Both CHAT and EMBEDDING configurations are protected from mutation/deletion while referenced by nonterminal Runs.
- Blocking Run states are `PENDING`, `RUNNING`, `WAITING_APPROVAL`, and `SUSPENDED`.

### Base URL Security

- Ordinary user URLs require HTTPS in production and reject loopback, private, link-local, multicast, unspecified, and cloud metadata addresses.
- Validate resolved IPs, reject DNS rebinding targets, and disable redirects unless every redirect target is revalidated.
- Admin-configured private hosts require an explicit server-side allowlist.
- `application-local.yml` may allow HTTP localhost/LAN model services.
- Startup/bootstrap does not call model APIs. This validation protects outbound requests but is not a model-verification workflow.

### Bootstrap And User Defaults

- Admin Bootstrap remains required.
- Replace `BOOTSTRAP_MODEL_*` with optional complete groups `BOOTSTRAP_CHAT_*` and `BOOTSTRAP_EMBEDDING_*`.
- An empty Chat/Embedding group is skipped. A partially populated group fails startup with a clear configuration error.
- Bootstrap creates encrypted official credentials and SYSTEM model configurations; it does not call providers.
- `BOOTSTRAP_RECONCILE=false` remains create-only by default.
- `application-local.yml` contains editable local admin, Chat, and Embedding placeholders.
- `.env.example` and `penmate-backend/.env.example` document production values.
- User-level AI preferences are templates copied into new projects, not live runtime inheritance.
- User defaults include Chat preferences, default Story Bible routing mode, optional default Embedding configuration, and default chunk target/overlap.
- Runtime Embedding binding, routing, chunking, retrieval parameters, and index state are project-level only. Remove Session-level routing overrides.

### Project Routing And Index Availability

- Story Bible routing modes remain `RETRIEVAL`, `LLM_SELECTOR`, and `RETRIEVAL_THEN_LLM`.
- A project without an active Embedding index can use only `LLM_SELECTOR`.
- An Embedding configuration change permanently writes affected projects to `LLM_SELECTOR`; successful rebuild does not restore the previous mode.
- With a healthy index, a user may still choose `LLM_SELECTOR` for Story Bible while `rag_query` uses vectors for manuscript/outline/reference documents.
- `story_bible_search` and automatic context assembly use the routing mode captured in the Run-bound Context Epoch.
- `RETRIEVAL`: aliases + lexical + pgvector candidates returned directly.
- `LLM_SELECTOR`: the selector uses the bound Story Bible catalog without pgvector.
- `RETRIEVAL_THEN_LLM`: aliases/lexical/pgvector narrow candidates, then LLM selects.
- Tool definitions, schemas, descriptions, and order remain stable for prompt prefix caching.
- `rag_query` always stays in the tool list. With no active index it returns a stable non-retryable `RAG_INDEX_UNAVAILABLE` result and never falls back to SQL `LIKE`.

### Indexed Content

- `STORY_BIBLE_NODE`: one node is the natural retrieval unit. Embed type/title/aliases/summary/body/attributes. Hydrate relations and chapter-effective progressions after retrieval.
- `MANUSCRIPT_CHUNK`: index only current chapter content from OSS. Never index historical chapter-version snapshots. Exclude the actively edited chapter from self-retrieval and inject active/selected text directly into Agent context.
- `OUTLINE_NODE`: one outline node is the natural unit; split only oversized content.
- `KNOWLEDGE_DOCUMENT`: parse and index user-uploaded TXT, Markdown, and HTML.
- Story Bible search and ordinary RAG remain separate application/tool entrypoints but share Embedding and pgvector infrastructure.
- `story_bible_search` searches only Story Bible nodes. `rag_query` defaults to manuscript, outline, and reference sources and accepts source-type filters.

### Parsing And Chunking

- Supported uploads: `.txt`, `.md`, `.markdown`, `.html`, `.htm` only.
- Strict UTF-8 and real content validation are required. Reject PDF, DOCX, images, binaries, mismatched extension/MIME, and invalid HTML.
- Use Jsoup to parse HTML and extract normalized index text. Preserve the original object in S3.
- Manuscript/reference target chunk: 800 Unicode characters; overlap: 120; hard maximum: 1200.
- Prefer Markdown/HTML headings, scene breaks, paragraphs, and sentence boundaries before length splitting.
- Story Bible uses a node per unit; only oversized `body_markdown` is split, and each subchunk repeats node identity/summary metadata.
- Outline uses one node per unit and paragraph splitting only above the maximum.
- Project settings may override document/manuscript chunk settings, not the structural Story Bible rules.

### Upload Workflow

- Replace the fake RAG upload URL with real S3 presigning through the object-storage port.
- Use initialize -> direct PUT -> complete workflow.
- The backend creates object keys with real sanitized extensions; clients cannot choose object keys.
- Complete validates ownership, upload token, object HEAD metadata, ETag/checksum, 10 MiB size limit, UTF-8, MIME/extension/content, and deletes invalid temporary objects.
- Create `rag_document` and enqueue parse/index only after completion validation.
- Presigned PUT lifetime defaults to 15 minutes.

### Embedding Calls

- Use OpenAI-compatible `POST {baseUrl}/embeddings` with array input.
- Batch limits: 32 chunks and 30,000 total characters.
- Connect timeout: 10 seconds. Response timeout: 60 seconds.
- Retry a batch at most three attempts with 1/2/4 second exponential backoff plus jitter.
- Retry connection errors, timeouts, 429, and 5xx. Do not retry 400/401/403/404 or malformed/vector-count errors.
- Detect dimension lazily on the first successful indexing response; never ask the user to enter it.
- Validate response count and uniform dimensions on every batch. A dimension change fails the entire build.
- Dimensions 1-2000 use pgvector `vector` (float32). Dimensions 2001-4000 use `halfvec` (float16). Reject dimensions above 4000.

### Embedding Spaces And pgvector

- Use the existing PenMate PostgreSQL database with `CREATE EXTENSION vector`.
- Docker/production/CI image: `pgvector/pgvector:0.8.5-pg18`.
- Embedding Space identity includes provider/protocol, normalized Base URL, model name, detected dimension, and distance metric. Credential identity is excluded.
- Metrics are `COSINE`, `INNER_PRODUCT`, and `L2`, stored on EMBEDDING model configuration.
- Different spaces receive separate PostgreSQL partitions and HNSW indexes.
- Keep partition provisioning, trusted dynamic DDL, type casting, and operator-class selection in the pgvector infrastructure adapter.
- HNSW build defaults are `m=16` and `ef_construction=64`; users cannot edit them.
- Query defaults: 30 vector candidates, final Top K 8, maximum 3 chunks per source, `ef_search=100`, no default hard similarity threshold.
- Project advanced settings may expose candidate count, Top K, `ef_search`, and an optional metric-specific threshold.

### Atomic Build And Incremental Updates

- Full rebuild creates an internal `index_build_id`; this is not a model configuration revision and is not user-visible.
- Project RAG is disabled while a required full rebuild is pending/running.
- Write all staged sources/chunks/vectors under the new build, validate totals/dimensions/failures, then switch `active_index_build_id` in one transaction.
- Failure leaves the project `REINDEX_REQUIRED`; staged data is unreachable and cleaned asynchronously.
- Successful activation asynchronously removes the old build.
- Normal chapter/node/outline/document edits use source-level incremental replacement. Mark only that source stale/unsearchable; keep the rest of the project index active.
- Every source job captures source revision/checksum and rechecks it before write/activation. Obsolete work is discarded and cannot overwrite newer content.
- Task business keys include project/source/type/revision for idempotency.
- Deletion makes the source unreachable immediately and cleans vectors asynchronously.

### Model Changes, Locks, Unbind, And Runs

- Use PostgreSQL transaction row locks, never JVM synchronization and never a lock held for an Agent Run duration.
- Lock order: model configuration; project AI configuration rows ordered by project ID; user-default rows.
- Run creation takes shared locks while capturing model/project configuration. Configuration mutation/unbind takes `FOR UPDATE` locks.
- Impact-preview endpoint evaluates candidate configuration changes before mutation.
- If any dependent project has a nonterminal Run, mutation/unbind fails atomically with 409.
- Embedding identity changes mark every dependent project `REINDEX_REQUIRED`, disable vector retrieval, and permanently set routing to `LLM_SELECTOR`.
- Referenced model configurations cannot be deleted until projects/user defaults are unbound.
- One-click unbind is all-or-nothing. If any project is blocked, no binding changes.
- Unbind does not read project content. Cleanup is asynchronous.
- Configuration edits do not automatically spend Embedding credits. UI offers optional “modify and rebuild all”, default off, plus per-project and bulk rebuild actions.
- Bulk project rebuild jobs may succeed/fail independently; the configuration mutation itself remains atomic.

### Durable Async Queue

- Upgrade `ops_async_jobs` to a PostgreSQL-backed at-least-once queue using `FOR UPDATE SKIP LOCKED`.
- Required job types: `RAG_PARSE_DOCUMENT`, `RAG_EMBED_DOCUMENT`, `RAG_REINDEX_SOURCE`, `RAG_REBUILD_PROJECT`, and `RAG_CLEANUP_EMBEDDING_SPACE`.
- Store payload, idempotency key, status, attempt count, scheduled time, lease owner/expiry, heartbeat, cancel request, progress counters, last error, and timestamps.
- Lease: 2 minutes. Heartbeat: 20 seconds. Maximum job executions: 5. Retry delays: 30 seconds, 2 minutes, 10 minutes, 30 minutes.
- Expired leases can be reclaimed. Handlers are idempotent.
- Full rebuild supports cooperative cancellation between batches. Queued work cancels immediately; running HTTP requests are allowed to finish before cancellation cleanup.
- Incremental source jobs are not manually cancellable; newer revisions supersede older work.
- User may operate only owned-project jobs. Admin can inspect/retry/cancel jobs without viewing project content.

### Capacity Protection

- Maximum upload size: 10 MiB.
- Maximum chunks per source: 20,000.
- Maximum active chunks per project: 100,000.
- Rebuild preflight displays source count, total bytes/characters, estimated chunks, and estimated Embedding batches.
- Do not estimate money without a reliable provider price catalog.
- Limits are system configuration, not ordinary user settings.

### Deployment And Data Safety

- Remove Milvus and etcd services, volumes, code/configuration, and environment variables from current Compose definitions.
- Remove `VECTOR_PROVIDER`, `VECTOR_ENDPOINT`, `VECTOR_API_KEY`, collection-prefix, Milvus, and etcd configuration.
- Keep S3 for source documents, current chapter bodies, version snapshots, and immutable artifacts.
- CI/CD may build/test/deploy the new app and pgvector image, but must never delete legacy MariaDB/Milvus/etcd containers or volumes.
- Old server services/volumes are removed manually by the operator after verification.

---

## Fresh V1-V7 Migration Layout

### V1: IAM And RBAC

- Keep users, roles, permissions, menus, audit, and logical relationship indexes.
- Include `iam_users.bio` directly and remove the temporary V8 migration.
- Remove project/model preference fields that belong in dedicated preference/configuration aggregates.
- Keep Snowflake business IDs and identity technical IDs.

### V2: Novel And Story Bible

- Keep `novel_projects.owner_user_id` as sole ownership.
- Remove `novel_members` completely.
- Preserve current chapters in S3 by `content_object_key`, chapter revisions/checksums, chapter-version snapshots, outlines, styles, Story Bible nodes/types/aliases/categories/tags/relations/progressions/history.
- Add/normalize explicit source revisions required for incremental indexing.

### V3: Storage And RAG/pgvector

- Enable pgvector extension.
- Define upload sessions, RAG documents, project AI configuration, Embedding spaces, index builds, source projections, chunks, vector partitions/metadata, retrieval logs, state/indexes, and uniqueness/idempotency constraints.
- Store chunk text for fast retrieval while preserving source references/revisions.
- Use logical references only; add indexes for ownership/project/build/source access paths.

### V4: Plugin And Model

- Define providers, capabilities/protocols, encrypted official/user credentials, unified model configurations, and user default preferences.
- Add scope/type/status/metric constraints and uniqueness rules.
- Enforce one credential per configuration through business/unique constraints without physical foreign keys.

### V5: Agent Domain

- Remove Session-level Story Bible routing overrides.
- Keep Run-bound configuration/context snapshots and immutable historical artifacts.
- Ensure active/nonterminal Run queries and model/project references support locking and mutation impact checks.
- Preserve stable tool catalog/prefix behavior.

### V6: Agent Execution Extensions And Ops Queue

- Merge the final Agent execution schema intent.
- Upgrade `ops_async_jobs` with leases, heartbeats, cancellation, retries, idempotency, progress, payload/result/error fields, and claim indexes.
- Include cleanup/migration operational records only where actively used.

### V7: Required System Metadata

- Seed one admin role/permissions/menus and Provider/capability/protocol catalog only.
- Do not seed accounts, passwords, API keys, model configurations, projects, books, Story Bible cases, Agent cases, or RAG cases.
- Keep demo/case SQL under test resources and invoke it only through explicit PowerShell/Bash scripts.

---

## Implementation Workstreams

### 1. Preserve And Commit Current Worktree

- Inventory every modified/untracked/deleted file and group by actual behavior.
- Run backend tests for current auth/security/profile changes before committing them.
- Run frontend format/lint/typecheck/unit/build/E2E checks appropriate to the current frontend refactor.
- Commit CI/tooling, frontend architecture/assets, and auth/profile/security as separate functional commits where dependencies permit.
- Never discard or overwrite overlapping user changes. Fold V8 into V1 only in the later baseline commit.

### 2. Dependencies, Configuration, Compose, And Documentation

- Add pgvector Java/JDBC integration only where needed; do not introduce Milvus replacement middleware.
- Replace PostgreSQL images with pinned pgvector image in local/prod/Testcontainers.
- Remove Milvus/etcd from active Compose and environment examples without destructive runtime commands.
- Rename Bootstrap variables and remove legacy vector variables.
- Add local pgvector/version preflight scripts and operator documentation.

### 3. Security And Ownership

- Complete `BearerAuthenticationFilter` integration and central authenticated Principal type.
- Convert auth/me/profile endpoints to Principal where appropriate while retaining JWT verification + Redis JTI semantics in the filter.
- Replace all client actor/owner IDs in controllers/DTOs/commands with Principal-derived identity.
- Add owner authorization application policies to every project-content use case.
- Add admin method/request authorization and negative tests proving admins cannot access user content.
- Delete collaboration/member backend/frontend/docs/tests.

### 4. Model And Project AI Configuration

- Introduce unified model aggregates, capability/protocol catalog, credential aggregate facade, masking/encryption, SSRF policy, and persistence adapters.
- Add user defaults and project AI configuration aggregates/use cases.
- Implement impact preview, locked update, disable, delete, unbind, and bulk rebuild orchestration.
- Rewrite Bootstrap for admin plus optional Chat/Embedding groups.
- Implement frontend configuration forms, scope/visibility, masked credential editing, impact confirmation, dependency display, and unbind/rebuild actions.

### 5. RAG Domain And pgvector Infrastructure

- Model project index/source/build states and invariant transitions in domain.
- Implement structure-aware parsers/chunkers with deterministic chunk IDs/content hashes.
- Implement OpenAI-compatible Embedding port/adapter, batching, retry, dimension detection, response validation, and SSRF checks.
- Implement vector/halfvec partition provisioning and metric-specific HNSW/search adapters.
- Implement atomic full build, incremental source swap, stale-revision rejection, deletion, and cleanup.
- Replace SQL `LIKE` retrieval with scoped pgvector retrieval and source diversity.

### 6. Async Queue And Workers

- Define generic async job domain/port and PostgreSQL implementation.
- Implement SKIP LOCKED claim, lease, heartbeat, retry, cancellation, progress, recovery, and idempotency tests.
- Register application-level RAG handlers through a handler registry without infrastructure-to-domain leakage.
- Implement job API/DTOs and ownership/admin authorization.

### 7. Upload And Source Change Integration

- Implement real RAG S3 pre-sign/complete workflow and frontend uploader.
- Validate TXT/Markdown/HTML and enqueue parsing/indexing transactionally.
- Hook chapter content confirmation, Story Bible node updates, outline updates, and document lifecycle into source-level reindex jobs.
- Ensure obsolete revisions cannot activate.

### 8. Agent Context And Tools

- Implement pgvector Story Bible semantic retriever.
- Make `story_bible_search` use Run-bound mode and shared resolver.
- Keep `rag_query` definition stable; implement availability-aware structured results.
- Query manuscript/outline/reference scopes separately from Story Bible, hydrate citations, and preserve immutable Run context/recovery behavior.
- Block relevant config changes while Runs are nonterminal.

### 9. Frontend Operations

- Add project AI/RAG settings without nested-card or marketing layouts.
- Provide model selection, optional Embedding binding, mode constraints, chunk/retrieval advanced settings, status/reason display, impact preview, rebuild estimate, start/cancel/retry, progress, source states, document upload, and one-click unbind.
- Keep IDs as strings and align API contracts with Principal-derived ownership.
- Preserve stable workbench tool/runtime behavior and surface non-retryable RAG unavailability coherently.
- Verify desktop/mobile layouts and critical workflows with Playwright.

---

## Test And Verification Matrix

### Static And Migration Evidence

- Fresh Flyway migration V1-V7 succeeds against PostgreSQL 18.4 + pgvector 0.8.5.
- Schema assertions prove vector extension, tables, constraints, indexes, no `novel_members`, no V8, and no active Milvus/etcd/MySQL/H2 artifacts.
- V7 contains only required non-secret metadata.
- Active sources/config/docs contain no legacy Bootstrap/vector variable names.

### Domain/Application Unit Tests

- Chunk boundary/overlap/UTF-8/HTML behavior.
- Index/source/build state transitions and atomic failure behavior.
- Embedding identity and invalidation matrix.
- Project routing constraints and permanent LLM_SELECTOR fallback.
- Lock order, nonterminal Run blocking, impact preview, all-or-nothing unbind.
- Principal ownership and admin content denial.
- SSRF host/IP/DNS/redirect policy.
- Stable tool catalog and mode-specific Story Bible resolver behavior.

### PostgreSQL/pgvector Integration Tests

- Testcontainers image `pgvector/pgvector:0.8.5-pg18`.
- COSINE/INNER_PRODUCT/L2 queries and HNSW index use.
- Vector/halfvec dimension routing and >4000 rejection.
- Separate Embedding Space isolation, project/build/source filters, atomic activation, stale-source swap, cleanup.
- `FOR SHARE`, `FOR UPDATE`, ordered row locks, `SKIP LOCKED`, lease reclamation, heartbeats, cancellation, retry, and idempotency.
- JSONB/timestamptz/Snowflake ID behavior remains intact.

### Adapter Contract Tests

- Mock OpenAI-compatible Embedding success, batch ordering, rate limits, transient/permanent failures, malformed counts, dimension changes, and timeouts.
- S3 presign/HEAD/download/delete/validation lifecycle.
- Redis session present/missing/revoked/outage behavior.
- Bootstrap empty/complete/partial Chat and Embedding groups.

### Frontend Verification

- Format, lint, TypeScript, unit tests, production build.
- Model scope/credential masking/edit semantics.
- Project mode constraints, impact confirmation, rebuild estimates/progress/cancel/retry, upload validation, unbind, unavailable tool/result presentation.
- Playwright owner/admin authorization and core desktop/mobile project workflows.

### Packaging And Deployment Evidence

- Backend executable JAR packages successfully and includes PostgreSQL/pgvector support without MySQL/MariaDB/Milvus clients.
- Compose config resolves PostgreSQL/Redis/S3/backend/frontend without Milvus/etcd services.
- CI equivalent tests pass without destructive deploy actions.
- Local pgvector preflight reports PostgreSQL 18.4 and pgvector 0.8.5.
- Production operator documentation contains manual legacy-container/volume cleanup and rollback boundaries.

---

## Planned Commit Boundaries

1. `docs: plan pgvector and DDD full refactor`
2. Existing dirty-worktree commits grouped by their current auth/profile, frontend architecture, and CI/tooling behavior.
3. `refactor(db): rebuild baseline for pgvector and single ownership`
4. `refactor(config): adopt pgvector runtime and bootstrap groups`
5. `feat(security): enforce principal ownership and admin boundaries`
6. `refactor(model): unify model configurations and project defaults`
7. `feat(rag): add project index domain and embedding pipeline`
8. `feat(ops): add durable PostgreSQL job queue`
9. `feat(storage): add validated RAG upload workflow`
10. `feat(agent): route Story Bible and RAG through project index`
11. `feat(frontend): add model and project RAG operations`
12. `test: verify pgvector security and indexing flows`
13. `docs: document pgvector development and manual cutover`

Commit boundaries may be split further when a workstream is independently testable; unrelated concerns must not be collapsed into one commit.

---

## Acceptance Criteria

- PostgreSQL/pgvector is the only relational/vector runtime in active code, tests, Compose, and environment configuration.
- Fresh V1-V7 succeeds and contains the final schema; V8 is removed after folding `bio` into V1.
- Milvus/etcd are absent from active deployment definitions, but CI/CD contains no destructive cleanup.
- Models support SYSTEM/USER and CHAT/EMBEDDING with provider capabilities, encrypted one-to-one credentials, masking, SSRF protection, optional Bootstrap groups, and correct visibility/authorization.
- Projects have exactly one owner, no collaboration/member runtime remains, and all actor identity comes from authenticated Principal.
- JWT is verified before Redis JTI lookup; revoked/missing/outage sessions fail closed.
- A project may omit Embedding, uses project-level settings only, and obeys permanent LLM_SELECTOR fallback after invalidation.
- TXT/Markdown/HTML upload, parse, structure-aware chunk, Embed, vector/halfvec HNSW index, scoped retrieve, citation hydrate, rebuild, cancel, retry, unbind, and cleanup work end to end.
- Current manuscript, Story Bible nodes, outlines, and knowledge documents are indexed according to their confirmed source rules; historical manuscript snapshots are not.
- Full rebuild is atomic; incremental changes replace only the affected source; obsolete jobs cannot overwrite newer content.
- `story_bible_search` obeys Run-bound mode. `rag_query` is always present and returns a stable unavailable result when required.
- PostgreSQL job queue survives worker crashes and proves lease/retry/idempotency/cancellation behavior.
- Frontend exposes complete model/project/upload/rebuild/progress/unbind workflows and passes unit/build/Playwright verification.
- Demo data remains separate and manually invoked. V7 contains no account, password, API key, model configuration, book, or case data.
- Existing user work is preserved and committed by function before the new implementation commits.
- All required tests and package/deployment checks pass, and the work is recorded in coherent local commits without pushing.
