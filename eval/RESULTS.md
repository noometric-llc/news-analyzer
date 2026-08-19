# Evaluation Results

Measured results from the NewsAnalyzer evaluation harness. Every number here is
reproducible from artifacts in this repository — the gold datasets, the scorers,
and the Promptfoo configurations are all public.

Raw run output (`eval/reports/`) is not committed; it contains full model
responses and is large. The aggregate summaries backing this page are archived
verbatim in [`results-archive/`](results-archive/).

---

## 1. Gold dataset composition

| File | Entries | Entity annotations | Source |
|---|---:|---:|---|
| [`datasets/gold/legislative.yaml`](datasets/gold/legislative.yaml) | 53 | 309 | Derived + human-reviewed |
| [`datasets/gold/executive.yaml`](datasets/gold/executive.yaml) | 20 | 125 | Derived + human-reviewed |
| [`datasets/gold/judicial.yaml`](datasets/gold/judicial.yaml) | 15 | 81 | Derived + human-reviewed |
| [`datasets/gold/conll_sample.yaml`](datasets/gold/conll_sample.yaml) | 25 | 87 | CoNLL-2003 (external benchmark) |
| **Total** | **113** | **601** | |

64 entries carry `curated: true` (human-reviewed annotations); 49 carry
`curated: false` (automated derivation only). Note that the 25 CoNLL entries are
counted as curated because their annotations come from the published benchmark,
not because they were hand-annotated here — 39 entries were reviewed by hand.

Every annotation is offset-validated: `article_text[start:end]` must equal the
entity text, spans must not overlap, and entities must be sorted by start offset.
Enforced by [`datasets/scripts/validate_gold.py`](datasets/scripts/validate_gold.py),
run in CI by [`.github/workflows/eval.yml`](../.github/workflows/eval.yml).

---

## 2. Entity extraction — extractor comparison

**Run date:** 2026-04-06 · **Config:** [`promptfooconfig.yaml`](promptfooconfig.yaml) ·
**Scorer:** [`assertions/entity_scorer.py`](assertions/entity_scorer.py) ·
**Archive:** [`results-archive/entity-extraction-baseline-2026-04-06.json`](results-archive/entity-extraction-baseline-2026-04-06.json)

Identical articles, identical gold annotations, identical scorer. The only
variable is the extractor.

| Dataset | Articles | | spaCy `en_core_web_sm` | | | Claude Sonnet | |
|---|---:|---|---:|---:|---:|---:|---:|
| | | | **P** | **R** | **F1** | **P** / **R** | **F1** |
| legislative | 49 | | 0.151 | 0.963 | **0.261** | 0.426 / 0.977 | **0.593** |
| judicial | 13 | | 0.192 | 0.925 | **0.318** | 0.456 / 0.938 | **0.614** |
| executive | 17 | | 0.220 | 0.983 | **0.359** | 0.432 / 1.000 | **0.603** |
| CoNLL-2003 | 47 | | 0.960 | 0.856 | **0.905** | 0.789 / 0.963 | **0.867** |

### Reading this honestly

**On the news corpora, the LLM extractor is decisively better** — the largest
gain is legislative, 0.261 → 0.593 (2.3×). The mechanism is precision, not
recall: spaCy already recalls 92–98% of gold entities but drowns them in false
positives (1,620 FP against 288 TP on legislative). Claude cuts legislative false
positives from 1,620 to 399 while slightly *increasing* recall.

**On CoNLL-2003, spaCy wins — 0.905 vs 0.867.** This is the more interesting
result. CoNLL is the external benchmark neither system was tuned against, and it
is the only dataset here where spaCy's precision is high (0.960). The reversal is
a caution against reading the news-corpus gains as a general statement about
extractor quality: they are a statement about *this domain*, where spaCy's
entity inventory mismatches the gold schema and generates volume.

Any single headline number from this table is a domain claim, not a model claim.

**Article-count caveat:** the counts above are those recorded at run time
(2026-04-06). The gold files have since been extended to 113 entries; the
legislative set was 49 at run time and is 53 now. Numbers have not been
regenerated against the current snapshot.

---

## 3. Cognitive bias detection — ontology grounding A/B

**Run date:** 2026-04-02 · **Configs:**
[`promptfoo-bias.yaml`](promptfoo-bias.yaml) /
[`promptfoo-bias-ungrounded.yaml`](promptfoo-bias-ungrounded.yaml) ·
**Scorer:** [`assertions/bias_scorer.py`](assertions/bias_scorer.py) ·
**Archive:** [`results-archive/bias-grounding-ab-2026-04-02.json`](results-archive/bias-grounding-ab-2026-04-02.json)

An A/B isolating one variable: whether the detector receives formal ontology
definitions of each distortion type. Same 42-article dataset, same prompts, same
scorer, same model. The providers differ only in the grounding block —
[`providers/bias_provider.py`](providers/bias_provider.py) vs
[`providers/bias_provider_ungrounded.py`](providers/bias_provider_ungrounded.py).

| | Grounded | Ungrounded | Δ |
|---|---:|---:|---:|
| Precision | 0.741 | 0.723 | −0.018 |
| Recall | **0.976** | **0.580** | **−0.396** |
| F1 | 0.842 | 0.644 | −0.198 |
| True positives | 40.0 | 23.5 | −16.5 |
| False positives | 14.0 | 9.0 | −5.0 |
| False negatives | 1.0 | 17.0 | +16.0 |
| Total detections | 53 | 31 | −22 |

By difficulty tier (F1):

| | easy | medium | hard |
|---|---:|---:|---:|
| Grounded | 0.800 | 0.844 | 0.893 |
| Ungrounded | 0.630 | 0.708 | 0.591 |

### What this does and does not show

**It shows a large, clean recall effect.** Removing ontology definitions costs
40 percentage points of recall. The ungrounded detector does not find the
distortions; it returns 31 detections where the grounded one returns 53.

**It does not show a hallucination effect.** Precision is essentially unchanged
(0.741 → 0.723) and false positives went *down* in absolute terms (14 → 9).
Fabrication would show up as precision collapse or FP growth. Neither happened.
The correct characterization is that grounding drives *sensitivity* — the
ungrounded model, lacking definitions, declines to classify rather than
inventing classifications.

This is a groundedness experiment, and it is a well-controlled one. It is not
evidence about hallucination rate, and is not presented as such.

Per-distortion-type breakdowns exist in the run output but are not published
here.

---

## 4. Scorer validation

The scorers are themselves tested, including negative paths — degraded inputs
that must cause the scorer to fail rather than pass silently.

- [`assertions/test_entity_scorer.py`](assertions/test_entity_scorer.py) — 35 tests
  covering all six match priorities, type-mismatch partial credit, and boundary
  conditions
- [`assertions/test_bias_scorer.py`](assertions/test_bias_scorer.py) — 14 tests
  including `test_below_threshold_fails` (mismatched categories must produce
  `pass: False`), `test_different_category_no_credit`, and
  `test_detections_with_no_gold`
- [`datasets/bias/scripts/test_gold_integrity.py`](datasets/bias/scripts/test_gold_integrity.py) —
  dataset invariants: unique IDs, valid types, `bias_count` consistency, faithful
  articles carry empty bias lists

### Matching strategy

[`entity_scorer.py`](assertions/entity_scorer.py) uses a six-priority matcher.
Type mismatches receive partial credit (0.5) rather than being scored as a miss,
so that a correctly located entity with the wrong label is not penalized as
harshly as a fabricated one:

| Priority | Rule | Credit |
|---:|---|---:|
| 1 | Exact text + type match | 1.0 |
| 2 | Exact text, type mismatch | 0.5 |
| 3 | Substring containment + type match | 1.0 |
| 4 | Substring containment, type mismatch | 0.5 |
| 5 | Levenshtein ≥ 0.8 + type match | 1.0 |
| 6 | Levenshtein ≥ 0.8, type mismatch | 0.5 |

---

## 5. Reproducing

```bash
cd eval/
npm install
export PROMPTFOO_PYTHON=/path/to/python
npm run eval:all
```

Requires the reasoning service on `localhost:8000`. That service is maintained
separately by Noometric LLC — see the [repository README](../README.md). The gold
datasets, scorers, and configurations needed to audit the methodology are all in
this repository.
