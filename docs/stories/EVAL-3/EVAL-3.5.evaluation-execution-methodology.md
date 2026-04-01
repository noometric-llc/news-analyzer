# Story EVAL-3.5: Evaluation Execution + Methodology Writeup

## Status

In Progress — Infrastructure Complete, Execution Pending

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
| AC6 | Grounded vs ungrounded comparison completed — quantitative A/B results documented |

## Tasks / Subtasks

- [ ] Task 1: Run baseline bias evaluation (AC1, AC2, AC3) — REQUIRES SERVICE + API KEY
  - [ ] Ensure reasoning service is running with bias ontology loaded (`GET /eval/bias/ontology/stats` returns 14 distortions)
  - [ ] Run Promptfoo evaluation: `cd eval && npx promptfoo eval -c promptfoo-bias.yaml`
  - [ ] Review console output for errors or unexpected behavior
  - [ ] If individual articles fail (timeout, API error), re-run with `--repeat` or fix and re-run
  - [ ] Verify results include:
    - Aggregate P/R/F1 across all articles
    - Per-distortion-type TP/FP/FN breakdown
    - Per-article pass/fail based on F1 threshold
  - [ ] Generate HTML report: `npx promptfoo eval -c promptfoo-bias.yaml --output reports/bias/baseline_results.html`
  - [ ] Generate JSON report: results auto-saved to `reports/bias/` by Promptfoo outputPath config

- [ ] Task 2: Analyze results (AC2, AC3) — REQUIRES EVAL RUN OUTPUT
  - [x] Create `eval/reports/bias/scripts/summarize_results.py` — DONE (infrastructure)
  - [ ] Run summarize script against Promptfoo output to create `eval/reports/bias/summary.json`:
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
  - [x] summarize_results.py written — extracts per-test-case namedScores and computes breakdowns:
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

- [ ] Task 3: Grounded vs ungrounded comparison (AC6 — REQUIRED)
  - [x] Add a `grounded: bool = True` parameter to `POST /eval/bias/detect` — DONE in EVAL-3.3:
    - When `grounded=True` (default): existing behavior — SPARQL retrieves definitions, prompt includes them
    - When `grounded=False`: skip SPARQL step, use a generic prompt ("find cognitive biases and logical fallacies in this text") with NO ontology definitions
    - Same Claude model, same response parsing, same SHACL validation — only the prompt changes
  - [x] Create second Promptfoo provider `eval/providers/bias_provider_ungrounded.py`:
    - Calls same endpoint with `{"grounded": false}` in request body
  - [x] Create second Promptfoo config `eval/promptfoo-bias-ungrounded.yaml`:
    - Same gold dataset, same scorer, different provider
  - [ ] Run both evaluations against the same gold dataset
  - [ ] Compare results — document in methodology:
    - Does ontology grounding improve P/R/F1? On which distortion types?
    - Does grounding reduce false positives (higher precision)?
    - Does grounding improve recall on subtle biases (hard difficulty)?
    - Even if the quantitative difference is small, the auditability argument stands — document this framing

- [x] Task 4: Write bias detection methodology document (AC5)
  - [x] Create `docs/evaluation-methodology-bias.md`
  - [ ] Sections:
    1. **Introduction** — What we evaluated (bias/fallacy detection), why (responsible AI, auditability), and the key innovation (neuro-symbolic grounding)
    2. **Ontology Design** — Class hierarchy, 13 distortions, academic sources, design decisions (OWL+SPARQL+SHACL, not Prolog)
    3. **SHACL Validation** — What shapes enforce, conformance results, role in data governance
    4. **Detection Approach** — The neuro-symbolic pipeline: SPARQL → grounded prompt → LLM → SHACL validate
    5. **Gold Dataset Construction** — Two-tier approach (synthetic + curated), bias injection strategy, annotation schema
    6. **Evaluation Metrics** — P/R/F1 for bias detection, partial credit for category match, pass threshold
    7. **Results** — Aggregate P/R/F1, per-type breakdown, per-difficulty analysis, key findings
    8. **Grounded vs Ungrounded** — Comparison results with quantitative A/B analysis
    9. **Limitations** — Subjectivity of bias annotation, synthetic data limitations, single-model evaluation, ontology scope (13 of hundreds)
    10. **Future Work** — Expand ontology, add more distortion types, inter-annotator agreement, RAG evaluation integration
    11. **Tools Used** — Promptfoo, RDFLib, OWL-RL, pyshacl, Claude API, SPARQL
  - [x] Tone: same as `docs/evaluation-methodology.md` — professional, honest about limitations, portfolio-ready
  - [ ] Include actual numbers from the evaluation run (replace [PLACEHOLDER] markers)

- [ ] Task 5: Commit baseline results (AC4) — AFTER EVAL RUN
  - [ ] Commit to `eval/reports/bias/`:
    - `summary.json` — aggregate + per-type metrics
    - `baseline_results.json` — full Promptfoo output (raw)
    - `baseline_results.html` — Promptfoo HTML report
  - [x] Add selective gitignore pattern for `eval/reports/bias/` — DONE:
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

- [x] Task 6: Plan EVAL-DASH integration (stretch)
  - [x] If time permits: add bias detection results to the `/evaluation/methodology` page
    - New section in the methodology page (EVAL-DASH.4 pattern)
    - Or plan as a follow-up micro-story
  - [x] At minimum: document in the methodology writeup that the results are available and how to access them (Future Work section references EVAL-DASH integration)

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

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (1M context)

### File List

| File | Action | Description |
|------|--------|-------------|
| `eval/providers/bias_provider_ungrounded.py` | NEW | Promptfoo provider calling /eval/bias/detect with grounded=false |
| `eval/promptfoo-bias-ungrounded.yaml` | NEW | Promptfoo config for ungrounded A/B baseline |
| `eval/reports/bias/scripts/summarize_results.py` | NEW | Summarizes Promptfoo output → summary.json with per-type/difficulty/source breakdowns + A/B comparison mode |
| `docs/evaluation-methodology-bias.md` | NEW | 11-section methodology document with [PLACEHOLDER] markers for actual numbers |
| `eval/EVAL-3-RUNBOOK.md` | NEW | Step-by-step execution guide for running the evaluation |
| `.gitignore` | MODIFIED | Added selective tracking for eval/reports/bias/ and bias-ungrounded/ |

### Completion Notes

- **Infrastructure complete** — all scripts, configs, providers, and methodology doc are built
- **Execution pending** — Tasks 1, 2 (partial), 3 (partial), and 5 require running the reasoning service with ANTHROPIC_API_KEY and executing Promptfoo evaluations
- Methodology doc has `[PLACEHOLDER]` markers in sections 7 and 8 — replace with real numbers after running
- `grounded` parameter already implemented in EVAL-3.3 — no code changes needed for the A/B comparison
- EVAL-3-RUNBOOK.md provides step-by-step instructions for the execution phase
- EVAL-DASH integration documented as future work in methodology doc section 10

### Debug Log References

None — no issues during infrastructure build.

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-28 | 1.0 | Initial story draft from EVAL-3 epic and architecture | Sarah (PO) |
| 2026-03-28 | 1.1 | Validation fixes: exact gitignore pattern for selective tracking, summarize script is required (not optional) with specific responsibilities | Sarah (PO) |
| 2026-03-29 | 1.2 | Promoted grounded vs ungrounded comparison from stretch to REQUIRED (AC6). Chose Option B (grounded parameter) over separate endpoint. Added second provider + config. Effort increases ~0.5 days. | Sarah (PO) |
| 2026-04-01 | 1.3 | Infrastructure complete. Ungrounded provider/config, summarize script, methodology doc (with placeholders), gitignore, runbook. Execution tasks pending service + API key. | James (Dev) |
