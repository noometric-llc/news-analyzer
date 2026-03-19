# Story EVAL-1.1: KB Fact Extractor Module

## Status

Done

## Story

**As a** AI evaluation engineer,
**I want** a Python module that extracts structured facts from the Knowledge Base across all three government branches,
**so that** I have a reliable, typed foundation of ground-truth facts to feed into synthetic article generation and downstream LLM evaluation.

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Fact extractor produces valid Fact tuples for at least 3 entity types per branch (Legislative: member, committee, terms; Executive: presidency, administration, EOs; Judicial: judge, court, appointing president) |
| AC2 | Facts include source attribution (data_source field populated for every Fact) |
| AC3 | Fact sets can be assembled by entity (e.g., all facts about a specific senator) |
| AC4 | Fact sets can be assembled by topic (e.g., "Banking Committee membership") |
| AC5 | Async API client handles pagination and error cases (retry, timeout, 404 handling) |
| AC6 | pytest tests verify extraction logic against known KB data |
| AC7 | FastAPI endpoint exposes fact extraction for manual inspection (`GET /eval/facts/{entity_type}/{id}` and `GET /eval/facts/sample`) |

## Tasks / Subtasks

- [x] Task 1: Create Pydantic data models (AC1, AC2)
  - [x] Create `reasoning-service/app/models/__init__.py`
  - [x] Create `reasoning-service/app/models/eval.py` with all enums: `GovernmentBranch`, `FactPredicate`, `FactConfidence`, `PerturbationType`, `ArticleType`, `Difficulty`
  - [x] Define core models: `Fact`, `FactSet`, `PerturbedFactSet`, `ArticleTestCase`, `BatchConfig`, `BatchResult`
  - [x] Add helper methods on `FactSet`: `get_fact(predicate)`, `get_facts(predicate)`
  - [x] Write pytest tests for model validation, serialization, and helper methods
- [x] Task 2: Create async Backend API Client (AC5)
  - [x] Create `reasoning-service/app/clients/__init__.py`
  - [x] Create `reasoning-service/app/clients/backend_client.py` with `BackendClient` class
  - [x] Implement Legislative endpoints: `get_members`, `get_member`, `get_member_committees`, `get_member_terms`, `get_committees`
  - [x] Implement Executive endpoints: `get_presidencies`, `get_current_presidency`, `get_administration`, `get_executive_orders`, `get_cabinet`, `get_cabinet_departments`
  - [x] Implement Judicial endpoints: `get_judges`
  - [x] Add pagination helper for auto-fetching all pages
  - [x] Add error handling: retry logic, timeout handling, 404 → None
  - [x] Add `close()` method for proper client lifecycle
  - [x] Extend Settings in `app/config.py` (or existing settings) with `backend_url` and eval-related config
  - [x] Write pytest tests with httpx mock for request construction, error handling, and pagination
- [x] Task 3: Create Fact Extractor service (AC1, AC2, AC3)
  - [x] Create `reasoning-service/app/services/eval/__init__.py`
  - [x] Create `reasoning-service/app/services/eval/fact_extractor.py` with `FactExtractor` class
  - [x] Implement `extract_member_facts(bioguide_id)` → FactSet (party, state, district, chamber, committees, leadership)
  - [x] Implement `extract_presidency_facts(presidency_id)` → FactSet (president name, term number, party, VP, cabinet, CoS, EOs)
  - [x] Implement `extract_judge_facts(judge_id)` → FactSet (judge name, court, appointing president, confirmation date, court level)
  - [x] Implement `extract_random_sample(branch, count)` → list[FactSet]
  - [x] Ensure every Fact has `data_source` populated
  - [x] Write pytest tests with mocked BackendClient for each extraction method
- [x] Task 4: Create Fact Set Builder service (AC3, AC4)
  - [x] Create `reasoning-service/app/services/eval/fact_set_builder.py` with `FactSetBuilder` class
  - [x] Implement `build_legislative_set(bioguide_id)` — enriches member facts with committee context
  - [x] Implement `build_executive_set(presidency_id)` — enriches presidency facts with administration and EO context
  - [x] Implement `build_entity_pool(branch, count)` — builds pool of FactSets for batch generation
  - [x] Write pytest tests for builder composition logic
- [x] Task 5: Create FastAPI endpoints (AC7)
  - [x] Create `reasoning-service/app/api/eval/__init__.py`
  - [x] Create `reasoning-service/app/api/eval/facts.py` with router
  - [x] Implement `GET /eval/facts/{entity_type}/{id}` — returns FactSet for a specific entity
  - [x] Implement `GET /eval/facts/sample?branch=...&count=...` — returns sample FactSets
  - [x] Register router in `main.py` with prefix `/eval/facts` and tag `eval-facts`
  - [x] Write pytest tests for endpoint request/response validation
- [x] Task 6: Integration verification
  - [x] Verify all new modules import correctly and app starts without errors
  - [x] Run full pytest suite — no regressions in existing tests

## Dev Notes

### Architecture Context

This story builds the **data extraction layer** for the EVAL-1 pipeline. The Fact Extractor reads from existing backend REST APIs (read-only — no database changes, no backend changes) and produces structured `Fact` tuples that EVAL-1.2 will use for article generation.

**Key architectural decision:** Facts-first perturbation — modify facts before article generation, not after. This means the data models defined here (especially `FactPredicate` enum) must be designed for type-safe perturbation logic downstream.

### Module Structure

All new code goes into the reasoning-service following the **vertical slice** pattern:

```
reasoning-service/app/
├── api/eval/                    # NEW — EVAL track endpoints
│   ├── __init__.py
│   └── facts.py                 # GET /eval/facts/{entity_type}/{id}
│                                # GET /eval/facts/sample?branch=...
├── services/eval/               # NEW — EVAL business logic
│   ├── __init__.py
│   ├── fact_extractor.py        # Queries backend APIs → Fact tuples
│   └── fact_set_builder.py      # Assembles coherent FactSets
├── models/                      # NEW — shared Pydantic models
│   ├── __init__.py
│   └── eval.py                  # Fact, FactSet, enums, etc.
└── clients/                     # NEW — typed API clients
    ├── __init__.py
    └── backend_client.py        # Async HTTPX client for backend
```

### Backend API Endpoints Consumed (Read-Only)

| Branch | Endpoint | Returns |
|--------|----------|---------|
| Legislative | `GET /api/members?page={p}&size={s}` | `Page<MemberDTO>` |
| Legislative | `GET /api/members/{bioguideId}` | `MemberDTO` |
| Legislative | `GET /api/members/{bioguideId}/committees` | `Page<CommitteeMembership>` |
| Legislative | `GET /api/members/{bioguideId}/terms` | `List<PositionHolding>` |
| Legislative | `GET /api/committees` | `Page<Committee>` |
| Executive | `GET /api/presidencies` | `Page<PresidencyDTO>` |
| Executive | `GET /api/presidencies/current` | `PresidencyDTO` |
| Executive | `GET /api/presidencies/{id}/administration` | `AdministrationDTO` |
| Executive | `GET /api/presidencies/{id}/executive-orders` | `Page<ExecutiveOrderDTO>` |
| Executive | `GET /api/appointees/cabinet` | `List<AppointeeDTO>` |
| Executive | `GET /api/government-organizations/cabinet-departments` | `List<GovOrg>` |
| Judicial | `GET /api/judges?page={p}&size={s}` | `Page<JudgeDTO>` |

### Data Models Reference

The `FactPredicate` enum constrains the set of fact predicates for type-safe perturbation:

- **Legislative:** `PARTY_AFFILIATION`, `STATE`, `DISTRICT`, `CHAMBER`, `COMMITTEE_MEMBERSHIP`, `LEADERSHIP_ROLE`, `TERM_START`, `TERM_END`
- **Executive:** `PRESIDENCY_NUMBER`, `VICE_PRESIDENT`, `CABINET_POSITION`, `EXECUTIVE_ORDER`, `CHIEF_OF_STAFF`, `AGENCY_HEAD`
- **Judicial:** `COURT`, `APPOINTING_PRESIDENT`, `CONFIRMATION_DATE`, `COURT_LEVEL`
- **Cross-branch:** `STATUTE_REFERENCE`, `STATUTE_SUBJECT`

`FactConfidence` has two tiers: `TIER_1` (official government source) and `TIER_2` (enrichment/derived).

### Configuration

Extend existing pydantic-settings `Settings` class:

```python
backend_url: str = "http://localhost:8080"
anthropic_api_key: str = ""              # Used in EVAL-1.2, define now
eval_default_model: str = "claude-sonnet-4-20250514"
eval_rate_limit_rpm: int = 50
eval_max_batch_size: int = 50
```

### Router Registration

Add to `main.py`:
```python
from app.api.eval import facts
app.include_router(facts.router, prefix="/eval/facts", tags=["eval-facts"])
```

### Testing

**Framework:** pytest + pytest-asyncio
**Test Location:** `reasoning-service/tests/` mirroring source structure:
```
reasoning-service/tests/
├── models/
│   └── test_eval_models.py
├── clients/
│   └── test_backend_client.py
├── services/eval/
│   ├── test_fact_extractor.py
│   └── test_fact_set_builder.py
└── api/eval/
    └── test_facts_api.py
```

**Testing Standards:**
- Use `pytest.fixture` for shared test setup
- Use `@pytest.mark.asyncio` for async tests
- Mock `BackendClient` responses with httpx mock — do NOT call live backend in unit tests
- Use `TestClient` from FastAPI for endpoint tests
- Follow Arrange/Act/Assert pattern
- Test error cases: 404, timeout, malformed response
- Formatter: Black (line length 88), Linter: Ruff, Type checker: mypy (strict)
- Run: `pytest` from `reasoning-service/` directory

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-15 | 1.0 | Initial story creation | Sarah (PO) |
| 2026-03-16 | 1.1 | Implementation complete — all 6 tasks done | James (Dev Agent) |

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6)

### Debug Log References

- Pydantic v2 `protected_namespaces` warning for `model_used` field — resolved with `model_config = {"protected_namespaces": ()}`
- Backend lacks single-resource GET for presidencies and judges — implemented `_find_presidency_by_id` and `_find_judge_by_id` that fetch paginated lists and filter by ID
- CommitteeMembership response format ambiguity — handled both nested object (`cm["committee"]["name"]`) and flat field (`cm["committeeName"]`) patterns
- Used httpx built-in `MockTransport` instead of adding respx/pytest-httpx dependency
- Architecture doc shows `FactSetBuilder(extractor)` but implementation uses `FactSetBuilder(extractor, client)` — `client` param added so `build_committee_topic()` (AC4) can call `get_members()` directly for topic-based member filtering

### Completion Notes List

- **93 new tests** added across 5 test files, all passing (6 resource cleanup + error propagation tests added during review)
- **Full regression**: 254 passed, 4 skipped, 4 warnings (all pre-existing)
- No new dependencies added — all libraries already in project (httpx, pydantic, FastAPI, pytest-asyncio)
- `app/config.py` centralizes settings via pydantic-settings with `.env` file support
- Architecture uses vertical slice pattern: `api/eval/`, `services/eval/`, `models/`, `clients/`
- `asyncio.gather()` used for concurrent API calls in extraction methods
- Entity pool builds count+5 extras for CONFLATE_INDIVIDUALS perturbation type
- Ready for EVAL-1.2 (Synthetic Article Generator) to consume FactSets

### File List

| File | Action |
|------|--------|
| `reasoning-service/app/models/__init__.py` | Created |
| `reasoning-service/app/models/eval.py` | Created |
| `reasoning-service/tests/models/__init__.py` | Created |
| `reasoning-service/tests/models/test_eval_models.py` | Created |
| `reasoning-service/app/config.py` | Created |
| `reasoning-service/app/clients/__init__.py` | Created |
| `reasoning-service/app/clients/backend_client.py` | Created |
| `reasoning-service/tests/clients/__init__.py` | Created |
| `reasoning-service/tests/clients/test_backend_client.py` | Created |
| `reasoning-service/app/services/eval/__init__.py` | Created |
| `reasoning-service/app/services/eval/fact_extractor.py` | Created |
| `reasoning-service/tests/services/eval/__init__.py` | Created |
| `reasoning-service/tests/services/eval/test_fact_extractor.py` | Created |
| `reasoning-service/app/services/eval/fact_set_builder.py` | Created |
| `reasoning-service/tests/services/eval/test_fact_set_builder.py` | Created |
| `reasoning-service/app/api/eval/__init__.py` | Created |
| `reasoning-service/app/api/eval/facts.py` | Created |
| `reasoning-service/app/main.py` | Modified (added eval router) |
| `reasoning-service/tests/api/__init__.py` | Created |
| `reasoning-service/tests/api/eval/__init__.py` | Created |
| `reasoning-service/tests/api/eval/test_facts_api.py` | Created |

## QA Results

### Review Date: 2026-03-17

### Reviewed By: Quinn (Test Architect)

**AC Verification:**

| AC | Status | Evidence |
|---|---|---|
| AC1 — 3+ entity types per branch | PASS | Legislative: member, committees, terms. Executive: presidency, administration (VP, CoS, cabinet). Judicial: judge, court, appointing president. |
| AC2 — data_source attribution | PASS | Dedicated `test_all_facts_have_data_source` in each extraction test class. Every Fact asserts non-empty `data_source`. |
| AC3 — Assembly by entity | PASS | `build_legislative_set`, `build_executive_set`, `build_judicial_set` delegate to extractor. Tested for all branches + None return case. |
| AC4 — Assembly by topic | PASS | `build_committee_topic` filters members by committee name match. Tested with matching and non-matching members. |
| AC5 — Async client, pagination, errors | PASS | `_get_all_pages` auto-paginates. `_get_or_none` handles 404→None. Retry on transient errors. 20 client tests. |
| AC6 — pytest tests | PASS | 93 new tests across 5 files: models (29), clients (20), fact_extractor (19), fact_set_builder (9), facts_api (16). |
| AC7 — FastAPI endpoints | PASS | `GET /eval/facts/{entity_type}/{id}` and `GET /eval/facts/sample` registered at prefix `/eval/facts`. Input validation via enum + Query params. Resource cleanup in `finally` blocks. |

**Code Quality Observations:**
- Vertical slice architecture cleanly separates concerns
- `asyncio.gather()` for concurrent API calls is a good production pattern
- Resource cleanup tested for success, 404, and exception paths (6 dedicated tests)
- No new dependencies introduced — all existing libraries reused
- `httpx.MockTransport` used for client tests — avoids extra test dependency

**Low-severity findings (3):** Architecture divergence on FactSetBuilder constructor (documented), removed external doc reference, O(n) judge/presidency lookup acceptable for eval volume.

### Gate Status

Gate: PASS → docs/qa/gates/EVAL-1.1-kb-fact-extractor-module.yml
