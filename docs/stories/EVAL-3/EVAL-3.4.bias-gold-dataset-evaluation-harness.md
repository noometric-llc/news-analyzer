# Story EVAL-3.4: Bias Gold Dataset + Evaluation Harness

## Status

Draft

## Story

**As an** AI evaluation engineer measuring bias detection quality,
**I want** a gold dataset of articles with known biases and a Promptfoo evaluation harness that scores detection quality with P/R/F1 metrics,
**so that** I can quantitatively evaluate the ontology-grounded bias detector and compare it against an ungrounded baseline.

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Synthetic biased articles generated with known bias injections |
| AC2 | Gold annotations follow defined YAML schema (article_text, biases[], metadata) |
| AC3 | Bias types in gold data are from the ontology (controlled vocabulary) |
| AC4 | `bias_scorer.py` computes P/R/F1 against gold annotations |
| AC5 | Partial credit (0.5) for correct category, wrong specific type |
| AC6 | Per-distortion-type breakdown in namedScores |
| AC7 | Promptfoo runs successfully: `npx promptfoo eval -c eval/promptfoo-bias.yaml` |
| AC8 | Results include both aggregate and per-type metrics |

## Tasks / Subtasks

**Task order: schema first (Task 1), then generation (Task 2) and curation (Task 3) which implement against the schema.**

- [ ] Task 1: Define gold annotation YAML schema (AC2, AC3)
  - [ ] Define schema for both synthetic and curated datasets:
    ```yaml
    - vars:
        article_text: "The senator's reckless policy will inevitably..."
        biases:
        - type: framing_effect         # Must match ontology individual name
          excerpt: "reckless policy"   # Exact text from article
          explanation: "Uses loaded language to frame policy negatively"
          academic_source: "Tversky & Kahneman, 1981"
        metadata:
          id: eval-3-bias-001
          source: synthetic | curated
          difficulty: easy | medium | hard
          bias_count: 1                # Number of biases in this article
          injected_types:              # For synthetic only — what was intentionally injected
          - framing_effect
    ```
  - [ ] Validate all `type` values against ontology's known distortion types
  - [ ] Ensure `metadata.id` is unique across both datasets

- [ ] Task 2: Create synthetic biased article generator (AC1, AC3)
  - [ ] Create `eval/datasets/bias/scripts/generate_biased_articles.py`
  - [ ] Script takes neutral EVAL-1 articles and injects specific biases via Claude:
    - Input: neutral article text + target bias type (from ontology)
    - Output: rewritten article with the specified bias injected
    - The injected bias IS the gold annotation — we know exactly what was put in
  - [ ] Prompt for bias injection:
    ```
    Rewrite the following news article to contain a clear example of {bias_type}.

    {bias_type}: {definition_from_ontology}
    Detection pattern: {pattern_from_ontology}

    Requirements:
    - Keep the article topic and key facts the same
    - Inject the bias naturally — it should be detectable but not cartoonish
    - Mark the specific excerpt where the bias appears with [BIAS_START] and [BIAS_END] tags
    - Maintain article length (~200-400 words)

    Original article:
    {article_text}
    ```
  - [ ] Parse Claude's response to extract the biased article and tagged excerpt
  - [ ] Generate multiple difficulty levels:
    - **Easy:** Heavy-handed bias, obvious language
    - **Medium:** Subtle but detectable with careful reading
    - **Hard:** Requires understanding the definition to identify
  - [ ] Source articles: use 10–15 existing EVAL-1 neutral synthetic articles from `eval/datasets/gold/`
  - [ ] Target: generate ~30–40 synthetic biased articles across 8–10 distortion types and 3 difficulty levels
  - [ ] CLI interface: `python generate_biased_articles.py --output eval/datasets/bias/synthetic_biased.yaml --count 3`
    - `--count N`: articles per distortion type (default 3). 3 per type × 10 types = 30 articles.
    - `--dry-run`: print prompts without calling Claude API (for testing)
    - `--types`: optional comma-separated list to limit which distortion types to generate
  - [ ] API key from env var `ANTHROPIC_API_KEY` (same as EVAL-2 scripts)
  - [ ] Rate limiting: sleep between API calls (`time.sleep(1.0)` or configurable). Generating 30–40 articles = 30–40 API calls — don't hit rate limits.
  - [ ] Reads ontology definitions via direct rdflib (not through API) to ground the injection prompts

- [ ] Task 3: Curate real-world biased excerpts (AC1, AC2, AC3)
  - [ ] Create `eval/datasets/bias/curated_biased.yaml`
  - [ ] Manually curate 15–20 real news excerpts with identifiable biases:
    - Source: publicly available news articles, opinion pieces, political commentary
    - Each excerpt: 1–3 paragraphs, clear enough to annotate
    - Annotation: bias type (from ontology), excerpt, explanation, academic source reference
  - [ ] Include a mix of:
    - Cognitive biases: framing, confirmation bias, anchoring
    - Logical fallacies: ad hominem, straw man, false dilemma
    - Multiple difficulty levels
  - [ ] Include 5–8 "faithful" articles (no bias) as negative examples — detector should return empty
  - [ ] Curation guidelines: if two people would disagree on whether a bias is present, mark as "hard" difficulty

- [ ] Task 4: Create bias evaluation scorer (AC4, AC5, AC6)
  - [ ] Create `eval/assertions/bias_scorer.py`
  - [ ] Follow `entity_scorer.py` pattern — `get_assert(output, context)` entry point
  - [ ] **Field mapping** (explicit — different from entity scorer):
    - Gold biases: `context["vars"]["biases"]` — list of `{type, excerpt, explanation}`
    - Detected annotations: `output.get("annotations", [])` — list of `{distortion_type, category, excerpt, explanation, confidence}`
    - Match key: `detected["distortion_type"]` vs `gold["type"]` (case-insensitive)
    - This matches the `BiasDetectionOutput` shape from EVAL-3.3's `/eval/bias/detect` endpoint
  - [ ] Scoring logic:
    - **Exact match** on `distortion_type` (case-insensitive) → **1.0 TP**
    - **Category match** (e.g., gold says "ad_hominem", detected says "straw_man" — both are informal_fallacy) → **0.5 TP partial credit**
    - **No match** → FP (detected but not in gold) or FN (in gold but not detected)
  - [ ] Category resolution: build a lookup map from distortion_type → category:
    ```python
    DISTORTION_CATEGORIES = {
        "confirmation_bias": "cognitive_bias",
        "anchoring_bias": "cognitive_bias",
        "framing_effect": "cognitive_bias",
        "availability_heuristic": "cognitive_bias",
        "bandwagon_effect": "cognitive_bias",
        "ad_hominem": "informal_fallacy",
        "straw_man": "informal_fallacy",
        "false_dilemma": "informal_fallacy",
        "slippery_slope": "informal_fallacy",
        "appeal_to_authority": "informal_fallacy",
        "red_herring": "informal_fallacy",
        "circular_reasoning": "informal_fallacy",
        "affirming_the_consequent": "formal_fallacy",
        "denying_the_antecedent": "formal_fallacy",
    }
    ```
  - [ ] Compute P/R/F1:
    ```python
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0
    ```
  - [ ] Per-distortion-type breakdown in `namedScores`:
    - `{type}_tp`, `{type}_fp`, `{type}_fn` for each of the 13 distortion types
    - Same pattern as `entity_scorer.py`'s per-entity-type breakdown
  - [ ] Pass threshold: F1 ≥ 0.3 (lower than entity extraction's 0.5 — bias detection is harder)
  - [ ] Return Promptfoo `GradingResult`:
    ```python
    return {
        "pass": f1 >= PASS_THRESHOLD,
        "score": f1,
        "reason": f"P={precision:.2f} R={recall:.2f} F1={f1:.2f} (TP={tp} FP={fp} FN={fn})",
        "namedScores": {
            "true_positives": tp,
            "false_positives": fp,
            "false_negatives": fn,
            "Precision": precision,
            "Recall": recall,
            "F1": f1,
            "{type}_tp": ...,  # per-type
            "{type}_fp": ...,
            "{type}_fn": ...,
        }
    }
    ```

- [ ] Task 5: Create bias Promptfoo provider (AC7)
  - [ ] Create `eval/providers/bias_provider.py`
  - [ ] Follow `llm_provider.py` pattern — `call_api(prompt, options, context)` entry point
  - [ ] Calls `POST http://localhost:8000/eval/bias/detect`:
    ```python
    def call_api(prompt, options, context):
        body = {"text": prompt, "confidence_threshold": 0.0, "include_ontology_metadata": False}
        response = requests.post("http://localhost:8000/eval/bias/detect", json=body, timeout=120)
        return {"output": response.json()}
    ```
  - [ ] Longer timeout (120s vs 60s) — bias detection with 13 definitions in prompt is slower

- [ ] Task 6: Create Promptfoo bias evaluation config (AC7, AC8)
  - [ ] Create `eval/promptfoo-bias.yaml`:
    ```yaml
    description: "EVAL-3: Cognitive Bias & Logical Fallacy Detection Evaluation"

    providers:
      - id: "file://providers/bias_provider.py"
        label: "Ontology-Grounded Bias Detector"

    prompts:
      - "{{article_text}}"

    defaultTest:
      assert:
        - type: python
          value: "file://assertions/bias_scorer.py"

    tests: "datasets/bias/synthetic_biased.yaml"

    derivedMetrics:
      - name: "Precision"
        value: "namedScores.Precision"
      - name: "Recall"
        value: "namedScores.Recall"
      - name: "F1"
        value: "namedScores.F1"

    outputPath: "reports/bias/"
    ```
  - [ ] Verify: `npx promptfoo eval -c eval/promptfoo-bias.yaml` runs without config errors (can use --dry-run initially)

- [ ] Task 7: Write scorer tests (AC4, AC5, AC6)
  - [ ] Create `eval/assertions/test_bias_scorer.py`
  - [ ] Test: perfect detection — all gold biases detected with exact type match → P=1.0, R=1.0, F1=1.0
  - [ ] Test: no detection — detector returns empty → P=0, R=0, F1=0
  - [ ] Test: false positives only — detects biases not in gold → P=0, R=0
  - [ ] Test: category partial credit — gold says "ad_hominem", detected "straw_man" (both informal_fallacy) → 0.5 TP
  - [ ] Test: mixed results — some correct, some FP, some FN → verify P/R/F1 math
  - [ ] Test: per-distortion-type namedScores has correct keys and values
  - [ ] Test: faithful article (no biases in gold) with no detections → P=1.0 (or 0/0 handled)
  - [ ] Test: faithful article with false detections → FPs counted
  - [ ] Test: multiple biases in same article scored independently
  - [ ] Test: pass threshold at F1 ≥ 0.3

- [ ] Task 8: Validate gold dataset integrity (AC2, AC3)
  - [ ] Write validation script or pytest tests:
    - All gold YAML files parse without errors
    - Every bias `type` is a valid distortion from the ontology
    - Every article has unique `metadata.id`
    - Synthetic articles have `metadata.injected_types` matching their `biases[].type`
    - Faithful articles have empty `biases[]` arrays
    - At least 3 difficulty levels represented
    - At least 5 different distortion types represented

## Dev Notes

### Relevant Source Tree

```
eval/
├── promptfooconfig.yaml                     # EXISTING — EVAL-2 entity config (no changes)
├── promptfoo-bias.yaml                      # NEW — bias evaluation config
├── providers/
│   ├── spacy_provider.py                    # EXISTING — no changes
│   ├── llm_provider.py                      # EXISTING — no changes
│   └── bias_provider.py                     # NEW
├── assertions/
│   ├── entity_scorer.py                     # EXISTING — no changes (REFERENCE PATTERN)
│   ├── test_entity_scorer.py                # EXISTING — no changes
│   ├── bias_scorer.py                       # NEW
│   └── test_bias_scorer.py                  # NEW
└── datasets/
    ├── gold/                                # EXISTING — entity extraction gold (no changes)
    └── bias/                                # NEW
        ├── synthetic_biased.yaml            # NEW — generated biased articles
        ├── curated_biased.yaml              # NEW — human-curated excerpts
        └── scripts/
            └── generate_biased_articles.py  # NEW — bias injection script
```

### Entity Scorer Pattern (Reference)

The bias scorer follows `entity_scorer.py` (266 lines) exactly:

| Aspect | entity_scorer.py | bias_scorer.py |
|---|---|---|
| Entry point | `get_assert(output, context)` | Same |
| Gold source | `context["vars"]["entities"]` | `context["vars"]["biases"]` |
| Detected source | `output["entities"]` | `output["annotations"]` |
| Match key | `text` (case-insensitive) + `entity_type` | `distortion_type` (exact) |
| Partial credit | Type mismatch → 0.5 | Category match → 0.5 |
| Per-type breakdown | `person_tp`, `government_org_fp`, etc. | `framing_effect_tp`, `ad_hominem_fp`, etc. |
| Pass threshold | F1 ≥ 0.5 | F1 ≥ 0.3 (bias is harder) |
| namedScores | TP, FP, FN, Precision, Recall, F1 + per-type | Same structure |

### Gold Dataset Design — Two Tiers

**Tier 1: Synthetic (automated, objective ground truth)**
- Take neutral EVAL-1 articles, inject specific biases via Claude
- Gold annotation = the bias that was intentionally injected
- Advantage: we KNOW the bias is there because we put it there
- Limitation: injected biases may not be as natural as real-world biases

**Tier 2: Curated (human-annotated, ecologically valid)**
- Real news excerpts with identifiable biases
- Human-annotated with bias type and explanation
- Advantage: real-world validity
- Limitation: subjective — annotators may disagree

**Why two tiers:** Synthetic data gives us objective quantitative metrics (the P/R/F1 numbers for the methodology writeup). Curated data gives us ecological validity (the system works on real text). Both are needed for a credible evaluation.

### Bias Injection Strategy

The generator uses ontology definitions to ground the injection prompt — the same neuro-symbolic pattern as detection, but in reverse:

```
Detection:  SPARQL → definitions → LLM "find biases matching these definitions"
Injection:  SPARQL → definitions → LLM "rewrite article to exhibit this definition"
```

The `[BIAS_START]` and `[BIAS_END]` tags in the injection prompt let us extract the exact excerpt without human review — automating the gold annotation.

**Difficulty levels:**
- **Easy:** Tell Claude "make the bias obvious and heavy-handed"
- **Medium:** Tell Claude "make the bias present but require careful reading to identify"
- **Hard:** Tell Claude "make the bias subtle — a reader would need to understand the formal definition to identify it"

### Source Articles for Injection

Use existing neutral articles from `eval/datasets/gold/*.yaml`:
- Parse the YAML files, extract `article_text` from entries where `metadata.perturbation_type` is "none" (faithful articles)
- These are already proven to be well-formed articles about government topics
- Select 10–15 diverse articles (mix of legislative, executive, judicial)

### Scorer — Category Partial Credit Logic

```python
def _get_category(distortion_type: str) -> str:
    """Map distortion type to its category for partial credit."""
    return DISTORTION_CATEGORIES.get(distortion_type, "unknown")

def _find_best_match(detected, gold_biases, already_matched):
    """Find best matching gold bias for a detected annotation."""
    # Priority 1: exact type match
    for i, gold in enumerate(gold_biases):
        if i in already_matched:
            continue
        if detected["distortion_type"].lower() == gold["type"].lower():
            return i, 1.0  # Full credit

    # Priority 2: same category match (partial credit)
    detected_cat = _get_category(detected["distortion_type"])
    for i, gold in enumerate(gold_biases):
        if i in already_matched:
            continue
        gold_cat = _get_category(gold["type"])
        if detected_cat == gold_cat and detected_cat != "unknown":
            return i, 0.5  # Partial credit

    return None, 0.0  # No match — false positive
```

### Promptfoo Provider — Output Shape

The bias provider receives the output from `/eval/bias/detect` and passes it to the scorer. The scorer expects:

```python
# output (from provider)
{
    "annotations": [
        {"distortion_type": "framing_effect", "category": "cognitive_bias", "excerpt": "...", "explanation": "...", "confidence": 0.82},
        ...
    ],
    "total_count": 2,
    "distortions_checked": ["confirmation_bias", "framing_effect", ...],
    "shacl_valid": true
}

# context["vars"] (from gold YAML)
{
    "article_text": "...",
    "biases": [
        {"type": "framing_effect", "excerpt": "...", "explanation": "...", "academic_source": "..."},
        ...
    ],
    "metadata": {...}
}
```

### Testing

**Test file:** `eval/assertions/test_bias_scorer.py`

**Testing framework:** pytest (same as `test_entity_scorer.py`)

**What to test:**
- Perfect detection (all gold biases found, exact match)
- No detection (empty output)
- False positives only
- Category partial credit (same category, different type)
- Mixed results with correct P/R/F1 math
- Per-distortion-type namedScores keys and values
- Faithful article handling (no gold biases)
- Multiple biases in one article
- Pass threshold (F1 ≥ 0.3)
- Gold dataset integrity (YAML parsing, type validation, uniqueness)

**Mock data for scorer tests:**
```python
MOCK_GOLD_BIASES = [
    {"type": "framing_effect", "excerpt": "reckless policy", "explanation": "..."},
    {"type": "ad_hominem", "excerpt": "incompetent senator", "explanation": "..."},
]

MOCK_DETECTED_PERFECT = {
    "annotations": [
        {"distortion_type": "framing_effect", "category": "cognitive_bias", "excerpt": "...", "confidence": 0.82},
        {"distortion_type": "ad_hominem", "category": "logical_fallacy", "excerpt": "...", "confidence": 0.75},
    ],
    "total_count": 2,
}
```

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-28 | 1.0 | Initial story draft from EVAL-3 epic and architecture | Sarah (PO) |
| 2026-03-28 | 1.1 | Validation fixes: reordered tasks (schema first), added generator rate limiting + dry-run + --count flag, explicit scorer field mapping for BiasDetectionOutput | Sarah (PO) |
