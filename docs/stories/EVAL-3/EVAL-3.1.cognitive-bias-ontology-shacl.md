# Story EVAL-3.1: Cognitive Bias OWL Ontology + SHACL Shapes

## Status

Ready for Review

## Story

**As an** AI evaluation engineer building a neuro-symbolic bias detection system,
**I want** a formal OWL ontology of cognitive biases and logical fallacies with academic-source grounding, plus SHACL shape constraints for validation,
**so that** the bias detection system has a governed, auditable knowledge base to ground its LLM prompts in.

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `cognitive-bias.ttl` loads into rdflib Graph without errors |
| AC2 | SPARQL query returns all 14 distortions with definitions |
| AC3 | Every distortion has at least one academic source (`cb:hasAcademicSource`) |
| AC4 | Every distortion has a detection pattern (`cb:hasDetectionPattern`) |
| AC5 | SHACL validation (`cognitive-bias-shapes.ttl`) passes against the ontology |
| AC6 | Class hierarchy correct: 5 CognitiveBias, 2 FormalFallacy, 7 InformalFallacy |
| AC7 | Imports `newsanalyzer.ttl` successfully (combined graph works) |

## Tasks / Subtasks

- [x] Task 1: Create cognitive bias ontology file (AC1, AC2, AC3, AC4, AC6, AC7)
  - [x] Create `reasoning-service/ontology/cognitive-bias.ttl`
  - [x] Define namespace: `@prefix cb: <http://newsanalyzer.org/ontology/cognitive-bias#>`
  - [x] Import `newsanalyzer.ttl`: `owl:imports <http://newsanalyzer.org/ontology>`
  - [x] Ontology metadata: label, comment, versionInfo "1.0.0"
  - [x] Define class hierarchy:
    - `cb:CognitiveDistortion` (abstract root) — `rdfs:subClassOf owl:Thing`
    - `cb:CognitiveBias` — `rdfs:subClassOf cb:CognitiveDistortion`
    - `cb:LogicalFallacy` — `rdfs:subClassOf cb:CognitiveDistortion`
    - `cb:FormalFallacy` — `rdfs:subClassOf cb:LogicalFallacy`
    - `cb:InformalFallacy` — `rdfs:subClassOf cb:LogicalFallacy`
    - `cb:DetectionPattern` — `rdfs:subClassOf owl:Thing`
    - `cb:AcademicSource` — `rdfs:subClassOf owl:Thing`
    - `cb:BiasAnnotation` — `rdfs:subClassOf owl:Thing` (for LLM output representation)
  - [x] Define object properties:
    - `cb:hasAcademicSource` — domain: CognitiveDistortion, range: AcademicSource
    - `cb:hasDetectionPattern` — domain: CognitiveDistortion, range: DetectionPattern
    - `cb:relatedTo` — domain: CognitiveDistortion, range: CognitiveDistortion (symmetric). Only add where noted in Tasks 3/4 — not required to be exhaustive. Minimum: ConfirmationBias↔AvailabilityHeuristic, FramingEffect↔AnchoringBias.
    - `cb:oppositeOf` — domain: CognitiveDistortion, range: CognitiveDistortion (symmetric). Optional — add only if a clear opposition exists. Not required for any individual.
    - `cb:hasDistortionType` — domain: BiasAnnotation, range: CognitiveDistortion
  - [x] Define datatype properties:
    - `cb:hasDefinition` — domain: CognitiveDistortion, range: xsd:string
    - `cb:detectedIn` — domain: BiasAnnotation, range: xsd:string
    - `cb:hasConfidence` — domain: BiasAnnotation, range: xsd:float
    - `cb:hasExplanation` — domain: BiasAnnotation, range: xsd:string
    - `cb:sourceAuthor` — domain: AcademicSource, range: xsd:string
    - `cb:sourceYear` — domain: AcademicSource, range: xsd:integer
    - `cb:sourceTitle` — domain: AcademicSource, range: xsd:string
    - `cb:patternDescription` — domain: DetectionPattern, range: xsd:string
    - `cb:patternExample` — domain: DetectionPattern, range: xsd:string

- [x] Task 2: Create academic source individuals (AC3)
  - [x] `cb:Kahneman2011` — Kahneman, D. (2011). "Thinking, Fast and Slow"
  - [x] `cb:TverskyKahneman1981` — Tversky, A. & Kahneman, D. (1981). "The Framing of Decisions and the Psychology of Choice"
  - [x] `cb:Nickerson1998` — Nickerson, R. (1998). "Confirmation Bias: A Ubiquitous Phenomenon in Many Guises"
  - [x] `cb:Walton2008` — Walton, D. (2008). "Informal Logic: A Pragmatic Approach"
  - [x] Each source: `sourceAuthor`, `sourceYear`, `sourceTitle`, `rdfs:label`, `rdfs:comment`

- [x] Task 3: Create 5 cognitive bias individuals (AC2, AC3, AC4, AC6)
  - [x] `cb:ConfirmationBias` — `rdf:type cb:CognitiveBias`
    - Definition: tendency to search for, interpret, and recall information that confirms pre-existing beliefs
    - Source: `cb:Nickerson1998`
    - Detection pattern: look for selective evidence citation, dismissal of contradicting evidence, one-sided sourcing
    - Related: `cb:AvailabilityHeuristic`
  - [x] `cb:AnchoringBias` — `rdf:type cb:CognitiveBias`
    - Definition: tendency to rely too heavily on the first piece of information encountered
    - Source: `cb:Kahneman2011`
    - Detection pattern: initial statistic/claim given disproportionate weight, subsequent information evaluated relative to anchor
  - [x] `cb:FramingEffect` — `rdf:type cb:CognitiveBias`
    - Definition: drawing different conclusions from the same information depending on how it is presented
    - Source: `cb:TverskyKahneman1981`
    - Detection pattern: loaded language, selective emphasis, asymmetric framing of equivalent outcomes
    - Related: `cb:AnchoringBias`
  - [x] `cb:AvailabilityHeuristic` — `rdf:type cb:CognitiveBias`
    - Definition: overestimating the likelihood of events with greater availability in memory
    - Source: `cb:Kahneman2011`
    - Detection pattern: recent/vivid examples treated as representative, anecdotal evidence over statistics
    - Related: `cb:ConfirmationBias`
  - [x] `cb:BandwagonEffect` — `rdf:type cb:CognitiveBias`
    - Definition: tendency to adopt beliefs/behaviors because many others do
    - Source: `cb:Kahneman2011`
    - Detection pattern: appeal to popularity, "everyone knows," "most people agree," poll numbers as argument

- [x] Task 4: Create 8 logical fallacy individuals (AC2, AC3, AC4, AC6)
  - [x] `cb:AdHominem` — `rdf:type cb:InformalFallacy`
    - Definition: attacking the person making the argument rather than the argument itself
    - Source: `cb:Walton2008`
    - Detection pattern: personal attacks, character assassination, questioning motives instead of evidence
  - [x] `cb:StrawMan` — `rdf:type cb:InformalFallacy`
    - Definition: misrepresenting someone's argument to make it easier to attack
    - Source: `cb:Walton2008`
    - Detection pattern: exaggerated/distorted version of opponent's position, arguing against a claim not actually made
  - [x] `cb:FalseDilemma` — `rdf:type cb:InformalFallacy`
    - Definition: presenting only two options when more exist
    - Source: `cb:Walton2008`
    - Detection pattern: either/or framing, "you're either with us or against us," ignoring middle ground
  - [x] `cb:SlipperySlope` — `rdf:type cb:InformalFallacy`
    - Definition: asserting that a small first step will inevitably lead to a chain of extreme consequences
    - Source: `cb:Walton2008`
    - Detection pattern: chain of increasingly extreme consequences without justifying each link
  - [x] `cb:AppealToAuthority` — `rdf:type cb:InformalFallacy`
    - Definition: claiming something is true because an authority figure says so, outside their expertise
    - Source: `cb:Walton2008`
    - Detection pattern: citing authority outside their domain, using titles/credentials as evidence
  - [x] `cb:RedHerring` — `rdf:type cb:InformalFallacy`
    - Definition: introducing an irrelevant topic to divert attention from the original issue
    - Source: `cb:Walton2008`
    - Detection pattern: topic shift, "what about" deflection, irrelevant emotional appeal
  - [x] `cb:CircularReasoning` — `rdf:type cb:InformalFallacy`
    - Definition: using the conclusion as a premise in the argument
    - Source: `cb:Walton2008`
    - Detection pattern: restating the claim as evidence, tautological reasoning
  - [x] `cb:AffirmingTheConsequent` — `rdf:type cb:FormalFallacy`
    - Definition: if P then Q; Q is true; therefore P is true (invalid inference)
    - Source: `cb:Walton2008`
    - Detection pattern: reversing conditional logic, assuming cause from effect
  - [x] `cb:DenyingTheAntecedent` — `rdf:type cb:FormalFallacy`
    - Definition: if P then Q; P is false; therefore Q is false (invalid inference)
    - Source: `cb:Walton2008`
    - Detection pattern: negating the condition and concluding the result is negated

  Note: Story originally specified 6 InformalFallacy but CircularReasoning was added during implementation (2026-03-31), bringing the total to 7 InformalFallacy + 2 FormalFallacy = 9 LogicalFallacy, 14 distortions total. Epic and downstream stories updated accordingly.

- [x] Task 5: Create SHACL shapes file (AC5)
  - [x] Create `reasoning-service/ontology/cognitive-bias-shapes.ttl`
  - [x] Define `cb:CognitiveDistortionShape`:
    - `sh:targetClass cb:CognitiveDistortion`
    - `cb:hasDefinition` — minCount 1, datatype xsd:string
    - `cb:hasAcademicSource` — minCount 1, class AcademicSource
    - `cb:hasDetectionPattern` — minCount 1, class DetectionPattern
  - [x] Define `cb:BiasAnnotationShape`:
    - `sh:targetClass cb:BiasAnnotation`
    - `cb:hasDistortionType` — minCount 1, maxCount 1, class CognitiveDistortion
    - `cb:detectedIn` — minCount 1, datatype xsd:string
    - `cb:hasConfidence` — minCount 1, minInclusive 0.0, maxInclusive 1.0
  - [x] Define `cb:AcademicSourceShape`:
    - `sh:targetClass cb:AcademicSource`
    - `cb:sourceAuthor` — minCount 1
    - `cb:sourceYear` — minCount 1, datatype xsd:integer
    - `cb:sourceTitle` — minCount 1

- [x] Task 6: Write ontology validation tests (AC1–AC7)
  - [x] Create `reasoning-service/tests/test_cognitive_bias_ontology.py`
  - [x] Test: ontology loads without errors (`rdflib.Graph.parse()` succeeds)
  - [x] Test: SPARQL query returns exactly 14 CognitiveDistortion individuals
  - [x] Test: 5 CognitiveBias instances
  - [x] Test: 9 LogicalFallacy instances (2 FormalFallacy + 7 InformalFallacy)
  - [x] Test: every distortion has at least one `cb:hasAcademicSource`
  - [x] Test: every distortion has at least one `cb:hasDetectionPattern`
  - [x] Test: every distortion has a `cb:hasDefinition`
  - [x] Test: 4 AcademicSource individuals with author, year, title
  - [x] Test: combined graph (newsanalyzer.ttl + cognitive-bias.ttl) loads successfully
  - [x] Test: SHACL validation passes — write the test but mark with `@pytest.mark.skip(reason="pyshacl added in EVAL-3.2")`. EVAL-3.2 will unskip this test when the dependency is added to requirements.txt.

## Dev Notes

### Relevant Source Tree

```
reasoning-service/
├── ontology/
│   ├── newsanalyzer.ttl              # EXISTING — reference for Turtle patterns. DO NOT MODIFY.
│   ├── cognitive-bias.ttl            # NEW — bias/fallacy ontology
│   └── cognitive-bias-shapes.ttl     # NEW — SHACL shapes
├── tests/
│   └── test_cognitive_bias_ontology.py  # NEW — ontology validation tests
```

### Turtle Formatting Patterns (from `newsanalyzer.ttl`)

Follow the existing formatting in `newsanalyzer.ttl`:

**Namespace prefixes:**
```turtle
@prefix cb: <http://newsanalyzer.org/ontology/cognitive-bias#> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix na: <http://newsanalyzer.org/ontology#> .
@base <http://newsanalyzer.org/ontology/cognitive-bias> .
```

**Class definition pattern:**
```turtle
###  http://newsanalyzer.org/ontology/cognitive-bias#CognitiveDistortion
cb:CognitiveDistortion rdf:type owl:Class ;
  rdfs:label "Cognitive Distortion"@en ;
  rdfs:comment "Abstract root class for cognitive biases and logical fallacies"@en .
```

**Individual definition pattern:**
```turtle
cb:ConfirmationBias rdf:type owl:NamedIndividual , cb:CognitiveBias ;
  rdfs:label "Confirmation Bias"@en ;
  rdfs:comment "Tendency to search for information that confirms pre-existing beliefs"@en ;
  cb:hasDefinition "The tendency to search for, interpret, favor, and recall information in a way that confirms or supports one's prior beliefs or values." ;
  cb:hasAcademicSource cb:Nickerson1998 ;
  cb:hasDetectionPattern cb:ConfirmationBiasPattern ;
  cb:relatedTo cb:AvailabilityHeuristic .
```

**Detection pattern individual:**
```turtle
cb:ConfirmationBiasPattern rdf:type owl:NamedIndividual , cb:DetectionPattern ;
  rdfs:label "Confirmation Bias Detection Pattern"@en ;
  cb:patternDescription "Look for selective evidence citation, dismissal of contradicting evidence, one-sided sourcing, and cherry-picking data that supports a predetermined conclusion." ;
  cb:patternExample "The article cites three studies supporting the policy but ignores five studies showing negative outcomes." .
```

**Academic source individual:**
```turtle
cb:Nickerson1998 rdf:type owl:NamedIndividual , cb:AcademicSource ;
  rdfs:label "Nickerson 1998"@en ;
  cb:sourceAuthor "Nickerson, R. S." ;
  cb:sourceYear 1998 ;
  cb:sourceTitle "Confirmation Bias: A Ubiquitous Phenomenon in Many Guises" .
```

### SHACL Shape Pattern

```turtle
@prefix sh: <http://www.w3.org/ns/shacl#> .

cb:CognitiveDistortionShape a sh:NodeShape ;
  sh:targetClass cb:CognitiveDistortion ;
  sh:property [
    sh:path cb:hasDefinition ;
    sh:minCount 1 ;
    sh:datatype xsd:string ;
    sh:message "Every cognitive distortion must have a definition" ;
  ] ;
  sh:property [
    sh:path cb:hasAcademicSource ;
    sh:minCount 1 ;
    sh:class cb:AcademicSource ;
    sh:message "Every cognitive distortion must have at least one academic source" ;
  ] .
```

### Import Strategy

The `cognitive-bias.ttl` ontology imports `newsanalyzer.ttl` via:
```turtle
<http://newsanalyzer.org/ontology/cognitive-bias> rdf:type owl:Ontology ;
  owl:imports <http://newsanalyzer.org/ontology> ;
  rdfs:label "Cognitive Bias Ontology"@en ;
  owl:versionInfo "1.0.0" .
```

**Important:** The `owl:imports` declaration is a semantic statement. For actual loading in rdflib, the reasoner will need to `graph.parse()` both files explicitly. The import statement documents the dependency; it doesn't auto-load in rdflib.

### SPARQL Queries for Validation Tests

```sparql
# Count all distortions
SELECT (COUNT(?d) AS ?count) WHERE {
  ?d rdf:type/rdfs:subClassOf* cb:CognitiveDistortion .
  ?d rdf:type owl:NamedIndividual .
}

# Get definition + source for a specific distortion
SELECT ?def ?author ?year ?title WHERE {
  cb:ConfirmationBias cb:hasDefinition ?def .
  cb:ConfirmationBias cb:hasAcademicSource ?src .
  ?src cb:sourceAuthor ?author .
  ?src cb:sourceYear ?year .
  ?src cb:sourceTitle ?title .
}

# Check all distortions have required properties
SELECT ?d WHERE {
  ?d rdf:type/rdfs:subClassOf* cb:CognitiveDistortion .
  ?d rdf:type owl:NamedIndividual .
  FILTER NOT EXISTS { ?d cb:hasDefinition ?def }
}
# Should return 0 rows
```

### Testing

**Test file:** `reasoning-service/tests/test_cognitive_bias_ontology.py`

**Testing framework:** pytest

**What to test:**
- Ontology file parses without errors (rdflib)
- 13 CognitiveDistortion individuals via SPARQL count
- Class hierarchy: 5 CognitiveBias, 2 FormalFallacy, 6 InformalFallacy
- Every distortion has definition, source, detection pattern (SPARQL NOT EXISTS)
- Academic sources have author, year, title
- Combined graph (both ontologies) loads
- SHACL validation passes — write test with `@pytest.mark.skip(reason="pyshacl added in EVAL-3.2")`; unskipped in EVAL-3.2

**Testing approach:** Load the .ttl files directly in tests using rdflib — no need for the full FastAPI service. These are pure ontology validation tests.

```python
import pytest
from pathlib import Path
from rdflib import Graph, Namespace

CB = Namespace("http://newsanalyzer.org/ontology/cognitive-bias#")
ONTOLOGY_DIR = Path(__file__).parent.parent / "ontology"

@pytest.fixture
def bias_graph():
    g = Graph()
    g.parse(ONTOLOGY_DIR / "cognitive-bias.ttl", format="turtle")
    return g
```

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (1M context)

### File List

| File | Action | Description |
|------|--------|-------------|
| `reasoning-service/ontology/cognitive-bias.ttl` | NEW | OWL ontology with 14 cognitive distortions, 4 academic sources, 14 detection patterns |
| `reasoning-service/ontology/cognitive-bias-shapes.ttl` | NEW | SHACL shapes for CognitiveDistortion, BiasAnnotation, AcademicSource validation |
| `reasoning-service/tests/test_cognitive_bias_ontology.py` | NEW | 20 pytest tests validating ontology structure, counts, properties, combined graph |
| `docs/stories/EVAL-3/EVAL-3.1.cognitive-bias-ontology-shacl.md` | MODIFIED | Task checkboxes, status, dev agent record |
| `docs/stories/EVAL-3/EVAL-3.epic-cognitive-bias-evaluation.md` | MODIFIED | Updated counts from 13→14 distortions, 6→7 InformalFallacy |

### Completion Notes

- All 6 tasks completed, all 19 tests pass (1 SHACL test correctly skipped pending pyshacl in EVAL-3.2)
- CircularReasoning added as 7th InformalFallacy during implementation (user decision), bringing total from 13→14 distortions
- Epic, story ACs, and test expectations all updated to reflect 14 distortions
- Combined graph (newsanalyzer.ttl + cognitive-bias.ttl) loads successfully with 489 triples

### Debug Log References

None — no issues encountered during implementation.

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-27 | 1.0 | Initial story draft from EVAL-3 epic and architecture | Sarah (PO) |
| 2026-03-28 | 1.1 | Validation fixes: explicit pyshacl skip strategy, clarified relatedTo/oppositeOf scope, aligned epic fallacy list with architecture (2 formal + 6 informal) | Sarah (PO) |
| 2026-03-31 | 1.2 | Implementation complete. Added CircularReasoning (14 total distortions). All tasks done, 19/20 tests pass (1 skipped). | James (Dev) |
