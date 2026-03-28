# EVAL-3 Brownfield Enhancement Architecture: Cognitive Bias & Logical Fallacy Evaluation via Ontology

**Version:** 1.0
**Date:** 2026-03-27
**Author:** Winston (Architect)

---

## 1. Introduction

This document outlines the architectural approach for enhancing NewsAnalyzer V2 with **EVAL-3: Cognitive Bias & Logical Fallacy Evaluation via Ontology**. Its primary goal is to serve as the guiding architectural blueprint for AI-driven development of a neuro-symbolic bias detection system while ensuring seamless integration with the existing reasoning service, OWL ontology, and EVAL-2 evaluation framework.

**Relationship to Existing Architecture:**
This document supplements the existing project architecture. EVAL-3 extends the reasoning-service with new ontology files, a SHACL validation layer, an ontology-grounded bias detector, and a Promptfoo evaluation harness — all following established patterns from EVAL-1 and EVAL-2.

### 1.1 Existing Project Analysis

#### Current Project State

- **Primary Purpose:** Polyglot news analysis platform with entity extraction, OWL reasoning, and knowledge base management
- **Current Tech Stack:** Java/Spring Boot (backend), Python/FastAPI (reasoning), TypeScript/Next.js (frontend)
- **Architecture Style:** Service-oriented monorepo (3 services + shared eval tooling)
- **Deployment:** Docker Compose on Hetzner Cloud (EU)

#### EVAL-3 Relevant Infrastructure

| Component | Status | Relevance to EVAL-3 |
|---|---|---|
| OWL Ontology (`newsanalyzer.ttl`) | Mature (v2.0, 256 lines) | Import — extend with bias/fallacy classes |
| OWL-RL Reasoner (`owl_reasoner.py`) | Fully implemented (399 lines) | Extend — add bias ontology loading + SPARQL methods |
| SPARQL Support | Working (`query_sparql()` method) | Core — ontology-grounded prompt building |
| Fallacy/Bias API (`fallacies.py`) | **Stub only** — returns empty lists | Implement — natural home for bias detection |
| Prolog/PySwip | Dead code — dependency listed, never used | Ignore — OWL+SHACL chosen over Prolog |
| EVAL-2 Promptfoo Harness | Fully implemented (scorer, providers, gold datasets) | Pattern — replicate for bias scoring |
| LLM Entity Extractor | Working (Claude API, structured output) | Pattern — bias detector follows same architecture |
| `biasScore` / `credibilityScore` properties | Already in ontology | Available for use |

#### Identified Constraints

- OWL-RL was chosen over Prolog for reasoning — EVAL-3 stays on this path
- The Promptfoo scorer pattern (`get_assert` with `namedScores`) is the established evaluation interface
- The fallacies API endpoint is already registered at `/fallacies/detect` with well-designed Pydantic models
- Creating gold data for bias detection is fundamentally harder than for entity extraction (subjective, context-dependent)

#### Key Architectural Decision: OWL + SPARQL + SHACL (No Prolog)

Prolog was evaluated and rejected for EVAL-3. Rationale:
- Prolog does not appear in AI Evaluation or Knowledge Engineering job descriptions
- OWL/SPARQL/SHACL are the skills KE roles actually require (RDF, OWL, SPARQL, SHACL appear in compiled JDs)
- PySwip adds Docker complexity (system-level SWI-Prolog binary) with no portfolio ROI
- SHACL (W3C Shapes Constraint Language) fills the "formal validation" role that Prolog would have played, with better career signal

### 1.2 Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-27 | 1.0 | Initial architecture document | Winston (Architect) |

---

## 2. Enhancement Scope and Integration Strategy

### 2.1 Enhancement Overview

**Enhancement Type:** New AI evaluation capability + ontology extension + SHACL validation layer
**Scope:** Medium-High — new ontology, new evaluation harness, new API implementation, SHACL validation
**Integration Impact:** Moderate — extends existing services, no structural changes to current architecture

### 2.2 Four-Layer Architecture

```
┌─────────────────────────────────────────────────┐
│  Layer 4: EVALUATION HARNESS                    │
│  Promptfoo config + bias scorer + gold dataset  │
│  (follows EVAL-2 pattern)                       │
├─────────────────────────────────────────────────┤
│  Layer 3: BIAS DETECTION API                    │
│  POST /fallacies/detect (implement stub)        │
│  LLM bias detector with ontology-grounded       │
│  prompts via SPARQL                             │
├─────────────────────────────────────────────────┤
│  Layer 2: SHACL VALIDATION                      │
│  Validate ontology entries + LLM output         │
│  against formal shape constraints               │
├─────────────────────────────────────────────────┤
│  Layer 1: OWL ONTOLOGY                          │
│  cognitive-bias.ttl — formal taxonomy of        │
│  biases & fallacies with academic grounding     │
└─────────────────────────────────────────────────┘
```

### 2.3 Integration Approach

| Aspect | Strategy |
|---|---|
| **Code Integration** | Extends reasoning-service only. No backend (Java) or frontend changes in core stories. |
| **Ontology Integration** | New `cognitive-bias.ttl` that imports `newsanalyzer.ttl`. Keeps concerns separate. |
| **API Integration** | Implements existing `/fallacies/detect` stub. Adds new `/eval/bias/*` endpoints. |
| **Eval Integration** | New Promptfoo config, scorer, provider — parallel to EVAL-2, not replacing it. |
| **SHACL Integration** | New dependency (`pyshacl`). New shapes file. Validation callable from reasoner or standalone. |

### 2.4 The Neuro-Symbolic Pattern (Portfolio Headline)

```
Traditional LLM approach:
  "Does this text contain confirmation bias?" → LLM guesses from training data

EVAL-3 neuro-symbolic approach:
  1. SPARQL query → retrieve formal definition of confirmation_bias from ontology
     (including: academic source, detection criteria, related biases, examples)
  2. Build grounded prompt → "Given this definition [from Nickerson 1998]..."
  3. LLM analyzes text against the formal definition
  4. SHACL validates the output conforms to expected schema
  5. Result is auditable: traceable to a cited academic source
```

### 2.5 Compatibility Requirements

- **Existing API:** `/fallacies/detect` response shape is already defined — implement it, don't change it
- **Existing ontology:** `newsanalyzer.ttl` is imported, not modified
- **Existing eval harness:** EVAL-2 entity extraction evaluation continues working independently
- **Existing tests:** No modifications to existing test suites
- **Performance:** Ontology loading adds ~50–100ms to reasoner startup

---

## 3. Tech Stack

### 3.1 Existing Technology Stack

| Category | Current Technology | Version | Usage in EVAL-3 | Notes |
|---|---|---|---|---|
| Runtime | Python | 3.11+ | All EVAL-3 code | No change |
| Web Framework | FastAPI | 0.109.0 | Bias detection endpoints | Implement existing stub |
| Validation | Pydantic | 2.5.3 | Request/response models | Existing models in fallacies.py |
| RDF/OWL | RDFLib | 7.0.0 | Ontology parsing, SPARQL | Core of EVAL-3 |
| OWL Reasoning | OWL-RL | 6.0.2 | Inference on bias ontology | Existing reasoner |
| LLM | Anthropic SDK | latest | Claude for bias detection | Same as EVAL-2 |
| Eval Framework | Promptfoo | >=0.100.0 | Bias evaluation harness | New config alongside EVAL-2 |
| Testing | pytest | 7.4.4 | Unit + integration tests | Existing |
| Linting | Ruff + mypy | 0.1.11 / 1.8.0 | Code quality | Existing |

### 3.2 New Technology Additions

| Technology | Version | Purpose | Rationale | Integration Method |
|---|---|---|---|---|
| **pyshacl** | 0.26+ | SHACL constraint validation for RDF data | W3C standard for KG validation; appears in KE job descriptions; validates ontology entries and LLM output | `pip install pyshacl` — pure Python, depends on rdflib (already installed) |

**One new dependency.** Everything else reuses existing infrastructure.

---

## 4. Data Models and Schema Changes

### 4.1 Cognitive Bias Ontology (`cognitive-bias.ttl`)

**Purpose:** Formal OWL taxonomy of cognitive biases and logical fallacies with academic-source grounding.
**Integration:** Imports `newsanalyzer.ttl`. Loaded by existing `OWLReasoner` alongside the current ontology.

#### Class Hierarchy

```
owl:Thing
├── cb:CognitiveDistortion          # Abstract root
│   ├── cb:CognitiveBias            # Systematic deviation from rationality
│   │   ├── cb:ConfirmationBias
│   │   ├── cb:AnchoringBias
│   │   ├── cb:FramingEffect
│   │   ├── cb:AvailabilityHeuristic
│   │   └── cb:BandwagonEffect
│   │
│   └── cb:LogicalFallacy           # Error in reasoning
│       ├── cb:FormalFallacy
│       │   ├── cb:AffirmingTheConsequent
│       │   └── cb:DenyingTheAntecedent
│       │
│       └── cb:InformalFallacy
│           ├── cb:AdHominem
│           ├── cb:StrawMan
│           ├── cb:FalseDilemma
│           ├── cb:SlipperySlope
│           ├── cb:AppealToAuthority
│           ├── cb:RedHerring
│           ├── cb:CircularReasoning
│           └── cb:HastyGeneralization
│
├── cb:DetectionPattern             # How to identify a distortion
├── cb:AcademicSource               # Citable reference
└── cb:BiasAnnotation               # Instance of detected bias
```

**Initial scope: 13 distortions** (5 cognitive biases + 8 logical fallacies)

#### Key Properties

| Property | Domain | Range | Purpose |
|---|---|---|---|
| `cb:hasDefinition` | CognitiveDistortion | xsd:string | Formal definition text |
| `cb:hasAcademicSource` | CognitiveDistortion | AcademicSource | Traceability to cited work |
| `cb:hasDetectionPattern` | CognitiveDistortion | DetectionPattern | How to detect in text |
| `cb:relatedTo` | CognitiveDistortion | CognitiveDistortion | Relationships between biases |
| `cb:oppositeOf` | CognitiveDistortion | CognitiveDistortion | Contrasting distortions |
| `cb:detectedIn` | BiasAnnotation | xsd:string | Text excerpt where bias found |
| `cb:hasDistortionType` | BiasAnnotation | CognitiveDistortion | Which bias was detected |
| `cb:hasConfidence` | BiasAnnotation | xsd:float | Detection confidence [0,1] |
| `cb:hasExplanation` | BiasAnnotation | xsd:string | Why this is a bias |
| `cb:sourceAuthor` | AcademicSource | xsd:string | Author(s) |
| `cb:sourceYear` | AcademicSource | xsd:integer | Publication year |
| `cb:sourceTitle` | AcademicSource | xsd:string | Work title |
| `cb:patternDescription` | DetectionPattern | xsd:string | Natural language criteria |
| `cb:patternExample` | DetectionPattern | xsd:string | Example of bias in text |

#### Academic Sources

- Kahneman, D. (2011). *Thinking, Fast and Slow*
- Tversky, A. & Kahneman, D. (1981). *The Framing of Decisions*
- Nickerson, R. (1998). *Confirmation Bias: A Ubiquitous Phenomenon*
- Walton, D. (2008). *Informal Logic: A Pragmatic Approach*

### 4.2 SHACL Shapes (`cognitive-bias-shapes.ttl`)

**Purpose:** Formal validation constraints ensuring ontology entries and LLM output conform to expected structure.

**Key Shapes:**

- `CognitiveDistortionShape` — every distortion MUST have: definition (1+), academic source (1+), detection pattern (1+)
- `BiasAnnotationShape` — every annotation MUST have: exactly 1 distortion type, text excerpt (1+), confidence [0,1]
- `AcademicSourceShape` — every source MUST have: author (1+), year (1), title (1)

### 4.3 Bias Evaluation Gold Dataset

**Two-tier approach:**

**Tier 1: Synthetic biased articles (automated)**
- Take EVAL-1 neutral synthetic articles
- LLM rewrites with intentionally injected biases
- Gold annotation = the bias type injected
- Automated ground truth for known biases

**Tier 2: Real-world excerpts (curated)**
- 20–30 real news excerpts with identifiable biases
- Human-annotated with bias type, text span, explanation
- More ecologically valid, smaller and more subjective

**Gold annotation schema:**
```yaml
- vars:
    article_text: "The senator's reckless policy..."
    biases:
    - type: framing_effect
      excerpt: "reckless policy"
      explanation: "Loaded language frames the policy negatively without evidence"
      academic_source: "Tversky & Kahneman, 1981"
    metadata:
      id: eval-3-bias-001
      source: synthetic | curated
      difficulty: easy | medium | hard
      bias_count: 1
```

### 4.4 Schema Integration Strategy

- **New Tables:** None — ontology is in-memory RDF, not PostgreSQL
- **New Files:** `cognitive-bias.ttl`, `cognitive-bias-shapes.ttl`, gold YAML files
- **Modified Tables:** None
- **Backward Compatibility:** Existing `newsanalyzer.ttl` imported, not modified

---

## 5. Component Architecture

### 5.1 Component 1: Cognitive Bias Ontology Loader

**Responsibility:** Load `cognitive-bias.ttl` alongside `newsanalyzer.ttl`. SPARQL access to definitions.
**Integration:** Extends existing `OWLReasoner` class — new methods, same singleton.

**Key Interfaces:**
- `load_bias_ontology(path)` — parse and merge into graph
- `get_distortion_definition(distortion_uri)` — SPARQL → definition + pattern + source
- `list_distortions(category?)` — all biases, fallacies, or all
- `get_related_distortions(distortion_uri)` — related/opposite via properties

### 5.2 Component 2: SHACL Validator

**Responsibility:** Validate RDF data against SHACL shapes. Dual purpose: ontology validation + LLM output validation.
**Integration:** New class `SHACLValidator` in own file. Separate from OWL reasoning.

**Key Interfaces:**
- `validate_ontology()` — validate cognitive-bias.ttl against shapes
- `validate_annotations(annotations_graph)` — validate LLM output as RDF
- `get_validation_report()` — human-readable conformance report

**Dependencies:** `pyshacl`, `rdflib.Graph`

### 5.3 Component 3: Ontology-Grounded Bias Detector

**Responsibility:** Core neuro-symbolic component. SPARQL query → grounded prompt → Claude → parse → SHACL validate.
**Integration:** Implements `/fallacies/detect` stub. New `/eval/bias/detect` for Promptfoo.

**Detection flow:**
1. SPARQL query → retrieve definitions from ontology
2. Build prompt grounded in formal definitions + academic sources
3. Call Claude with structured output instructions
4. Parse response into BiasAnnotation objects
5. SHACL-validate annotations
6. Return validated results

**Pattern:** Follows `LLMEntityExtractor` — async detect(), structured prompt, JSON parsing, configurable model/rate-limit.

### 5.4 Component 4: Bias Evaluation Scorer

**Responsibility:** Promptfoo custom assertion scoring bias detection quality against gold.
**Pattern:** Follows `entity_scorer.py` — `get_assert()` entry point, `namedScores` for `derivedMetrics`.

**Scoring strategy:**
- Match on `distortion_type` (exact — controlled vocabulary from ontology)
- Partial credit (0.5) for correct category (e.g., "informal_fallacy" when gold says "ad_hominem")
- Per-distortion-type breakdown in namedScores

### 5.5 Component 5: Bias Promptfoo Provider

**Responsibility:** Promptfoo provider calling `/eval/bias/detect`.
**Pattern:** Follows `llm_provider.py` — `call_api()` entry point.

### 5.6 Component 6: Synthetic Bias Article Generator

**Responsibility:** Generate articles with intentionally injected biases for gold dataset.
**Pattern:** Standalone script (like `derive_gold.py`). Uses ontology definitions to guide injection.

### 5.7 Component Interaction Diagram

```mermaid
graph TD
    subgraph "Evaluation Time"
        PF[Promptfoo CLI] --> BP[Bias Provider]
        BP --> BDE[/eval/bias/detect]
        PF --> BS[Bias Scorer]
        BS --> Gold[Gold Dataset YAML]
    end

    subgraph "Runtime"
        BDE --> BD[BiasDetector]
        FD[/fallacies/detect] --> BD
        BD -->|1. SPARQL| OWL[OWLReasoner]
        OWL --> CBO[cognitive-bias.ttl]
        OWL --> NA[newsanalyzer.ttl]
        BD -->|2. Grounded prompt| Claude[Claude API]
        BD -->|3. Validate| SV[SHACL Validator]
        SV --> Shapes[cognitive-bias-shapes.ttl]
    end

    subgraph "Dataset Creation"
        Gen[Bias Article Generator] --> OWL
        Gen --> Claude
        Gen --> Gold
    end
```

---

## 6. API Design

### 6.1 Endpoint 1: `POST /fallacies/detect` (Implement Existing Stub)

Existing Pydantic models, no response shape changes.

**Request:**
```json
{ "text": "...", "context": "optional" }
```

**Response:**
```json
{
  "fallacies": [{ "type": "ad_hominem", "excerpt": "...", "explanation": "...", "confidence": 0.82 }],
  "biases": [{ "type": "framing_effect", "excerpt": "...", "explanation": "...", "confidence": 0.75 }],
  "overall_quality_score": 0.45
}
```

### 6.2 Endpoint 2: `POST /eval/bias/detect` (New — Promptfoo Provider Target)

**Request:**
```json
{
  "text": "...",
  "distortion_types": null,
  "confidence_threshold": 0.0,
  "include_ontology_metadata": true
}
```

**Response:**
```json
{
  "annotations": [{
    "distortion_type": "framing_effect",
    "category": "cognitive_bias",
    "excerpt": "...",
    "explanation": "...",
    "confidence": 0.75,
    "ontology_metadata": {
      "definition": "...",
      "academic_source": "Tversky & Kahneman, 1981",
      "detection_pattern": "..."
    }
  }],
  "total_count": 1,
  "distortions_checked": ["confirmation_bias", "framing_effect", "..."]
}
```

### 6.3 Endpoint 3: `GET /eval/bias/ontology/stats` (New)

**Response:**
```json
{
  "total_distortions": 13, "cognitive_biases": 5, "logical_fallacies": 8,
  "academic_sources": 4, "detection_patterns": 13,
  "shacl_valid": true, "shacl_violations": []
}
```

### 6.4 Endpoint 4: `POST /eval/bias/ontology/validate` (New)

**Response:**
```json
{
  "conforms": true, "results_count": 0, "violations": [],
  "shapes_evaluated": ["CognitiveDistortionShape", "AcademicSourceShape", "BiasAnnotationShape"]
}
```

### 6.5 Router Registration

```python
app.include_router(eval_bias.router, prefix="/eval/bias", tags=["eval-bias"])
```

---

## 7. Source Tree

### New File Organization

```
reasoning-service/
├── app/
│   ├── main.py                              # MODIFY — register eval_bias router
│   ├── api/
│   │   ├── fallacies.py                     # MODIFY — implement stub
│   │   └── eval/
│   │       └── bias.py                      # NEW — /eval/bias/* endpoints
│   └── services/
│       ├── owl_reasoner.py                  # MODIFY — bias ontology loading + SPARQL
│       ├── shacl_validator.py               # NEW — SHACL validation service
│       └── eval/
│           └── bias_detector.py             # NEW — OntologyGroundedBiasDetector
├── ontology/
│   ├── newsanalyzer.ttl                     # EXISTING — no changes
│   ├── cognitive-bias.ttl                   # NEW — bias/fallacy ontology
│   └── cognitive-bias-shapes.ttl            # NEW — SHACL shapes
├── tests/
│   ├── test_shacl_validator.py              # NEW
│   └── services/eval/
│       └── test_bias_detector.py            # NEW

eval/
├── promptfoo-bias.yaml                      # NEW — bias evaluation config
├── providers/
│   └── bias_provider.py                     # NEW
├── assertions/
│   ├── bias_scorer.py                       # NEW
│   └── test_bias_scorer.py                  # NEW
└── datasets/
    └── bias/                                # NEW
        ├── synthetic_biased.yaml
        ├── curated_biased.yaml
        └── scripts/
            └── generate_biased_articles.py  # NEW
```

**Summary:** 12 new files, 3 modified files, 0 existing files changed structurally.

---

## 8. Infrastructure and Deployment

**No infrastructure changes.** EVAL-3 is entirely within the existing reasoning-service container.

- Docker image: adds `pyshacl` to `requirements.txt` (+~5MB)
- Startup: +~50–100ms (loading second .ttl file)
- Memory: +~2–5MB (small ontology)
- New ports: None
- Database changes: None
- Rollback: Standard git revert + redeploy

Ontology `.ttl` files ship inside the Docker image. No external file mounts needed.

---

## 9. Coding Standards

### Enhancement-Specific Standards

- **Ontology URIs:** Use `cb:` namespace prefix (`http://newsanalyzer.org/ontology/cognitive-bias#`). Never mix into `na:` namespace.
- **SPARQL queries as constants:** Module-level `SPARQL_*` string constants, not inline construction. Testable and auditable.
- **Academic source required:** Every `CognitiveDistortion` MUST have `cb:hasAcademicSource`. SHACL enforces; dev agent must also follow.
- **LLM response parsing:** Follow `LLMEntityExtractor._parse_response()` pattern — robust JSON extraction with fallbacks.
- **Ontology loading failures:** Warning, not error. Service must not crash. Existing functionality keeps working.
- **Turtle formatting:** 2-space indentation, `rdfs:comment` annotations, follow `newsanalyzer.ttl` patterns.

### Critical Integration Rules

- `/fallacies/detect` response MUST match existing `FallacyDetectionResponse` Pydantic model
- New `OWLReasoner` methods must not change behavior of existing methods
- Never expose ontology URIs or SPARQL queries in error responses
- Use existing logging patterns (`logger = logging.getLogger(__name__)`)

---

## 10. Testing Strategy

### Test Categories

| Category | Count (est.) | Key Tests |
|---|---|---|
| SHACL validator | ~10 | Valid ontology passes; missing property fails; invalid range fails |
| Bias detector | ~12 | SPARQL returns definitions; prompt contains ontology data; LLM parse; dry-run; SHACL validates output |
| Bias scorer | ~10 | Perfect detection; no detection; FPs; partial credit; per-type breakdown |
| Ontology validation | ~7 | 13 distortions present; SHACL conforms; hierarchy correct; sources complete |
| Integration/regression | ~5 | E2E detection; existing endpoints unaffected; graceful degradation |
| Gold dataset validation | ~5 | Schema valid; types from ontology; unique IDs |

**Estimated total: ~40–50 new tests.** Combined with existing: ~900+ project-wide.

### Regression

- Full existing test suite must pass after each story
- EVAL-2 entity evaluation produces identical results (separate config)
- GitHub Actions runs pytest automatically

---

## 11. Security

Minimal security impact — internal evaluation tooling.

- No new authentication (reasoning service is internal)
- No new secrets (reuses `ANTHROPIC_API_KEY`)
- No PII in ontology or gold datasets
- SPARQL injection: N/A — queries are hardcoded constants
- pyshacl: well-maintained, pure Python, standard dependency scanning
- Ontology files ship in Docker image (same protection as source code)

---

## 12. Story Breakdown

| Story | Name | Scope | Effort |
|---|---|---|---|
| **EVAL-3.1** | Cognitive Bias OWL Ontology + SHACL Shapes | `cognitive-bias.ttl` (13 distortions) + `cognitive-bias-shapes.ttl` + ontology validation tests | ~1.5 days |
| **EVAL-3.2** | SHACL Validator Service + Reasoner Extension | `SHACLValidator` class, extend `OWLReasoner`, ontology stats endpoint | ~1 day |
| **EVAL-3.3** | Ontology-Grounded Bias Detector | `OntologyGroundedBiasDetector`, implement `/fallacies/detect`, new `/eval/bias/detect` | ~2 days |
| **EVAL-3.4** | Bias Gold Dataset + Evaluation Harness | Synthetic article generator, gold dataset, Promptfoo config + scorer + provider | ~2 days |
| **EVAL-3.5** | Evaluation Execution + Methodology Writeup | Run baseline, analyze results, methodology doc, update EVAL-DASH | ~1 day |

**Total: ~7.5 days.** Dependency chain: EVAL-3.1 → 3.2 → 3.3 → 3.4 → 3.5

---

## 13. LLM Cost Estimate

| Metric | Entity Extraction (EVAL-2) | Bias Detection (EVAL-3) |
|---|---|---|
| Tokens per article | ~600–900 | ~1,600–2,400 |
| Cost per article | ~$0.004 | ~$0.008–$0.012 |
| Cost for 50 articles | ~$0.20 | ~$0.40–$0.60 |
| Cost for 100 articles | ~$0.40 | ~$0.80–$1.20 |

~2–3× more expensive due to ontology definitions in prompt. Still negligible for portfolio project.

---

## 14. Portfolio Output

After EVAL-3 completion, the project demonstrates:

**AI Evaluation Track:**
- Bias/fairness testing with quantitative metrics (Tier 1 JD skill)
- LLM evaluation framework (Promptfoo)
- Gold dataset creation and curation
- Responsible AI / explainable AI patterns

**Knowledge Engineering Track:**
- Formal OWL ontology with academic grounding
- SPARQL query design for knowledge retrieval
- SHACL validation for data quality governance
- Neuro-symbolic AI (LLM + formal knowledge)
- W3C Semantic Web standards (RDF, OWL, SPARQL, SHACL)

**Shareable artifacts:**
- `/evaluation/methodology` page (extended with bias detection section)
- Ontology file viewable in Protégé
- SHACL validation report demonstrating KG governance
- Side-by-side bias detection results with P/R/F1

---

*End of EVAL-3 Architecture Document*
