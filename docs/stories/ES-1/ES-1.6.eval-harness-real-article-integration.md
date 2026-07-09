# Story ES-1.6: Eval Harness Real-Article Integration

## Status

Ready for Done

## Sequencing Note (Deliberate Deviation from the Epic's Dependency Graph)

The epic's dependency graph shows `ES-1.6 → depends on → ES-1.5` (Grounded-Query Interface). This story is being drafted **before** ES-1.5, per an explicit product decision on 2026-07-07. Re-reading ES-1.6's actual PRD acceptance criteria (`docs/prd/ES-1.md` Story 1.6), none of them require ES-1.5's grounded-query endpoint or its "no silent blending" (NFR4) semantics — they only require reading persisted `Article`/`Entity`/`ArticleBiasAnnotation` data, all of which is already complete as of ES-1.4. The dependency arrow in the diagram reflects an assumed *build order*, not a hard technical requirement. If ES-1.5 later builds a canonical grounded-query interface, this story's eval-only read path can coexist with it or be revisited to use it — they serve different consumers (eval tooling vs. future product/agent consumers) with different rigor requirements.

## Scope Decision: "Validation" Means Read-Access, Not Precision/Recall Scoring

**This significantly narrows PRD Story 1.6's literal wording** ("the eval harness gains a code path to source validation input... for validation"), and the narrowing is deliberate, not an oversight — recorded here per an explicit product decision on 2026-07-07.

The existing eval harness scores `SyntheticArticle` extractions against a **human-curated gold-standard ground truth** (`eval/datasets/gold/*.yaml`, scored via `eval/assertions/entity_scorer.py`'s precision/recall/F1 logic). Real, ingested `Article` records have no such ground truth — nobody has manually annotated every entity in a real news article the way the synthetic gold dataset was curated. Building that curation pipeline is a real, separate body of work, not a side effect of wiring up a read path.

This story therefore implements **read-access / smoke-test integration**: a way to fetch a real article's already-computed extraction and bias-detection results (produced by the production `ArticleService` pipeline from ES-1.1–ES-1.4) for qualitative/manual review, proving the full pipeline works end-to-end on real content. It does **not** re-run extraction through the promptfoo scoring framework, and it does **not** compute precision/recall/F1 for real articles. Scoring real articles against a curated ground truth is explicitly deferred — added to `docs/evaluation-methodology.md`'s Future Work section (Task 4) rather than silently dropped.

## Story

**As a** developer validating extraction quality,
**I want** the eval harness to read real ingested `Article` records' extraction and bias-detection results,
**so that** I can confirm the full production pipeline works correctly on real content, not just synthetic data.

## Acceptance Criteria

1. A new backend endpoint (`GET /api/eval/real-articles/{articleId}`) returns a persisted `Article`'s metadata bundled with its linked `Entity` and `ArticleBiasAnnotation` records — read-only, no re-extraction triggered, sourced entirely from data already computed by the production ingestion pipeline (FR8, scoped per the Scope Decision above).
2. If the requested article's `extractionStatus`/`biasDetectionStatus` are not both `SUCCESS`, the endpoint still returns whatever data exists (never blocks on partial pipeline failure, consistent with the independent-failure-tracking principle from ES-1.3/ES-1.4) — both statuses are visible in the response so the caller can tell what's actually trustworthy.
3. A new Python script under `eval/` calls this endpoint to fetch and display a real article's stored results — coexists alongside, not replacing, the existing `SyntheticArticle`-based promptfoo provider path (`eval/providers/spacy_provider.py`, `eval/providers/bias_provider.py`). This is explicitly **not** a promptfoo provider itself (no scoring/assertion happens), so it is not wired into `eval/promptfooconfig.yaml`.
4. `docs/evaluation-methodology.md` is updated with a new section describing the real-article read path — its purpose (pipeline smoke-test / qualitative review), and an explicit note that precision/recall scoring against real articles is deferred future work pending ground-truth curation effort.
5. Unit and integration tests cover: successful bundled retrieval with entities and annotations present, an article with zero entities/annotations, a nonexistent article ID (404), and an article with partial pipeline failure (e.g., `extractionStatus = SUCCESS` but `biasDetectionStatus = FAILED`) still returning available data per AC2.
6. At least one real ingested test article demonstrates the full path end-to-end: ingest → extract → bias-detect → new eval endpoint read — captured as an integration test mirroring `ArticleExtractionIntegrationTest`'s real-Postgres, Testcontainers-based pattern.

### Integration Verification

- **IV1:** Existing eval harness runs against `SyntheticArticle` data (`spacy_provider.py`, `bias_provider.py`, `entity_scorer.py`, existing `promptfooconfig.yaml`/`promptfoo-bias.yaml` configs) continue to pass unchanged — this story's new path is purely additive.
- **IV2:** No change to the eval harness's existing reasoning-service call patterns — the new real-article read path calls the **news-analyzer Java backend** (a new endpoint on this side of the boundary), not the reasoning-service directly, so `spacy_provider.py`/`bias_provider.py` are untouched.
- **IV3:** Existing `/api/eval/datasets/*` endpoints (`EvalDatasetController`, synthetic-article CRUD from the EVAL-1 epic) remain unaffected — the new endpoint lives at a distinctly-named path (`/api/eval/real-articles/...`, not `/api/eval/datasets/...`), no route collision, no shared code modified.

## Tasks / Subtasks

- [x] Task 1: Add article-scoped query methods to existing repositories (AC: 1)
  - [x] `EntityRepository.findByArticleId(UUID articleId)` — standard Spring Data derived query method, mirroring existing methods like `findByEntityType`
  - [x] `ArticleBiasAnnotationRepository.findByArticleId(UUID articleId)` — same pattern; also updated its stale class-level comment, which incorrectly said the future consumer would be "ES-1.5 grounded-query interface" — that's now ES-1.6, per this story's own sequencing decision
- [x] Task 2: Create the eval-domain bundled-read endpoint (AC: 1, 2)
  - [x] New DTO `RealArticleEvaluationDTO` — composes an `ArticleDTO` field plus `List<EntityDTO>` and `List<ArticleBiasAnnotationDTO>` (both DTOs already exist from ES-1.3/ES-1.4 — reused directly, no parallel nested DTOs created)
  - [x] New `EvalRealArticleService` (in `org.newsanalyzer.service.eval`, matching `EvalDatasetService`'s package convention) — delegates the `Article` lookup to the existing `ArticleService.getArticleById()` (reusing its already-stable 404 handling rather than re-deriving it), then fetches linked entities/annotations via Task 1's new repository methods and assembles the bundled DTO regardless of `extractionStatus`/`biasDetectionStatus` values (AC2 — no gating on pipeline success). Class-level `@Transactional(readOnly = true)`, mirroring `EvalDatasetService`'s default-read-only convention.
  - [x] New `EvalRealArticleController` (in `org.newsanalyzer.controller.eval`, matching `EvalDatasetController`'s package/`@RestController`/`@Tag`/`@Operation` conventions) — `GET /api/eval/real-articles/{articleId}`
- [x] Task 3: Add the real-article read script to the eval harness (AC: 3)
  - [x] `eval/scripts/read_real_article.py` — calls `GET /api/eval/real-articles/{articleId}` and prints/logs the bundled result for manual review. Mirrors `eval/datasets/scripts/derive_gold.py`'s established convention for backend-calling scripts (`requests` + `argparse --backend-url` defaulting to `http://localhost:8080`, timeout=30, `raise_for_status()`) rather than the reasoning-service-calling providers' env-var convention, since this script talks to the Java backend, not the reasoning service.
  - [x] **Explicit non-negotiable:** confirmed via grep — not referenced in `promptfooconfig.yaml`, `promptfoo-bias.yaml`, or `promptfoo-bias-ungrounded.yaml`
- [x] Task 4: Update `docs/evaluation-methodology.md` (AC: 4)
  - [x] New "7. Real-Article Read Path (Story ES-1.6)" section inserted between "6. Tooling" and "Future Work" (renumbered to "8."), covering Purpose / How It Works / Scope Boundary
  - [x] Added "Real-article ground-truth curation and scoring" as new Future Work item 6, cross-referencing this story's Scope Decision
- [x] Task 5: Write unit tests for the new endpoint (AC: 5)
  - [x] `EvalRealArticleServiceTest` — 4 tests: bundled retrieval with data present, zero-entities/zero-annotations case, article-not-found case, partial-pipeline-failure case. Mirrors `EvalDatasetServiceTest`'s manual-construction convention (not `@InjectMocks`).
  - [x] `EvalRealArticleControllerTest` — 2 tests: `@WebMvcTest` + `@AutoConfigureMockMvc(addFilters = false)`, mocked service, verifies the 404 path and successful bundled response shape. Mirrors `EvalDatasetControllerTest`'s conventions (not `ArticleControllerTest`'s `@WithMockUser`/csrf approach — this endpoint has no auth-sensitive behavior to test, matching the existing eval-domain controller's precedent).
- [x] Task 6: Integration test for the full flow (AC: 6)
  - [x] `EvalRealArticleIntegrationTest` (real DB via Testcontainers, mirroring `ArticleExtractionIntegrationTest`) — ingests an article through the real `ArticleService` pipeline (mocked `ReasoningServiceClient` at the HTTP boundary only), then calls `EvalRealArticleService.getRealArticleEvaluation()` directly and verifies the bundled entities/annotations match what was persisted, including `ontologyMetadata` round-tripping through the JSONB column
- [x] Task 7: Regression verification (IV1, IV2, IV3)
  - [x] Full existing suite passes: 875 tests, 0 failures, 0 errors (up from 868 at ES-1.4 — 7 new tests: 4 `EvalRealArticleServiceTest`, 2 `EvalRealArticleControllerTest`, 1 `EvalRealArticleIntegrationTest`)
  - [x] Existing eval harness Python tests: 84 tests, 0 failures (`test_entity_scorer.py`, `test_bias_scorer.py`, `test_derive_gold.py`) — run via `pytest`, not just inspected, confirming IV1
  - [x] Existing `/api/eval/datasets/*` endpoint tests (`EvalDatasetControllerTest`, `EvalDatasetServiceTest`) included in the 875-test full run, unaffected

## Dev Notes

Pulled directly from `docs/prd/ES-1.md`, `docs/architecture/ES-1-ARCHITECT-HANDOFF.md`, and verified against actual code (`ArticleController.java`, `EntityController.java`, `EntityRepository.java`, `EvalDatasetController.java`, `EvalDatasetService.java`, `eval/providers/spacy_provider.py`, `docs/evaluation-methodology.md`) as they exist after ES-1.4 — no invented details.

**Relevant Source Tree** (new/modified files):
```
backend/src/main/java/org/newsanalyzer/
├── repository/
│   ├── EntityRepository.java                        # MODIFIED — add findByArticleId()
│   └── ArticleBiasAnnotationRepository.java          # MODIFIED — add findByArticleId()
├── dto/eval/
│   └── RealArticleEvaluationDTO.java                 # NEW
├── service/eval/
│   └── EvalRealArticleService.java                   # NEW
└── controller/eval/
    └── EvalRealArticleController.java                # NEW
backend/src/test/java/org/newsanalyzer/
├── service/eval/EvalRealArticleServiceTest.java        # NEW
├── controller/eval/EvalRealArticleControllerTest.java  # NEW
└── service/eval/EvalRealArticleIntegrationTest.java    # NEW — mirrors EvalDatasetServiceTest's
                                                          # package placement (service/eval/), not
                                                          # ArticleExtractionIntegrationTest's (service/)
eval/scripts/
└── read_real_article.py                              # NEW
docs/
└── evaluation-methodology.md                          # MODIFIED — new section + Future Work entry
```

**What Already Exists (from ES-1.1–ES-1.4 — do not recreate):**
- `Article`, `Entity`, `ArticleBiasAnnotation` models, all persisted and linked via `article_id` FKs — done. This story only *reads* them, never writes.
- `ArticleController` (`GET /api/articles/{id}`, `POST /api/articles`) — product-domain, unrelated to this story's eval-domain endpoint. Do not add eval-specific logic here.
- `EvalDatasetController`/`EvalDatasetService` (`/api/eval/datasets/*`, `org.newsanalyzer.controller.eval`/`service.eval` packages) — the established "eval domain" precedent from the EVAL-1 epic, built entirely around `SyntheticArticle`. This story's new controller/service live in the same packages but are a distinct, separate class family — do not extend or modify `EvalDatasetController`/`EvalDatasetService` themselves, since they're specifically synthetic-article-scoped by design and name.
- `EntityDTO`, `ArticleBiasAnnotationDTO` — both already have every field needed for the bundled response; reuse directly rather than inventing new nested shapes.
- `eval/providers/spacy_provider.py`/`bias_provider.py` — call the **reasoning service** directly (confirmed by reading `spacy_provider.py`: `REASONING_SERVICE_URL` env var, direct HTTP POST to `/entities/extract`). This story's new script is architecturally different — it calls the **news-analyzer Java backend**, not the reasoning service, since it's reading already-computed results rather than triggering new extraction.

**Existing Patterns to Mirror (verified against actual code):**
- **Eval-domain package/controller conventions:** `EvalDatasetController` uses `@RequestMapping("/api/eval/datasets")`, `@RestController`, `@Tag`, `@Operation`, `ResponseEntity<DTO>` — mirror this shape exactly for `EvalRealArticleController` at `/api/eval/real-articles`.
- **Repository query methods:** `EntityRepository` already has several `findBy*` derived query methods (`findByEntityType`, `findBySchemaOrgType`, etc.) — `findByArticleId` follows the identical Spring Data JPA convention, no custom `@Query` needed.
- **404 handling:** `ArticleService.getArticleById()` throws `ResourceNotFoundException("Article", id)` on a missing article, caught by the existing `GlobalExceptionHandler` — mirror this exact pattern in `EvalRealArticleService` rather than inventing new not-found handling.
- **Integration test division of labor:** `ArticleExtractionIntegrationTest` mocks only `ReasoningServiceClient` (the actual external HTTP boundary) and runs everything else — real Postgres via Testcontainers — mirror this exactly for Task 6's integration test.

**Key Constraints:**
- No re-extraction, no scoring, no ground-truth curation in this story (see Scope Decision above) — resist scope creep back toward the PRD's literal "validation" wording without re-confirming with the user first
- The new script must not be registered as a promptfoo provider (Task 3's non-negotiable) — it has a fundamentally different purpose (read/display) than the existing providers (extract/score)
- No changes to `EvalDatasetController`/`EvalDatasetService`/`SyntheticArticle` — this story is purely additive alongside them, never modifying synthetic-article code

**Previous Story Context (ES-1.1–ES-1.4):** All four `Ready for Done`, clean QA gates (100, 95, 100, 100 after re-review each time). ES-1.4's QA review established a useful general principle worth carrying into this story: trace actual control flow before trusting a severity claim about "what could go wrong" — don't assume the worst case is real without verifying it against the code.

### Testing

- **Test file locations:** `backend/src/test/java/org/newsanalyzer/service/eval/EvalRealArticleServiceTest.java` (new), `backend/src/test/java/org/newsanalyzer/controller/eval/EvalRealArticleControllerTest.java` (new), `backend/src/test/java/org/newsanalyzer/service/eval/EvalRealArticleIntegrationTest.java` (new — package placement mirrors `EvalDatasetServiceTest.java`'s `service/eval/` location, testing approach mirrors `ArticleExtractionIntegrationTest.java`'s Testcontainers pattern)
- **Test standards:** JUnit 5 + Mockito, per `coding-standards.md`
- **Frameworks/patterns:** Unit tests mock repositories directly (mirroring `EntityServiceTest`'s conventions); the AC6 integration test needs a real database (Testcontainers) but a mocked `ReasoningServiceClient` at the HTTP boundary only — same division of labor as ES-1.3/ES-1.4's integration tests.
- **Specific requirement:** Full existing suite (868 tests as of ES-1.4) must pass unchanged. Existing Python eval-harness tests (`pytest` under `eval/assertions/`, `eval/datasets/scripts/`) must also remain unaffected — verify by running them, not just by inspection, since this is the first ES-1 story to touch the `eval/` directory at all.

## Change Log

| Date | Version | Description | Author |
|---|---|---|---|
| 2026-07-07 | 0.1 | Initial draft, created from `docs/prd/ES-1.md` and `docs/architecture/ES-1-ARCHITECT-HANDOFF.md`. Two explicit product decisions recorded: (1) drafted ahead of ES-1.5, deviating from the epic's dependency graph, since ES-1.6's actual ACs don't require ES-1.5's grounded-query endpoint; (2) "validation" scoped down to read-access/smoke-test rather than precision/recall scoring, since real articles have no curated ground truth and building that curation pipeline is out of scope for this story. Both decisions are recorded in this file and will be reflected in the epic's dependency graph and `docs/evaluation-methodology.md`'s Future Work section. | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-08 | 0.2 | Validated (GO, readiness 10/10) — found and fixed one file-placement inconsistency (integration test package location). Status: Draft → Approved — cleared for dev agent pickup. | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-08 | 1.0 | All 7 tasks implemented and verified. Status: Approved → Ready for Review. | James (Dev Agent) |
| 2026-07-09 | 1.1 | QA review: independently re-verified transaction handling, cross-language Jackson/Python JSON contract, and error-handling conventions rather than trusting reported test counts alone. No issues found. Gate: PASS (quality score 100). Status: Ready for Review → Ready for Done. | Sarah (PO) / Quinn (QA) |

## Dev Agent Record

### Agent Model Used

James (Dev persona) / Claude Sonnet 5

### Debug Log References

No blocking failures. Targeted test runs after each task; full regression (Java + Python) run at Task 7.

### Completion Notes List

- **Task 1:** Two stale class-level comments corrected as a side effect. `ArticleBiasAnnotationRepository`'s comment predicted its next consumer would be "ES-1.5 grounded-query interface" — that's now ES-1.6, per this story's own sequencing decision.
- **Task 2 (design reuse over reinvention):** `EvalRealArticleService` delegates the `Article` lookup to the existing `ArticleService.getArticleById()` rather than re-querying `ArticleRepository` directly — reuses its already-stable, QA-verified `ResourceNotFoundException` handling instead of duplicating it. This is a genuine cross-domain dependency (eval domain → product domain), a first for the `service.eval` package, but a deliberate and narrow one: read-only, one method call, no new coupling beyond what's needed.
- **Task 2 (DTO composition):** `RealArticleEvaluationDTO` composes an existing `ArticleDTO` field rather than flattening `Article`'s fields into a new shape, and reuses `EntityDTO`/`ArticleBiasAnnotationDTO` as-is for the linked-record lists — no new nested DTOs invented, since ES-1.3/ES-1.4 already built exactly what was needed.
- **Task 3:** Confirmed via direct code reading (not assumption) that `spacy_provider.py`/`bias_provider.py` call the reasoning service directly via `REASONING_SERVICE_URL`, while `derive_gold.py` calls this Java backend directly via `--backend-url` (argparse, `requests`, default `http://localhost:8080`). The new script follows `derive_gold.py`'s convention, since it talks to the same backend, not the reasoning service.
- **Task 4:** Inserted as a new numbered section 7 (renumbering the existing "Future Work" to section 8) rather than appending to an existing section, since this is a genuinely new evaluation capability, not a tooling detail or a future-only aspiration.
- **Task 7:** Full regression run: 875 Java tests (0 failures, up from 868) + 84 Python eval-harness tests (0 failures, run via the `newsanalyzer` conda environment's `pytest`, not just inspected) — first ES-1 story to touch the `eval/` directory, so this Python-side verification was a genuinely new regression-check step, not a copy-paste of prior stories' Task 10/Task 7.

### File List

**New:**
- `backend/src/main/java/org/newsanalyzer/dto/eval/RealArticleEvaluationDTO.java`
- `backend/src/main/java/org/newsanalyzer/service/eval/EvalRealArticleService.java`
- `backend/src/main/java/org/newsanalyzer/controller/eval/EvalRealArticleController.java`
- `eval/scripts/read_real_article.py`
- `backend/src/test/java/org/newsanalyzer/service/eval/EvalRealArticleServiceTest.java`
- `backend/src/test/java/org/newsanalyzer/controller/eval/EvalRealArticleControllerTest.java`
- `backend/src/test/java/org/newsanalyzer/service/eval/EvalRealArticleIntegrationTest.java`

**Modified:**
- `backend/src/main/java/org/newsanalyzer/repository/EntityRepository.java` (added `findByArticleId()`)
- `backend/src/main/java/org/newsanalyzer/repository/ArticleBiasAnnotationRepository.java` (added `findByArticleId()`, corrected stale comment)
- `docs/evaluation-methodology.md` (new section 7, renumbered Future Work to section 8, added Future Work item 6)

## QA Results

### Review Date: 2026-07-09

### Reviewed By: Quinn (Test Architect)

### Code Quality Assessment

A smaller, lower-risk surface than ES-1.3/ES-1.4 — one new read-only GET endpoint, no external HTTP calls, no complex transaction boundary — but reviewed with the same independent-verification discipline, not a lighter pass just because it looked simpler. Two design choices stand out as good judgment rather than just "code that works": delegating the `Article` lookup to the existing, already-stable `ArticleService.getArticleById()` instead of re-deriving 404 handling, and composing `RealArticleEvaluationDTO` from the existing `ArticleDTO`/`EntityDTO`/`ArticleBiasAnnotationDTO` instead of inventing parallel nested shapes. Both are the kind of reuse that reduces what could possibly be wrong, not just what was typed.

I traced several things by hand rather than trusting that "tests pass" implies they're correct:
- The `@Transactional(readOnly = true)` cross-bean call chain (`EvalRealArticleService` → `ArticleService.getArticleById()`, both read-only, default `REQUIRED` propagation) — confirmed they correctly share one transaction, no lazy-loading risk.
- The new Python script's field access (`article['extractionStatus']`, `entity['entityType']`) assumes Jackson serializes these DTOs to camelCase JSON. Verified this directly — no global Jackson naming-strategy override exists anywhere in this project's config — rather than assuming the cross-language contract holds.
- The new script has no explicit `try`/`except` around `requests.get()`, so a connection failure crashes with a raw traceback. Checked whether this was sloppiness or precedent: `eval/datasets/scripts/derive_gold.py`, the script this one is explicitly said to mirror, has the exact same characteristic. Consistent with established convention, not a new gap.
- A minor N+1 query pattern exists in `toEntityDTO()`'s lazy `entity.getGovernmentOrganization()` access for articles with many linked entities. Traced its origin: this is copy-pasted verbatim from `EntityService.toDTO()`'s own existing pattern, not something this story introduced. Noted as a future consideration, not a defect of this story.

### Refactoring Performed

None. No issues found that warranted a direct fix during this review.

### Compliance Check

- Coding Standards: ✓
- Project Structure: ✓ — correctly placed in `service.eval`/`controller.eval`/`dto.eval` packages, correctly kept separate from `EvalDatasetController`/`EvalDatasetService` rather than extending them
- Testing Strategy: ✓ — all 4 AC5-required scenarios covered at unit level, AC6's full pipeline proven at integration level against real Postgres
- All ACs Met: ✓ (1-6, all verified)

### Improvements Checklist

- [x] Independently re-ran full Java suite (875/875) and Python eval-harness suite (84/84) rather than trusting reported counts
- [x] Verified File List completeness against `git status` — exact match
- [ ] (Future, not blocking) If this endpoint's usage ever grows beyond manual/CLI smoke-testing, consider addressing the inherited N+1 government-org lazy-loading pattern across both call sites (`EntityService` and `EvalRealArticleService`) together

### Security Review

No new exposure surface. `ArticleDTO`'s fields (including `rawText`) were already readable via the existing `GET /api/articles/{id}`; this endpoint reuses the same DTO rather than introducing new sensitive data access. No authentication on this endpoint matches the whole application's existing, already-accepted unauthenticated posture — not a new gap introduced by this story.

### Performance Considerations

Timeout/transaction handling correct. The one N+1 query note above is pre-existing and inherited, not a regression introduced here — appropriate for a manual-review endpoint, worth revisiting only if usage patterns change.

### Files Modified During Review

None.

### Gate Status

Gate: PASS → docs/qa/gates/ES-1.6-eval-harness-real-article-integration.yml

### Recommended Status

✓ Ready for Done
(Story owner decides final status)
