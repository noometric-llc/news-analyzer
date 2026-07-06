# Story ES-1.3: Entity Extraction Integration

## Status

Ready for Done

## Story

**As an** internal developer,
**I want** article ingestion to automatically call the existing entity-extraction endpoint and persist linked entities,
**so that** extracted entities are traceable back to the specific article they came from.

## Acceptance Criteria

1. After an `Article` is persisted, `ArticleService` calls the existing reasoning-service `POST /entities/extract` with the article's rawText.
2. Returned entities are persisted as `Entity` records with `article_id` set to the source article (FR4).
3. The 30s timeout budget documented in `reasoning-service-contract.md` is respected (NFR1); no change to that contract.
4. If the extraction call fails, the `Article` itself still persists (per NFR3) and the failure is recorded in an explicit status field.
5. Integration test verifies the full flow: submit article → entities appear, correctly linked, in `/api/entities` queries.
6. `POST /api/articles` is rate limited — this endpoint now triggers a real external HTTP call, closing the cost-bearing risk explicitly deferred from Story ES-1.2.

### Integration Verification

- **IV1:** Manually created entities and existing pre-migration entities remain unaffected.
- **IV2:** The existing eval-harness direct call path to `/entities/extract` (`spacy_provider.py`) is untouched.
- **IV3:** Existing reasoning-service test suite remains unaffected — no changes on that side of the boundary.

## Tasks / Subtasks

- [x] Task 1: Fix and complete reasoning-service configuration (AC: 3)
  - [x] Fix `application-dev.yml`'s existing `reasoning-service.timeout: 5000` → `30000` (matches NFR1's documented 30s budget — the existing value was simply wrong)
  - [x] Add the missing `reasoning-service` section to base `application.yml` (env-var overridable: `${REASONING_SERVICE_URL:...}`, `${NOOMETRIC_API_KEY:}`)
  - [x] Create `ReasoningServiceConfig.java` (`@ConfigurationProperties(prefix = "reasoning-service")`), mirroring `CongressApiConfig`'s shape: `baseUrl`, `apiKey`, `timeout`, `isConfigured()`
- [x] Task 2: Create extraction request/response models (AC: 1)
  - [x] `ExtractedEntityData` — `text`, `entityType`, `start`, `end`, `confidence`, `schemaOrgType`, `schemaOrgData`, `properties`, mapped from the contract's snake_case JSON
  - [x] `EntityExtractionResponse` — `entities: List<ExtractedEntityData>`, `totalCount`
- [x] Task 3: Create `ReasoningServiceClient` (AC: 1, 3)
  - [x] `extractEntities(String text, float confidenceThreshold)` → `EntityExtractionResponse`
  - [x] `RestTemplate` via `RestTemplateBuilder`, 30s timeout, `X-Noometric-API-Key` header — mirrors `CongressApiClient`'s **actual verified** pattern, not the aspirational `@Retryable` style
- [x] Task 4: Extend `EntityService` with one new method (AC: 2)
  - [x] `createEntityFromExtraction(ExtractedEntityData extracted, UUID articleId)` — maps extraction data directly to a new `Entity`, sets `articleId`; existing `/api/entities` surface and `CreateEntityRequest` are untouched
  - [x] Map reasoning-service's lowercase `entity_type` strings (`"person"`, `"government_org"`, etc.) to the internal `EntityType` enum via `EntityType.valueOf(rawType.toUpperCase())`
- [x] Task 5: Restructure `ArticleService.createArticle()` into separate transactional steps (AC: 1, 2, 4) — **the must-fix pattern from architecture review**
  - [x] Step 1 (`@Transactional`, short): persist `Article` (unchanged from ES-1.2)
  - [x] Step 2 (no transaction): call `ReasoningServiceClient.extractEntities()`
  - [x] Step 3 (`@Transactional`, short): on success, persist extracted entities via `EntityService.createEntityFromExtraction()` and set `extractionStatus = SUCCESS`; on failure/timeout, catch it and set `extractionStatus = FAILED` — the `Article` from Step 1 persists regardless
  - [x] **Explicit non-negotiable**: no single method may hold a DB transaction open across the external HTTP call
- [x] Task 6: Add rate limiting to `/api/articles` (AC: 6)
  - [x] Hand-rolled in-memory limiter (e.g., a sliding-window counter in a singleton `@Component`) — global, not per-IP, given MVP's actual risk is aggregate request volume driving up LLM cost, not any single bad actor
  - [x] Exceeding the limit returns 429 Too Many Requests
- [x] Task 7: Write `ReasoningServiceClientTest`
  - [x] Mock the HTTP layer (`MockRestServiceServer` or Mockito) — verify request shape (`text`, `confidence_threshold`, auth header), response parsing, timeout configuration
- [x] Task 8: Update `ArticleServiceTest`
  - [x] Extraction success: entities created and linked via `article_id`
  - [x] Extraction failure: `Article` still persists, `extractionStatus = FAILED`, no entities created
- [x] Task 9: Integration test for the full flow (AC: 5)
  - [x] Real DB (Testcontainers, mirroring `ArticleRepositoryTest`), mocked `ReasoningServiceClient` (HTTP boundary only) — submit article → verify entities appear, correctly linked, queryable via `EntityRepository`
- [x] Task 10: Regression verification (IV1, IV2, IV3)
  - [x] Full existing suite (843 tests as of ES-1.2) passes unchanged
  - [x] Eval harness's direct `/entities/extract` call path (`spacy_provider.py`) untouched — no reasoning-service-side changes
  - [x] Reasoning-service test suite unaffected — this story only adds a Java-side caller

## Dev Notes

Pulled directly from `docs/prd/ES-1.md`, `docs/architecture/ES-1-ARCHITECT-HANDOFF.md`, `docs/architecture/coding-standards.md`, `docs/api/reasoning-service-contract.md`, and verified against actual code (`CongressApiClient.java`, `CongressApiConfig.java`, `EntityService.java`, `EntityType.java`, `application.yml`/`application-dev.yml`) — no invented details.

**Relevant Source Tree** (new/modified files):
```
backend/src/main/java/org/newsanalyzer/
├── config/
│   └── ReasoningServiceConfig.java        # NEW
├── dto/
│   ├── ExtractedEntityData.java           # NEW
│   └── EntityExtractionResponse.java      # NEW
├── service/
│   ├── ReasoningServiceClient.java        # NEW — first Java-side reasoning-service caller
│   ├── ArticleService.java                # MODIFIED — restructured into separate transactional steps
│   └── EntityService.java                 # MODIFIED — new createEntityFromExtraction() method
└── (rate limiter component, exact location TBD by implementer)
backend/src/main/resources/
├── application.yml                        # MODIFIED — add missing reasoning-service section
└── application-dev.yml                    # MODIFIED — fix timeout 5000 → 30000
backend/src/test/java/org/newsanalyzer/
├── service/ReasoningServiceClientTest.java # NEW
├── service/ArticleServiceTest.java        # MODIFIED — extraction success/failure cases
└── (new integration test for AC5 — real DB + mocked HTTP boundary)
```

**What Already Exists (from ES-1.1/ES-1.2 — do not recreate):**
- `Article`, `ArticleStatus`, `ArticleRepository`, `ArticleDTO`, `CreateArticleRequest`, `ArticleService.createArticle()` (persistence-only version — this story restructures it), `ArticleController` — all done, 843 tests passing
- `Entity.articleId`/`Entity.article` relation — done since ES-1.1, currently always `null` (nothing populates it yet — this story is the first to do so)

**Existing Patterns to Mirror (verified against actual code, not assumed):**
- **HTTP client pattern:** `CongressApiClient` — plain `RestTemplate` built via `RestTemplateBuilder.setConnectTimeout(...).setReadTimeout(...)`, manual retry/rate-limit tracking with `AtomicInteger`/`AtomicLong`. **Not** Spring's `@Retryable`/`@CircuitBreaker` — that pattern was proposed in the Factbase handoff doc but never actually implemented anywhere in this codebase.
- **Config pattern:** `CongressApiConfig` — `@Configuration @ConfigurationProperties(prefix = "...") @Data`, with an `isConfigured()` helper checking the API key is non-empty.
- **Config gap found during drafting:** `application-dev.yml` already has a `reasoning-service` section, but `timeout: 5000` (5s) contradicts NFR1's documented 30s budget — this is a plain error to fix, not a design choice. The base `application.yml` has no `reasoning-service` section at all, and there is zero existing wiring for `NOOMETRIC_API_KEY` anywhere in the backend (confirmed via full-codebase search) — this story adds it for the first time.
- **Entity type mapping:** `EntityType` is a plain Java enum (`PERSON`, `GOVERNMENT_ORG`, `ORGANIZATION`, `LOCATION`, `EVENT`, `CONCEPT`) with no custom string conversion. The contract's lowercase snake_case values map directly via `EntityType.valueOf(rawType.toUpperCase())` — no converter class needed (unlike `ArticleStatus`, which does need one).
- **Transaction boundary (must-fix, not optional):** the architecture review explicitly flagged that `ArticleService`'s pipeline must not hold one `@Transactional` method open across the ~30s external HTTP call. This is the single most important thing to get right in this story — see Task 5.

**Key Constraints:**
- 30s timeout for `/entities/extract` (NFR1) — no change to the reasoning-service contract itself, just correct client-side configuration
- Extraction failure must never block `Article` persistence (NFR3) — `extractionStatus` explicitly distinguishes this from success
- Rate limiting is global, in-memory, hand-rolled — no new dependency, per architecture doc guidance and this story's drafting decision
- No changes to `docs/api/reasoning-service-contract.md`'s documented request/response shape — this story is a new consumer, not a contract change

**Previous Story Context (ES-1.1, ES-1.2):** Both `Ready for Done`, clean QA gates (100, 95). ES-1.2 built persistence-only ingestion; this story adds the first real reasoning-service integration on top of it.

### Testing

- **Test file locations:** `backend/src/test/java/org/newsanalyzer/service/ReasoningServiceClientTest.java`, updates to `ArticleServiceTest.java`, new integration test for AC5
- **Test standards:** JUnit 5 + Mockito, per `coding-standards.md`
- **Frameworks/patterns:** `ReasoningServiceClientTest` mocks the HTTP layer (no live reasoning-service dependency in tests). The AC5 integration test needs a real database (Testcontainers, mirroring `ArticleRepositoryTest`) but a mocked `ReasoningServiceClient` — real persistence, fake external call.
- **Specific requirement:** Full existing suite (843 tests) must pass unchanged.

## Change Log

| Date | Version | Description | Author |
|---|---|---|---|
| 2026-07-04 | 0.1 | Initial draft, created from `docs/prd/ES-1.md` and `docs/architecture/ES-1-ARCHITECT-HANDOFF.md`, following the ES-1.1/ES-1.2 pattern. Added AC6 (rate limiting) not present in the PRD's literal text, carried over from ES-1.2's explicit deferral decision. Found and will fix an existing config error (`reasoning-service.timeout: 5000` vs. NFR1's 30000). | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-04 | 0.2 | Status: Draft → Approved — cleared for dev agent pickup | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-05 | 1.0 | All 10 tasks implemented and verified. Status: Approved → Ready for Review. | James (Dev Agent) |
| 2026-07-06 | 1.1 | Applied QA fixes for both CONCERNS findings from the 2026-07-05 gate: (1) wired `NOOMETRIC_API_KEY` through `deploy/dev/docker-compose.yml`, `deploy/production/docker-compose.yml`, and `deploy/production/docker-compose.build.yml`; (2) made entity-extraction batch persistence atomic (`EntityService.createEntitiesFromExtraction()`) so a mid-batch mapping failure rolls back the whole batch instead of leaving earlier entities orphaned under a `FAILED` status. Added test coverage for both (`EntityServiceTest` x4, `ArticleExtractionIntegrationTest` x1 proving real-DB rollback). Status: Changes Required → Ready for Review. | James (Dev Agent) |
| 2026-07-06 | 1.2 | QA re-review: both fixes independently verified (compose files re-grepped directly; transaction/self-invocation mechanics hand-traced). One stale Javadoc found and fixed (still described pre-fix per-entity-transaction behavior). Gate: CONCERNS → PASS (quality score 100). Status: Ready for Review → Ready for Done. | Sarah (PO) / Quinn (QA) |

## Dev Agent Record

### Agent Model Used

James (Dev persona) / Claude Sonnet 5

### Debug Log References

No blocking failures. Targeted test runs after each task; full regression run at Task 10.

### Completion Notes List

- Task 1: Fixed `application-dev.yml`'s `reasoning-service.timeout` (5000 → 30000). Discovered the base `application.yml` needed the `reasoning-service` section instead of `application-dev.yml`, because a missing `application-prod.yml` means profile-specific config silently doesn't load in production — logged as `TD-002` in `docs/TECHNICAL-DEBT.md` per user direction (flag only, do not fix in this story).
- Task 3/Task 7: `ReasoningServiceConfig` deliberately exposes a `RestTemplate` `@Bean` (rather than having `ReasoningServiceClient` build one internally, as `CongressApiClient` does) so `ReasoningServiceClientTest` can use a real `MockRestServiceServer` for genuine request/response verification — `CongressApiClientTest` documents in a comment that it couldn't do this with the internal-construction pattern. See `ReasoningServiceConfig`'s Javadoc for the full rationale.
- Task 5: `ArticleService.createArticle()` intentionally has no `@Transactional` annotation on the orchestrating method — the two DB-touching steps (`articleRepository.save()`, `EntityService.createEntityFromExtraction()`) each get their own short transaction via Spring Data JPA/`@Transactional`-on-`EntityService`, so no transaction is held open across the ~30s external HTTP call. This is the must-fix pattern called out in the architecture review.
- Task 9: AC5's literal text says entities should be verifiable "in `/api/entities` queries," but Task 9's own subtask description (and the actual implementation) verifies linkage via `EntityRepository` directly rather than through the `/api/entities` REST endpoint — there is no existing "list entities by article" query endpoint, and adding one was out of scope for this story. Flagging this AC-wording/Task-wording mismatch for QA visibility rather than silently resolving it.
- Task 10: Full regression run: 853 tests, 0 failures, 0 errors (up from 843 at ES-1.2 — 10 new tests added: 4 `ReasoningServiceClientTest`, 3 new `ArticleServiceTest` cases, 1 new `ArticleControllerTest` 429 case, 2 `ArticleExtractionIntegrationTest`).

### QA Fix Round (2026-07-06)

Applied fixes for both CONCERNS-level findings from Quinn's 2026-07-05 review (`docs/qa/gates/ES-1.3-entity-extraction-integration.yml`):

- **Fix 1 — `NOOMETRIC_API_KEY` docker-compose wiring:** QA traced the full host→container path and found `application.yml`'s `reasoning-service.api-key: ${NOOMETRIC_API_KEY:}` binding was correct, but no compose file actually forwarded that variable into the container's environment (unlike `REASONING_SERVICE_URL`/`CONGRESS_API_KEY`, which were both wired). Added `NOOMETRIC_API_KEY` to the backend service's `environment:` block in `deploy/dev/docker-compose.yml`, `deploy/production/docker-compose.yml`, and (not explicitly named by QA but found to have the identical gap during the fix) `deploy/production/docker-compose.build.yml`.
- **Fix 2 — atomic entity-extraction batch persistence:** Per user's explicit design decision (presented 3 options: all-or-nothing batch / best-effort-skip-and-continue / accept-and-document — user chose all-or-nothing), replaced `ArticleService`'s per-entity loop with a single call to a new `EntityService.createEntitiesFromExtraction(List<ExtractedEntityData>, UUID)` method. That method is `@Transactional` and internally calls the existing (unchanged) `createEntityFromExtraction()` per entity via a self-invocation that still participates in the ambient transaction (no proxy bypass concern here since the transaction boundary itself lives on the outer batch method, which *is* invoked cross-bean from `ArticleService`). Result: if any single entity fails to map (e.g. an `entity_type` value `EntityType.valueOf()` doesn't recognize), the whole batch rolls back — `extractionStatus=FAILED` now always means zero entities from that extraction attempt exist in the database, restoring the invariant Task 8 originally described.
- Added test coverage that was missing even before this fix round: `EntityServiceTest` had no direct tests for `createEntityFromExtraction()` at all (it was only ever exercised indirectly through mocked `ArticleServiceTest` calls) — added 4 new tests covering the single-entity method, the unrecognized-entity_type failure case, the all-valid batch case, and the batch-aborts-at-first-failure case. Also extended `ArticleExtractionIntegrationTest` with a new test (`testCreateArticle_oneEntityInBatchFailsToMap_wholeBatchRolledBack`) that proves the rollback against a real Postgres transaction manager — the Mockito-based unit test can show the batch method throws and stops early, but only a real database can prove the already-processed entity actually gets rolled back rather than merely "not further added to."
- Full regression after fixes: 858 tests, 0 failures, 0 errors (up from 853 — 5 new tests: 4 `EntityServiceTest`, 1 `ArticleExtractionIntegrationTest`).

### File List

**New:**
- `backend/src/main/java/org/newsanalyzer/config/ReasoningServiceConfig.java`
- `backend/src/main/java/org/newsanalyzer/dto/ExtractedEntityData.java`
- `backend/src/main/java/org/newsanalyzer/dto/EntityExtractionResponse.java`
- `backend/src/main/java/org/newsanalyzer/service/ReasoningServiceClient.java`
- `backend/src/main/java/org/newsanalyzer/service/ArticleIngestionRateLimiter.java`
- `backend/src/test/java/org/newsanalyzer/service/ReasoningServiceClientTest.java`
- `backend/src/test/java/org/newsanalyzer/service/ArticleExtractionIntegrationTest.java`

**Modified:**
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/java/org/newsanalyzer/service/EntityService.java` (QA fix round: added `createEntitiesFromExtraction()` atomic batch method)
- `backend/src/main/java/org/newsanalyzer/service/ArticleService.java` (QA fix round: calls the new atomic batch method instead of looping per-entity)
- `backend/src/main/java/org/newsanalyzer/controller/ArticleController.java` (QA review: stale Javadoc fix)
- `backend/src/test/java/org/newsanalyzer/service/ArticleServiceTest.java` (QA fix round: updated 2 tests for the new batch method call)
- `backend/src/test/java/org/newsanalyzer/service/EntityServiceTest.java` (QA fix round: 4 new tests)
- `backend/src/test/java/org/newsanalyzer/service/ArticleExtractionIntegrationTest.java` (QA fix round: 1 new test proving real-DB atomic rollback)
- `backend/src/test/java/org/newsanalyzer/controller/ArticleControllerTest.java`
- `docs/TECHNICAL-DEBT.md` (TD-002 entry)
- `deploy/dev/docker-compose.yml` (QA fix round: `NOOMETRIC_API_KEY` wiring)
- `deploy/production/docker-compose.yml` (QA fix round: `NOOMETRIC_API_KEY` wiring)
- `deploy/production/docker-compose.build.yml` (QA fix round: `NOOMETRIC_API_KEY` wiring)

## QA Results

### Review Date: 2026-07-05

### Reviewed By: Quinn (Test Architect)

### Code Quality Assessment

Solid implementation of the story's most important architectural constraint: `ArticleService.createArticle()` correctly avoids holding a DB transaction open across the external HTTP call, with clear Javadoc explaining why. The DTOs map the reasoning-service contract exactly (verified field-by-field against `docs/api/reasoning-service-contract.md`). The decision to expose `RestTemplate` as an injectable bean (deviating from `CongressApiClient`'s pattern) is well-justified and pays off immediately — `ReasoningServiceClientTest` gets genuine `MockRestServiceServer` request/response verification instead of repeating `CongressApiClientTest`'s documented testing gap. Test coverage is proportionate to risk: unit tests for the HTTP client, service-level tests for both success/failure paths (including a broader unexpected-exception safety net), a real dedicated 429 rate-limit test, and a full Testcontainers-backed integration test for the AC5 flow. Full regression: 853/853 passing (up from 843 at ES-1.2 baseline).

Two real gaps were found during review — both are honest byproducts of test doubles necessarily standing in for a real external service and a real container runtime, not sloppiness. Neither is a regression the Dev could have caught with the test types already in place; both need attention before this feature does anything useful in a running dev/prod environment.

### Refactoring Performed

- **File**: `backend/src/main/java/org/newsanalyzer/controller/ArticleController.java`
  - **Change**: Updated the stale class-level Javadoc, which still read "Persistence only at this stage (Story ES-1.2) — no extraction or bias-detection calls occur here yet."
  - **Why**: No longer accurate — `POST /api/articles` now triggers entity extraction as of this story. Stale doc comments actively mislead the next reader more than no comment at all.
  - **How**: Reworded to reflect that extraction now happens on submission, while still correctly noting bias-detection is not yet wired up. Verified `ArticleControllerTest` (9/9) and the full suite (853/853) still pass — comment-only change, zero behavioral risk.

### Compliance Check

- Coding Standards: ✓ Mirrors verified existing patterns (`CongressApiConfig`/`CongressApiClient` shape); deviations are narrow and documented.
- Project Structure: ✓ New files land in the expected `config/`, `dto/`, `service/` packages per the story's Relevant Source Tree.
- Testing Strategy: ✓ Unit + integration test levels used appropriately; mocks bound at the actual HTTP/external-service boundary, not deeper.
- All ACs Met: ✓ with one caveat — AC5's literal text ("...in `/api/entities` queries") is verified via `EntityRepository` directly rather than the REST endpoint, because no "list entities by article" query endpoint exists. Task 9's own subtask wording already anticipated this (says "queryable via EntityRepository"), and Dev flagged the AC-wording mismatch honestly in Completion Notes rather than silently reinterpreting the AC. This is a documentation inconsistency between AC5's prose and Task 9's scope, not a missed requirement — no functional gap.

### Improvements Checklist

- [x] Fixed stale Javadoc in `ArticleController` (see Refactoring Performed)
- [ ] **Wire `NOOMETRIC_API_KEY` through to the backend container** in `deploy/dev/docker-compose.yml` and `deploy/production/docker-compose.yml` — see Security Review below. Without this, every real extraction call in any docker-compose-based environment sends an empty API key and gets `401 Unauthorized` from the reasoning service, silently degrading every article's `extractionStatus` to `FAILED`.
- [ ] **Fix partial-entity-persistence on mid-batch extraction failure** — see Reliability finding below. Either wrap the entity-creation loop in one transaction, or explicitly decide (and document) that partial persistence on batch failure is acceptable and adjust Task 8's "no entities created" language to match reality.
- [ ] Consider adding an integration-level test that exercises the *actual* docker-compose environment variable wiring (or at minimum a `.env.example`-vs-`docker-compose.yml` consistency check in CI) — this class of gap (a var documented in `.env.example` but never referenced in a compose file's `environment:` block) is exactly what unit/integration tests structurally cannot catch, since they instantiate Spring beans directly rather than going through container env injection.
- [ ] Consider a repository-level test proving the multi-entity partial-failure scenario against a real DB (extend `ArticleExtractionIntegrationTest` with a response containing one valid + one entity with an unrecognized `entity_type`), since the current unit test for this path uses a single-entity mocked batch and can't distinguish "zero entities persisted" from "partial persistence."

### Security Review

**Finding (Medium): `NOOMETRIC_API_KEY` is never passed into the backend container.** `application.yml` now correctly binds `reasoning-service.api-key: ${NOOMETRIC_API_KEY:}` (Task 1, done correctly — this always loads regardless of active profile, deliberately avoiding the TD-002 trap). However, tracing the full path from host to container: neither `deploy/dev/docker-compose.yml` nor `deploy/production/docker-compose.yml` lists `NOOMETRIC_API_KEY` in the backend service's `environment:` block (confirmed via `grep` — `REASONING_SERVICE_URL` and `CONGRESS_API_KEY` *are* wired in both files; `NOOMETRIC_API_KEY` is absent from both). Neither compose file uses an `env_file:` directive that would inject it implicitly. `.env.example` documents the variable (line 40) as something an operator should set, creating a false impression that setting it in `.env` is sufficient — but Docker Compose only forwards variables that are either referenced in an `environment:` entry or pulled in via `env_file:`, so a bare `.env` entry with no corresponding `environment:` reference never reaches the container. **Net effect:** every real (non-mocked) extraction call in any docker-compose-driven environment today sends an empty `X-Noometric-API-Key` header, which per `reasoning-service-contract.md`'s auth section causes a hard `401 Unauthorized` — meaning `extractionStatus` will always resolve to `FAILED` in practice, article persistence still succeeds (NFR3 holds), but the story's actual value delivery (AC1/AC2) is silently inert until this is fixed. This is distinct from — and not covered by — the existing TD-002 entry, which is about the *missing `application-prod.yml` profile file*, not the compose *env-var pass-through* gap. Recommend either a new TD entry or a direct compose fix before this story is considered functionally complete in any real environment.

No other security concerns: no hardcoded secrets, API key correctly stays env-var driven at the Java config layer, rate limiting is a reasonable MVP-scoped mitigation for the cost-control risk it targets (global in-memory counter is intentionally simple, not a hard security perimeter — acceptable given the story's explicit design rationale, though it resets per-instance and would under-protect in a horizontally-scaled deployment; out of scope for this MVP per the story's own stated risk model).

### Performance Considerations

Timeout wiring is correct end-to-end: `application.yml`'s `reasoning-service.timeout: 30000` matches NFR1's documented 30s budget, `ReasoningServiceConfig`'s `RestTemplateBuilder` applies it to both connect and read timeouts, and no transaction is held open across the call (verified by reading `ArticleService.createArticle()`/`extractAndPersistEntities()` directly — the orchestrating method has no `@Transactional`, and each DB-touching step is its own short transaction via Spring Data JPA's per-method default or `EntityService`'s own `@Transactional` methods). This was the single most important thing to get right in this story and it's correctly implemented.

**Finding (Medium — Reliability, not strictly performance): partial entity persistence on mid-batch extraction failure.** `ArticleService.extractAndPersistEntities()` loops over `response.getEntities()` and calls `entityService.createEntityFromExtraction()` once per entity. Because that method is `@Transactional` and is invoked cross-bean (a real proxied call, not self-invocation), each call commits independently as soon as it returns. If entity N in a multi-entity response throws — e.g., `EntityType.valueOf(extracted.getEntityType().toUpperCase())` throws `IllegalArgumentException` for any `entity_type` value the reasoning service returns that isn't one of the six enum values, which is entirely possible if that side of the contract ever adds a new type without a coordinated version bump — entities 1..N-1 remain persisted in the database even though the exception is caught by `ArticleService`'s broad `catch (Exception e)` and `extractionStatus` is set to `FAILED`. This contradicts Task 8's stated invariant: "Extraction failure: Article still persists, extractionStatus = FAILED, **no entities created**." The existing test for this path (`ArticleServiceTest.testCreateArticle_unexpectedExceptionDuringPersistence_stillMarksFailed`) uses a single-entity list with a fully mocked `entityService`, so it cannot and does not distinguish "zero entities persisted" from "partial persistence" — it only proves the `extractionStatus` field ends up `FAILED`, not that zero entities exist in the database afterward. `ArticleExtractionIntegrationTest`'s failure-path test (`testCreateArticle_extractionFails_articlePersistsWithNoEntities`) only covers the network-failure case (exception thrown by `reasoningServiceClient.extractEntities()` itself, before any entity is ever processed), not the mid-batch case. Net risk: low probability today (the reasoning service is a stable, versioned contract with a fixed enum shape) but real, and the status field's semantics are misleading if it ever occurs — a partial extraction reads as a total failure with orphaned entities silently attached to the article.

### Files Modified During Review

- `backend/src/main/java/org/newsanalyzer/controller/ArticleController.java` (stale Javadoc fix — see Refactoring Performed above)

Dev/PO: please update the story's File List to reflect this QA-side edit if not already reflected.

### Gate Status

Gate: CONCERNS → docs/qa/gates/ES-1.3-entity-extraction-integration.yml

### Recommended Status

✗ Changes Required — See unchecked items above. Both the `NOOMETRIC_API_KEY` compose wiring and the partial-entity-persistence gap should be addressed (or explicitly, consciously deferred with a TD entry and PO sign-off) before this story is truly done — the core architecture and test discipline are excellent, but the feature does not actually work end-to-end in a running docker-compose environment today.
(Story owner decides final status)

---

### Review Date: 2026-07-06 (Re-Review)

### Reviewed By: Quinn (Test Architect)

### Code Quality Assessment

Both CONCERNS findings from the 2026-07-05 review are genuinely closed, not just marked done. I independently re-verified each rather than trusting the Completion Notes:

- **`NOOMETRIC_API_KEY` wiring**: re-grepped all three compose files myself — `deploy/dev/docker-compose.yml`, `deploy/production/docker-compose.yml`, and `deploy/production/docker-compose.build.yml` all now forward the variable, matching the existing convention in each file (`${VAR:-default}` in dev/build, bare `${VAR}` in prod alongside the other required secrets).
- **Atomic batch persistence**: re-read `EntityService.createEntitiesFromExtraction()` and `ArticleService.extractAndPersistEntities()` directly, not just the tests. Traced the transaction mechanics by hand: `createEntitiesFromExtraction()` is invoked cross-bean from `ArticleService` (goes through the Spring proxy, opens one real transaction), and its internal calls to `createEntityFromExtraction()` are same-class self-invocations that bypass the proxy but still execute under the already-open, thread-bound transaction — so they correctly participate in the same unit of work rather than opening their own. This is the right call: the classic Spring self-invocation gotcha (an inner `@Transactional` silently being ignored) is usually a bug; here it's exactly the desired behavior, since the goal is one shared transaction for the whole batch, not N independent ones. Dev's own Completion Notes described this mechanism accurately.
- The new test coverage is well-designed, not just present: `EntityServiceTest`'s new unit test proves the batch *stops* at the failing entity (Mockito can't prove a rollback), while `ArticleExtractionIntegrationTest`'s new test proves the *actual rollback* against a real Postgres transaction manager — asserting zero linked entities after a two-entity batch where the first is valid and the second fails to map. That's the right division of labor between test levels: don't try to prove a database-level guarantee with a mock-based test.

### Refactoring Performed

- **File**: `backend/src/main/java/org/newsanalyzer/service/ArticleService.java`
  - **Change**: Three Javadoc comments (class-level, `createArticle()`, and `extractAndPersistEntities()`) still described the *old* per-entity-transaction behavior — one even said "each in its own short transaction," which is precisely what the fix just changed. Updated all three to accurately describe the new atomic-batch behavior.
  - **Why**: These comments exist specifically to explain the transaction-boundary design — the single most important architectural property in this story. Leaving them describing the pre-fix behavior would actively mislead the next reader about how the code they're looking at actually behaves, which is worse than having no comment at all.
  - **How**: Reworded to reference `createEntitiesFromExtraction()` (the batch method) and describe one atomic transaction per batch rather than one per entity. Verified `ArticleServiceTest` (7), `ArticleControllerTest` (9), and `ArticleExtractionIntegrationTest` (3) — 19/19 — still pass; comment-only change.

### Compliance Check

- Coding Standards: ✓
- Project Structure: ✓
- Testing Strategy: ✓ — test-level selection (unit vs. integration) correctly matches what each test can actually prove
- All ACs Met: ✓ (AC5's prose-vs-Task9-scope note from the prior review still stands as a documentation-only inconsistency, not a functional gap — no change in position)

### Improvements Checklist

- [x] `NOOMETRIC_API_KEY` wired through all three docker-compose files (verified independently)
- [x] Mid-batch entity persistence made atomic, with both unit and real-DB integration proof
- [x] Fixed stale Javadoc in `ArticleService` referencing the old per-entity-transaction behavior (this review)
- [ ] (Still future/optional, not blocking) Consider a CI-level consistency check between `.env.example` and compose `environment:` blocks, so this exact class of gap can't silently recur for the next new env var
- [ ] (Still future/optional, not blocking) AC5's literal wording ("in `/api/entities` queries") could be updated to match Task 9's actual `EntityRepository`-based scope, for the next reader's sake — cosmetic only

### Security Review

No open findings. `NOOMETRIC_API_KEY` now reaches the container in all three compose variants and binds correctly at the Java config layer (unchanged from prior review, already verified correct).

### Performance Considerations

No open findings. Timeout wiring and transaction-boundary correctness both previously verified and unchanged by this fix round except for the (now-fixed) batch atomicity itself.

### Files Modified During Review

- `backend/src/main/java/org/newsanalyzer/service/ArticleService.java` (stale Javadoc fix — see Refactoring Performed above)

### Gate Status

Gate: PASS → docs/qa/gates/ES-1.3-entity-extraction-integration.yml

### Recommended Status

✓ Ready for Done
(Story owner decides final status)
