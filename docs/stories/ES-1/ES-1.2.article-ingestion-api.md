# Story ES-1.2: Article Ingestion API (Persistence Only)

## Status

Ready for Done

## Story

**As an** internal developer or the eval harness,
**I want** to submit an article via an API endpoint and have it persisted,
**so that** real article content exists as a queryable record before any extraction or bias analysis runs.

## Acceptance Criteria

1. `POST /api/articles` accepts source/outlet, url, publicationDate, and rawText, and persists a new `Article` record.
2. The endpoint returns the created `Article` per existing DTO/response conventions (`ArticleDTO`, mirroring `EntityDTO`).
3. Input validation follows existing Bean Validation patterns; invalid requests return 400 with a clear error body, per the existing `GlobalExceptionHandler` pattern.
4. No extraction or bias-detection calls occur at this stage — this story is persistence only.
5. Duplicate submissions (same URL and/or text submitted more than once) are **not deduplicated at MVP** — each submission creates a new `Article` record. This is deliberate: determining what counts as a "duplicate" is itself a design question deferred to Phase 2, given MVP's low, manual ingestion volume.
6. `ArticleControllerTest` and `ArticleServiceTest` cover creation, validation failure, and retrieval.

### Integration Verification

- **IV1:** No existing `/api/entities` or `/api/government-orgs` endpoint behavior changes.
- **IV2:** New endpoint is documented via the existing OpenAPI/Swagger annotations, appearing correctly without disrupting existing entries.
- **IV3:** Existing test suite (`mvn test`) passes unchanged; no shared configuration modification beyond additive entries.

## Tasks / Subtasks

- [x] Task 1: Create `ArticleDTO` (AC: 2)
  - [x] Response DTO mirroring `EntityDTO`'s structure and `@Schema` annotation style: `id`, `sourceName`, `url`, `publicationDate`, `rawText`, `ingestedAt`, `extractionStatus`, `biasDetectionStatus`, `reliabilityScore`
- [x] Task 2: Create `CreateArticleRequest` (AC: 1, 3)
  - [x] Request DTO mirroring `CreateEntityRequest`: `sourceName` (`@NotBlank`), `url` (optional), `publicationDate` (optional), `rawText` (`@NotBlank` + `@Size(max = 100_000)` — the size cap from the architecture doc's Security Integration section; rate limiting is explicitly deferred to ES-1.3, not this story)
- [x] Task 3: Create `ArticleService` (AC: 1, 2)
  - [x] `createArticle(CreateArticleRequest)` → persists via `ArticleRepository`, maps `Article` → `ArticleDTO`
  - [x] `getArticleById(UUID)` → throws `ResourceNotFoundException("Article", id)` if absent (reusing the existing generic exception — no new exception class needed, confirmed against actual code)
- [x] Task 4: Create `ArticleController` (AC: 1, 2, 3)
  - [x] `POST /api/articles` mirroring `EntityController.createEntity`'s structure (`@Valid @RequestBody`, `ResponseEntity<ArticleDTO>`, `HttpStatus.CREATED`)
  - [x] `GET /api/articles/{id}` for retrieval (needed for AC6's test coverage requirement)
- [x] Task 5: Write `ArticleControllerTest` (AC: 6)
  - [x] `@WebMvcTest(ArticleController.class)` mirroring `EntityControllerTest` — creation success, validation failure (400), rawText-too-long (400), retrieval success, retrieval not-found (404), unauthorized access (401) — 6 tests
- [x] Task 6: Write `ArticleServiceTest` (AC: 6)
  - [x] `@ExtendWith(MockitoExtension.class)` mirroring `EntityServiceTest` — creation logic, field-mapping verification (`ArgumentCaptor`), retrieval, not-found exception path — 4 tests
- [x] Task 7: Regression verification (IV1, IV2, IV3)
  - [x] Full existing suite passes unchanged — verified via targeted run (103/103: prior 93 + 10 new) AND full-project run (**841/841**, entire backend test suite, zero failures)
  - [x] New endpoints documented via existing `@Tag`/`@Operation` Swagger annotations, following `EntityController`'s exact pattern
  - [x] No shared configuration changes — no new environment variables, no `SecurityConfig`/`GlobalExceptionHandler` modifications (existing handler already covers all validation exceptions this story triggers)

## Dev Notes

Pulled directly from `docs/prd/ES-1.md`, `docs/architecture/ES-1-ARCHITECT-HANDOFF.md`, `docs/architecture/coding-standards.md`, and verified against actual code (`EntityDTO.java`, `CreateEntityRequest.java`, `EntityController.java`, `GlobalExceptionHandler.java`, `ResourceNotFoundException.java`) — no invented details.

**Relevant Source Tree** (new files for this story):
```
backend/src/main/java/org/newsanalyzer/
├── dto/
│   ├── ArticleDTO.java              # NEW
│   └── CreateArticleRequest.java    # NEW
├── service/
│   └── ArticleService.java          # NEW
└── controller/
    └── ArticleController.java       # NEW
backend/src/test/java/org/newsanalyzer/
├── controller/ArticleControllerTest.java  # NEW
└── service/ArticleServiceTest.java        # NEW
```

**What Already Exists (from Story ES-1.1 — do not recreate):**
- `Article` JPA entity, `ArticleStatus` enum, `ArticleStatusConverter`, `ArticleRepository` — all done, tested (93 tests passing project-wide), table is `evidence_articles` (not `articles` — see ES-1.1's Completion Notes for why)
- `ArticleRepository extends JpaRepository<Article, UUID>` has zero custom query methods yet — `findById`/`save` are all this story needs

**Existing Patterns to Mirror (verified against actual code, not assumed):**
- **DTO pattern:** `EntityDTO`/`CreateEntityRequest` — plain Lombok `@Data`/`@NoArgsConstructor`/`@AllArgsConstructor` DTOs, no MapStruct or separate mapper class; mapping happens by hand inside the Service layer. `EntityDTO` uses `@Schema(description = "...")` from `io.swagger.v3.oas.annotations.media.Schema` for API docs.
- **Validation pattern:** `CreateEntityRequest` uses `jakarta.validation.constraints` annotations (`@NotBlank`, `@NotNull`) directly on DTO fields.
- **Controller pattern:** `EntityController` — `@Slf4j @RestController @RequestMapping("/api/...") @RequiredArgsConstructor @Tag(...)`, constructor injection of the Service (never field injection), `log.info(...)` at the top of each handler method before delegating to the service.
- **Not-found exception pattern:** `ResourceNotFoundException` (in `org.newsanalyzer.exception`) is **generic**, not entity-specific — constructor takes `(String resourceType, Object id)` and formats the message itself. Use `new ResourceNotFoundException("Article", id)`; do not create a new `ArticleNotFoundException` class.
- **Error response pattern:** `GlobalExceptionHandler` (`@ControllerAdvice`) already handles `ResourceNotFoundException` → 404, `MethodArgumentNotValidException`/`ConstraintViolationException` → 400 (Bean Validation failures), `DataIntegrityViolationException` → 409. Nothing needs to be added to it for this story — it already covers everything `CreateArticleRequest`'s validation annotations will trigger.

**Key Constraints:**
- `rawText` gets a `@Size(max = 100_000)` cap on `CreateArticleRequest` (Security Integration requirement) — this is Bean Validation at the request layer, separate from the `Article` JPA entity's unbounded `TEXT` column (which stays as-is; the cap is about rejecting abusive input, not a DB schema change)
- Rate limiting on `/api/articles` is explicitly **deferred to ES-1.3** — the cost-bearing reasoning-service call doesn't exist until then, so rate-limiting now would protect against a risk that isn't present yet
- No extraction or bias-detection calls in this story (AC4) — `ArticleService.createArticle()` only persists; it does not call `ReasoningServiceClient` (which doesn't exist yet — that's ES-1.3)
- Duplicate submissions are explicitly NOT deduplicated at MVP (AC5) — do not add a uniqueness check on `url` or similar

**Previous Story Context (ES-1.1):** Schema, model, and repository layer are done and QA-passed (gate PASS, quality score 100). This story is purely the API/Service layer on top of that foundation.

### Testing

- **Test file locations:** `backend/src/test/java/org/newsanalyzer/controller/ArticleControllerTest.java`, `backend/src/test/java/org/newsanalyzer/service/ArticleServiceTest.java`
- **Test standards:** JUnit 5 + Mockito, Given/When/Then structure, per `coding-standards.md`
- **Frameworks/patterns:** `ArticleServiceTest` uses `@ExtendWith(MockitoExtension.class)` (pure Mockito, no Spring context — mirrors `EntityServiceTest`). `ArticleControllerTest` uses `@WebMvcTest(ArticleController.class)` (web-layer slice, mocked service, no DB — mirrors `EntityControllerTest`). Neither needs Testcontainers/`@DataJpaTest` — that's already covered by `ArticleRepositoryTest` from ES-1.1.
- **Specific requirement:** Regression check — full existing suite (93 tests as of ES-1.1) must still pass unchanged.

## Change Log

| Date | Version | Description | Author |
|---|---|---|---|
| 2026-07-04 | 0.1 | Initial draft, created from `docs/prd/ES-1.md` and `docs/architecture/ES-1-ARCHITECT-HANDOFF.md`, following the ES-1.1 pattern | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-04 | 0.2 | Status: Draft → Approved — cleared for dev agent pickup | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-04 | 0.3 | All 7 tasks implemented and tested. Full backend suite (841 tests) passes with zero failures. Status: Approved → Ready for Review | James (Dev) |
| 2026-07-04 | 0.4 | QA review passed — gate PASS (quality score 95). One gap found and fixed during review (missing length validation on sourceName/url); full suite now 843/843. Status: Ready for Review → Ready for Done | Sarah (PO) / Steve Kosuth-Wood |

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (James, Dev agent persona)

### Debug Log References

- Verified folder structure and ES-1.1 foundation (`Article.java`, `ArticleRepository.java`) before starting — no naming collisions
- Compile after DTO/Service/Controller: `mvn -o compile` — BUILD SUCCESS, 199 source files
- Targeted regression: `mvn -o clean test -Dtest=ArticleTest,EntityTest,EntityServiceTest,EntityControllerTest,EntityRepositoryTest,ArticleRepositoryTest,ArticleServiceTest,ArticleControllerTest` — **103/103 pass**
- Full-project regression: `mvn -o test` (entire backend suite, no filter) — **841/841 pass, 0 failures, 0 errors**

### Completion Notes List

- **Architecture doc's exception-handling reference was imprecise, corrected in Dev Notes before implementation**: the doc implied an `EntityNotFoundException`-per-resource pattern; actual code has a single generic `ResourceNotFoundException(resourceType, id)`. Used the real pattern — no new exception class created.
- **Rate limiting deliberately deferred to ES-1.3**, per explicit user decision during story drafting — only the `rawText` `@Size(max = 100_000)` cap was added this story, since the cost-bearing risk (reasoning-service calls) doesn't exist until ES-1.3.
- **DTO/mapping pattern**: no MapStruct or separate mapper class — manual field-by-field mapping inside `ArticleService.toDTO()`, matching `EntityService`'s exact existing convention (verified, not assumed).
- **Test security pattern**: `@WebMvcTest` slices apparently don't inherit the app's real `SecurityConfig` (which permits all requests) — they get Spring Boot's default test security (requires auth). Mirrored `EntityControllerTest`'s `@WithMockUser`/`.with(csrf())` pattern exactly, including a `testUnauthorizedAccess` test for consistency with the established sibling test, even though AC6 didn't explicitly require it.
- Ran the **entire backend test suite** (841 tests, not just the targeted ~10 new/related classes) for IV1/IV3 verification — this project has many other modules (Congress, Federal Register, etc.) that could theoretically be affected by shared configuration changes; confirming zero impact project-wide is stronger evidence than a targeted run alone.

### File List

**New files:**
- `backend/src/main/java/org/newsanalyzer/dto/ArticleDTO.java`
- `backend/src/main/java/org/newsanalyzer/dto/CreateArticleRequest.java`
- `backend/src/main/java/org/newsanalyzer/service/ArticleService.java`
- `backend/src/main/java/org/newsanalyzer/controller/ArticleController.java`
- `backend/src/test/java/org/newsanalyzer/controller/ArticleControllerTest.java`
- `backend/src/test/java/org/newsanalyzer/service/ArticleServiceTest.java`

**Modified files:** None — this story is purely additive (new DTOs, Service, Controller, tests). No existing files required changes.

## QA Results

### Review Date: 2026-07-04

### Reviewed By: Quinn (Test Architect)

### Code Quality Assessment

Solid implementation, consistent with `EntityDTO`/`CreateEntityRequest`/`EntityController`/`EntityService`'s established patterns (verified against actual code by the dev, not assumed — including catching and correcting an inaccurate architecture-doc claim about exception handling before writing any code, which is exactly the right instinct). Independently re-ran the full backend suite from a clean build myself — confirmed passing, not just trusted the dev's report.

One real gap found during review: `CreateArticleRequest` capped `rawText` at 100,000 characters (matching the architecture doc's Security Integration requirement) but left `sourceName` and `url` completely unvalidated for length, even though `Article.sourceName` is `VARCHAR(255)` and `Article.url` is `VARCHAR(1000)` at the DB level. An oversized value for either field would have bypassed Bean Validation entirely and hit the database directly, surfacing as a raw `DataIntegrityViolationException` → 409 "Data integrity violation" — a confusing, low-information error instead of the clean 400 with a specific field-level message that AC3 actually calls for ("invalid requests return 400 with a clear error body"). This is a good example of testing the happy path (valid input) and one deliberately-chosen edge case (`rawText` over the app-level cap) while missing that two *other* fields had DB-level constraints with no matching application-level validation at all.

**Fixed directly during this review**: added `@Size(max = 255)` to `sourceName` and `@Size(max = 1000)` to `url`, matching `Article.java`'s actual column lengths exactly — not an arbitrary API-level choice, but a deliberate mirror of the real DB constraint, so validation always fires before the DB ever sees an oversized value. Added `testCreateArticleWithSourceNameTooLong` and `testCreateArticleWithUrlTooLong` to `ArticleControllerTest` to lock this in.

### Refactoring Performed

- **File**: `backend/src/main/java/org/newsanalyzer/dto/CreateArticleRequest.java`
  - **Change**: Added `@Size(max = 255)` to `sourceName`, `@Size(max = 1000)` to `url`
  - **Why**: Neither field had any length validation, despite both having DB-level length constraints (`Article.sourceName` VARCHAR(255), `Article.url` VARCHAR(1000)) that would otherwise be hit directly, producing a confusing 409 instead of a clean 400
  - **How**: Bean Validation annotations matching the DB column lengths exactly — verified this doesn't change any existing test's behavior (all prior tests use short, valid values)
- **File**: `backend/src/test/java/org/newsanalyzer/controller/ArticleControllerTest.java`
  - **Change**: Added `testCreateArticleWithSourceNameTooLong` and `testCreateArticleWithUrlTooLong`
  - **Why**: Locks in the fix above — without these, a future refactor could silently remove the `@Size` caps and nothing would catch it
  - **How**: Mirrors the existing `testCreateArticleWithRawTextTooLong` pattern exactly (same file, same style)

### Compliance Check

- Coding Standards: ✓
- Project Structure: ✓
- Testing Strategy: ✓ (gap found and closed during this review)
- All ACs Met: ✓ — all 6 acceptance criteria have direct test coverage

### Requirements Traceability

| AC | Requirement | Test Coverage | Status |
|----|---|---|---|
| 1 | `POST /api/articles` accepts source/outlet, url, publicationDate, rawText | `testCreateArticle` (Controller + Service) | ✓ Full |
| 2 | Returns `ArticleDTO` mirroring `EntityDTO` conventions | `testCreateArticle` asserts response shape | ✓ Full |
| 3 | Bean Validation, 400 on invalid input via `GlobalExceptionHandler` | `testCreateArticleWithInvalidData`, `testCreateArticleWithRawTextTooLong`, `testCreateArticleWithSourceNameTooLong` (new), `testCreateArticleWithUrlTooLong` (new) | ✓ Full (after fix — previously had a real gap) |
| 4 | No extraction/bias-detection calls | Code inspection — `ArticleService` has no `ReasoningServiceClient` reference; confirmed absent | ✓ Full |
| 5 | No deduplication at MVP | Code inspection — no uniqueness constraint or check anywhere in the new code | ✓ Full |
| 6 | `ArticleControllerTest`/`ArticleServiceTest` cover creation, validation failure, retrieval | 8 controller tests + 4 service tests | ✓ Full |

**IV1** (no existing endpoint changes), **IV2** (Swagger docs), **IV3** (no regression) — all verified via the full 843-test backend suite (independently re-run from clean build), not just the targeted new tests.

### Improvements Checklist

- [x] Added `@Size` validation on `sourceName`/`url` matching DB column lengths, plus tests
- [ ] Consider one live smoke test (either an automated `@SpringBootTest` end-to-end test, or a documented manual `curl` against a running instance) — not blocking. The three existing test layers (`ArticleRepositoryTest` for persistence against real Postgres, `ArticleServiceTest` for logic, `ArticleControllerTest` for the web layer) give strong confidence, and the wiring pattern (`@RequiredArgsConstructor` constructor injection, standard Spring stereotypes) is identical to `Entity`'s already-proven-in-production stack — so this is genuinely low risk, not a real gap like ES-1.1's missing DB round-trip test was. Worth doing eventually, not worth blocking on.

### Security Review

The `sourceName`/`url` length-validation fix has a secondary security benefit beyond error-message clarity: it closes a small resource-exhaustion vector where oversized field values could reach the database layer before being rejected. No other concerns — endpoint is appropriately unauthenticated for now (matches project-wide posture), and the architecture doc's rate-limiting requirement is correctly deferred to ES-1.3 where the actual cost-bearing risk (reasoning-service calls) will exist.

### Performance Considerations

No concerns. No new query patterns, no shared configuration changes (confirmed via full-suite regression).

### Files Modified During Review

- `backend/src/main/java/org/newsanalyzer/dto/CreateArticleRequest.java` — added `@Size` validation (see Refactoring Performed)
- `backend/src/test/java/org/newsanalyzer/controller/ArticleControllerTest.java` — added 2 tests (see Refactoring Performed)

Dev should update the story's File List to note `CreateArticleRequest.java` and `ArticleControllerTest.java` were touched during QA review (both were already in the File List as new files — no new entries needed, just noting the additional QA-driven changes within them).

### Gate Status

Gate: PASS → docs/qa/gates/ES-1.2-article-ingestion-api.yml

### Recommended Status

[✓ Ready for Done] — the one real gap found (missing length validation) was fixed and tested during this review; the one open item (live smoke test) is a low-risk, non-blocking future improvement, not a defect. (Story owner decides final status.)
