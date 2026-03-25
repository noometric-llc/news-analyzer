# Entity Extraction Evaluation Methodology

**Project:** NewsAnalyzer v2
**Date:** 2026-03-25
**Status:** Baseline evaluation complete

---

## 1. Introduction

This document describes the evaluation methodology for measuring named entity extraction quality in the NewsAnalyzer system. The evaluation compares two extractors — a spaCy NER pipeline (baseline) and a Claude LLM extractor (challenger) — using precision, recall, and F1 metrics against a curated gold dataset of government news articles.

### What We're Measuring

Named Entity Recognition (NER) is the task of identifying and classifying named entities in text. The evaluation measures:

- **Detection**: Can the extractor find entities in the text?
- **Classification**: Does it assign the correct entity type?
- **Boundary accuracy**: Does it identify the correct text span?

### Entity Taxonomy

Both extractors produce entities in a shared 7-type taxonomy:

| Type | Description | Example |
|------|-------------|---------|
| `person` | Named individuals | Elizabeth Warren, John Roberts |
| `government_org` | Government bodies, agencies, courts | Senate, EPA, Supreme Court |
| `organization` | Non-government organizations | Georgetown University, AP |
| `location` | Geographic entities | Washington, Pennsylvania |
| `event` | Named events | Civil War, inauguration |
| `concept` | Political groups, ideologies | Republican, Democratic |
| `legislation` | Laws, bills, executive orders | Affordable Care Act |

---

## 2. Gold Dataset Construction

### Automated Derivation

The gold dataset was derived from synthetic articles generated in a prior evaluation phase (EVAL-1). Each article was generated from a structured FactSet containing subject/predicate/object tuples about U.S. government entities. The derivation script maps FactSet predicates to expected entity annotations:

| Predicate | Entity Type | Example |
|-----------|-------------|---------|
| (subject) | person | "Roger F. Wicker" |
| STATE | location | "Mississippi" |
| COMMITTEE_MEMBERSHIP | government_org | "Banking Committee" |
| PARTY_AFFILIATION | concept | "Republican" |
| COURT | government_org | "Supreme Court" |

The script locates each entity in the article text and computes character offsets (start/end positions).

### Auto-Enrichment

After automated derivation, an enrichment step added commonly missed entities:

- **Dateline locations**: "WASHINGTON", "JACKSON, Miss.", etc.
- **Government organizations**: First occurrence of "Senate", "Congress", "House of Representatives"
- **Multi-word cities**: "SALT LAKE CITY", "BATON ROUGE", etc.

This added 190 entities across 88 articles automatically.

### Human Curation

64 articles underwent manual curation (out of 113 total):

- Verified all derived entity annotations (text, type, character offsets)
- Added entities not present in the original FactSet (quoted sources, organizations, locations)
- Removed false annotations
- Flagged with `curated: true` and date

### Dataset Statistics

| Branch | Total Articles | Curated | Entities |
|--------|---------------|---------|----------|
| Legislative | 53 | 14 | 308 |
| Executive | 20 | 15 | 125 |
| Judicial | 15 | 10 | 81 |
| CoNLL-2003 | 25 | 25 | 87 |
| **Total** | **113** | **64** | **601** |

### External Validation

25 sentences from the CoNLL-2003 shared task (Reuters newswire) are included as an external validation set. This verifies that the scorer works correctly on a widely-used NER benchmark with known entity annotations.

---

## 3. Evaluation Design

### Metrics: Precision, Recall, F1

These are the standard metrics for NER evaluation:

- **Precision** = TP / (TP + FP) — Of entities extracted, what fraction are correct?
- **Recall** = TP / (TP + FN) — Of gold entities, what fraction were found?
- **F1** = 2 × P × R / (P + R) — Harmonic mean balancing precision and recall

### Why Fuzzy Matching

Strict exact-match evaluation penalizes extractors for reasonable boundary differences:

| Extracted | Gold | Strict Match | Fuzzy Match |
|-----------|------|-------------|-------------|
| "Banking Committee" | "Senate Banking Committee" | FP + FN | TP (substring) |
| "John Fettermann" | "John Fetterman" | FP + FN | TP (Levenshtein ≥ 0.8) |
| "EPA" (organization) | "EPA" (government_org) | FP + FN | 0.5 TP (type mismatch) |

The fuzzy matching strategy uses a 6-priority system:

1. Exact text + type match → full TP
2. Exact text + type mismatch → 0.5 partial TP
3. Substring containment + type match → full TP
4. Substring containment + type mismatch → 0.5 partial TP
5. Levenshtein similarity ≥ 0.8 + type match → full TP
6. Levenshtein similarity ≥ 0.8 + type mismatch → 0.5 partial TP

This approach is standard in NER evaluation literature and prevents inflating error rates due to boundary variations.

### Model Comparison Methodology

Both extractors receive identical input (same article text) and are scored by the same scorer against the same gold annotations. This controlled comparison isolates the extraction quality difference from all other variables.

---

## 4. Results

### Aggregate Comparison

| Dataset | Extractor | Precision | Recall | F1 |
|---------|-----------|-----------|--------|-----|
| **Legislative** (53) | spaCy | 0.151 | 0.963 | 0.261 |
| | Claude | **0.426** | **0.977** | **0.593** |
| **Executive** (20) | spaCy | 0.220 | 0.983 | 0.359 |
| | Claude | **0.432** | **1.000** | **0.603** |
| **Judicial** (15) | spaCy | 0.192 | 0.925 | 0.318 |
| | Claude | **0.456** | **0.938** | **0.614** |
| **CoNLL-2003** (25) | spaCy | **0.960** | 0.856 | **0.905** |
| | Claude | 0.789 | **0.963** | 0.867 |

### Per-Entity-Type Performance (Legislative — Largest Dataset)

| Entity Type | spaCy P | spaCy R | spaCy F1 | Claude P | Claude R | Claude F1 |
|-------------|---------|---------|----------|----------|----------|-----------|
| person | 0.20 | 1.00 | 0.33 | **0.56** | 1.00 | **0.72** |
| government_org | 0.18 | 1.00 | 0.31 | **0.48** | 1.00 | **0.65** |
| location | 0.13 | 0.88 | 0.23 | **0.42** | **0.92** | **0.58** |
| concept | 0.14 | 1.00 | 0.25 | **0.33** | 1.00 | **0.49** |
| organization | 0.03 | 1.00 | 0.06 | 0.03 | 1.00 | 0.06 |
| event | 0.00 | 0.00 | 0.00 | **0.25** | **1.00** | **0.40** |

---

## 5. Analysis

### Performance Patterns

**spaCy's weakness is precision, not recall.** The small `en_core_web_sm` model finds most entities (recall > 0.90 on government articles) but generates massive false positives — 1,620 FPs on 53 legislative articles. This suggests the model over-triggers on government-domain text, tagging many non-entity phrases.

**Claude's advantage is disciplined extraction.** Claude achieves similar recall but with 4× fewer false positives (399 vs 1,620 on legislative). This drives the F1 improvement from 0.261 to 0.593 — a 2.3× gain.

**spaCy excels on CoNLL-2003.** On general newswire (the domain spaCy was trained on), it achieves 0.905 F1 with near-perfect precision (0.960). This confirms the model is well-calibrated for its training domain; the government article weakness is a domain gap issue.

**Organization type is problematic for both extractors.** Both score F1 ≈ 0.06 on `organization` in legislative articles, suggesting the gold annotations or the entity type boundary between `organization` and `government_org` may need refinement.

### Cost/Quality Tradeoff

| Metric | spaCy | Claude (Sonnet) |
|--------|-------|-----------------|
| Cost per article | $0.00 | ~$0.004 |
| Cost for 50 articles | $0.00 | ~$0.20 |
| Avg F1 (government) | 0.31 | 0.60 |
| F1 improvement | — | +0.29 (+94%) |

Claude nearly doubles spaCy's F1 on government articles for approximately $0.004 per article. For evaluation and development purposes, this is a negligible cost. In production, the decision depends on volume and latency requirements.

### Limitations

1. **Gold dataset size**: 113 articles with 64 curated is sufficient for methodology demonstration but small for production-grade evaluation
2. **Synthetic articles**: Generated articles may not fully represent real-world news complexity
3. **Single annotator**: Curation was performed by one person; inter-annotator agreement was not measured
4. **Entity type ambiguity**: The boundary between `organization` and `government_org` is subjective for some entities

---

## 6. Tooling

### Why Promptfoo

Promptfoo was selected as the evaluation framework for:

- **Dual-provider comparison**: Built-in support for running multiple extractors against the same test cases
- **Custom Python assertions**: Allows complex scoring logic (fuzzy matching) without framework constraints
- **Web dashboard**: Side-by-side visualization of results
- **CI integration**: CLI-based execution suitable for GitHub Actions
- **derivedMetrics**: Automatic aggregation of P/R/F1 from per-test-case scores

### Custom Components

| Component | Purpose |
|-----------|---------|
| `spacy_provider.py` | Calls existing `/entities/extract` endpoint |
| `llm_provider.py` | Calls new `/eval/extract/llm` endpoint |
| `entity_scorer.py` | Fuzzy-matching scorer with per-type breakdown |

### CI Integration

A GitHub Actions workflow runs the spaCy evaluation on pushes that modify evaluation or extraction code. The Claude evaluation requires API keys and runs locally.

---

## 7. Future Work

1. **EVAL-3: Cognitive Bias Evaluation** — Extend the harness pattern to evaluate bias detection using an ontology-grounded approach
2. **Larger gold dataset** — Expand to 200+ curated articles with multiple annotators for inter-annotator agreement
3. **Additional extractors** — Add GPT-4, Gemini, and larger spaCy models for broader comparison
4. **Active learning** — Use evaluation results to identify articles where annotation would most improve the dataset
5. **Prompt engineering** — Iterate on Claude's extraction prompt to improve precision on `concept` and `organization` types
