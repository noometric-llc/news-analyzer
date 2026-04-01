# Story EVAL-3.3: Ontology-Grounded Bias Detector

## Status

Ready for Review

## Story

**As an** AI evaluation engineer building a neuro-symbolic bias detection system,
**I want** a bias detector that queries the OWL ontology via SPARQL for formal definitions, builds grounded LLM prompts from those definitions, and validates output via SHACL,
**so that** the system produces auditable, academically-traceable bias detection results rather than relying on the LLM's unverifiable internal knowledge.

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `POST /fallacies/detect` returns real bias/fallacy detections (not empty stub) |
| AC2 | `POST /eval/bias/detect` returns annotations with ontology metadata (definition, academic source, detection pattern) |
| AC3 | Detection prompt contains SPARQL-retrieved definitions and academic sources |
| AC4 | Response includes `distortions_checked` list showing which biases were evaluated |
| AC5 | SHACL validates output annotations before returning |
| AC6 | Dry-run mode returns empty results without calling Claude API |
| AC7 | Graceful error handling: Claude failure returns error response, doesn't crash service |
| AC8 | Existing `/entities/extract` and `/eval/extract/llm` endpoints unaffected |

## Tasks / Subtasks

- [x] Task 1: Create OntologyGroundedBiasDetector service (AC3, AC5, AC6, AC7)
  - [x] Create `reasoning-service/app/services/eval/bias_detector.py`
  - [x] Class `OntologyGroundedBiasDetector`:
    - Constructor: takes optional `api_key`, `model` (follows `LLMEntityExtractor` pattern)
    - Lazy Anthropic client initialization (`_get_client()`)
    - Instance attributes: `_api_key`, `_model`, `_client`
  - [x] Main method `async detect(self, text: str, distortion_types: list[str] | None = None, confidence_threshold: float = 0.0, model: str | None = None, grounded: bool = True) -> BiasDetectionOutput`:
    - **Step 1 — Retrieve definitions:** If `grounded=True`, call `get_reasoner().get_all_distortion_definitions()` (from EVAL-3.2). If `grounded=False`, skip SPARQL — pass `definitions=None` to prompt builder.
    - If `distortion_types` is not None, filter to requested types only
    - **Step 2 — Build prompt:** Call `_build_prompt(text, definitions)` — when `definitions` is None, build a generic ungrounded prompt (see Task 2)
    - Log the full grounded prompt at DEBUG level (valuable for debugging detection quality and methodology writeup)
    - **Step 3 — Call Claude:** `client.messages.create()` with system prompt + user prompt
    - **Step 4 — Parse response:** `_parse_response(response_text)` → list of annotation dicts
    - **Step 5 — SHACL validate:** Convert annotations to RDF triples, validate against BiasAnnotationShape
    - Log SHACL violations as warnings (don't reject — return with a `shacl_valid` flag)
    - **Step 6 — Filter by confidence_threshold and return**
    - Rate limiting: `await asyncio.sleep(60 / settings.eval_rate_limit_rpm)`
  - [x] Dry-run check: if `settings.eval_dry_run`, return empty result immediately (Step 0)
  - [x] Error handling: wrap Claude call in try/except, log error, raise HTTPException with descriptive message

- [x] Task 2: Build system prompt and prompt builder (AC3)
  - [x] Module-level `_SYSTEM_PROMPT` constant — follows `LLMEntityExtractor` pattern:
    ```
    You are a cognitive bias and logical fallacy detection system for news articles.

    Analyze the provided text for cognitive biases and logical fallacies.
    Use ONLY the definitions provided below — do not rely on your own knowledge
    of biases/fallacies. Each detection must be traceable to the provided definition.

    For each detected bias or fallacy, provide:
    - "distortion_type": the exact type identifier from the definitions below
    - "category": "cognitive_bias" or "logical_fallacy"
    - "excerpt": the exact text excerpt demonstrating the distortion
    - "explanation": why this excerpt demonstrates this specific distortion,
      referencing the definition
    - "confidence": your confidence (0.0 to 1.0)

    Important:
    - Only flag distortions you are confident about (>0.5 confidence)
    - Quote the EXACT text from the article in excerpt
    - If no distortions are found, return an empty array []
    - Return ONLY a JSON array. No explanation, no markdown fences.
    ```
  - [x] `_build_prompt(self, text: str, definitions: list[dict]) -> str` method:
    - Constructs user prompt with two sections:
      1. **DEFINITIONS:** — formatted list of all distortion definitions with detection patterns and sources
      2. **TEXT TO ANALYZE:** — the article text
    - Definition format per distortion:
      ```
      [confirmation_bias] Confirmation Bias (Nickerson, 1998)
      Definition: The tendency to search for, interpret, favor...
      Detection pattern: Look for selective evidence citation...
      ```
    - This is the neuro-symbolic core — the prompt is grounded in SPARQL-retrieved formal definitions, not LLM training data
  - [x] `_build_ungrounded_prompt(self, text: str) -> str` method (for EVAL-3.5 A/B comparison):
    - No definitions section — just: "Analyze the following text for cognitive biases and logical fallacies. For each one found, provide distortion_type, category, excerpt, explanation, and confidence."
    - Same JSON output format requirement so the scorer works identically
    - Used when `grounded=False` is passed to `detect()`

- [x] Task 3: Build response parser (AC2, AC5)
  - [x] `_parse_response(self, response_text: str) -> list[dict]` method:
    - Follow `LLMEntityExtractor._parse_response()` pattern:
      1. Try direct JSON parse
      2. Strip markdown fences (` ```json ... ``` `)
      3. Find JSON array anywhere in text
    - Validate each annotation dict has required fields: `distortion_type`, `excerpt`, `explanation`, `confidence`
    - Validate `distortion_type` is in the ontology's known distortions (controlled vocabulary)
    - Clamp confidence to [0.0, 1.0]
    - Skip invalid annotations with warning log (don't crash on malformed LLM output)
  - [x] `_resolve_category(self, distortion_type: str) -> str` method:
    - Derive category from ontology class hierarchy, NOT from LLM response
    - Build a lookup map at init (or lazily on first call) from SPARQL:
      ```sparql
      SELECT ?d ?class WHERE {
        ?d rdf:type owl:NamedIndividual .
        VALUES ?class { cb:CognitiveBias cb:LogicalFallacy }
        ?d rdf:type/rdfs:subClassOf* ?class .
      }
      ```
    - Maps distortion_type → "cognitive_bias" or "logical_fallacy"
    - If LLM returns a category that disagrees with ontology, use the ontology's answer and log a warning
    - This ensures the category is always correct regardless of LLM behavior
  - [x] `_convert_to_rdf(self, annotations: list[dict]) -> Graph` method:
    - Convert parsed annotations to RDF `BiasAnnotation` triples for SHACL validation
    - Each annotation becomes a `cb:BiasAnnotation` individual with `cb:hasDistortionType`, `cb:detectedIn`, `cb:hasConfidence`, `cb:hasExplanation`
  - [x] `_validate_annotations(self, annotations_graph: Graph) -> bool` method:
    - Call `SHACLValidator.validate(annotations_graph)`
    - Log violations as warnings
    - Return `shacl_valid` boolean (True if conforms)

- [x] Task 4: Define output models (AC2, AC4)
  - [x] Pydantic models in `bias_detector.py` (or separate models file):
    ```python
    class BiasAnnotation(BaseModel):
        distortion_type: str
        category: str  # "cognitive_bias" or "logical_fallacy"
        excerpt: str
        explanation: str
        confidence: float
        ontology_metadata: OntologyMetadata | None = None

    class OntologyMetadata(BaseModel):
        definition: str
        academic_source: str
        detection_pattern: str

    class BiasDetectionOutput(BaseModel):
        annotations: list[BiasAnnotation]
        total_count: int
        distortions_checked: list[str]
        shacl_valid: bool
    ```

- [x] Task 5: Implement `/fallacies/detect` endpoint (AC1, AC8)
  - [x] Modify `reasoning-service/app/api/fallacies.py`:
    - Replace TODO stub with real implementation
    - Import `OntologyGroundedBiasDetector`
    - Call `detector.detect(text=request.text)`
    - Map `BiasDetectionOutput` to existing `FallacyDetectionResponse` model:
      - annotations with `category == "logical_fallacy"` → `fallacies` list
      - annotations with `category == "cognitive_bias"` → `biases` list
      - `overall_quality_score` = 1.0 - (detected_count / distortions_checked_count) — higher score = fewer biases found
    - **Critical:** Response shape MUST match existing `FallacyDetectionResponse` Pydantic model exactly. Do NOT add fields.
  - [x] Handle case where bias ontology not loaded: return 503 with message "Bias ontology not loaded"

- [x] Task 6: Implement `/eval/bias/detect` endpoint (AC2, AC4)
  - [x] Add to `reasoning-service/app/api/eval/bias.py` (created in EVAL-3.2):
  - [x] New Pydantic request model:
    ```python
    class BiasDetectRequest(BaseModel):
        text: str
        distortion_types: list[str] | None = None
        confidence_threshold: float = 0.0
        include_ontology_metadata: bool = True
        grounded: bool = True  # False = skip SPARQL, use generic prompt (for A/B comparison in EVAL-3.5)
    ```
  - [x] `POST /eval/bias/detect` endpoint:
    - Create `OntologyGroundedBiasDetector` instance
    - Call `detector.detect(text, distortion_types, confidence_threshold)`
    - If `include_ontology_metadata`, enrich each annotation with definition/source/pattern from ontology
    - Return full `BiasDetectionOutput` with ontology metadata
  - [x] Handle case where bias ontology not loaded: return 503

- [x] Task 7: Write tests (AC1–AC8)
  - [x] Create `reasoning-service/tests/services/eval/test_bias_detector.py`
  - [x] Test: `_build_prompt()` contains ontology definitions and academic sources
  - [x] Test: `_build_prompt()` with filtered `distortion_types` only includes requested types
  - [x] Test: `_parse_response()` extracts valid annotations from well-formed JSON
  - [x] Test: `_parse_response()` handles markdown-fenced JSON
  - [x] Test: `_parse_response()` skips annotations with invalid `distortion_type`
  - [x] Test: `_parse_response()` clamps confidence to [0, 1]
  - [x] Test: `_parse_response()` handles empty array response
  - [x] Test: `_parse_response()` handles malformed JSON gracefully (returns empty)
  - [x] Test: `_convert_to_rdf()` produces valid RDF triples
  - [x] Test: dry-run mode returns empty `BiasDetectionOutput` without API call
  - [x] Test: `/fallacies/detect` endpoint returns `FallacyDetectionResponse` shape (mock detector)
  - [x] Test: `/eval/bias/detect` endpoint returns annotations with ontology metadata (mock detector)
  - [x] Test: existing `/entities/extract` endpoint still works (regression)
  - [x] Run full existing test suite

## Dev Notes

### Relevant Source Tree

```
reasoning-service/
├── app/
│   ├── api/
│   │   ├── fallacies.py                     # MODIFY — implement stub with BiasDetector
│   │   └── eval/
│   │       └── bias.py                      # MODIFY — add POST /eval/bias/detect endpoint
│   └── services/
│       ├── owl_reasoner.py                  # EXISTS (extended in EVAL-3.2) — SPARQL queries
│       ├── shacl_validator.py               # EXISTS (from EVAL-3.2) — annotation validation
│       └── eval/
│           ├── llm_entity_extractor.py      # REFERENCE PATTERN — follow this architecture
│           └── bias_detector.py             # NEW — OntologyGroundedBiasDetector
├── tests/
│   └── services/eval/
│       └── test_bias_detector.py            # NEW
```

### Distortion Type Naming Convention (CRITICAL)

The ontology uses PascalCase URIs (`cb:ConfirmationBias`), but the JSON API and scorer use snake_case (`confirmation_bias`). The conversion rule:

| Layer | Format | Example |
|-------|--------|---------|
| OWL Ontology URI | PascalCase | `cb:ConfirmationBias` |
| Prompt `[type_id]` | snake_case | `[confirmation_bias]` |
| LLM JSON response | snake_case | `"distortion_type": "confirmation_bias"` |
| Scorer match key | snake_case | `DISTORTION_CATEGORIES["confirmation_bias"]` |
| Gold dataset YAML | snake_case | `type: confirmation_bias` |

**Where conversion happens:** In `_build_prompt()` when formatting the definitions section. The prompt builder takes the URI local name (`ConfirmationBias`), converts to snake_case, and uses that as the `[type_id]`:

```python
def _uri_to_snake(uri_local_name: str) -> str:
    """Convert PascalCase URI local name to snake_case for JSON API."""
    # ConfirmationBias → confirmation_bias
    import re
    return re.sub(r'(?<!^)(?=[A-Z])', '_', uri_local_name).lower()
```

**The system prompt instructs Claude to return the exact `distortion_type` identifiers from the definitions section** — so the prompt builder's snake_case `[type_id]` flows through to the LLM response, which flows to the scorer. All layers must agree on snake_case.

**Validation in `_parse_response()`:** Check that each `distortion_type` in the LLM response exists in the set of known snake_case identifiers. Reject unknown types with a warning log.

### LLMEntityExtractor Pattern (Reference)

The bias detector follows the same architecture as [llm_entity_extractor.py](reasoning-service/app/services/eval/llm_entity_extractor.py):

| Aspect | LLMEntityExtractor | OntologyGroundedBiasDetector |
|---|---|---|
| Constructor | `api_key`, `model`, lazy client | Same |
| Main method | `async extract(text, model)` | `async detect(text, distortion_types, confidence_threshold, model)` |
| System prompt | Static `_SYSTEM_PROMPT` constant | Static — but user prompt includes dynamic ontology definitions |
| User prompt | `f"TEXT:\n{text}"` | Definitions section + text section |
| Response parsing | `_parse_response()` with JSON extraction + fallbacks | Same pattern + distortion type validation |
| Rate limiting | `asyncio.sleep(60 / rpm)` | Same |
| Dry-run | `settings.eval_dry_run` check | Same |
| Output | `list[dict]` | `BiasDetectionOutput` Pydantic model |

**Key difference:** The bias detector adds two steps that the entity extractor doesn't have:
1. **Pre-LLM:** SPARQL query to retrieve definitions (neuro-symbolic step)
2. **Post-LLM:** SHACL validation of output annotations

### RDF Triple Creation Pattern (for `_convert_to_rdf`)

The dev agent knows Turtle syntax from EVAL-3.1 but may not know the rdflib Python API. Here's the pattern for creating BiasAnnotation individuals programmatically:

```python
from rdflib import Graph, Literal, BNode, Namespace, URIRef
from rdflib.namespace import RDF, XSD

CB = Namespace("http://newsanalyzer.org/ontology/cognitive-bias#")

def _convert_to_rdf(annotations: list[dict]) -> Graph:
    g = Graph()
    g.bind("cb", CB)

    for annotation in annotations:
        ann = BNode()  # anonymous individual (no URI needed)
        g.add((ann, RDF.type, CB.BiasAnnotation))
        g.add((ann, CB.hasDistortionType, CB[annotation["distortion_type"]]))
        g.add((ann, CB.detectedIn, Literal(annotation["excerpt"])))
        g.add((ann, CB.hasConfidence, Literal(annotation["confidence"], datatype=XSD.float)))
        g.add((ann, CB.hasExplanation, Literal(annotation["explanation"])))

    return g
```

Use `BNode()` (blank node) for annotations since they're transient — they don't need persistent URIs.

### The Neuro-Symbolic Data Flow

```
1. Request arrives: text + optional distortion_types filter
2. SPARQL → get_all_distortion_definitions() returns 13 definitions
3. Build prompt: _SYSTEM_PROMPT + definitions + article text
4. Claude analyzes text against formal definitions
5. Parse JSON response → list of annotation dicts
6. Convert annotations to RDF triples (BiasAnnotation individuals)
7. SHACL validate against BiasAnnotationShape
8. Return validated, ontology-enriched annotations
```

### Mapping BiasDetectionOutput → FallacyDetectionResponse

The `/fallacies/detect` endpoint must return the existing Pydantic model. Mapping:

```python
# From BiasDetectionOutput annotations
fallacies = [
    Fallacy(
        type=a.distortion_type,
        excerpt=a.excerpt,
        explanation=a.explanation,
        confidence=a.confidence,
    )
    for a in output.annotations
    if a.category == "logical_fallacy"
]

biases = [
    BiasDetection(
        type=a.distortion_type,
        excerpt=a.excerpt,
        explanation=a.explanation,
        confidence=a.confidence,
    )
    for a in output.annotations
    if a.category == "cognitive_bias"
]

# Quality score heuristic: fewer biases detected = higher quality
# NOTE: This is a rough heuristic, not a rigorous metric. The score depends on
# how many distortion types were checked, not just how many were found.
# A well-written article about a biased topic may still flag legitimately.
# Document this caveat in the code comment.
quality = 1.0 - min(len(output.annotations) / len(output.distortions_checked), 1.0) if output.distortions_checked else 0.0
```

### Settings / Configuration

Reuses existing settings from `reasoning-service/app/config.py` (or wherever settings are defined):
- `settings.anthropic_api_key` — Claude API key
- `settings.eval_default_model` — Default model (e.g., "claude-sonnet-4-20250514")
- `settings.eval_dry_run` — Skip API calls (for testing)
- `settings.eval_rate_limit_rpm` — Requests per minute limit

No new settings required.

### Token Usage Estimate

Per detection call (~1,600–2,400 tokens total):
- System prompt: ~300 tokens
- Definitions (13 distortions): ~800–1,200 tokens
- Article text: ~300–500 tokens
- Response: ~200–400 tokens
- Cost: ~$0.008–$0.012 per article

### Testing

**Test file:** `reasoning-service/tests/services/eval/test_bias_detector.py`

**Testing framework:** pytest + pytest-asyncio

**What to test:**
- Prompt builder includes ontology definitions (verify string contains definition text)
- Prompt builder respects `distortion_types` filter
- Response parser handles well-formed, markdown-fenced, and malformed JSON
- Response parser validates distortion types against ontology
- RDF conversion produces valid BiasAnnotation triples
- Dry-run returns empty without API call
- Endpoint tests with mocked detector (verify response shape)
- Regression: existing endpoints unaffected

**Mocking strategy:**
- Mock `AsyncAnthropic.messages.create()` to return canned Claude responses
- Mock `get_reasoner()` to return a reasoner with test ontology loaded
- Mock `SHACLValidator.validate()` for tests that don't need real SHACL

**Test fixtures:**
```python
MOCK_CLAUDE_RESPONSE = '''[
  {
    "distortion_type": "framing_effect",
    "category": "cognitive_bias",
    "excerpt": "reckless policy",
    "explanation": "Uses loaded language to frame negatively",
    "confidence": 0.82
  },
  {
    "distortion_type": "ad_hominem",
    "category": "logical_fallacy",
    "excerpt": "the senator's incompetence",
    "explanation": "Attacks the person rather than the argument",
    "confidence": 0.75
  }
]'''
```

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (1M context)

### File List

| File | Action | Description |
|------|--------|-------------|
| `reasoning-service/app/services/eval/bias_detector.py` | NEW | OntologyGroundedBiasDetector — core neuro-symbolic bias detection service (~340 lines). Includes system/ungrounded prompts, prompt builder, JSON parser, RDF converter, SHACL validator, Pydantic output models, PascalCase↔snake_case helpers. |
| `reasoning-service/app/api/fallacies.py` | MODIFIED | Replaced TODO stub with real implementation using OntologyGroundedBiasDetector. Maps BiasDetectionOutput → FallacyDetectionResponse. |
| `reasoning-service/app/api/eval/bias.py` | MODIFIED | Added POST /eval/bias/detect endpoint with BiasDetectRequest (grounded parameter), ontology metadata enrichment. |
| `reasoning-service/tests/services/eval/test_bias_detector.py` | NEW | 20 tests — naming conversion, prompt building, JSON parsing, category resolution, RDF conversion, SHACL validation, dry-run, full mocked flow. |

### Completion Notes

- All 7 tasks complete, 82 tests pass across EVAL-3.1/3.2/3.3 + existing regression (0 failures)
- PascalCase→snake_case naming convention implemented via `_uri_to_snake()` / `_snake_to_pascal()` helpers
- Category resolution uses ontology class hierarchy (not LLM output) — overrides LLM if they disagree
- SHACL validation merges ontology into annotation graph so class references resolve
- `grounded` parameter threaded through detect() → prompt builder → system prompt for EVAL-3.5 A/B
- Existing /entities/extract and /eval/extract/llm endpoints unaffected (AC8 confirmed via regression suite)

### Debug Log References

None — no issues encountered during implementation.

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-28 | 1.0 | Initial story draft from EVAL-3 epic and architecture | Sarah (PO) |
| 2026-03-28 | 1.1 | Validation fixes: rdflib triple creation code example, category derived from ontology not LLM, quality score caveat documented, DEBUG logging for grounded prompt | Sarah (PO) |
| 2026-03-29 | 1.2 | Added `grounded` parameter to detect() and BiasDetectRequest for EVAL-3.5 A/B comparison. Added _build_ungrounded_prompt(). Added CRITICAL dev note on PascalCase→snake_case naming convention across all layers. | Sarah (PO) |
| 2026-04-01 | 1.3 | Implementation complete. 82 tests pass (20 new + 62 regression), 0 failures. | James (Dev) |
