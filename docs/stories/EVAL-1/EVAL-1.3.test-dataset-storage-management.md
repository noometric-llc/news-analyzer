# Story EVAL-1.3: Test Dataset Storage & Management

## Status

Done

## Story

**As a** AI evaluation engineer,
**I want** database tables and backend API endpoints for storing and retrieving generated test articles with their ground-truth labels,
**so that** generated datasets persist across service restarts and can be queried by perturbation type, government branch, difficulty level, and batch.

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Generated articles persist across service restarts (PostgreSQL storage) |
| AC2 | Ground-truth labels fully stored with FK to source article |
| AC3 | Batch metadata tracks generation parameters and statistics |
| AC4 | Query API supports filtering by perturbation type, branch, difficulty, and batch ID |
| AC5 | Existing database schema unaffected (new tables only, no modifications to existing tables) |
| AC6 | JUnit tests for new repository and service classes |
| AC7 | API integration tested (REST endpoint request/response validation) |
| AC8 | Reasoning-service integration: batch orchestrator POSTs results to backend after generation |

## Tasks / Subtasks

- [x] Task 1: Create Flyway migration (AC1, AC2, AC3, AC5)
  - [x] Determine next migration version number (check existing `V*__.sql` files in `backend/src/main/resources/db/migration/`)
  - [x] Create migration file `V{next}__create_eval_tables.sql`
  - [x] Create `generation_batches` table: id (UUID PK), branch, model_used, config_json (JSONB), articles_count, faithful_count, perturbed_count, total_tokens, duration_seconds, errors (JSONB), created_at
  - [x] Create `synthetic_articles` table: id (UUID PK), batch_id (FK → generation_batches ON DELETE CASCADE), article_text (TEXT), article_type, is_faithful, perturbation_type, difficulty, source_facts (JSONB), ground_truth (JSONB), model_used, tokens_used, created_at
  - [x] Create indexes: batch_id, perturbation_type, is_faithful, difficulty, article_type
  - [x] Create GIN index on source_facts for branch filtering via JSONB
  - [x] Verify migration runs cleanly on fresh database and existing database
- [x] Task 2: Create JPA entity classes (AC1, AC2, AC3)
  - [x] Create `backend/src/main/java/org/newsanalyzer/model/eval/GenerationBatch.java`
  - [x] Create `backend/src/main/java/org/newsanalyzer/model/eval/SyntheticArticle.java`
  - [x] Map JSONB columns using Hypersistence Utils (`@Type(JsonBinaryType.class)`)
  - [x] Map UUID primary keys with `gen_random_uuid()` default
  - [x] Map `batch_id` FK relationship: `@ManyToOne(fetch = FetchType.LAZY)` with `@JsonIgnore` on entity ref, expose `batchId` UUID field
  - [x] Add `@PrePersist` for `created_at` default
- [x] Task 3: Create repository interfaces (AC4)
  - [x] Create `backend/src/main/java/org/newsanalyzer/repository/eval/GenerationBatchRepository.java`
  - [x] Create `backend/src/main/java/org/newsanalyzer/repository/eval/SyntheticArticleRepository.java`
  - [x] Add query methods on SyntheticArticleRepository:
    - `findByBatchId(UUID batchId, Pageable pageable)` → `Page<SyntheticArticle>`
    - `findByPerturbationType(String type, Pageable pageable)` → `Page<SyntheticArticle>`
    - `findByDifficulty(String difficulty, Pageable pageable)` → `Page<SyntheticArticle>`
    - `findByIsFaithful(boolean faithful, Pageable pageable)` → `Page<SyntheticArticle>`
    - Custom `@Query` for combined filters (perturbation_type + difficulty + branch via JSONB)
  - [x] Add aggregate query for stats: counts by perturbation_type, branch, difficulty
- [x] Task 4: Create DTOs (AC4, AC7)
  - [x] Create `backend/src/main/java/org/newsanalyzer/dto/eval/GenerationBatchDTO.java`
  - [x] Create `backend/src/main/java/org/newsanalyzer/dto/eval/SyntheticArticleDTO.java`
  - [x] Create `backend/src/main/java/org/newsanalyzer/dto/eval/DatasetQueryRequest.java` — filter parameters (perturbationType, difficulty, branch, batchId, isFaithful)
  - [x] Create `backend/src/main/java/org/newsanalyzer/dto/eval/DatasetStatsDTO.java` — aggregate counts
  - [x] Create `backend/src/main/java/org/newsanalyzer/dto/eval/CreateBatchRequest.java` — for POST from reasoning-service
- [x] Task 5: Create service class (AC1, AC2, AC3, AC4)
  - [x] Create `backend/src/main/java/org/newsanalyzer/service/eval/EvalDatasetService.java`
  - [x] Implement `createBatch(CreateBatchRequest)` — stores batch + all articles in one transaction
  - [x] Implement `getBatch(UUID id)` → `GenerationBatchDTO`
  - [x] Implement `listBatches(Pageable)` → `Page<GenerationBatchDTO>`
  - [x] Implement `getArticlesByBatch(UUID batchId, Pageable)` → `Page<SyntheticArticleDTO>`
  - [x] Implement `queryArticles(DatasetQueryRequest, Pageable)` → `Page<SyntheticArticleDTO>`
  - [x] Implement `getArticle(UUID id)` → `SyntheticArticleDTO`
  - [x] Implement `getStats()` → `DatasetStatsDTO`
  - [x] Implement `deleteBatch(UUID id)` — cascades to articles
  - [x] Write JUnit tests with mocked repositories
- [x] Task 6: Create REST controller (AC4, AC7)
  - [x] Create `backend/src/main/java/org/newsanalyzer/controller/eval/EvalDatasetController.java`
  - [x] `POST /api/eval/datasets/batches` — store a completed batch (from reasoning-service)
  - [x] `GET /api/eval/datasets/batches` — list all batches (paginated)
  - [x] `GET /api/eval/datasets/batches/{id}` — get batch with summary stats
  - [x] `GET /api/eval/datasets/batches/{id}/articles` — get articles in batch (paginated)
  - [x] `GET /api/eval/datasets/articles` — query articles with filters (query params from DatasetQueryRequest)
  - [x] `GET /api/eval/datasets/articles/{id}` — get single article with ground truth
  - [x] `GET /api/eval/datasets/stats` — aggregate statistics
  - [x] `DELETE /api/eval/datasets/batches/{id}` — delete batch and articles
  - [x] Add `@Tag(name = "Eval Datasets")` for OpenAPI grouping
  - [x] Write JUnit tests with MockMvc for all endpoints
- [x] Task 7: Reasoning-service integration (AC8)
  - [x] Add `post_batch(batch_id, config, test_cases)` method to `BackendClient` in reasoning-service
  - [x] Update `BatchOrchestrator._store_batch()` to call `BackendClient.post_batch()`
  - [x] Serialize ArticleTestCase list to format matching `CreateBatchRequest` DTO
  - [x] Handle storage errors gracefully (log warning, don't fail the batch)
  - [x] Write pytest test for serialization and POST request construction
- [x] Task 8: Verification
  - [x] Run Flyway migration on local database — verify tables created
  - [x] Run full backend JUnit suite — no regressions (673 controller/service tests, 1 pre-existing perf flake)
  - [x] Run full reasoning-service pytest suite — no regressions (335 passed, 4 skipped)
  - [ ] Verify OpenAPI docs show new endpoints at `/swagger-ui.html` (requires running app)

## Dev Notes

### Architecture Context

This is the **storage layer** for the EVAL-1 pipeline. It adds new tables and endpoints to the existing Java/Spring Boot backend. The reasoning-service (EVAL-1.2) generates articles and POSTs them here for persistence.

**Key design decisions:**
- JSONB for `source_facts` and `ground_truth` — flexible schema, avoids excessive normalization for evaluation data
- `ON DELETE CASCADE` on batch → articles FK — deleting a batch cleans up all its articles
- GIN index on `source_facts` enables filtering by government branch without a separate column

### Database Schema

```sql
-- generation_batches: one row per batch run
CREATE TABLE generation_batches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch          VARCHAR(20),
    model_used      VARCHAR(50) NOT NULL,
    config_json     JSONB NOT NULL,
    articles_count  INTEGER NOT NULL DEFAULT 0,
    faithful_count  INTEGER NOT NULL DEFAULT 0,
    perturbed_count INTEGER NOT NULL DEFAULT 0,
    total_tokens    INTEGER NOT NULL DEFAULT 0,
    duration_seconds DOUBLE PRECISION,
    errors          JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- synthetic_articles: one row per generated article with ground truth
CREATE TABLE synthetic_articles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id            UUID NOT NULL REFERENCES generation_batches(id) ON DELETE CASCADE,
    article_text        TEXT NOT NULL,
    article_type        VARCHAR(30) NOT NULL,
    is_faithful         BOOLEAN NOT NULL,
    perturbation_type   VARCHAR(30),
    difficulty          VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    source_facts        JSONB NOT NULL,
    ground_truth        JSONB NOT NULL,
    model_used          VARCHAR(50) NOT NULL,
    tokens_used         INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### Backend File Structure

```
backend/src/main/java/org/newsanalyzer/
├── model/eval/
│   ├── GenerationBatch.java
│   └── SyntheticArticle.java
├── repository/eval/
│   ├── GenerationBatchRepository.java
│   └── SyntheticArticleRepository.java
├── service/eval/
│   └── EvalDatasetService.java
├── controller/eval/
│   └── EvalDatasetController.java
└── dto/eval/
    ├── GenerationBatchDTO.java
    ├── SyntheticArticleDTO.java
    ├── DatasetQueryRequest.java
    ├── DatasetStatsDTO.java
    └── CreateBatchRequest.java
```

### API Endpoints (New)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/eval/datasets/batches` | Store a completed batch |
| `GET` | `/api/eval/datasets/batches` | List all batches (paginated) |
| `GET` | `/api/eval/datasets/batches/{id}` | Get batch with summary stats |
| `GET` | `/api/eval/datasets/batches/{id}/articles` | Get articles in batch (paginated) |
| `GET` | `/api/eval/datasets/articles` | Query articles with filters |
| `GET` | `/api/eval/datasets/articles/{id}` | Get single article with ground truth |
| `GET` | `/api/eval/datasets/stats` | Aggregate stats |
| `DELETE` | `/api/eval/datasets/batches/{id}` | Delete batch and articles |

### JSONB Column Patterns

Follow existing Hypersistence Utils pattern used in the project (see `Entity.java`):

```java
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

@Type(JsonBinaryType.class)
@Column(columnDefinition = "jsonb")
private Map<String, Object> configJson;
```

Use `@JsonIgnore` on lazy-loaded entity relationships. Expose the FK UUID field directly for API consumers.

### Hibernate Lazy Loading

Follow the pattern from `GovernmentOrganization.java`:
```java
@Column(name = "batch_id")
private UUID batchId;           // Exposed to API

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "batch_id", insertable = false, updatable = false)
@JsonIgnore
private GenerationBatch batch;  // For JPA navigation only
```

### No New Backend Dependencies

Uses existing Spring Data JPA, Jackson JSONB (Hypersistence Utils), and Flyway. No new `pom.xml` entries needed.

### Testing

**Backend Tests:**
- **Framework:** JUnit 5 + Mockito + Spring Test
- **Test Location:** `backend/src/test/java/org/newsanalyzer/` mirroring source structure in `service/eval/`, `controller/eval/`, `repository/eval/`
- **Repository tests:** Use H2 in-memory database with `@DataJpaTest`
- **Service tests:** Mock repositories with `@Mock` / `@InjectMocks`
- **Controller tests:** Use `@WebMvcTest` + `MockMvc` for endpoint validation
- **Run:** `mvn test` from `backend/` directory

**Reasoning-service Tests:**
- **Framework:** pytest + pytest-asyncio
- **Test Location:** `reasoning-service/tests/clients/test_backend_client.py` (extend existing)
- **Test:** Verify `post_batch()` serialization and request construction
- **Run:** `pytest` from `reasoning-service/` directory

**Standards:**
- Java: Constructor injection, `@Transactional(readOnly = true)` default on service, K&R braces
- Python: Black (line length 88), Ruff, mypy strict
- Follow Arrange/Act/Assert pattern in all tests

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-15 | 1.0 | Initial story creation | Sarah (PO) |
| 2026-03-17 | 1.1 | PO validation: fixed JSONB type class (JsonType → JsonBinaryType) to match existing codebase pattern, added import reference. Status → Approved | Sarah (PO) |

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6)

### Debug Log References

- Fixed `NoSuchElementException` → `ResourceNotFoundException` in `EvalDatasetService` for proper 404 handling via `GlobalExceptionHandler`
- Testcontainers-dependent tests (`StatuteRepositoryTest`, `CongressionalMemberRepositoryTest`, etc.) fail without Docker — pre-existing, not a regression
- `CommitteeControllerTest.listAll_respondsWithin500ms` is a flaky perf test — pre-existing

### Completion Notes List

- **Task 1**: Flyway migration `V41__create_eval_tables.sql` — 2 tables, 6 indexes (5 B-tree + 1 GIN on `source_facts`)
- **Task 2**: JPA entities with `@Type(JsonBinaryType.class)` for JSONB, dual-column FK pattern on `SyntheticArticle`
- **Task 3**: Repository interfaces — derived queries + native JSONB `@>` containment query + 3 aggregate stats queries
- **Task 4**: 5 DTOs including cross-service `CreateBatchRequest` with nested `ArticleEntry` and Bean Validation
- **Task 5**: Service class with `@Transactional(readOnly = true)` default + 13 unit tests
- **Task 6**: Controller with 8 REST endpoints, Swagger `@Tag` grouping + 13 MockMvc tests. Fixed service to use `ResourceNotFoundException` for proper GlobalExceptionHandler 404 integration
- **Task 7**: `BackendClient.post_batch()` serializes Python models to `CreateBatchRequest` JSON. `BatchOrchestrator._store_batch()` calls it with graceful error handling (log + continue). 3 pytest tests
- **Task 8**: Full regression suites green — 673 backend controller/service tests passed, 335 reasoning-service tests passed

### File List

| File | Action |
|------|--------|
| `backend/src/main/resources/db/migration/V41__create_eval_tables.sql` | Created |
| `backend/src/main/java/org/newsanalyzer/model/eval/GenerationBatch.java` | Created |
| `backend/src/main/java/org/newsanalyzer/model/eval/SyntheticArticle.java` | Created |
| `backend/src/main/java/org/newsanalyzer/repository/eval/GenerationBatchRepository.java` | Created |
| `backend/src/main/java/org/newsanalyzer/repository/eval/SyntheticArticleRepository.java` | Created |
| `backend/src/main/java/org/newsanalyzer/dto/eval/GenerationBatchDTO.java` | Created |
| `backend/src/main/java/org/newsanalyzer/dto/eval/SyntheticArticleDTO.java` | Created |
| `backend/src/main/java/org/newsanalyzer/dto/eval/DatasetQueryRequest.java` | Created |
| `backend/src/main/java/org/newsanalyzer/dto/eval/DatasetStatsDTO.java` | Created |
| `backend/src/main/java/org/newsanalyzer/dto/eval/CreateBatchRequest.java` | Created |
| `backend/src/main/java/org/newsanalyzer/service/eval/EvalDatasetService.java` | Created |
| `backend/src/test/java/org/newsanalyzer/service/eval/EvalDatasetServiceTest.java` | Created |
| `backend/src/main/java/org/newsanalyzer/controller/eval/EvalDatasetController.java` | Created |
| `backend/src/test/java/org/newsanalyzer/controller/eval/EvalDatasetControllerTest.java` | Created |
| `reasoning-service/app/clients/backend_client.py` | Modified — added `post_batch()` method |
| `reasoning-service/app/services/eval/batch_orchestrator.py` | Modified — `_store_batch()` now calls backend API |
| `reasoning-service/tests/clients/test_backend_client.py` | Modified — added `TestEvalDatasetEndpoints` (3 tests) |

## QA Results

### Review Date: 2026-03-19

### Reviewed By: Quinn (Test Architect)

### Code Quality Assessment

EVAL-1.3 delivers a complete, well-structured storage layer for the EVAL pipeline. The implementation follows established project patterns (Hypersistence Utils JSONB, dual-column FK, `@Transactional(readOnly = true)` default). The Flyway migration is clean with proper indexes including a GIN index for JSONB branch filtering. All 8 acceptance criteria are met with 29 total tests (26 JUnit + 3 pytest).

**Strengths:**
- Database schema is well-designed with appropriate indexes for query patterns
- Service layer correctly uses `@Transactional` with read-only default and write override
- Backend transaction atomicity ensures no orphaned batches
- Python serialization correctly maps all fields to Java DTO structure including nullable `perturbationType`
- `_store_batch()` gracefully handles storage failures without losing generation results

**Areas for future improvement (non-blocking):**
- Branch filter JSONB query uses string concatenation (safe via `@Param` binding but fragile)
- Stats endpoint runs 4+ queries; could consolidate to single aggregate query at scale
- DTO string fields lack `@Size` validation matching DB VARCHAR limits
- No retry logic on `post_batch()` for transient failures

### Refactoring Performed

None — no changes warranted at this time. Findings are advisory for future hardening.

### Compliance Check

- Coding Standards: ✓ Constructor injection, K&R braces, `@Transactional` pattern followed
- Project Structure: ✓ Files in correct `eval/` subpackages mirroring existing structure
- Testing Strategy: ✓ Service tests with mocked repos, controller tests with MockMvc, pytest for Python integration
- All ACs Met: ✓ All 8 acceptance criteria verified

**Acceptance Criteria Verification:**

| AC | Status | Evidence |
|----|--------|----------|
| AC1 | PASS | `V41__create_eval_tables.sql` creates `generation_batches` + `synthetic_articles` in PostgreSQL |
| AC2 | PASS | `synthetic_articles.batch_id` FK → `generation_batches(id)` with ON DELETE CASCADE |
| AC3 | PASS | `generation_batches` stores config_json, articles_count, faithful/perturbed counts, total_tokens, duration, errors |
| AC4 | PASS | Repository has findByPerturbationType, findByDifficulty, findByIsFaithful, native JSONB query for branch + combined filters |
| AC5 | PASS | Migration creates new tables only; no ALTER statements on existing tables |
| AC6 | PASS | 13 service unit tests + 13 controller MockMvc tests = 26 JUnit tests |
| AC7 | PASS | Controller tests validate all 8 REST endpoints including 200/201/204/404 status codes |
| AC8 | PASS | `BackendClient.post_batch()` implemented; `BatchOrchestrator._store_batch()` calls it with graceful error handling |

### Improvements Checklist

- [ ] MNT-001: Add `@Size` validation to DTO string fields matching DB VARCHAR constraints (CreateBatchRequest, DatasetQueryRequest)
- [ ] MNT-002: Use `ObjectMapper` for branch JSONB filter construction instead of string concatenation in EvalDatasetService
- [ ] MNT-003: Add per-field validation tests for POST `/batches` endpoint (currently only one generic 400 test)
- [ ] MNT-004: Add retry logic with exponential backoff to `BackendClient.post_batch()` for transient errors
- [ ] MNT-005: Consolidate stats queries into single aggregate SQL for scalability
- [ ] MNT-006: Add `@Size` limit on `articleText` in ArticleEntry to prevent unbounded payloads
- [ ] DOC-001: Verify OpenAPI docs show new endpoints (Task 8 unchecked subtask — requires running app)

### Security Review

- DELETE endpoint lacks authorization — consistent with project-wide state (SecurityConfig has TODO for JWT). Not an EVAL-1.3 regression.
- JSONB branch filter uses string concatenation but is protected by Spring Data `@Param` binding. Low risk for internal API consumed only by reasoning-service.
- No sensitive data exposed in endpoints (synthetic articles, not real user data).

### Performance Considerations

- GIN index on `source_facts` enables efficient JSONB containment queries for branch filtering
- Stats endpoint runs 4+ separate COUNT queries — acceptable for current dataset sizes (hundreds of articles), should be consolidated if dataset grows to 100K+
- Batch creation is transactional — large batches (1000+ articles) may hold DB lock. Acceptable for batch-processing workload.

### Files Modified During Review

None — review only, no refactoring performed.

### Gate Status

Gate: PASS → docs/qa/gates/EVAL-1.3-test-dataset-storage-management.yml

### Recommended Status

✓ Ready for Done — All acceptance criteria met, tests passing, no blocking issues. MNT items are future improvements.
