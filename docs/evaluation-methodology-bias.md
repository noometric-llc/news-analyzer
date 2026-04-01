# Cognitive Bias & Logical Fallacy Detection — Evaluation Methodology

**Project:** NewsAnalyzer v2 — EVAL-3
**Date:** 2026-04-01
**Status:** Infrastructure complete — awaiting evaluation run

---

## 1. Introduction

This document describes the evaluation methodology for the NewsAnalyzer cognitive bias and logical fallacy detection system. The core innovation is **neuro-symbolic grounding**: instead of asking an LLM "does this text contain bias?" (unauditable, relying on training data), we query a formal OWL ontology via SPARQL to retrieve academically-cited definitions and detection patterns, then ground the LLM prompt in those definitions.

The result is **auditable** (traceable to cited academic sources), **governable** (SHACL-validated), and **evaluable** (Promptfoo harness with P/R/F1 metrics).

### What We're Measuring

Cognitive bias and logical fallacy detection is the task of identifying reasoning errors in news text. The evaluation measures:

- **Detection**: Can the system identify that a distortion is present?
- **Classification**: Does it assign the correct distortion type?
- **Auditability**: Is each detection traceable to a formal definition and academic source?

### Why Neuro-Symbolic?

Traditional LLM approach:
> "Does this text contain confirmation bias?" → LLM guesses from training data

EVAL-3 neuro-symbolic approach:
> 1. SPARQL retrieves the formal definition of confirmation bias from the ontology (including academic source: Nickerson, 1998)
> 2. Build prompt: "Given this definition [from Nickerson 1998], does the text exhibit this pattern?"
> 3. LLM analyzes against the formal definition
> 4. SHACL validates the output structure
> 5. Result is auditable: traceable to a cited academic source

---

## 2. Ontology Design

### Class Hierarchy

The cognitive bias ontology (`reasoning-service/ontology/cognitive-bias.ttl`) defines a formal taxonomy:

```
CognitiveDistortion (abstract root)
├── CognitiveBias (5 individuals)
│   ├── ConfirmationBias — Nickerson, 1998
│   ├── AnchoringBias — Kahneman, 2011
│   ├── FramingEffect — Tversky & Kahneman, 1981
│   ├── AvailabilityHeuristic — Kahneman, 2011
│   └── BandwagonEffect — Kahneman, 2011
└── LogicalFallacy
    ├── FormalFallacy (2 individuals)
    │   ├── AffirmingTheConsequent — Walton, 2008
    │   └── DenyingTheAntecedent — Walton, 2008
    └── InformalFallacy (7 individuals)
        ├── AdHominem — Walton, 2008
        ├── StrawMan — Walton, 2008
        ├── FalseDilemma — Walton, 2008
        ├── SlipperySlope — Walton, 2008
        ├── AppealToAuthority — Walton, 2008
        ├── RedHerring — Walton, 2008
        └── CircularReasoning — Walton, 2008
```

**14 distortions total** — 5 cognitive biases + 9 logical fallacies (7 informal + 2 formal).

### Design Decisions

- **OWL + SPARQL + SHACL** chosen over Prolog — these are the W3C Semantic Web standards that knowledge engineering roles require
- **Named Individuals** (not subclasses) — enables SPARQL enumeration and SHACL targeting
- **Academic sources as first-class entities** — every distortion links to a citable publication
- **Detection patterns per distortion** — natural language criteria for what to look for in text

### Academic Sources

| Citation | Used By |
|----------|---------|
| Kahneman, D. (2011). *Thinking, Fast and Slow* | AnchoringBias, AvailabilityHeuristic, BandwagonEffect |
| Tversky, A. & Kahneman, D. (1981). *The Framing of Decisions* | FramingEffect |
| Nickerson, R. (1998). *Confirmation Bias: A Ubiquitous Phenomenon* | ConfirmationBias |
| Walton, D. (2008). *Informal Logic: A Pragmatic Approach* | All 9 logical fallacies |

---

## 3. SHACL Validation

SHACL (Shapes Constraint Language) provides formal constraints on RDF data. Three shapes validate the system:

| Shape | Target | Constraints |
|-------|--------|------------|
| `CognitiveDistortionShape` | Every distortion in the ontology | Must have: definition, academic source, detection pattern |
| `BiasAnnotationShape` | Every LLM detection output | Must have: exactly 1 distortion type, text excerpt, confidence [0,1] |
| `AcademicSourceShape` | Every cited source | Must have: author, year, title |

SHACL validation runs at two points:
1. **Startup** — validates the ontology itself (all 14 distortions have required properties)
2. **Runtime** — validates each LLM detection output before returning it

---

## 4. Detection Approach

### Neuro-Symbolic Pipeline

```
1. Request arrives: article text
2. SPARQL → retrieve all 14 distortion definitions from ontology
3. Build prompt: system prompt + definitions section + article text
4. Claude analyzes text against formal definitions
5. Parse JSON response → list of bias annotations
6. Convert to RDF triples (BiasAnnotation individuals)
7. SHACL validate against BiasAnnotationShape
8. Return validated, ontology-enriched annotations
```

### Grounded Prompt Structure

The user prompt sent to Claude includes a DEFINITIONS section with every distortion:

```
[confirmation_bias] Confirmation Bias (Nickerson, 1998)
Definition: The tendency to search for, interpret, favor...
Detection pattern: Look for selective evidence citation...

[ad_hominem] Ad Hominem (Walton, 2008)
Definition: Attacking the person making the argument...
Detection pattern: Look for personal attacks...

TEXT TO ANALYZE:
{article text}
```

The system prompt instructs Claude to return the exact `distortion_type` identifiers from the definitions — creating a closed-vocabulary classification problem rather than open-ended generation.

---

## 5. Gold Dataset Construction

### Two-Tier Approach

**Tier 1: Synthetic (automated ground truth)**
- Neutral EVAL-1 articles rewritten with intentionally injected biases via Claude
- The injected bias IS the gold annotation — objective ground truth
- Multiple difficulty levels: easy (obvious), medium (careful reading), hard (subtle)
- Generated by `eval/datasets/bias/scripts/generate_biased_articles.py`

**Tier 2: Curated (ecologically valid)**
- Real news excerpts with human-annotated biases
- More realistic but subjective — annotators may disagree
- Includes faithful (no bias) articles as negative examples

**Why two tiers:** Synthetic data gives us objective quantitative metrics. Curated data gives ecological validity. Both are needed for a credible evaluation.

### Annotation Schema

```yaml
- vars:
    article_text: "The senator's reckless policy..."
    biases:
      - type: framing_effect        # Ontology controlled vocabulary
        excerpt: "reckless policy"   # Exact text from article
        explanation: "Uses loaded language..."
        academic_source: "Tversky & Kahneman, 1981"
    metadata:
      id: eval-3-bias-001
      source: synthetic
      difficulty: medium
      bias_count: 1
```

---

## 6. Evaluation Metrics

### Scoring

Precision, recall, and F1 computed per article, then aggregated:

- **Exact match** on `distortion_type` (case-insensitive) → **1.0 TP**
- **Category match** (e.g., gold="ad_hominem", detected="straw_man" — both informal_fallacy) → **0.5 partial credit**
- **No match** → FP (detected but not in gold) or FN (in gold but not detected)

The partial credit scoring reflects the reality that bias categories overlap — detecting a different fallacy in the same category shows the detector identified the right *kind* of reasoning error.

### Pass Threshold

F1 ≥ 0.3 (lower than entity extraction's 0.5 — bias detection is inherently harder and more subjective).

### Derived Metrics

Promptfoo aggregates per-article scores into:
- **Aggregate P/R/F1** across all articles
- **Per-distortion-type** TP/FP/FN breakdown
- **Per-difficulty** breakdown (easy/medium/hard)
- **Per-source** breakdown (synthetic/curated)

---

## 7. Results

<!-- PLACEHOLDER: Replace with actual numbers after running evaluation -->

### Baseline Evaluation (Grounded)

| Metric | Value |
|--------|-------|
| Precision | [PLACEHOLDER] |
| Recall | [PLACEHOLDER] |
| F1 | [PLACEHOLDER] |
| Total articles | [PLACEHOLDER] |
| True positives | [PLACEHOLDER] |
| False positives | [PLACEHOLDER] |
| False negatives | [PLACEHOLDER] |

### Per-Distortion-Type Performance

| Distortion Type | P | R | F1 | Notes |
|----------------|---|---|----|----|
| framing_effect | [PLACEHOLDER] | | | |
| ad_hominem | [PLACEHOLDER] | | | |
| confirmation_bias | [PLACEHOLDER] | | | |
| ... | | | | |

### Per-Difficulty Performance

| Difficulty | P | R | F1 | Articles |
|-----------|---|---|----|----------|
| easy | [PLACEHOLDER] | | | |
| medium | [PLACEHOLDER] | | | |
| hard | [PLACEHOLDER] | | | |

---

## 8. Grounded vs Ungrounded Comparison

<!-- PLACEHOLDER: Replace with actual A/B results -->

The key experiment: does ontology grounding improve detection quality?

| Metric | Grounded | Ungrounded | Delta |
|--------|----------|------------|-------|
| Precision | [PLACEHOLDER] | [PLACEHOLDER] | [PLACEHOLDER] |
| Recall | [PLACEHOLDER] | [PLACEHOLDER] | [PLACEHOLDER] |
| F1 | [PLACEHOLDER] | [PLACEHOLDER] | [PLACEHOLDER] |

**Setup:** Same Claude model, same gold dataset, same scorer. Only difference: the grounded prompt includes SPARQL-retrieved ontology definitions; the ungrounded prompt says "find biases" with no definitions.

**Regardless of quantitative difference**, the ontology-grounded approach provides:
- **Auditability** — every detection traces to a formal definition and academic source
- **Governability** — SHACL validates both the knowledge base and LLM outputs
- **Reproducibility** — the ontology is versioned; the same definitions produce the same prompts

---

## 9. Limitations

1. **Subjectivity of bias detection** — Unlike entity extraction, bias detection involves judgment. Reasonable people may disagree on whether a text exhibits a particular bias. Our synthetic gold data mitigates this (we injected the bias, so we know it's there), but curated data remains subjective.

2. **Synthetic data limitations** — Injected biases may be more obvious or artificial than naturally occurring biases. The "hard" difficulty level attempts to address this.

3. **Ontology scope** — 14 distortions out of hundreds of documented cognitive biases and logical fallacies. The ontology is designed to be extensible, but current coverage is limited.

4. **Single model evaluation** — Only Claude was tested. Different LLMs may perform differently with grounded prompts.

5. **English-only** — All articles and definitions are in English.

6. **No inter-annotator agreement** — Curated annotations were done by a single annotator. Production evaluation should include multiple annotators and measure agreement (Cohen's κ).

---

## 10. Future Work

- **Expand ontology** — Add more cognitive biases (Dunning-Kruger, hindsight bias, etc.) and fallacies (tu quoque, genetic fallacy, etc.)
- **Inter-annotator agreement** — Multiple human annotators for curated data
- **Multi-model comparison** — Test with GPT-4, Gemini, Llama alongside Claude
- **RAG integration** — Retrieve relevant definitions dynamically based on article content rather than including all 14 in every prompt
- **Severity scoring** — Not just presence/absence, but how severe the bias is
- **EVAL-DASH integration** — Surface bias detection results in the evaluation dashboard

---

## 11. Tools Used

| Tool | Version | Purpose |
|------|---------|---------|
| RDFLib | 7.6.0 | OWL ontology parsing, SPARQL queries |
| OWL-RL | 7.1.4 | OWL 2 RL reasoning |
| pyshacl | 0.31.0 | SHACL constraint validation |
| Anthropic SDK | latest | Claude API for bias detection |
| Promptfoo | latest | Evaluation harness, P/R/F1 scoring |
| FastAPI | 0.109.0 | API endpoints for detection |
| pytest | 7.4.4 | Automated testing |

---

*End of Bias Detection Evaluation Methodology*
