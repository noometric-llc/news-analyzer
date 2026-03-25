# Epic EVAL-2: Entity Extraction Evaluation Harness

## Epic Overview

| Field | Value |
|-------|-------|
| **Epic ID** | EVAL-2 |
| **Epic Name** | Entity Extraction Evaluation Harness |
| **Track** | AI Evaluation |
| **Epic Type** | New Feature — Evaluation Infrastructure |
| **Priority** | HIGHEST (Job Search Portfolio Priority) |
| **Status** | COMPLETE |
| **Created** | 2026-03-19 |
| **Owner** | Sarah (PO) |
| **Depends On** | EVAL-1 Complete (Synthetic Articles + Ground Truth Storage) |
| **Blocked By** | None — EVAL-1 is complete |
| **Triggered By** | Job search strategy — entity extraction evaluation is the #1 most demonstrable AI eval skill |

## Executive Summary

Build a Promptfoo-based evaluation harness that measures entity extraction quality (precision/recall/F1) by comparing two extractors — the existing spaCy NER pipeline and a new LLM-based Claude extractor — against a curated gold dataset derived from EVAL-1's synthetic articles.

This is the second of three epics in the **AI Evaluation Track**:

| Order | Epic | Status | Delivers |
|-------|------|--------|----------|
| **EVAL-1** | KB Fact Extraction & Synthetic Article Generator | **DONE** | Structured facts + labeled synthetic articles |
| **EVAL-2** | Entity Extraction Evaluation Harness | **APPROVED** | Gold dataset, dual-extractor comparison, precision/recall/F1, Promptfoo integration |
| **EVAL-3** | Cognitive Bias & Logical Fallacy Evaluation via Ontology | PLANNED | Ontology-grounded bias detection, neuro-symbolic AI evaluation |

### Motivation

AI Evaluation Engineer roles require demonstrated experience with:
- **Evaluation methodology design** — defining metrics, building gold datasets, scoring pipelines
- **Standard evaluation tooling** — Promptfoo, Ragas, or similar frameworks
- **Model comparison** — systematic side-by-side evaluation with quantitative metrics
- **Precision/recall/F1** — the fundamental metrics of NLP evaluation
- **Annotation methodology** — gold dataset creation with quality controls

This epic addresses all five directly, producing the single most portfolio-valuable artifact in the project.

## Business Value

1. **Highest portfolio ROI** — Entity extraction evaluation with P/R/F1 is the most commonly requested AI eval skill (appears in 7 of 9 target job descriptions)
2. **Demonstrates evaluation methodology** — Not just running evals, but *designing* them: gold dataset curation, fuzzy matching strategy, metric selection
3. **Shows industry tooling proficiency** — Promptfoo integration signals familiarity with the eval tooling ecosystem
4. **Model comparison narrative** — "spaCy at X% F1 vs Claude at Y% F1" is a compelling interview talking point
5. **Foundation for EVAL-3** — The harness pattern, scorer, and Promptfoo infrastructure are reused for cognitive bias evaluation

## Existing System Context

### What We Have (From EVAL-1)

| Component | Location | Used For |
|-----------|----------|----------|
| Synthetic articles with ground-truth facts | `synthetic_articles` table (PostgreSQL) | Gold dataset derivation |
| FactSets (subject/predicate/object tuples) | `source_facts` JSONB column | Deriving expected entity annotations |
| spaCy entity extractor | `reasoning-service/app/services/entity_extractor.py` | Baseline extractor to evaluate |
| Schema mapper (spaCy → internal types) | `reasoning-service/app/services/schema_mapper.py` | Entity type classification rules |
| Anthropic SDK integration | `reasoning-service/requirements.txt` | LLM extractor (no new dependency) |
| Backend storage API | `GET /api/eval/datasets/articles` | Fetching articles for gold derivation |

### What We're Adding

| Component | Location | Purpose |
|-----------|----------|---------|
| Gold dataset (curated YAML) | `eval/datasets/gold/` | Ground-truth entity annotations |
| Gold derivation script | `eval/datasets/scripts/derive_gold.py` | FactSet → entity annotation pipeline |
| LLM entity extractor | `reasoning-service/app/services/eval/llm_entity_extractor.py` | Claude-based extraction (challenger) |
| LLM extraction endpoint | `reasoning-service/app/api/eval/extraction.py` | `POST /eval/extract/llm` |
| Promptfoo config | `eval/promptfooconfig.yaml` | Evaluation orchestration |
| Custom Python providers | `eval/providers/` | Call spaCy + LLM endpoints |
| Custom Python scorer | `eval/assertions/entity_scorer.py` | TP/FP/FN computation with fuzzy matching |
| Methodology write-up | `eval/README.md` + `docs/evaluation-methodology.md` | Portfolio documentation |

### Integration Points

- **Reads from:** Backend REST API (EVAL-1 stored articles), reasoning-service entity extraction endpoints
- **Writes to:** `eval/datasets/gold/` (YAML files), `eval/reports/` (evaluation results)
- **Calls:** Reasoning-service `/entities/extract` (spaCy) and `/eval/extract/llm` (Claude)
- **No impact on:** Existing KB pages, admin pages, sync pipelines, frontend, or EVAL-1 artifacts

## Stories

### EVAL-2.1: Gold Dataset Derivation & Curation

**Goal:** Build a derivation pipeline that transforms EVAL-1's FactSets into entity annotations, then manually curate 50–60 articles as the primary evaluation benchmark.

**Scope:**
- Gold annotation YAML schema definition
- Python derivation script: fetches synthetic articles from backend API, maps FactSet predicates to expected entity types, locates entity spans in article text via string matching, outputs YAML
- Automated derivation of 100+ articles across all 3 government branches
- Manual curation of 50–60 articles (review annotations, fix span boundaries, add missed entities)
- CoNLL-2003 sanity check subset (20–30 sentences converted to same YAML format)
- pytest tests for the derivation script

**Acceptance Criteria:**
- [ ] Derivation script produces valid gold annotations from stored EVAL-1 articles
- [ ] Gold annotations include entity text, type, character offsets (start/end), and metadata
- [ ] 50–60 articles are manually curated with `curated: true` flag
- [ ] All 3 government branches represented in curated dataset (legislative, executive, judicial)
- [ ] CoNLL-2003 sample (20–30 sentences) included in same YAML format
- [ ] Derivation script has pytest coverage for predicate → entity type mapping and offset calculation
- [ ] Gold dataset files organized by branch in `eval/datasets/gold/`

### EVAL-2.2: LLM Entity Extractor

**Goal:** Build a Claude-based entity extraction endpoint that produces output in the same format as the existing spaCy extractor, enabling side-by-side comparison.

**Scope:**
- `LLMEntityExtractor` class using Anthropic SDK (already installed from EVAL-1.2)
- Prompt engineering for government-domain entity extraction with structured JSON output
- Entity types matching existing taxonomy: person, government_org, organization, location, event, concept, legislation
- Real confidence scores from Claude's assessment (unlike spaCy's fixed 0.85)
- Dry-run mode support (reuse `EVAL_DRY_RUN` setting)
- FastAPI endpoint: `POST /eval/extract/llm`
- Response format matching `/entities/extract` shape for scorer compatibility
- pytest tests for prompt construction, response parsing, dry-run mode

**Acceptance Criteria:**
- [ ] LLM extractor produces entities with text, type, start/end offsets, and confidence scores
- [ ] Output format matches existing `/entities/extract` response shape (scorer-compatible)
- [ ] All 7 entity types supported (person, government_org, organization, location, event, concept, legislation)
- [ ] Confidence scores are real model assessments, not fixed values
- [ ] Dry-run mode works without API calls
- [ ] FastAPI endpoint registered and documented
- [ ] pytest tests verify prompt construction and response parsing (no API calls needed)

### EVAL-2.3: Promptfoo Harness & Entity Scorer

**Goal:** Set up the Promptfoo evaluation infrastructure with custom Python providers and a fuzzy-matching entity scorer that computes precision/recall/F1.

**Scope:**
- `eval/` directory with `package.json` (Promptfoo dependency) and npm scripts
- `promptfooconfig.yaml` defining dual providers (spaCy + Claude), test cases from gold dataset, assertions, and derivedMetrics
- Python custom provider for spaCy (`POST /entities/extract`)
- Python custom provider for LLM (`POST /eval/extract/llm`)
- Entity scorer with fuzzy matching (exact match, substring containment, Levenshtein similarity ≥ 0.8)
- Per-type breakdown scores (person_tp, person_fp, etc.)
- Partial credit for type mismatches (e.g., organization vs government_org)
- derivedMetrics: Precision, Recall, F1 computed from namedScores
- pytest tests for the entity scorer (critical — see test matrix in architecture doc)
- Verify end-to-end: `npx promptfoo eval` runs successfully against gold dataset

**Acceptance Criteria:**
- [ ] `eval/` directory set up with Promptfoo installed and `npm run eval` works
- [ ] Both providers (spaCy, LLM) call their respective endpoints and return entities
- [ ] Entity scorer correctly computes TP/FP/FN with fuzzy matching
- [ ] Scorer handles edge cases: no entities extracted, perfect extraction, substring matches, type mismatches
- [ ] derivedMetrics produce Precision, Recall, F1 in Promptfoo output
- [ ] Per-entity-type breakdown available in results
- [ ] `promptfoo view` displays side-by-side comparison dashboard
- [ ] pytest tests cover all scorer scenarios (see architecture doc Section 9)

### EVAL-2.4: Evaluation Execution & Methodology Write-up

**Goal:** Run full evaluations, analyze results, produce portfolio-ready documentation, and integrate into CI.

**Scope:**
- Execute evaluations across all gold dataset branches (legislative, executive, judicial, CoNLL)
- Analyze results: aggregate P/R/F1, per-type breakdown, spaCy vs Claude comparison
- Identify patterns: where does spaCy fail? where does Claude excel? cost/quality tradeoff
- Write `eval/README.md` — getting-started guide, how to run evals, how to add test cases
- Write `docs/evaluation-methodology.md` — portfolio-facing deep dive on methodology
  - Why these metrics, how gold dataset was built, fuzzy matching rationale, cost/quality analysis
- CI integration: GitHub Actions workflow that runs Promptfoo eval on prompt/model changes
- Generate and commit baseline evaluation reports

**Acceptance Criteria:**
- [ ] Full evaluation run completed across all gold dataset branches
- [ ] Results show meaningful comparison between spaCy and Claude extractors
- [ ] `eval/README.md` written with usage instructions, examples, and how to extend
- [ ] `docs/evaluation-methodology.md` written as portfolio-ready methodology explanation
- [ ] Baseline evaluation reports committed to `eval/reports/`
- [ ] CI workflow runs eval on changes to `eval/` or `reasoning-service/app/services/eval/`
- [ ] Analysis section in methodology doc covers per-type performance, cost/quality, and improvement opportunities

## Compatibility Requirements

- [x] Existing APIs remain unchanged — spaCy extractor is read-only, no modifications
- [x] Database schema changes are backward compatible — no schema changes at all
- [x] UI changes follow existing patterns — no UI changes in this epic
- [x] Performance impact is minimal — evaluation runs are manual/CI, not on serving path

## Risk Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Gold dataset curation takes too long | Medium | Medium | Start with 30 curated articles, expand to 50–60 iteratively |
| LLM extraction JSON parsing failures | Medium | Low | Robust parsing with fallbacks; dry-run mode for testing |
| Claude API costs during evaluation runs | Low | Low | Small dataset (50–60 articles), dry-run mode, rate limiting from EVAL-1.2 |
| Promptfoo Node.js adds complexity | Low | Low | Isolated in `eval/` directory; all custom logic in Python |
| spaCy performs so poorly comparison isn't meaningful | Low | Medium | Expected — the gap itself is the interesting finding for portfolio |

**Rollback Plan:** All additions are new files in `eval/` (top-level) and one new endpoint in reasoning-service. Delete `eval/` directory, remove extraction endpoint — zero impact on existing system.

## Technical Notes

### New Dependencies

| Dependency | Location | Purpose |
|------------|----------|---------|
| `promptfoo` (npm) | `eval/package.json` | Evaluation orchestration CLI |
| `requests` (Python) | `eval/providers/` | HTTP calls from Promptfoo providers (likely already available) |
| No new reasoning-service deps | — | LLM extractor uses existing `anthropic` SDK |

### No New Infrastructure Required
- No new databases, containers, or services
- Node.js required only for `npx promptfoo` CLI (already available from frontend)
- All evaluation runs are local or CI — no production deployment needed

### Key Architecture Document
- [EVAL-2.architecture.md](EVAL-2.architecture.md) — Full architecture with module structure, data flow, Promptfoo config, scorer design, and gold dataset schema

## Definition of Done

- [x] Gold dataset: 50–60 curated articles + CoNLL-2003 sample _(EVAL-2.1)_ — 64 curated + 25 CoNLL
- [x] Gold derivation script tested and producing valid annotations _(EVAL-2.1)_ — 35 pytest tests
- [x] LLM entity extractor producing scored entities via Claude API _(EVAL-2.2)_ — 26 unit + 8 endpoint tests
- [x] Promptfoo harness running with dual providers and entity scorer _(EVAL-2.3)_ — 35 scorer tests
- [x] Precision/recall/F1 metrics computed and displayed in dashboard _(EVAL-2.3)_
- [x] Side-by-side spaCy vs Claude comparison visible in Promptfoo UI _(EVAL-2.3)_
- [x] Full evaluation executed with results analyzed _(EVAL-2.4)_ — Claude F1=0.60 vs spaCy F1=0.31 on gov articles
- [x] `eval/README.md` and `docs/evaluation-methodology.md` written _(EVAL-2.4)_
- [x] CI integration for regression testing _(EVAL-2.4)_ — `.github/workflows/eval.yml`
- [x] pytest coverage for derivation script, LLM extractor, and entity scorer _(all stories)_ — 104 tests total
- [x] Existing functionality verified — no regressions _(all stories)_ — 366/366 pre-existing tests pass
- [ ] Post-implementation: Sage writes Getting Started learning notes _(after epic close)_
