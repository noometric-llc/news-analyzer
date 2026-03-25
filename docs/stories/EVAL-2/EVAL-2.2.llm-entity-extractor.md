# Story EVAL-2.2: LLM Entity Extractor

## Status

Ready for Review

## Story

**As a** AI evaluation engineer,
**I want** a Claude-based entity extraction endpoint that produces output in the same format as the existing spaCy extractor,
**so that** I can run side-by-side comparisons of extraction quality using the same scorer and gold dataset.

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | LLM extractor produces entities with text, type, start/end character offsets, and confidence scores |
| AC2 | Output format matches existing `/entities/extract` response shape (text, entity_type, start, end, confidence) |
| AC3 | All 7 entity types supported: person, government_org, organization, location, event, concept, legislation |
| AC4 | Confidence scores are real model assessments (0.0–1.0), not fixed values |
| AC5 | Dry-run mode works without API calls (reuses existing `EVAL_DRY_RUN` setting) |
| AC6 | FastAPI endpoint `POST /eval/extract/llm` registered and accessible |
| AC7 | pytest tests verify prompt construction, response parsing, and dry-run mode (no API calls needed) |

## Tasks / Subtasks

- [x] Task 1: Create LLM Entity Extractor service (AC1, AC3, AC4)
  - [x] Create `reasoning-service/app/services/eval/llm_entity_extractor.py` with `LLMEntityExtractor` class
  - [x] Define `ENTITY_TYPES` constant matching the 7 internal types: person, government_org, organization, location, event, concept, legislation
  - [x] Implement `__init__` with lazy Anthropic SDK client initialization (same pattern as `ArticleGenerator` from EVAL-1.2)
  - [x] Implement `_build_prompt(text: str) -> str`:
    - System instructions for government news entity extraction
    - Entity type definitions matching SchemaMapper classification rules
    - Classification guidance: government_org vs organization distinction
    - Output format: JSON array of `{text, type, confidence}`
    - Instruction to extract ALL entities, not just the most prominent
  - [x] Implement `async def extract(text: str, model: str | None = None) -> list[dict]`:
    - Build prompt
    - Check `settings.eval_dry_run` — if true, return empty list
    - Call Claude API via `AsyncAnthropic.messages.create()`
    - Parse JSON response with error handling (malformed JSON fallback)
    - Return list of `{"text": str, "type": str, "start": int, "end": int, "confidence": float}`
  - [x] Implement `_parse_response(response_text: str, original_text: str) -> list[dict]`:
    - Parse JSON array from response
    - For each entity, locate text span in `original_text` to compute start/end offsets
    - Handle: entity not found in text (skip with warning), multiple occurrences (use first)
    - Validate entity type is in `ENTITY_TYPES` (skip unknowns with warning)
    - Validate confidence is 0.0–1.0 (clamp if outside range)
  - [x] Implement rate limiting: `asyncio.sleep(60 / settings.eval_rate_limit_rpm)` between calls

- [x] Task 2: Create FastAPI endpoint (AC2, AC5, AC6)
  - [x] Create `reasoning-service/app/api/eval/extraction.py` with router
  - [x] Define `ExtractionRequest` Pydantic model: `text: str`, `model: str | None = None`, `confidence_threshold: float = 0.0`
  - [x] Define `ExtractionResponse` Pydantic model matching existing `/entities/extract` shape:
    - `entities: list[Entity]` (reuse existing `Entity` model from `api/entities.py`)
    - `total_count: int`
  - [x] Implement `POST /eval/extract/llm` endpoint:
    - Instantiate `LLMEntityExtractor`
    - Call `extract(request.text, request.model)`
    - Filter by `confidence_threshold`
    - Map results to `Entity` response model (ensure field names match exactly)
    - Return `ExtractionResponse`
  - [x] Register router in `main.py`: `app.include_router(extraction.router, prefix="/eval/extract", tags=["eval-extraction"])`

- [x] Task 3: Write pytest tests (AC7)
  - [x] Create `reasoning-service/tests/services/eval/test_llm_entity_extractor.py`
  - [x] Test `_build_prompt()`: verify all 7 entity types appear in prompt, verify article text is embedded
  - [x] Test `_parse_response()` with valid JSON:
    - Standard case: 3 entities with valid types and confidences
    - Entity text found at correct offset in original text
  - [x] Test `_parse_response()` edge cases:
    - Entity text not found in original text (should skip)
    - Unknown entity type (should skip)
    - Confidence outside 0.0–1.0 range (should clamp)
    - Malformed JSON response (should return empty list, not crash)
    - Empty JSON array response
  - [x] Test dry-run mode: verify no API calls made, returns empty list
  - [x] Test endpoint via TestClient: request/response validation, confidence filtering
  - [x] Create `reasoning-service/tests/api/eval/test_extraction_api.py` for endpoint tests

- [x] Task 4: Verification
  - [x] Verify `POST /eval/extract/llm` returns same response shape as `POST /entities/extract`
  - [x] Run full reasoning-service pytest suite — no regressions
  - [x] Test dry-run mode end-to-end: `POST /eval/extract/llm` with `EVAL_DRY_RUN=true`

## Dev Notes

### Architecture Context

This story creates the **challenger extractor** for side-by-side comparison with spaCy. The critical design constraint is **output format compatibility** — both extractors must produce the same response shape so the Promptfoo scorer (EVAL-2.3) can score them identically.

### Response Format Compatibility

The existing `/entities/extract` returns:

```json
{
  "entities": [
    {
      "text": "John Fetterman",
      "entity_type": "person",
      "start": 8,
      "end": 23,
      "confidence": 0.85,
      "schema_org_type": "Person",
      "schema_org_data": {"@context": "https://schema.org", "@type": "Person", "name": "John Fetterman"},
      "properties": {}
    }
  ],
  "total_count": 1
}
```

The LLM extractor MUST return the same top-level shape (`entities[]` + `total_count`). The `schema_org_type`, `schema_org_data`, and `properties` fields can be omitted or set to defaults — the Promptfoo scorer only uses `text`, `entity_type`, `start`, `end`, and `confidence`.

### Prompt Design

From architecture doc Section 4.2:

```
You are a named entity extraction system for government news articles.

Extract ALL named entities from the following text. For each entity, provide:
- "text": the exact text span as it appears in the article
- "type": one of [person, government_org, organization, location, event, concept, legislation]
- "confidence": your confidence in the extraction and classification (0.0 to 1.0)

Classification rules:
- government_org: Any government body, agency, committee, department, branch, or court
- organization: Non-government organizations (companies, NGOs, parties as organizations)
- person: Named individuals
- location: Countries, states, cities, geographic features
- concept: Political groups, nationalities, ideologies, policies
- legislation: Laws, statutes, bills, executive orders by name
- event: Named events (elections, hearings, summits)

Return ONLY a JSON array. No explanation.

TEXT:
{article_text}
```

### Span Offset Computation

Claude returns entity `text` but not character offsets. The `_parse_response()` method must locate each entity in the original text to compute `start` and `end`:

```python
start = original_text.find(entity_text)
if start == -1:
    start = original_text.lower().find(entity_text.lower())  # Case-insensitive fallback
if start == -1:
    logger.warning(f"Entity '{entity_text}' not found in text, skipping")
    continue
end = start + len(entity_text)
```

### Existing Patterns to Follow

- **Lazy client init**: Same pattern as `ArticleGenerator.__init__()` from EVAL-1.2
- **Dry-run mode**: Check `settings.eval_dry_run` before API call
- **Rate limiting**: `asyncio.sleep(60 / settings.eval_rate_limit_rpm)` from EVAL-1.2
- **Router registration**: Follow pattern in `main.py` for existing eval routers

### Module Structure

```
reasoning-service/app/
├── api/eval/
│   ├── facts.py            # EVAL-1.1
│   ├── articles.py         # EVAL-1.2
│   ├── batches.py          # EVAL-1.2
│   └── extraction.py       # NEW — POST /eval/extract/llm
└── services/eval/
    ├── fact_extractor.py    # EVAL-1.1
    ├── fact_set_builder.py  # EVAL-1.1
    ├── perturbation_engine.py  # EVAL-1.2
    ├── article_generator.py    # EVAL-1.2
    ├── batch_orchestrator.py   # EVAL-1.2
    └── llm_entity_extractor.py # NEW
```

### No New Dependencies

Uses existing `anthropic` SDK (installed in EVAL-1.2). No new `requirements.txt` entries.

### Testing

**Framework:** pytest + pytest-asyncio
**Test Location:**
```
reasoning-service/tests/
├── services/eval/
│   └── test_llm_entity_extractor.py    # NEW
└── api/eval/
    └── test_extraction_api.py          # NEW
```

**Testing Standards:**
- Mock Anthropic SDK in extractor tests — verify prompt construction and response parsing
- Use `@pytest.mark.asyncio` for all async tests
- Test `_parse_response()` as a pure function (no mocking needed)
- Endpoint tests use FastAPI `TestClient`
- Formatter: Black (line length 88), Linter: Ruff, Type checker: mypy (strict)
- Run: `pytest` from `reasoning-service/` directory

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-19 | 1.0 | Initial story creation | Sarah (PO) |
| 2026-03-25 | 1.1 | All tasks complete. LLM extractor + endpoint + 34 tests. Ready for Review. | James (Dev Agent) |

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6)

### Debug Log References

- API endpoint tests initially failed: mock path targeted `app.api.eval.extraction.LLMEntityExtractor` but the endpoint uses a lazy import inside the function body, so the class isn't a module-level attribute. Fixed by patching at `app.services.eval.llm_entity_extractor.LLMEntityExtractor`.
- 5 pre-existing test failures in `TestExtractJudgeFacts` (from EVAL-1 FJC import changes) — not related to EVAL-2.2.

### Completion Notes List

- **Task 1**: `LLMEntityExtractor` class (~200 lines) with system prompt, JSON parsing with 3-tier fallback (direct, markdown fences, bracket search), offset computation via `str.find()`, deduplication, type validation, confidence clamping
- **Task 2**: `POST /eval/extract/llm` endpoint with `LLMExtractionResponse` matching spaCy shape. `schema_org_type`/`schema_org_data`/`properties` defaulted to empty values for compatibility.
- **Task 3**: 26 unit tests + 8 endpoint tests = 34 total, all passing. Covers prompt construction, all parse edge cases, dry-run, model override, confidence filtering, error handling.
- **Task 4**: Full regression suite: 366 passed, 5 failed (pre-existing), 4 skipped. Zero new regressions.

### File List

| File | Action |
|------|--------|
| `reasoning-service/app/services/eval/llm_entity_extractor.py` | Created |
| `reasoning-service/app/api/eval/extraction.py` | Created |
| `reasoning-service/app/main.py` | Modified (added eval_extraction router) |
| `reasoning-service/tests/services/eval/test_llm_entity_extractor.py` | Created |
| `reasoning-service/tests/api/eval/test_extraction_api.py` | Created |

## QA Results

_(To be filled by QA agent)_
