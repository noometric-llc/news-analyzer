# Story EVAL-3.5: Evaluation Execution + Methodology Writeup

## Status

Draft

## Story

**As an** AI evaluation engineer presenting bias detection work in a portfolio,
**I want** to run the baseline bias evaluation, analyze the results, and document the methodology in a polished writeup,
**so that** I have quantitative results and a shareable methodology page demonstrating both AI evaluation and knowledge engineering skills.

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Baseline evaluation completed against full gold dataset (synthetic + curated) |
| AC2 | Aggregate P/R/F1 reported for bias detection |
| AC3 | Per-distortion-type breakdown available (which biases are detected well vs poorly) |
| AC4 | Baseline results committed to `eval/reports/bias/` |
| AC5 | Methodology document written covering ontology design, grounding approach, evaluation metrics |
| AC6 | Grounded vs ungrounded comparison attempted (if time permits — stretch) |

## Tasks / Subtasks

- [ ] Task 1: Run baseline bias evaluation (AC1, AC2, AC3)
  - [ ] Ensure reasoning service is running with bias ontology loaded (`GET /eval/bias/ontology/stats` returns 13 distortions)
  - [ ] Run Promptfoo evaluation: `cd eval && npx promptfoo eval -c promptfoo-bias.yaml`
  - [ ] Review console output for errors or unexpected behavior
  - [ ] If individual articles fail (timeout, API error), re-run with `--repeat` or fix and re-run
  - [ ] Verify results include:
    - Aggregate P/R/F1 across all articles
    - Per-distortion-type TP/FP/FN breakdown
    - Per-article pass/fail based on F1 threshold
  - [ ] Generate HTML report: `npx promptfoo eval -c promptfoo-bias.yaml --output reports/bias/baseline_results.html`
  - [ ] Generate JSON report: results auto-saved to `reports/bias/` by Promptfoo outputPath config

- [ ] Task 2: Analyze results (AC2, AC3)
  - [ ] Create `eval/reports/bias/summary.json` — aggregate metrics:
    ```json
    {
      "generated": "2026-03-XX",
      "aggregate": {
        "precision": 0.XX,
        "recall": 0.XX,
        "f1": 0.XX,
        "total_articles": N,
        "total_biases_in_gold": N,
        "total_detected": N,
        "true_positives": N,
        "false_positives": N,
        "false_negatives": N
      },
      "by_distortion_type": {
        "framing_effect": { "tp": N, "fp": N, "fn": N, "precision": 0.XX, "recall": 0.XX, "f1": 0.XX },
        "ad_hominem": { ... },
        ...
      },
      "by_difficulty": {
        "easy": { "precision": 0.XX, "recall": 0.XX, "f1": 0.XX, "article_count": N },
        "medium": { ... },
        "hard": { ... }
      },
      "by_source": {
        "synthetic": { "precision": 0.XX, "recall": 0.XX, "f1": 0.XX, "article_count": N },
        "curated": { ... }
      }
    }
    ```
  - [ ] Extract summary from Promptfoo's raw JSON output. Promptfoo's `derivedMetrics` provides aggregate P/R/F1 but NOT per-difficulty or per-source breakdowns — those require parsing the per-test-case `namedScores`. Write `eval/reports/bias/scripts/summarize_results.py` to:
    - Read the raw Promptfoo JSON output
    - Compute per-distortion-type P/R/F1 from individual test case namedScores
    - Compute per-difficulty and per-source breakdowns by grouping test cases on `metadata.difficulty` and `metadata.source`
    - Output `summary.json` with the structure defined below
  - [ ] Identify patterns:
    - Which distortion types are detected well (high F1)?
    - Which are missed (low recall)?
    - Which generate false positives (low precision)?
    - Does difficulty level correlate with detection quality?
    - Does synthetic vs curated affect results?

- [ ] Task 3: Grounded vs ungrounded comparison (AC6 — stretch)
  - [ ] Create a second Promptfoo provider that calls a "naive" bias detector:
    - Option A: Create a separate `/eval/bias/detect-ungrounded` endpoint that uses the same Claude call but WITHOUT ontology definitions in the prompt (just "find biases in this text")
    - Option B: Add a `grounded: false` parameter to the existing detect endpoint that skips the SPARQL step
  - [ ] Run Promptfoo with both providers against the same gold dataset
  - [ ] Compare: does ontology grounding improve P/R/F1? On which distortion types?
  - [ ] This is the headline finding for the methodology writeup — even if the difference is small, the auditability argument stands
  - [ ] If time doesn't permit, note this as planned future work in the methodology doc

- [ ] Task 4: Write bias detection methodology document (AC5)
  - [ ] Create `docs/evaluation-methodology-bias.md`
  - [ ] Sections:
    1. **Introduction** — What we evaluated (bias/fallacy detection), why (responsible AI, auditability), and the key innovation (neuro-symbolic grounding)
    2. **Ontology Design** — Class hierarchy, 13 distortions, academic sources, design decisions (OWL+SPARQL+SHACL, not Prolog)
    3. **SHACL Validation** — What shapes enforce, conformance results, role in data governance
    4. **Detection Approach** — The neuro-symbolic pipeline: SPARQL → grounded prompt → LLM → SHACL validate
    5. **Gold Dataset Construction** — Two-tier approach (synthetic + curated), bias injection strategy, annotation schema
    6. **Evaluation Metrics** — P/R/F1 for bias detection, partial credit for category match, pass threshold
    7. **Results** — Aggregate P/R/F1, per-type breakdown, per-difficulty analysis, key findings
    8. **Grounded vs Ungrounded** — Comparison results (or planned future work)
    9. **Limitations** — Subjectivity of bias annotation, synthetic data limitations, single-model evaluation, ontology scope (13 of hundreds)
    10. **Future Work** — Expand ontology, add more distortion types, inter-annotator agreement, RAG evaluation integration
    11. **Tools Used** — Promptfoo, RDFLib, OWL-RL, pyshacl, Claude API, SPARQL
  - [ ] Tone: same as `docs/evaluation-methodology.md` — professional, honest about limitations, portfolio-ready
  - [ ] Include actual numbers from the evaluation run (not placeholders)

- [ ] Task 5: Commit baseline results (AC4)
  - [ ] Commit to `eval/reports/bias/`:
    - `summary.json` — aggregate + per-type metrics
    - `baseline_results.json` — full Promptfoo output (raw)
    - `baseline_results.html` — Promptfoo HTML report
  - [ ] Add selective gitignore pattern for `eval/reports/bias/`:
    ```gitignore
    # Ignore generated bias reports except committed baselines
    eval/reports/bias/*
    !eval/reports/bias/summary.json
    !eval/reports/bias/baseline_results.json
    !eval/reports/bias/baseline_results.html
    ```
  - [ ] Update `docs/ROADMAP.md`:
    - EVAL-3 status → Complete
    - Add EVAL-3 completion note with key metrics

- [ ] Task 6: Plan EVAL-DASH integration (stretch)
  - [ ] If time permits: add bias detection results to the `/evaluation/methodology` page
    - New section in the methodology page (EVAL-DASH.4 pattern)
    - Or plan as a follow-up micro-story
  - [ ] At minimum: document in the methodology writeup that the results are available and how to access them

## Dev Notes

### Relevant Source Tree

```
eval/
├── promptfoo-bias.yaml                      # EXISTS (from EVAL-3.4) — run this
├── reports/
│   ├── baseline/                            # EXISTING — EVAL-2 entity results
│   └── bias/                                # NEW — bias evaluation results
│       ├── summary.json                     # NEW — aggregate metrics
│       ├── baseline_results.json            # NEW — full Promptfoo output
│       ├── baseline_results.html            # NEW — HTML report
│       └── scripts/
│           └── summarize_results.py         # NEW — optional extraction script

docs/
├── evaluation-methodology.md                # EXISTING — EVAL-2 methodology (no changes)
├── evaluation-methodology-bias.md           # NEW — EVAL-3 methodology
└── ROADMAP.md                               # MODIFY — update EVAL-3 status
```

### Running the Evaluation

```bash
# Ensure reasoning service is running
curl http://localhost:8000/eval/bias/ontology/stats
# Should return: {"total_distortions": 13, "shacl_valid": true, ...}

# Run the evaluation
cd eval
npx promptfoo eval -c promptfoo-bias.yaml

# Generate reports
npx promptfoo eval -c promptfoo-bias.yaml --output reports/bias/baseline_results.html
```

**Expected runtime:** ~30–60 minutes for 40–50 articles. Each article takes ~10–20 seconds (Claude API call with 13 definitions in prompt). Plan accordingly.

**Cost estimate:** 40–50 articles × ~$0.01 per article = ~$0.40–$0.50 for the full evaluation run.

### Summary.json Structure

Follow the EVAL-2 pattern from `eval/reports/baseline/summary.json` but adapted for bias detection:
- Top-level `aggregate` instead of `branches` (bias isn't branch-specific)
- `by_distortion_type` parallels the per-branch breakdown
- New dimensions: `by_difficulty` and `by_source` (synthetic vs curated)

### Methodology Document Pattern

Follow `docs/evaluation-methodology.md` (218 lines, written for EVAL-2). Same tone, structure, and level of detail. The bias methodology document should:
- Be self-contained (readable without the entity extraction methodology)
- Reference the ontology file and SHACL shapes file
- Include actual numbers, not placeholders
- Acknowledge limitations honestly
- Highlight the neuro-symbolic innovation as the key differentiator

### What "Good" Results Look Like

**Realistic expectations for bias detection P/R/F1:**

| Metric | Optimistic | Realistic | Pessimistic |
|---|---|---|---|
| Precision | 0.60+ | 0.40–0.60 | 0.20–0.40 |
| Recall | 0.70+ | 0.50–0.70 | 0.30–0.50 |
| F1 | 0.60+ | 0.45–0.60 | 0.25–0.45 |

Bias detection is inherently harder than entity extraction (EVAL-2 Claude F1 was ~0.60). Lower numbers are expected and should be framed honestly:
- "Bias detection F1 of 0.45 demonstrates the challenge of this task"
- "Ontology grounding provides auditability regardless of raw accuracy"
- "Per-type analysis shows the detector excels at [X] but struggles with [Y]"

Even mediocre quantitative results are a win because the portfolio value is the **approach** (neuro-symbolic grounding, SHACL validation, formal ontology), not just the numbers.

### Testing

This story is primarily execution and analysis — fewer automated tests than previous stories.

**What to test:**
- `summary.json` parses as valid JSON with expected structure
- All distortion types from ontology appear in `by_distortion_type`
- P/R/F1 values are in valid range [0, 1]
- Article count in summary matches gold dataset article count
- Methodology document exists and is non-empty

**Manual verification:**
- Review Promptfoo HTML report for obvious anomalies
- Spot-check 5–10 individual article results for sensible detections
- Verify per-difficulty breakdown shows expected pattern (easy > medium > hard)

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-28 | 1.0 | Initial story draft from EVAL-3 epic and architecture | Sarah (PO) |
| 2026-03-28 | 1.1 | Validation fixes: exact gitignore pattern for selective tracking, summarize script is required (not optional) with specific responsibilities | Sarah (PO) |
