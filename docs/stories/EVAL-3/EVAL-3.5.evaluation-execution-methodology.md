# Story EVAL-3.5: Evaluation Execution + Methodology Writeup

## Status

Ready for Review

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

- [x] Task 1: Run baseline bias evaluation (AC1, AC2, AC3) — REQUIRES SERVICE + API KEY
  - [x] Ensure reasoning service is running with bias ontology loaded (`GET /eval/bias/ontology/stats` returns 14 distortions)
  - [x] Run Promptfoo evaluation: `cd eval && npx promptfoo eval -c promptfoo-bias.yaml`
  - [x] Review console output for errors or unexpected behavior
  - [x] If individual articles fail (timeout, API error), re-run with `--repeat` or fix and re-run
  - [x] Verify results include:
    - Aggregate P/R/F1 across all articles
    - Per-distortion-type TP/FP/FN breakdown
    - Per-article pass/fail based on F1 threshold
  - [x] Generate HTML report: `npx promptfoo eval -c promptfoo-bias.yaml --output reports/bias/baseline_results.html`
  - [x] Generate JSON report: results auto-saved to `reports/bias/` by Promptfoo outputPath config

- [x] Task 2: Analyze results (AC2, AC3) — REQUIRES EVAL RUN OUTPUT
  - [x] Create `eval/reports/bias/scripts/summarize_results.py` — DONE (infrastructure)
  - [x] Run summarize script against Promptfoo output to create `eval/reports/bias/summary.json`
  - [x] summarize_results.py written — extracts per-test-case namedScores and computes breakdowns
  - [x] Identify patterns:
    - 7/14 distortion types detected perfectly (F1=1.0)
    - framing_effect weakest (F1=0.36) — high FP rate
    - Cognitive biases harder than logical fallacies
    - Hard articles scored highest F1 (0.89) — fewer FPs on subtle text

- [x] Task 3: Grounded vs ungrounded comparison (AC6 — REQUIRED)
  - [x] Add a `grounded: bool = True` parameter to `POST /eval/bias/detect` — DONE in EVAL-3.3
  - [x] Create second Promptfoo provider `eval/providers/bias_provider_ungrounded.py`
  - [x] Create second Promptfoo config `eval/promptfoo-bias-ungrounded.yaml`
  - [x] Run both evaluations against the same gold dataset
  - [x] Compare results — documented in methodology:
    - Grounded F1=0.84 vs Ungrounded F1=0.64 (+0.20)
    - Recall improved +0.40 (0.58→0.98) — grounding helps find biases LLM misses
    - Formal fallacies require grounding (denying_the_antecedent: 0.00→1.00)
    - Hard articles benefit most (+0.30 F1)
    - Precision comparable (+0.02) — grounding doesn't reduce FPs

- [x] Task 4: Write bias detection methodology document (AC5)
  - [x] Create `docs/evaluation-methodology-bias.md`
  - [x] All 11 sections written with actual evaluation numbers (placeholders replaced 2026-04-05)
  - [x] Tone: same as `docs/evaluation-methodology.md` — professional, honest about limitations, portfolio-ready
  - [x] Include actual numbers from the evaluation run (placeholders replaced)

- [x] Task 5: Commit baseline results (AC4) — AFTER EVAL RUN
  - [x] Commit to `eval/reports/bias/`:
    - `summary.json` — aggregate + per-type metrics
    - `baseline_results.json` — full Promptfoo output (raw)
  - [x] Add selective gitignore pattern for `eval/reports/bias/` — DONE
  - [x] Update `docs/ROADMAP.md`:
    - EVAL-3 status → Complete (2026-04-05)
    - Added EVAL-3 completion note with key metrics (F1=0.84, A/B comparison)

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
| `docs/evaluation-methodology-bias.md` | MODIFIED | Replaced [PLACEHOLDER] markers in sections 7 and 8 with actual evaluation numbers and analysis |
| `docs/ROADMAP.md` | MODIFIED | EVAL-3 status → Complete, added completion note with key metrics |

### Completion Notes

- **Infrastructure complete** (2026-04-01) — all scripts, configs, providers, and methodology doc built
- **Evaluation runs complete** (2026-04-02) — grounded (42 articles, F1=0.84) and ungrounded (42 articles, F1=0.64) baselines executed
- **Methodology doc finalized** (2026-04-05) — all [PLACEHOLDER] markers in sections 7 and 8 replaced with actual numbers and analysis
- **ROADMAP updated** (2026-04-05) — EVAL-3 marked Complete with key metrics
- Grounded vs ungrounded A/B comparison documented: +0.20 F1, +0.40 recall. Formal fallacies require grounding (denying_the_antecedent: 0.00→1.00)
- EVAL-DASH integration documented as future work in methodology doc section 10

### Debug Log References

None — no issues during infrastructure build or methodology finalization.

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-28 | 1.0 | Initial story draft from EVAL-3 epic and architecture | Sarah (PO) |
| 2026-03-28 | 1.1 | Validation fixes: exact gitignore pattern for selective tracking, summarize script is required (not optional) with specific responsibilities | Sarah (PO) |
| 2026-03-29 | 1.2 | Promoted grounded vs ungrounded comparison from stretch to REQUIRED (AC6). Chose Option B (grounded parameter) over separate endpoint. Added second provider + config. Effort increases ~0.5 days. | Sarah (PO) |
| 2026-04-01 | 1.3 | Infrastructure complete. Ungrounded provider/config, summarize script, methodology doc (with placeholders), gitignore, runbook. Execution tasks pending service + API key. | James (Dev) |
| 2026-04-05 | 1.4 | Story complete. Methodology doc finalized with actual numbers (sections 7+8). ROADMAP updated. All tasks checked. Grounded F1=0.84, Ungrounded F1=0.64, A/B delta=+0.20. | James (Dev) |

## QA Results

### Review Date: 2026-04-06

### Reviewed By: Quinn (Test Architect)

### Gate Status

Gate: WAIVED → docs/qa/gates/EVAL-3.5-evaluation-execution-methodology.yml
