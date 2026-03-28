# Epic EVAL-3: Cognitive Bias & Logical Fallacy Evaluation via Ontology

## Epic Overview

| Field | Value |
|-------|-------|
| **Epic ID** | EVAL-3 |
| **Epic Name** | Cognitive Bias & Logical Fallacy Evaluation via Ontology |
| **Track** | AI Evaluation / Knowledge Engineering |
| **Epic Type** | New Feature — Neuro-Symbolic AI + Ontology Engineering |
| **Priority** | HIGHEST (Job Search — demonstrates both AI Eval AND KE skills) |
| **Status** | PROPOSED |
| **Created** | 2026-03-27 |
| **Owner** | Sarah (PO) |
| **Depends On** | EVAL-1 Complete, EVAL-2 Complete, EVAL-DASH Complete |
| **Blocked By** | None |
| **Estimated Effort** | ~1.5 weeks (5 stories, all required) |

## Executive Summary

Build an ontology-grounded cognitive bias and logical fallacy detection system. The core innovation: instead of asking an LLM "does this text contain bias?" (unauditable, relies on training data), we query a formal OWL ontology via SPARQL to retrieve academically-cited definitions and detection patterns, then ground the LLM prompt in those formal definitions. The output is validated against SHACL shape constraints.

This is **neuro-symbolic AI** — combining LLM natural language understanding with formal symbolic knowledge. The result is auditable (traceable to cited academic sources), governable (SHACL-validated), and evaluable (Promptfoo harness with P/R/F1 metrics).

### Why This Epic Matters for Career Goals

EVAL-3 is the only epic that simultaneously demonstrates skills for **both** target job tracks:

**AI Evaluation Track:**
- Bias/fairness testing with quantitative metrics (Tier 1 skill from compiled JDs)
- LLM evaluation framework (Promptfoo)
- Gold dataset creation for subjective tasks
- Responsible AI / explainable AI patterns

**Knowledge Engineering Track:**
- Formal OWL ontology modeling with academic grounding
- SPARQL query design for knowledge retrieval
- SHACL validation for data quality governance
- W3C Semantic Web standards (RDF, OWL, SPARQL, SHACL)

### Problem Statement

> LLM-based analysis of cognitive bias and logical fallacies currently has no auditability — when a model says "this text contains confirmation bias," there's no way to trace that judgment to a formal definition or academic source. This makes the analysis unreliable and unexplainable.

EVAL-3 solves this by grounding LLM analysis in a formal ontology where every bias and fallacy has a citable academic source, a formal definition, and a detection pattern — making the system's reasoning transparent and auditable.

## Business Value

1. **Demonstrates neuro-symbolic AI** — The pattern of combining LLMs with formal knowledge is increasingly in demand for responsible AI roles
2. **Showcases OWL + SPARQL + SHACL trifecta** — The three W3C standards that KE job descriptions ask for, demonstrated in a working system
3. **Extends proven evaluation framework** — Reuses EVAL-2's Promptfoo harness, proving the framework is generalizable
4. **Adds bias/fairness testing** — Directly addresses Tier 1 skill gap from AI eval job descriptions
5. **Produces shareable methodology** — Extends the `/evaluation/methodology` page with bias detection evaluation

## Architecture Reference

Full technical design: [EVAL-3.architecture.md](EVAL-3.architecture.md)

Key architectural decisions:
- **OWL + SPARQL + SHACL** (no Prolog — Prolog doesn't appear in target job descriptions)
- **Separate ontology file** (`cognitive-bias.ttl`) importing `newsanalyzer.ttl`
- **pyshacl** as only new dependency (pure Python, integrates with existing rdflib)
- **13 initial distortions** — 5 cognitive biases + 8 logical fallacies
- **Two-tier gold dataset** — synthetic (automated, objective) + curated (human, subjective)
- **Existing stub implementation** — `/fallacies/detect` endpoint already has Pydantic models defined

## Stories

---

### EVAL-3.1: Cognitive Bias OWL Ontology + SHACL Shapes

**Goal:** Create the formal OWL ontology of cognitive biases and logical fallacies with academic sources, plus SHACL shapes for validation.

**Scope:**
- New `reasoning-service/ontology/cognitive-bias.ttl` with `cb:` namespace
- 13 `CognitiveDistortion` individuals: 5 biases (ConfirmationBias, AnchoringBias, FramingEffect, AvailabilityHeuristic, BandwagonEffect) + 8 fallacies (6 InformalFallacy: AdHominem, StrawMan, FalseDilemma, SlipperySlope, AppealToAuthority, RedHerring, CircularReasoning + 2 FormalFallacy: AffirmingTheConsequent, DenyingTheAntecedent)
- Class hierarchy: CognitiveDistortion → CognitiveBias / LogicalFallacy → FormalFallacy / InformalFallacy
- Each distortion: `hasDefinition`, `hasAcademicSource`, `hasDetectionPattern`, `relatedTo`
- Academic sources as first-class entities: Kahneman (2011), Tversky & Kahneman (1981), Nickerson (1998), Walton (2008)
- New `reasoning-service/ontology/cognitive-bias-shapes.ttl` — SHACL shapes for CognitiveDistortion, BiasAnnotation, AcademicSource
- Ontology validation tests (pytest): 13 distortions present, SHACL conformance, hierarchy correct, sources complete

**Acceptance Criteria:**
- [ ] `cognitive-bias.ttl` loads into rdflib Graph without errors
- [ ] SPARQL query returns all 13 distortions with definitions
- [ ] Every distortion has at least one academic source
- [ ] Every distortion has a detection pattern
- [ ] SHACL validation (`cognitive-bias-shapes.ttl`) passes against the ontology
- [ ] Class hierarchy: 5 CognitiveBias, 2 FormalFallacy, 6 InformalFallacy
- [ ] Imports `newsanalyzer.ttl` successfully (combined graph works)

**Effort:** ~1.5 days

---

### EVAL-3.2: SHACL Validator Service + Reasoner Extension

**Goal:** Add SHACL validation as a new capability and extend the OWL reasoner to load and query the bias ontology.

**Scope:**
- New `reasoning-service/app/services/shacl_validator.py` — `SHACLValidator` class with `validate_ontology()`, `validate_annotations()`, `get_validation_report()`
- Add `pyshacl` to `requirements.txt`
- Extend `OWLReasoner` with methods: `load_bias_ontology()`, `get_distortion_definition()`, `list_distortions()`, `get_related_distortions()`
- SPARQL query constants (`SPARQL_*`) for bias definition retrieval
- New API endpoints: `GET /eval/bias/ontology/stats`, `POST /eval/bias/ontology/validate`
- Register `eval_bias` router in `main.py`
- Tests for SHACLValidator and new reasoner methods

**Acceptance Criteria:**
- [ ] `pyshacl` installed and importable
- [ ] `SHACLValidator.validate_ontology()` returns conformance report
- [ ] `OWLReasoner.load_bias_ontology()` loads cognitive-bias.ttl alongside newsanalyzer.ttl
- [ ] `OWLReasoner.get_distortion_definition()` returns definition + source + pattern via SPARQL
- [ ] `GET /eval/bias/ontology/stats` returns distortion counts and SHACL status
- [ ] `POST /eval/bias/ontology/validate` returns detailed conformance report
- [ ] Existing OWLReasoner methods (`infer()`, `check_consistency()`) still work correctly
- [ ] Existing tests pass without modification

**Effort:** ~1 day

---

### EVAL-3.3: Ontology-Grounded Bias Detector

**Goal:** Implement the core neuro-symbolic bias detection: SPARQL → grounded prompt → Claude → parse → SHACL validate.

**Scope:**
- New `reasoning-service/app/services/eval/bias_detector.py` — `OntologyGroundedBiasDetector` class
  - `detect(text, distortion_types?, confidence_threshold?)` — main detection method
  - Step 1: SPARQL query for definitions from ontology
  - Step 2: Build prompt grounded in formal definitions + academic sources
  - Step 3: Call Claude with structured output instructions
  - Step 4: Parse JSON response into BiasAnnotation objects
  - Step 5: SHACL-validate annotations
  - Step 6: Return validated results
- Implement `/fallacies/detect` stub in `fallacies.py` (existing Pydantic models, real implementation)
- New `reasoning-service/app/api/eval/bias.py` — `POST /eval/bias/detect` endpoint (Promptfoo target)
- Configurable: model, rate limit, dry-run mode (follows LLMEntityExtractor pattern)
- Tests with mocked Claude responses

**Acceptance Criteria:**
- [ ] `POST /fallacies/detect` returns real bias/fallacy detections (not empty stub)
- [ ] `POST /eval/bias/detect` returns annotations with ontology metadata
- [ ] Detection prompt contains SPARQL-retrieved definitions and academic sources
- [ ] Response includes `distortions_checked` list
- [ ] SHACL validates output annotations before returning
- [ ] Dry-run mode returns empty results without Claude API call
- [ ] Graceful error handling: Claude failure returns error, doesn't crash
- [ ] Existing `/entities/extract` and `/eval/extract/llm` endpoints unaffected

**Effort:** ~2 days

---

### EVAL-3.4: Bias Gold Dataset + Evaluation Harness

**Goal:** Create ground truth for bias detection evaluation and build the Promptfoo harness.

**Scope:**
- New `eval/datasets/bias/scripts/generate_biased_articles.py` — takes neutral EVAL-1 articles, injects specific biases via LLM
- New `eval/datasets/bias/synthetic_biased.yaml` — 30–50 articles with injected biases (gold annotation = injected type)
- New `eval/datasets/bias/curated_biased.yaml` — 20–30 real news excerpts, human-annotated
- Gold annotation schema: `article_text`, `biases[]` (type, excerpt, explanation, academic_source), `metadata` (id, source, difficulty, bias_count)
- New `eval/assertions/bias_scorer.py` — Promptfoo scorer with `get_assert()`, P/R/F1, per-distortion-type namedScores
- New `eval/providers/bias_provider.py` — calls `/eval/bias/detect`
- New `eval/promptfoo-bias.yaml` — Promptfoo config for bias evaluation
- Scoring: exact match on distortion_type (1.0 TP), category match (0.5 partial), per-type breakdown

**Acceptance Criteria:**
- [ ] Synthetic biased articles generated with known bias injections
- [ ] Gold annotations follow defined YAML schema
- [ ] Bias types in gold data are from the ontology (controlled vocabulary)
- [ ] `bias_scorer.py` computes P/R/F1 against gold
- [ ] Partial credit (0.5) for correct category, wrong specific type
- [ ] Per-distortion-type breakdown in namedScores
- [ ] Promptfoo runs successfully: `npx promptfoo eval -c eval/promptfoo-bias.yaml`
- [ ] Results include both aggregate and per-type metrics

**Effort:** ~2 days

---

### EVAL-3.5: Evaluation Execution + Methodology Writeup

**Goal:** Run the baseline bias detection evaluation, analyze results, and document the methodology.

**Scope:**
- Execute bias evaluation against full gold dataset
- Analyze results: aggregate P/R/F1, per-distortion-type breakdown, per-difficulty breakdown
- Compare: ontology-grounded detection vs ungrounded (prompt without definitions) — A/B test
- Write `docs/evaluation-methodology-bias.md` — methodology document for bias detection
- Update EVAL-DASH methodology page with bias detection section (or plan as follow-up)
- Commit baseline results to `eval/reports/bias/`

**Acceptance Criteria:**
- [ ] Baseline evaluation completed against full gold dataset
- [ ] Aggregate P/R/F1 reported for bias detection
- [ ] Per-distortion-type breakdown available
- [ ] Grounded vs ungrounded comparison documented (if time permits)
- [ ] Methodology document written and committed
- [ ] Baseline results committed to `eval/reports/bias/`
- [ ] EVAL-DASH update planned or implemented

**Effort:** ~1 day

---

## Sizing Summary

| Story | Description | Effort | Required? |
|-------|-------------|--------|-----------|
| EVAL-3.1 | OWL Ontology + SHACL Shapes | ~1.5 days | Yes |
| EVAL-3.2 | SHACL Validator + Reasoner Extension | ~1 day | Yes |
| EVAL-3.3 | Ontology-Grounded Bias Detector | ~2 days | Yes |
| EVAL-3.4 | Gold Dataset + Evaluation Harness | ~2 days | Yes |
| EVAL-3.5 | Evaluation Execution + Methodology | ~1 day | Yes |
| **Total** | | **~7.5 days** | |

**Dependency chain:** EVAL-3.1 → 3.2 → 3.3 → 3.4 → 3.5

## Compatibility Requirements

- [ ] Existing ontology (`newsanalyzer.ttl`) unchanged — imported, not modified
- [ ] Existing API endpoints (`/entities/*`, `/eval/extract/*`) unchanged
- [ ] Existing EVAL-2 evaluation harness produces identical results
- [ ] No database schema changes
- [ ] No backend (Java) or frontend changes in core stories
- [ ] One new dependency: `pyshacl` (pure Python)

## Risk Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Gold data subjectivity — bias detection ground truth requires human judgment | High | Medium | Two-tier approach: synthetic (objective) + curated (subjective). Focus quantitative metrics on synthetic tier. |
| Ontology scope creep — 13 distortions could grow | Medium | Low | Explicitly scoped in EVAL-3.1. Additional distortions are future work. |
| LLM prompt quality — grounded prompts may not improve accuracy | Low | Low | The evaluation harness measures this. Auditability is the portfolio value regardless. |
| pyshacl compatibility with rdflib 7.0.0 | Low | Medium | Verify in EVAL-3.2 before building on it. Pure Python, actively maintained. |

## Definition of Done

- [ ] OWL ontology with 13 distortions, academic sources, detection patterns
- [ ] SHACL validation passing for all ontology entries
- [ ] Bias detection API implemented (both public and eval endpoints)
- [ ] Detection prompts grounded in SPARQL-retrieved ontology definitions
- [ ] Gold dataset with synthetic + curated biased articles
- [ ] Promptfoo evaluation harness with P/R/F1 metrics
- [ ] Baseline results committed
- [ ] Methodology documented
- [ ] All existing tests still passing
- [ ] No regressions to existing functionality
