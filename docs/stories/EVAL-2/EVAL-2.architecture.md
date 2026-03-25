# EVAL-2: Entity Extraction Evaluation Harness — Architecture

**Created:** 2026-03-19
**Author:** Winston (Architect)
**Status:** APPROVED
**Epic:** EVAL-2 — Entity Extraction Evaluation Harness

---

## Overview

This document defines the architecture for the EVAL-2 evaluation harness: a Promptfoo-based pipeline that measures entity extraction quality (precision/recall/F1) across two extractors — the existing spaCy NER pipeline and a new LLM-based extractor — against a curated gold dataset derived from EVAL-1's synthetic articles.

**Key architectural decisions:**
- **Dual-extractor comparison** — spaCy vs Claude, side-by-side in Promptfoo
- **Top-level `eval/` directory** — evaluation as a first-class cross-cutting concern
- **Gold dataset derivation** — automated FactSet → entity annotation, with human curation layer
- **Promptfoo orchestration** — Node.js CLI drives evaluation; all custom logic in Python
- **Fuzzy matching** — Levenshtein + type-aware scoring to handle entity boundary variations

---

## 1. What We're Evaluating

### The Existing Entity Extractor (Baseline)

The reasoning-service has a spaCy-based entity extraction pipeline:

```
POST /entities/extract  →  spaCy en_core_web_sm  →  SchemaMapper  →  ExtractedEntity[]
```

**Current entity types:**

| spaCy Label | Internal Type | Schema.org Type |
|---|---|---|
| PERSON | person | Person |
| ORG (gov keywords) | government_org | GovernmentOrganization |
| ORG (other) | organization | Organization |
| GPE, LOC | location | Place |
| EVENT | event | Event |
| NORP | concept | Thing |
| LAW | legislation | Legislation |

**Known limitations:**
- Fixed 0.85 confidence for all entities (spaCy `en_core_web_sm` doesn't provide confidence)
- Small model — misses nuanced government entities
- Government org classification depends on keyword matching, not semantic understanding

### The New LLM Extractor (Challenger)

A new endpoint using Claude to extract entities via structured prompts. This gives us:
- Real confidence scores (from the model's assessment)
- Better understanding of government-specific entities
- Structured output with entity type reasoning

**The evaluation question:** How much better (or worse) is LLM extraction than spaCy for government news articles, and at what cost?

---

## 2. Module Structure

```
NewsAnalyzer_V2/
├── eval/                                    # NEW — Evaluation harness (top-level)
│   ├── package.json                         # Promptfoo dependency
│   ├── promptfooconfig.yaml                 # Main evaluation config
│   ├── providers/
│   │   ├── spacy_provider.py                # Calls POST /entities/extract
│   │   └── llm_provider.py                  # Calls POST /eval/extract/llm
│   ├── assertions/
│   │   └── entity_scorer.py                 # Computes TP/FP/FN, emits namedScores
│   ├── datasets/
│   │   ├── gold/                            # Curated gold annotations (YAML)
│   │   │   ├── legislative.yaml             # Legislative branch articles
│   │   │   ├── executive.yaml               # Executive branch articles
│   │   │   ├── judicial.yaml                # Judicial branch articles
│   │   │   └── conll_sample.yaml            # CoNLL-2003 sanity check subset
│   │   └── scripts/
│   │       └── derive_gold.py               # FactSet → gold annotation derivation
│   └── reports/                             # Generated evaluation reports (gitignored)
│       └── .gitkeep
│
├── reasoning-service/app/
│   ├── api/eval/
│   │   └── extraction.py                    # NEW — POST /eval/extract/llm
│   └── services/eval/
│       └── llm_entity_extractor.py          # NEW — Claude-based entity extraction
```

### Why This Structure?

**Teaching moment:** Evaluation code lives *outside* the service being evaluated. This is a fundamental principle — your eval harness should be independent of the system under test. If the eval code lived inside reasoning-service, you couldn't test whether the service's own biases are leaking into your evaluation. The top-level `eval/` directory makes this separation visible.

Promptfoo is Node.js, but the custom providers and assertions are Python scripts that Promptfoo executes. The Python scripts call the reasoning-service over HTTP — same as any real client would.

---

## 3. Gold Dataset Design

### 3.1 Annotation Schema

Each gold annotation follows this format:

```yaml
# Gold dataset entry
- id: "eval-2-leg-001"
  article_id: "uuid-from-eval-1"           # FK to synthetic_articles table
  article_text: "Senator John Fetterman (D-PA) announced..."
  article_type: "news_report"
  branch: "legislative"
  source: "derived"                         # "derived" | "curated" | "conll"
  entities:
    - text: "John Fetterman"
      type: "person"
      start: 8                              # Character offset
      end: 23
      role: "subject"                       # Optional context
    - text: "Pennsylvania"
      type: "location"
      start: 29
      end: 41
    - text: "Senate Banking Committee"
      type: "government_org"
      start: 67
      end: 92
    - text: "Democratic"
      type: "concept"
      start: 25
      end: 27                               # "D" in "(D-PA)" — may not be extracted
      note: "abbreviated_form"
  metadata:
    perturbation_type: "none"               # From EVAL-1 ground truth
    difficulty: "medium"
    fact_count: 7                           # Facts in source FactSet
    curated: true                           # Has been human-reviewed
    curated_date: "2026-03-20"
```

### 3.2 Derivation Pipeline

**Step 1 — Automated derivation** (`eval/datasets/scripts/derive_gold.py`):

```
EVAL-1 synthetic_articles (via backend API)
    │
    ├── Article text
    └── source_facts (JSONB) → FactSet
            │
            ├── fact.subject → PERSON entity (legislative members, presidents, judges)
            ├── fact.object where predicate=STATE → LOCATION entity
            ├── fact.object where predicate=COMMITTEE_MEMBERSHIP → GOVERNMENT_ORG entity
            ├── fact.object where predicate=PARTY_AFFILIATION → CONCEPT entity
            ├── fact.object where predicate=COURT → GOVERNMENT_ORG entity
            └── fact.object where predicate=CABINET_POSITION → role context
```

The derivation script:
1. Fetches stored synthetic articles from `GET /api/eval/datasets/articles`
2. For each article, extracts the `source_facts` FactSet from ground truth
3. Maps FactSet predicates → expected entity types (see table below)
4. Locates entity mentions in the article text via string matching
5. Produces character offsets (start/end) for each entity span
6. Outputs YAML gold files organized by branch

**FactPredicate → Entity Type mapping:**

| FactPredicate | Gold Entity Type | Example Object |
|---|---|---|
| (fact.subject — always) | person | "John Fetterman" |
| STATE | location | "Pennsylvania" |
| DISTRICT | location | "PA-12" |
| COMMITTEE_MEMBERSHIP | government_org | "Banking Committee" |
| COURT | government_org | "Supreme Court" |
| PARTY_AFFILIATION | concept | "Democratic" |
| VICE_PRESIDENT | person | "Kamala Harris" |
| CABINET_POSITION | (context only) | "Secretary of Defense" |
| AGENCY_HEAD | (context only) | — |

**Step 2 — Human curation:**
- Review 50–100 derived annotations manually
- Fix span boundaries where string matching is imprecise
- Add entities that appear in the article but aren't in the FactSet (e.g., location datelines, quoted sources)
- Mark `curated: true` and `curated_date`
- This subset becomes the **primary evaluation benchmark**

**Step 3 — CoNLL-2003 sanity check:**
- Convert 20–30 sentences from CoNLL-2003 into the same YAML format
- Map CoNLL entity types (PER → person, ORG → organization, LOC → location)
- Purpose: validate that our scorer works on a known benchmark, establishing credibility

### 3.3 Dataset Size Targets

| Dataset | Articles | Estimated Entities | Purpose |
|---|---|---|---|
| Legislative (curated) | 20–30 | ~150 | Primary benchmark — richest entity types |
| Executive (curated) | 15–20 | ~100 | Secondary benchmark |
| Judicial (curated) | 10–15 | ~60 | Smallest branch, fewer entity types |
| CoNLL-2003 sample | 20–30 sentences | ~80 | External validation |
| Derived (uncurated) | 100+ | ~500+ | Extended testing, not primary metric |

**Total curated benchmark: ~50–65 articles, ~310+ annotated entities.**

This is deliberately small. For a portfolio piece, you want to be able to explain every annotation decision. A thoughtful 50-article dataset with clear methodology beats a noisy 10,000-article auto-generated one.

---

## 4. LLM Entity Extractor

### 4.1 Design

A new service module that uses Claude to extract entities with structured output:

```python
# reasoning-service/app/services/eval/llm_entity_extractor.py

class LLMEntityExtractor:
    """Extracts entities from text using Claude API with structured output."""

    ENTITY_TYPES = ["person", "government_org", "organization", "location",
                    "event", "concept", "legislation"]

    async def extract(self, text: str, model: str | None = None) -> list[dict]:
        """Extract entities from text using Claude.

        Returns list of:
            {"text": str, "type": str, "start": int, "end": int, "confidence": float}
        """
        prompt = self._build_prompt(text)

        if settings.eval_dry_run:
            return []

        response = await self._client.messages.create(
            model=model or settings.eval_default_model,
            max_tokens=2000,
            messages=[{"role": "user", "content": prompt}],
        )

        return self._parse_response(response.content[0].text, text)
```

### 4.2 Prompt Design

The prompt instructs Claude to:
1. Identify all named entities in the text
2. Classify each into one of the 7 entity types (matching spaCy's classification)
3. Provide confidence (0.0–1.0) for each entity
4. Return as JSON array

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

### 4.3 New Endpoint

```python
# reasoning-service/app/api/eval/extraction.py

@router.post("/extract/llm")
async def extract_entities_llm(request: ExtractionRequest) -> ExtractionResponse:
    """Extract entities using Claude LLM.

    Used by Promptfoo evaluation harness for model comparison.
    """
```

**Input/output format matches the existing `/entities/extract` response shape** — same `text`, `entity_type`, `start`, `end`, `confidence` fields. This is critical: Promptfoo's scorer needs a uniform format from both providers.

---

## 5. Promptfoo Configuration

### 5.1 Main Config

```yaml
# eval/promptfooconfig.yaml

description: "NewsAnalyzer Entity Extraction Evaluation"

providers:
  - id: "file://providers/spacy_provider.py"
    label: "spaCy en_core_web_sm"
  - id: "file://providers/llm_provider.py"
    label: "Claude Sonnet"
    config:
      model: "claude-sonnet-4-5-20250929"

tests: "datasets/gold/legislative.yaml"
# Can override: promptfoo eval -t datasets/gold/executive.yaml

defaultTest:
  assert:
    - type: python
      value: "file://assertions/entity_scorer.py"

derivedMetrics:
  - name: "Precision"
    value: "true_positives / (true_positives + false_positives)"
  - name: "Recall"
    value: "true_positives / (true_positives + false_negatives)"
  - name: "F1"
    value: "2 * true_positives / (2 * true_positives + false_positives + false_negatives)"

outputPath:
  - "reports/results.json"
  - "reports/report.html"
```

### 5.2 Custom Python Provider

```python
# eval/providers/spacy_provider.py

import requests

def call_api(prompt, options, context):
    """Call the spaCy entity extraction endpoint."""
    response = requests.post(
        "http://localhost:8000/entities/extract",
        json={"text": prompt, "confidence_threshold": 0.0},
        timeout=30,
    )
    response.raise_for_status()
    return {"output": response.json()}
```

```python
# eval/providers/llm_provider.py

import requests

def call_api(prompt, options, context):
    """Call the LLM entity extraction endpoint."""
    model = options.get("config", {}).get("model", "claude-sonnet-4-5-20250929")
    response = requests.post(
        "http://localhost:8000/eval/extract/llm",
        json={"text": prompt, "model": model},
        timeout=60,
    )
    response.raise_for_status()
    return {"output": response.json()}
```

### 5.3 Custom Assertion / Scorer

This is the core of the evaluation harness — where precision/recall/F1 are computed:

```python
# eval/assertions/entity_scorer.py

def get_assert(output, context):
    """Score entity extraction against gold annotations.

    Compares extracted entities to gold standard using fuzzy matching.
    Returns namedScores for Promptfoo's derivedMetrics.
    """
    extracted = output.get("entities", [])
    gold = context["vars"]["entities"]  # From gold dataset YAML

    tp, fp, fn = 0, 0, 0
    matched_gold = set()

    for ext in extracted:
        match = find_best_match(ext, gold, matched_gold)
        if match is not None:
            tp += 1
            matched_gold.add(match)
        else:
            fp += 1

    fn = len(gold) - len(matched_gold)

    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0

    return {
        "pass": f1 >= 0.5,  # Configurable threshold
        "score": f1,
        "reason": f"P={precision:.2f} R={recall:.2f} F1={f1:.2f} (TP={tp} FP={fp} FN={fn})",
        "namedScores": {
            "true_positives": tp,
            "false_positives": fp,
            "false_negatives": fn,
            "precision": precision,
            "recall": recall,
            "f1": f1,
        },
    }


def find_best_match(extracted, gold_entities, already_matched):
    """Find best matching gold entity using fuzzy matching.

    Match criteria:
    1. Type must match (exact)
    2. Text must be similar (Levenshtein ratio >= 0.8 OR substring containment)

    Returns index of best match, or None.
    """
    ...
```

### 5.4 Matching Strategy

**Why fuzzy matching?** Entity boundaries vary between extractors. spaCy might extract "Banking Committee" while the gold annotation says "Senate Banking Committee." A strict exact-match scorer would penalize correct extractions unfairly.

**Matching rules (in priority order):**

| Rule | Example | Threshold |
|---|---|---|
| Exact match (text + type) | "John Fetterman" = "John Fetterman" | 1.0 |
| Substring containment + type match | "Banking Committee" ⊂ "Senate Banking Committee" | 0.9 |
| Levenshtein similarity + type match | "Fetterman" ≈ "John Fetterman" | ≥ 0.8 |
| Type mismatch | "EPA" (organization) vs "EPA" (government_org) | Partial credit (0.5) |

**Per-type breakdown:** The scorer also emits per-type namedScores (`person_tp`, `person_fp`, `person_fn`, etc.) so you can see F1 by entity type in the dashboard. This is standard practice in NER evaluation.

---

## 6. Data Flow

```
                    ┌─────────────────────────────┐
                    │  promptfoo eval              │
                    │  (Node.js CLI)               │
                    └──────────┬──────────────────┘
                               │ Reads gold dataset YAML
                               │ Sends article_text to providers
                    ┌──────────▼──────────────────┐
          ┌─────────┤     For each test case:      ├─────────┐
          │         └──────────────────────────────┘         │
          │                                                   │
┌─────────▼─────────┐                             ┌──────────▼─────────┐
│  spacy_provider.py │                             │  llm_provider.py   │
│                    │                             │                    │
│ POST /entities/    │                             │ POST /eval/        │
│      extract       │                             │      extract/llm   │
└─────────┬─────────┘                             └──────────┬─────────┘
          │                                                   │
          │  entities[]                              entities[]│
          │                                                   │
          └─────────┬─────────────────────────────┬──────────┘
                    │                             │
                    ▼                             ▼
          ┌──────────────────────────────────────────┐
          │  entity_scorer.py                        │
          │                                          │
          │  Compare extracted vs. gold              │
          │  → TP / FP / FN per entity               │
          │  → Precision / Recall / F1               │
          │  → Per-type breakdown                    │
          │  → namedScores for derivedMetrics        │
          └──────────────────┬───────────────────────┘
                             │
                    ┌────────▼────────┐
                    │  Promptfoo UI   │
                    │                 │
                    │  Side-by-side   │
                    │  comparison     │
                    │  dashboard      │
                    │                 │
                    │  reports/       │
                    │  results.json   │
                    │  report.html    │
                    └─────────────────┘
```

---

## 7. New Dependencies

### eval/ (new directory)

```json
// eval/package.json
{
  "name": "newsanalyzer-eval",
  "private": true,
  "scripts": {
    "eval": "promptfoo eval",
    "eval:view": "promptfoo view",
    "eval:legislative": "promptfoo eval -t datasets/gold/legislative.yaml",
    "eval:executive": "promptfoo eval -t datasets/gold/executive.yaml",
    "eval:all": "promptfoo eval -t datasets/gold/"
  },
  "devDependencies": {
    "promptfoo": "^0.100.0"
  }
}
```

Python dependencies for providers/assertions: `requests` (standard library-adjacent, likely already available). No new reasoning-service dependencies needed.

### reasoning-service (additions)

No new pip dependencies. The LLM extractor uses the `anthropic` SDK already installed from EVAL-1.2.

---

## 8. Configuration & Environment

### Existing (reused from EVAL-1)

| Variable | Service | Purpose |
|----------|---------|---------|
| `ANTHROPIC_API_KEY` | reasoning-service | Claude API for LLM extractor |
| `EVAL_DEFAULT_MODEL` | reasoning-service | Default model for extraction |
| `EVAL_DRY_RUN` | reasoning-service | Skip API calls |

### New

| Variable | Service | Default | Purpose |
|----------|---------|---------|---------|
| `PROMPTFOO_PYTHON` | eval | (system python) | Path to reasoning-service venv Python for assertions |
| `EVAL_SCORER_SIMILARITY_THRESHOLD` | eval | `0.8` | Minimum Levenshtein ratio for fuzzy match |
| `EVAL_SCORER_TYPE_MISMATCH_CREDIT` | eval | `0.5` | Partial credit for correct text, wrong type |

---

## 9. Testing Strategy

| Layer | Framework | What's Tested |
|-------|-----------|---------------|
| **Gold derivation script** | pytest | FactSet → entity annotation mapping, offset calculation |
| **LLM entity extractor** | pytest + mock | Prompt construction, response parsing, dry-run mode |
| **LLM extraction endpoint** | pytest + TestClient | Request/response validation |
| **Entity scorer** | pytest | TP/FP/FN computation, fuzzy matching, edge cases |
| **Promptfoo integration** | Manual + CI | End-to-end eval run against gold dataset |

### Scorer Unit Tests (Critical)

The entity scorer is the heart of the harness. Tests must cover:

| Scenario | Expected |
|---|---|
| Perfect extraction (exact match all entities) | P=1.0, R=1.0, F1=1.0 |
| No entities extracted | P=0.0, R=0.0, F1=0.0 |
| All entities extracted + 3 false positives | P<1.0, R=1.0 |
| 2 of 5 gold entities extracted, no FPs | P=1.0, R=0.4 |
| Substring match ("Banking Committee" vs "Senate Banking Committee") | Counted as TP |
| Type mismatch ("EPA" as org vs government_org) | Partial credit |
| Duplicate extraction of same entity | Only one TP, rest are FPs |

---

## 10. Story Breakdown (Proposed)

| Story | Title | Scope | Depends On |
|-------|-------|-------|------------|
| **EVAL-2.1** | Gold Dataset Derivation & Curation | Derivation script, YAML schema, initial automated dataset, curation of 50–65 articles | EVAL-1 complete |
| **EVAL-2.2** | LLM Entity Extractor | `llm_entity_extractor.py`, extraction endpoint, prompt design, pytest | EVAL-1.2 (anthropic SDK) |
| **EVAL-2.3** | Promptfoo Harness & Entity Scorer | `eval/` directory setup, Promptfoo config, providers, scorer with fuzzy matching, pytest for scorer | EVAL-2.1, EVAL-2.2 |
| **EVAL-2.4** | Evaluation Execution & Methodology Write-up | Run evaluations, analyze results, write both `eval/README.md` and `docs/evaluation-methodology.md`, CI integration | EVAL-2.3 |

**Teaching note on story sequencing:** EVAL-2.1 and EVAL-2.2 are independent — they can be developed in parallel. EVAL-2.3 depends on both (it needs gold data to test against and both extractors to call). EVAL-2.4 is the capstone — it's where you actually *run* the evaluation and produce the portfolio artifact.

---

## 11. Portfolio Output

When complete, EVAL-2 produces these demonstrable artifacts:

1. **Side-by-side comparison dashboard** — Promptfoo web UI showing spaCy vs Claude extraction quality
2. **Precision/recall/F1 metrics** — Per-type and aggregate, computed by your custom scorer
3. **Gold dataset with methodology** — Shows you understand annotation pipelines
4. **Evaluation methodology write-up** — Both `eval/README.md` (getting-started + usage) and `docs/evaluation-methodology.md` (portfolio-facing deep dive) explaining:
   - Why you chose these metrics
   - How you built the gold dataset (automated derivation + human curation)
   - How fuzzy matching works and why exact match is insufficient for NER evaluation
   - Cost/quality tradeoff between spaCy (free) and Claude (paid)
   - How Promptfoo enables regression testing as prompts evolve
5. **CI integration** — Eval runs on prompt/model changes, catches quality regressions

This directly addresses 3 of 3 items from the job search advice:
- ✅ Entity extraction evaluation harness with precision/recall/F1
- ✅ Methodology write-up
- ✅ Promptfoo integration

---

## Appendix: Relation to EVAL-3

EVAL-3 (Cognitive Bias Ontology Evaluation) will build on this harness:
- Reuse Promptfoo infrastructure and scorer pattern
- Add bias-detection-specific metrics
- Add Ragas for RAG evaluation (ontology-grounded prompts)
- The evaluation methodology established here becomes the foundation

This is the "boring but correct" approach to building evaluation capability: prove you can measure something simple (entity extraction) before claiming you can measure something complex (cognitive bias detection).
