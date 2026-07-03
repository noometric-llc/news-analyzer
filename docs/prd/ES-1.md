# Evidence Store Foundation Brownfield Enhancement PRD

**Document Version:** 0.1
**Created:** 2026-07-02
**Author:** John (PM) / Steve Kosuth-Wood
**Status:** Draft
**Template:** brownfield-prd-template-v2

---

## Table of Contents

1. [Intro Project Analysis and Context](#1-intro-project-analysis-and-context)
2. [Requirements](#2-requirements)
3. [User Interface Enhancement Goals](#3-user-interface-enhancement-goals)
4. [Technical Constraints and Integration Requirements](#4-technical-constraints-and-integration-requirements)
5. [Epic and Story Structure](#5-epic-and-story-structure)
6. [Epic Details](#6-epic-details)
7. [Change Log](#7-change-log)

---

## 1. Intro Project Analysis and Context

### 1.1 Analysis Source

**Source:** IDE-based analysis of existing documentation. No separate `document-project` task run — existing architecture docs provided sufficient coverage.

Documents analyzed:
- `docs/architecture/coding-standards.md`
- `docs/architecture/source-tree.md`
- `docs/architecture/FACTBASE_EXPANSION_ARCHITECT_HANDOFF.md`
- `docs/api/reasoning-service-contract.md`
- `docs/TECHNICAL-DEBT.md`
- `docs/analysis/ES-1-project-brief.md` (source project brief for this enhancement)
- `docs/analysis/ES-1-brainstorming-session-results.md` (originating brainstorm)

### 1.2 Current Project State

NewsAnalyzer is a polyglot monorepo: Java 17/Spring Boot backend, Next.js/React frontend, Python/FastAPI reasoning service (private repo, `noometric-intelligence`, boundary via `REASONING_SERVICE_URL`).

The backend today centers on a **transient** `Entity` extraction pipeline (`EntityController` → `EntityService` → reasoning-service `POST /entities/extract`) and an authoritative government-data **Factbase** (`GovernmentOrganization`, with `Person`/`GovernmentPosition`/`PositionHolding`/`Regulation` planned per the Factbase Expansion architect handoff). **No article persistence exists** — article text is processed and discarded; the only article-shaped table, `SyntheticArticle`, exists solely for eval/synthetic-data purposes.

A second reasoning-service capability, `POST /eval/bias/detect`, already exists as a documented, stable contract endpoint (cognitive-bias/logical-fallacy annotation grounded in an OWL ontology) but is currently called only by the eval harness — never by production code.

### 1.3 Available Documentation Analysis

| Documentation | Status | Location |
|---|---|---|
| Tech Stack Documentation | Complete | `docs/architecture/tech-stack.md` |
| Source Tree/Architecture | Complete | `docs/architecture/source-tree.md` |
| Coding Standards | Complete | `docs/architecture/coding-standards.md` |
| API Documentation | Complete | `docs/api/reasoning-service-contract.md` + auto-generated Swagger for the Java backend |
| External API Documentation | N/A | MVP ingestion is manual/API-driven; no external source integration in scope |
| UX/UI Guidelines | N/A | No UI changes in scope this phase |
| Technical Debt Documentation | Checked | `docs/TECHNICAL-DEBT.md` — one active item (legacy admin factbase page cleanup), unrelated to this work |

### 1.4 Enhancement Scope Definition

**Enhancement Type:**
- [x] New Feature Addition
- [x] Integration with New Systems (production use of an existing-but-unused reasoning-service endpoint)

**Enhancement Description:**
Add persisted `Article` storage, link newly extracted entities back to their source article, and call the existing `/eval/bias/detect` endpoint at ingestion time to attach real bias/fallacy annotations — establishing a source-attributed evidence layer distinct from the government Factbase.

**Impact Assessment:**
- [x] Moderate Impact (some existing code changes) — new tables, a new ingestion pathway, and a change to how newly extracted `Entity` records populate their source linkage. No existing functionality is restructured.

### 1.5 Goals and Background Context

**Goals**
- Persist ingested articles as first-class, source-attributed evidence records
- Link newly extracted entities to their originating article via a real foreign key (not the current freeform string)
- Attach real bias/fallacy annotations per article via `/eval/bias/detect`, replacing the placeholder-score plan from the brief
- Guarantee a grounded-query interface that never silently blends evidence-backed and ungrounded content
- Enable the eval harness to validate extraction against real ingested content, not only synthetic data

**Background Context**

NewsAnalyzer has no way today to ground a claim in "here's what was reported, by whom, with what bias signal attached" — article text is processed and discarded. This blocks the political-analysis-agent vision explored in the 2026-07-01 brainstorming session (`docs/analysis/ES-1-brainstorming-session-results.md`) and formalized in `docs/analysis/ES-1-project-brief.md`.

Since drafting that brief, research for this PRD surfaced that the reliability/bias-scoring capability assumed to be future, unbuilt work in `noometric-intelligence` already exists as a stable, documented reasoning-service contract endpoint (`/eval/bias/detect`). This PRD upgrades the plan accordingly: rather than shipping a placeholder score, the MVP wires in real per-article bias/fallacy annotations, while deliberately deferring cross-article source-level aggregation (a genuinely harder problem, flagged in the brainstorm as carrying sampling-representativeness and correlated-bias risk).

---

## 2. Requirements

### Functional

1. FR1: The system shall provide an API endpoint to submit an article (source/outlet, URL, publication date, raw text) for ingestion and persistence.
2. FR2: Upon ingestion, the system shall call the existing reasoning-service `POST /entities/extract` endpoint against the persisted article text and persist the returned entities.
3. FR3: Upon ingestion, the system shall call the existing reasoning-service `POST /eval/bias/detect` endpoint against the persisted article text and persist the returned bias/fallacy annotations, linked to the article.
4. FR4: Each `Entity` record created from article-based extraction shall be linked to its originating `Article` via a foreign key, replacing reliance on the freeform `source` string for newly extracted entities.
5. FR5: The system shall expose a query interface that returns Evidence Store-backed results with citations to source articles, or an explicit "no grounded evidence found" response when no matching evidence exists — never a silent fallback to ungrounded content.
6. FR6: The system shall store per-article bias/fallacy annotations as structured, queryable data linked to the article — raw annotations only, not yet aggregated into a source-level score.
7. FR7: A nullable source-level reliability-score field shall exist in the schema for future cross-article aggregation, populated as null at MVP.
8. FR8: The evaluation harness shall be able to run entity-extraction validation against real ingested `Article` records in addition to `SyntheticArticle` records.

### Non Functional

1. NFR1: The enhancement must not degrade existing `/entities/extract` performance or exceed the documented 30s timeout budget for that call path.
2. NFR2: The new `/eval/bias/detect` call must respect its documented 60s timeout and must not block or fail article persistence if bias detection fails — the article and its extracted entities persist regardless; bias annotations are best-effort and retriable.
3. NFR3: Partial ingestion failures (extraction or bias-detection call failure) must be recorded as an explicit failure state, not silently dropped, so they can be retried.
4. NFR4: The grounded-query interface must guarantee zero silent blending — every response either cites Evidence Store data or explicitly signals no grounded evidence was found.
5. NFR5: Full article text retention must not assume an irreversible "store forever" policy — implementation should not preclude a future retention/redaction policy once the copyright question (flagged in the brief) is resolved.

### Compatibility Requirements

1. CR1: Existing `/api/entities` endpoints and DTOs continue to function unchanged; the new article FK on `Entity` must be nullable to preserve entities not sourced from an article.
2. CR2: New tables/columns are introduced via additive Flyway migrations only — no destructive changes to `entities` or `government_organizations`; the existing `Entity.source` string field remains untouched for legacy rows.
3. CR3: Not applicable for MVP — no UI changes are in scope this phase.
4. CR4: No changes to the `REASONING_SERVICE_URL` contract, its `X-Noometric-API-Key` auth, or existing consumers of `/entities/extract` and `/eval/bias/detect` (eval harness, nginx proxy routes) — this enhancement is a new consumer, not a contract change.

---

## 3. User Interface Enhancement Goals

Not applicable — this enhancement has no UI component. All work is backend/API/schema, per the Impact Assessment in Section 1.4.

---

## 4. Technical Constraints and Integration Requirements

### Existing Technology Stack

**Languages:** Java 17, TypeScript, Python 3.11
**Frameworks:** Spring Boot 3.2.2, Next.js 14/React 18, FastAPI
**Database:** PostgreSQL 15 (JSONB), Flyway migrations, Redis available (in-memory Caffeine used instead for the comparable Factbase-expansion precedent)
**Infrastructure:** Docker Compose (dev + Hetzner production), Nginx reverse proxy, OTel/Prometheus/Loki/Tempo/Grafana observability
**External Dependencies:** `noometric-intelligence` reasoning service via `REASONING_SERVICE_URL`, `X-Noometric-API-Key` auth

### Integration Approach

**Database Integration Strategy:** New Flyway migration(s) adding an `evidence_articles` table and a nullable `article_id` FK on `entities`, following the additive-only pattern already used for the government-org migrations (`V3`, `V4`). Naming follows existing conventions (`snake_case`, `idx_{table}_{columns}`, `fk_{table}_{columns}`). *(Named `evidence_articles`, not `articles` — a table by that name already exists from `V1__initial_schema.sql`, a dead design never wired to any application code, discovered during ES-1.1 implementation.)*

**API Integration Strategy:** New `ArticleController` under `/api/articles`, mirroring `EntityController`'s structure. `ArticleService` orchestrates: persist article → call `/entities/extract` → call `/eval/bias/detect` → persist entities and annotations, all linked via `article_id`.

**Frontend Integration Strategy:** None for MVP — no UI scope this phase.

**Testing Integration Strategy:** New `ArticleControllerTest`/`ArticleServiceTest`/`ArticleRepositoryTest`/`ArticleTest` mirroring the existing `Entity*Test` structure. Eval harness gains a real-article validation path alongside the existing `SyntheticArticle` path.

### Code Organization and Standards

**File Structure Approach:** Standard layered classes (`Article`, `ArticleRepository`, `ArticleService`, `ArticleController`, `ArticleDTO`, `CreateArticleRequest`) under the existing `model/`, `repository/`, `service/`, `controller/`, `dto/` packages — no new top-level package needed, since MVP ingestion is manual/API-driven, not a scheduled sync job.

**Naming Conventions:** Existing table/column/index/constraint conventions from `coding-standards.md`, no deviation.

**Coding Standards:** Constructor injection, `@Transactional(readOnly = true)` default with override for writes, `@JsonIgnore` on the lazy-loaded `Entity → Article` relation with the FK id exposed directly for API consumers — same pattern as `GovernmentOrganization`.

**Documentation Standards:** JavaDoc on public service/controller methods. `docs/api/reasoning-service-contract.md`'s "Called by" list for `/eval/bias/detect` needs a changelog entry once ingestion calls it in production.

### Deployment and Operations

**Build Process Integration:** No changes — standard Maven build within the existing CI/CD pipeline.

**Deployment Strategy:** Ships as part of the normal backend deploy; Flyway migration runs automatically on startup, per existing pattern.

**Monitoring and Logging:** Leverage the existing OTel/Prometheus/Loki/Tempo/Grafana stack — trace the new ingestion path and add basic success/failure and latency metrics for the bias-detect call, consistent with existing dashboards.

**Configuration Management:** No new environment variables — reuses the existing `REASONING_SERVICE_URL`/`NOOMETRIC_API_KEY` under the existing contract.

### Risk Assessment and Mitigation

**Technical Risks:** `/eval/bias/detect` has only ever been exercised by the eval harness against short article excerpts — production article length/characteristics are untested against it.

**Integration Risks:** Chaining two sequential reasoning-service calls (extract + bias-detect) per ingestion increases partial-failure surface area versus today's single-call pattern.

**Deployment Risks:** Low — additive migrations only, no destructive schema changes, no new infrastructure.

**Mitigation Strategies:** Best-effort bias detection (NFR2) and explicit failure-state tracking (NFR3) contain the partial-failure risk; keeping MVP ingestion manual/low-volume (rather than automated high-throughput) limits blast radius while `/eval/bias/detect`'s real-world behavior gets validated.

---

## 5. Epic and Story Structure

**Epic Structure Decision:** Single epic. All work (article persistence, entity linkage, bias-annotation integration, grounded-query interface, eval-harness integration) serves one coherent outcome — a working Evidence Store — and the stories are tightly sequential rather than representing multiple unrelated features.

---

## 6. Epic Details

### Epic 1: Evidence Store Foundation

**Epic Goal:** Establish a persisted, source-attributed Evidence Store — with real bias/fallacy signal from the existing reasoning-service contract — that unblocks the political-analysis-agent vision without introducing new methodology into this repo or breaking existing Entity/Factbase functionality.

**Integration Requirements:** All new endpoints/tables are additive. Existing `/api/entities` and `/api/government-orgs` behavior is unchanged. The `REASONING_SERVICE_URL` contract (both consumed endpoints, auth) is unchanged — this epic is a new consumer only, never a contract modification.

---

#### Story 1.1 Article Persistence Model

As a backend developer,
I want a persisted Article model and database schema,
so that ingested article content and metadata can be stored and referenced by other records.

**Acceptance Criteria**
1. An `Article` JPA entity exists with fields: id (UUID), source/outlet name, url, publicationDate, rawText, ingestedAt, following existing `Entity` model conventions.
2. A Flyway migration creates the `evidence_articles` table additively, following the naming and indexing conventions in `coding-standards.md`.
3. `Entity` gains a nullable `article_id` FK column via an additive migration, with `@JsonIgnore` on the lazy relation and the FK id exposed directly for API consumers, per the `GovernmentOrganization` pattern.
4. An `ArticleRepository` (Spring Data JPA) exists, mirroring `EntityRepository`'s structure, providing the persistence access used by Story 1.2 onward.
5. Unit tests (`ArticleTest`) verify entity mapping and constraints.

**Integration Verification**
- IV1: Existing `Entity` records with a null `article_id` continue to load, serialize, and pass through existing `EntityController`/`EntityService`/`EntityRepository` tests unchanged.
- IV2: Existing Flyway migration history is unaffected — the new migration runs cleanly on top of the current schema version with no destructive statements.
- IV3: No measurable startup or query performance regression on existing `/api/entities` endpoints.

#### Story 1.2 Article Ingestion API (Persistence Only)

As an internal developer or the eval harness,
I want to submit an article via an API endpoint and have it persisted,
so that real article content exists as a queryable record before any extraction or bias analysis runs.

**Acceptance Criteria**
1. `POST /api/articles` accepts source/outlet, url, publicationDate, and rawText, and persists a new `Article` record.
2. The endpoint returns the created `Article` per existing DTO/response conventions (`ArticleDTO`, mirroring `EntityDTO`).
3. Input validation follows existing Bean Validation patterns; invalid requests return 400 with a clear error body, per the existing `GlobalExceptionHandler` pattern.
4. No extraction or bias-detection calls occur at this stage — this story is persistence only.
5. Duplicate submissions (same URL and/or text submitted more than once) are **not deduplicated at MVP** — each submission creates a new `Article` record. This is a deliberate scope decision, not an oversight: determining what counts as a "duplicate" (same URL? same raw-text hash? same URL+publicationDate?) is itself a design question that shouldn't be rushed given MVP's low, manual ingestion volume where duplicates are both unlikely and low-cost. Deduplication is deferred to Phase 2 if real-world usage shows it's needed.
6. `ArticleControllerTest` and `ArticleServiceTest` cover creation, validation failure, and retrieval.

**Integration Verification**
- IV1: No existing `/api/entities` or `/api/government-orgs` endpoint behavior changes.
- IV2: New endpoint is documented via the existing OpenAPI/Swagger annotations, appearing correctly without disrupting existing entries.
- IV3: Existing test suite (`mvn test`) passes unchanged; no shared configuration modification beyond additive entries.

#### Story 1.3 Entity Extraction Integration

As an internal developer,
I want article ingestion to automatically call the existing entity-extraction endpoint and persist linked entities,
so that extracted entities are traceable back to the specific article they came from.

**Acceptance Criteria**
1. After an `Article` is persisted, `ArticleService` calls the existing reasoning-service `POST /entities/extract` with the article's rawText.
2. Returned entities are persisted as `Entity` records with `article_id` set to the source article (FR4).
3. The 30s timeout budget documented in `reasoning-service-contract.md` is respected (NFR1); no change to that contract.
4. If the extraction call fails, the `Article` itself still persists (per NFR3) and the failure is recorded in an explicit status field.
5. Integration test verifies the full flow: submit article → entities appear, correctly linked, in `/api/entities` queries.

**Integration Verification**
- IV1: Manually created entities and existing pre-migration entities remain unaffected.
- IV2: The existing eval-harness direct call path to `/entities/extract` (`spacy_provider.py`) is untouched.
- IV3: Existing reasoning-service test suite remains unaffected — no changes on that side of the boundary.

#### Story 1.4 Bias/Fallacy Annotation Integration

As an internal developer,
I want article ingestion to call the existing bias-detection endpoint and store the returned annotations,
so that each article carries a real, ontology-grounded bias signal instead of the placeholder score originally planned.

**Prerequisite:** Before implementation starts, confirm with `noometric-intelligence` whether `/eval/bias/detect` has a rate limit or quota that MVP's ingestion volume could realistically hit (identified as an open question during PO validation — the reasoning-service contract doesn't document one, and this story is the endpoint's first production caller).

**Acceptance Criteria**
1. After (or in parallel with) entity extraction, `ArticleService` calls the existing reasoning-service `POST /eval/bias/detect` with the article's rawText, `grounded: true`.
2. Returned annotations (distortion_type, category, excerpt, explanation, confidence, ontology_metadata) are persisted as structured records linked to the `Article` (FR6).
3. The call is best-effort per NFR2 — a failure or timeout does not block or roll back article/entity persistence; failure is recorded in an explicit status field, distinct from the extraction failure status (NFR3).
4. The nullable source-level `reliability_score` field (FR7) exists on the relevant schema but is not populated by this story — explicitly deferred, no aggregation logic implemented yet.
5. `docs/api/reasoning-service-contract.md` is updated to add "Article ingestion service (news-analyzer)" to the `/eval/bias/detect` "Called by" list.
6. Integration test verifies annotations are persisted and queryable per article; a simulated failure (e.g., timeout) is tested to confirm article/entity persistence still succeeds.

**Integration Verification**
- IV1: The existing eval-harness callers of `/eval/bias/detect` (`bias_provider.py`, `bias_provider_ungrounded.py`) are unaffected.
- IV2: Existing `/api/entities` and Story 1.3 extraction behavior are unaffected by bias-detection failures (verified via the failure-simulation test in AC6).
- IV3: No change to `REASONING_SERVICE_READ_TIMEOUT_LLM` or other documented timeout configuration — this story consumes existing configuration (CR4).

#### Story 1.5 Grounded-Query Interface

As a future consumer (e.g., the eval harness or a later agent),
I want to query the Evidence Store and receive either cited, grounded results or an explicit "no evidence found" response,
so that no consumer of this data can silently receive ungrounded or fabricated information.

**Acceptance Criteria**
1. A query endpoint/service method accepts a search term or entity reference and returns matching `Article`-backed evidence (article metadata, linked entities, linked bias annotations) with citations.
2. When no matching grounded evidence exists, the response is an explicit, structurally distinct "no grounded evidence found" result (FR5, NFR4).
3. Response schema clearly distinguishes grounded fields from any metadata about the query itself — no blending of grounded and ungrounded content in a single unlabeled field.
4. Unit and integration tests cover both the "evidence found" and "no evidence found" paths, including a regression test directly enforcing NFR4.

**Integration Verification**
- IV1: This is a new read-only endpoint — no existing endpoint's behavior changes.
- IV2: Query performance on existing `/api/entities` search endpoints is unaffected.
- IV3: Existing frontend (Knowledge Explorer) continues to function unchanged, since no frontend integration is in scope this phase.

#### Story 1.6 Eval Harness Real-Article Integration

As a developer validating extraction quality,
I want the eval harness to run entity-extraction validation against real ingested Article records,
so that pipeline quality is no longer validated only against synthetic data.

**Acceptance Criteria**
1. The eval harness gains a code path to source validation input from persisted `Article` records, alongside its existing `SyntheticArticle`-based path (FR8), coexisting rather than replacing it.
2. Documentation (`docs/evaluation-methodology.md`) is updated to describe the new real-article validation option.
3. At least one real ingested test article is used to demonstrate the full path end-to-end: ingest → extract → bias-detect → eval-harness validation read.

**Integration Verification**
- IV1: Existing eval harness runs against `SyntheticArticle` data continue to pass unchanged.
- IV2: No change to the eval harness's existing reasoning-service call patterns (`spacy_provider.py`, `bias_provider.py`).
- IV3: Existing eval dataset generation (`/eval/facts`, `/eval/articles` reasoning-service endpoints) is unaffected.

---

## 7. Change Log

| Change | Date | Version | Description | Author |
|---|---|---|---|---|
| Initial draft | 2026-07-02 | 0.1 | Created from `docs/analysis/ES-1-project-brief.md` via BMad `create-brownfield-prd` workflow | John (PM) / Steve Kosuth-Wood |
| PO validation fixes | 2026-07-02 | 0.2 | Added `ArticleRepository` to Story 1.1 AC; documented duplicate-submission behavior in Story 1.2; added rate-limit-confirmation prerequisite to Story 1.4 | Sarah (PO) / Steve Kosuth-Wood |
| Table rename reconciliation | 2026-07-03 | 0.3 | Renamed `articles` → `evidence_articles` throughout — Story ES-1.1 implementation discovered a pre-existing, unused table of that name from `V1__initial_schema.sql`. Reconciled after the fact; no requirements changed, only the table name. | Sarah (PO) / Steve Kosuth-Wood |
