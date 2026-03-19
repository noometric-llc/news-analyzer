# Story EVAL-1.2: Synthetic Article Generator with Perturbation Engine

## Status

Done

## Story

**As a** AI evaluation engineer,
**I want** a module that takes Fact sets, generates realistic news articles via the Claude API, and applies controlled perturbations to produce labeled test datasets,
**so that** I can create ground-truth labeled test articles for evaluating LLM factual accuracy.

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Generates realistic articles that read like actual news reporting across 4 article types (news report, breaking news, opinion, analysis) |
| AC2 | All 6 perturbation types produce articles with exactly one controlled error: WRONG_PARTY, WRONG_COMMITTEE, WRONG_STATE, HALLUCINATE_ROLE, OUTDATED_INFO, CONFLATE_INDIVIDUALS |
| AC3 | Each article paired with complete ground-truth label (ArticleTestCase with source_facts, perturbations_applied, expected_findings, difficulty) |
| AC4 | Batch generation runs unattended with configurable parameters (entity_count, perturbation_types, article_types, model) |
| AC5 | Dry-run mode works without API calls (logs prompts, returns placeholder text, 0 tokens) |
| AC6 | Rate limiting prevents API throttling (configurable RPM) |
| AC7 | Article type (news, opinion, etc.) affects writing style but preserves factual content |
| AC8 | pytest tests verify perturbation logic (unit tests, no API calls needed) |

## Tasks / Subtasks

- [x] Task 1: Add anthropic dependency and configuration (AC5)
  - [x] Add `anthropic>=0.40.0` to `reasoning-service/requirements.txt`
  - [x] Add `EVAL_DRY_RUN` setting to Settings class (default `false`) — already existed from EVAL-1.1
  - [x] Verify import and basic SDK initialization works
- [x] Task 2: Create Perturbation Engine (AC2, AC3, AC8)
  - [x] Create `reasoning-service/app/services/eval/perturbation_engine.py` with `PerturbationEngine` class
  - [x] Implement `perturb(fact_set, perturbation_type, entity_pool)` → `PerturbedFactSet`
  - [x] Implement `_wrong_party(fact_set)` — swap D↔R/Independent, difficulty=EASY
  - [x] Implement `_wrong_committee(fact_set)` — assign incorrect committee, difficulty=MEDIUM
  - [x] Implement `_wrong_state(fact_set)` — incorrect state/district, difficulty=EASY
  - [x] Implement `_hallucinate_role(fact_set)` — invent role not in KB, difficulty=MEDIUM
  - [x] Implement `_outdated_info(fact_set)` — use superseded info (requires valid_from/valid_to), difficulty=HARD
  - [x] Implement `_conflate(fact_set, entity_pool)` — mix attributes of two people, difficulty=ADVERSARIAL
  - [x] Implement `_replace_fact` helper for swapping a fact value in a FactSet
  - [x] Handle incompatible perturbation-predicate combinations gracefully (skip + log warning)
  - [x] Write pytest tests for each perturbation type — verify correct changes, difficulty ratings, and changed_facts metadata
  - [x] Write pytest tests for edge cases: missing predicate, incompatible branch, empty entity_pool for CONFLATE
- [x] Task 3: Create Article Generator (AC1, AC5, AC6, AC7)
  - [x] Create `reasoning-service/app/services/eval/article_generator.py` with `ArticleGenerator` class
  - [x] Implement `_build_prompt(fact_set, article_type)` — constructs generation prompt with style instructions per article type
  - [x] Implement `generate(fact_set, article_type)` → `tuple[str, int]` (article_text, tokens_used)
  - [x] Integrate Anthropic SDK: `AsyncAnthropic` client with configurable model and API key
  - [x] Implement dry-run mode: when `EVAL_DRY_RUN=true`, return prompt text with 0 tokens (no API call)
  - [x] Implement rate limiting: `asyncio.sleep(60 / settings.eval_rate_limit_rpm)` between calls
  - [x] Write pytest tests for prompt construction (verify facts embedded, style instructions correct per type)
  - [x] Write pytest test for dry-run mode (no API calls made)
- [x] Task 4: Create Batch Orchestrator (AC3, AC4, AC6)
  - [x] Create `reasoning-service/app/services/eval/batch_orchestrator.py` with `BatchOrchestrator` class
  - [x] Implement `run_batch(config: BatchConfig)` → `BatchResult`
  - [x] Flow: build entity pool → for each entity generate faithful article + N perturbed articles → collect ArticleTestCases
  - [x] Build `expected_findings` list from `changed_facts` for each perturbed article
  - [x] Track total_tokens, faithful_count, perturbed_count, errors
  - [x] Implement `_store_batch()` — POST results to backend API (EVAL-1.3 endpoint, stub for now)
  - [x] Handle per-entity errors gracefully (log, continue, include in BatchResult.errors)
  - [x] Write pytest tests with mocked ArticleGenerator and PerturbationEngine
- [x] Task 5: Create FastAPI endpoints (AC4, AC5)
  - [x] Create `reasoning-service/app/api/eval/articles.py` with router for article inspection
  - [x] Create `reasoning-service/app/api/eval/batches.py` with router for batch operations
  - [x] Implement `POST /eval/articles/generate` — generate single article from provided FactSet (for testing/inspection)
  - [x] Implement `GET /eval/articles/{id}` — retrieve generated article (stub until EVAL-1.3 storage)
  - [x] Implement `POST /eval/batches` — kick off batch generation with BatchConfig body
  - [x] Implement `GET /eval/batches/{id}/status` — check batch progress (stub until EVAL-1.3 storage)
  - [x] Register routers in `main.py` with prefixes `/eval/articles` and `/eval/batches`
  - [x] Write pytest tests for endpoint request/response validation
- [x] Task 6: Integration verification
  - [x] Verify all new modules import correctly and app starts without errors
  - [x] Run full pytest suite — no regressions in existing tests
  - [x] Test dry-run mode end-to-end: `POST /eval/batches` with `dry_run: true`

## Dev Notes

### Architecture Context

This story builds on EVAL-1.1's Fact extraction layer and adds two key capabilities:

1. **Perturbation Engine** — deterministic, testable without LLM. Modifies FactSets before article generation (facts-first perturbation pattern).
2. **Article Generator** — uses Claude API to produce realistic articles from FactSets.

The Batch Orchestrator ties them together for unattended batch runs.

### Dependency on EVAL-1.1

This story requires all models, the BackendClient, FactExtractor, and FactSetBuilder from EVAL-1.1 to be complete.

### Perturbation-Predicate Compatibility Matrix

Not all perturbation types apply to all predicates. The engine must validate:

| Perturbation | Applicable Predicates | Branch |
|---|---|---|
| WRONG_PARTY | PARTY_AFFILIATION | Legislative, Executive |
| WRONG_COMMITTEE | COMMITTEE_MEMBERSHIP | Legislative |
| WRONG_STATE | STATE, DISTRICT | Legislative |
| HALLUCINATE_ROLE | LEADERSHIP_ROLE, CABINET_POSITION, COURT | All |
| OUTDATED_INFO | Any with valid_from/valid_to set | All |
| CONFLATE_INDIVIDUALS | Any (mixes facts from two entities) | All |

When incompatible (e.g., WRONG_COMMITTEE for a judicial FactSet), **skip gracefully** and log a warning — do not raise an error.

### Perturbation → Difficulty Mapping

| Perturbation | Default Difficulty | Reasoning |
|---|---|---|
| WRONG_PARTY | EASY | Party affiliation is widely known |
| WRONG_STATE | EASY | State is basic biographical info |
| WRONG_COMMITTEE | MEDIUM | Committee assignments are less well-known |
| HALLUCINATE_ROLE | MEDIUM | Plausible but fabricated — requires KB lookup |
| OUTDATED_INFO | HARD | Was once true, requires temporal awareness |
| CONFLATE_INDIVIDUALS | ADVERSARIAL | Blends real facts from two real people |

### Article Generator Prompt Pattern

The prompt instructs Claude to:
- Use ONLY the provided facts (no invented claims)
- Write 200-400 words in a style matching the article type
- Include realistic but fictional quotes where appropriate
- Produce text that reads like a real published article

Style instructions vary by `ArticleType`:
- **NEWS_REPORT** → AP/Reuters neutral tone with dateline
- **BREAKING_NEWS** → urgent, concise, lead with most important fact
- **OPINION** → newspaper column with author perspective
- **ANALYSIS** → political analysis with context and interpretation

### Module Structure

```
reasoning-service/app/services/eval/
├── perturbation_engine.py    # Deterministic perturbation logic
├── article_generator.py      # Claude API prompt + generation
└── batch_orchestrator.py     # End-to-end batch coordination

reasoning-service/app/api/eval/
├── articles.py               # POST /eval/articles/generate, GET /eval/articles/{id}
└── batches.py                # POST /eval/batches, GET /eval/batches/{id}/status
```

### Configuration

Uses settings defined in EVAL-1.1 plus:
```python
eval_dry_run: bool = False    # EVAL_DRY_RUN env var — skip API calls
```

### New Dependency

```
# reasoning-service/requirements.txt
anthropic>=0.40.0             # Claude API SDK
```

### Router Registration

Add to `main.py`:
```python
from app.api.eval import articles, batches
app.include_router(articles.router, prefix="/eval/articles", tags=["eval-articles"])
app.include_router(batches.router, prefix="/eval/batches", tags=["eval-batches"])
```

### Testing

**Framework:** pytest + pytest-asyncio
**Test Location:**
```
reasoning-service/tests/
├── services/eval/
│   ├── test_perturbation_engine.py
│   ├── test_article_generator.py
│   └── test_batch_orchestrator.py
└── api/eval/
    ├── test_articles_api.py
    └── test_batches_api.py
```

**Testing Standards:**
- Perturbation engine tests are **pure unit tests** — no API calls, no mocks of external services needed
- Article generator tests mock the Anthropic SDK — verify prompt construction and dry-run mode
- Batch orchestrator tests mock both ArticleGenerator and PerturbationEngine
- Use `@pytest.mark.asyncio` for all async tests
- Test error cases: incompatible perturbations, missing entity_pool, API errors
- Formatter: Black (line length 88), Linter: Ruff, Type checker: mypy (strict)
- Run: `pytest` from `reasoning-service/` directory

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-15 | 1.0 | Initial story creation | Sarah (PO) |
| 2026-03-17 | 1.1 | PO validation: reordered Task 5 (anthropic dependency) to Task 1 to fix sequencing | Sarah (PO) |
| 2026-03-17 | 1.2 | Implementation complete — all 6 tasks done, 74 new tests, full regression passes | James (Dev Agent) |

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6)

### Debug Log References

- `eval_dry_run` setting already existed in config.py from EVAL-1.1 forward-planning — no change needed
- `articles.py` initially had `from app.config import settings` inside function body — caused `AttributeError` in tests when patching `app.api.eval.articles.settings`. Fixed by moving import to module level.

### Completion Notes List

- **74 new tests** added across 5 test files, all passing
- **Full regression**: 334 passed, 4 skipped, 4 warnings (all pre-existing)
- No new dependencies added beyond `anthropic>=0.40.0` (v0.85.0 installed)
- PerturbationEngine is fully deterministic — no LLM calls needed for testing
- ArticleGenerator uses lazy client init for testability and dry-run compatibility
- BatchOrchestrator has two-level error handling: entity-level and perturbation-level
- GET endpoints for articles/{id} and batches/{id}/status return 501 (stub for EVAL-1.3)
- `_store_batch()` is a logging stub — EVAL-1.3 will add the backend POST endpoint
- Ready for EVAL-1.3 (Test Dataset Storage & Management) to add persistence

### File List

| File | Action |
|------|--------|
| `reasoning-service/requirements.txt` | Modified (added anthropic>=0.40.0) |
| `reasoning-service/app/services/eval/perturbation_engine.py` | Created |
| `reasoning-service/tests/services/eval/test_perturbation_engine.py` | Created |
| `reasoning-service/app/services/eval/article_generator.py` | Created |
| `reasoning-service/tests/services/eval/test_article_generator.py` | Created |
| `reasoning-service/app/services/eval/batch_orchestrator.py` | Created |
| `reasoning-service/tests/services/eval/test_batch_orchestrator.py` | Created |
| `reasoning-service/app/api/eval/articles.py` | Created |
| `reasoning-service/app/api/eval/batches.py` | Created |
| `reasoning-service/tests/api/eval/test_articles_api.py` | Created |
| `reasoning-service/tests/api/eval/test_batches_api.py` | Created |
| `reasoning-service/app/main.py` | Modified (added eval articles + batches routers) |

## QA Results

### Review Date: 2026-03-17

### Reviewed By: Quinn (Test Architect)

**Summary:** EVAL-1.2 delivers a complete synthetic article generation pipeline with perturbation engine, article generator, and batch orchestrator. All 8 acceptance criteria are met with 74 new tests and full regression passing.

**Acceptance Criteria Verification:**
| AC | Status | Notes |
|----|--------|-------|
| AC1 | PASS | 4 article types (NEWS_REPORT, BREAKING_NEWS, OPINION, ANALYSIS) with distinct style instructions |
| AC2 | PASS | All 6 perturbation types implemented with correct difficulty ratings and single controlled error |
| AC3 | PASS | ArticleTestCase includes source_facts, perturbations_applied, expected_findings, difficulty |
| AC4 | PASS | BatchConfig with entity_count, perturbation_types, article_types, model — runs unattended |
| AC5 | PASS | Dry-run returns prompt text with 0 tokens, no API calls made |
| AC6 | PASS | Rate limiting via asyncio.sleep(60/rpm) between calls |
| AC7 | PASS | Style instructions vary by ArticleType, factual content preserved via prompt constraints |
| AC8 | PASS | 30 perturbation engine unit tests, no API calls needed |

**Test Coverage:**
- 74 new tests across 5 test files
- 334 total tests passing, 4 skipped (pre-existing), 4 warnings (pre-existing)
- No regressions

**Findings:**
- MNT-001 (low): Per-call rate limiting, not global — acceptable for single-batch runs
- MNT-002 (low): `_store_batch()` is a stub — by design, deferred to EVAL-1.3
- DOC-001 (low): GET endpoints return 501 — by design, deferred to EVAL-1.3

### Gate Status

Gate: PASS → docs/qa/gates/EVAL-1.2-synthetic-article-generator.yml
