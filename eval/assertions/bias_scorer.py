"""
Bias Scorer — Promptfoo custom assertion for bias detection evaluation (EVAL-3.4).

Computes precision, recall, and F1 by comparing detected bias annotations
against gold-standard annotations:

  1. Exact distortion_type match (case-insensitive)  → full TP (1.0)
  2. Same category match (e.g., both informal_fallacy) → partial TP (0.5)
  3. No match → FP (detected) or FN (gold)

Called by Promptfoo as: file://assertions/bias_scorer.py
"""

from __future__ import annotations

from typing import Any


# Map each distortion type to its category for partial credit scoring.
# Must stay in sync with the cognitive bias ontology (14 distortions).
DISTORTION_CATEGORIES = {
    "confirmation_bias": "cognitive_bias",
    "anchoring_bias": "cognitive_bias",
    "framing_effect": "cognitive_bias",
    "availability_heuristic": "cognitive_bias",
    "bandwagon_effect": "cognitive_bias",
    "ad_hominem": "informal_fallacy",
    "straw_man": "informal_fallacy",
    "false_dilemma": "informal_fallacy",
    "slippery_slope": "informal_fallacy",
    "appeal_to_authority": "informal_fallacy",
    "red_herring": "informal_fallacy",
    "circular_reasoning": "informal_fallacy",
    "affirming_the_consequent": "formal_fallacy",
    "denying_the_antecedent": "formal_fallacy",
}

# Minimum F1 to pass the assertion
PASS_THRESHOLD = 0.3

# Partial credit for correct category, wrong specific type
CATEGORY_MATCH_CREDIT = 0.5


def _get_category(distortion_type: str) -> str:
    """Map distortion type to its category for partial credit."""
    return DISTORTION_CATEGORIES.get(distortion_type.lower(), "unknown")


def _find_best_match(
    detected: dict,
    gold_biases: list[dict],
    already_matched: set[int],
) -> tuple[int | None, float]:
    """Find the best matching gold bias for a detected annotation.

    Returns (gold_index, credit) or (None, 0.0) for no match.
    """
    detected_type = detected.get("distortion_type", "").lower()

    # Priority 1: exact type match
    for i, gold in enumerate(gold_biases):
        if i in already_matched:
            continue
        if detected_type == gold.get("type", "").lower():
            return i, 1.0

    # Priority 2: same category match (partial credit)
    detected_cat = _get_category(detected_type)
    for i, gold in enumerate(gold_biases):
        if i in already_matched:
            continue
        gold_cat = _get_category(gold.get("type", ""))
        if detected_cat == gold_cat and detected_cat != "unknown":
            return i, CATEGORY_MATCH_CREDIT

    return None, 0.0


def get_assert(output: Any, context: Any) -> dict:
    """Promptfoo custom assertion entry point.

    Args:
        output: The detection response from the bias provider.
            Expected shape: {"annotations": [...], "total_count": N, ...}
        context: Promptfoo context with vars from the gold YAML.
            Expected: context["vars"]["biases"] = [{type, excerpt, ...}, ...]

    Returns:
        Promptfoo GradingResult dict with pass, score, reason, namedScores.
    """
    # Extract gold and detected
    vars_data = context.get("vars", {}) if isinstance(context, dict) else {}
    gold_biases = vars_data.get("biases", []) or []

    if isinstance(output, dict):
        detected = output.get("annotations", []) or []
    else:
        detected = []

    # Score
    tp = 0.0
    fp = 0.0
    fn = 0.0
    already_matched: set[int] = set()

    # Per-type tracking
    per_type_tp: dict[str, float] = {}
    per_type_fp: dict[str, float] = {}
    per_type_fn: dict[str, float] = {}

    # Match detected annotations against gold
    for det in detected:
        match_idx, credit = _find_best_match(det, gold_biases, already_matched)
        det_type = det.get("distortion_type", "unknown").lower()

        if match_idx is not None and credit > 0:
            tp += credit
            already_matched.add(match_idx)
            per_type_tp[det_type] = per_type_tp.get(det_type, 0) + credit
        else:
            fp += 1.0
            per_type_fp[det_type] = per_type_fp.get(det_type, 0) + 1

    # Unmatched gold = false negatives
    for i, gold in enumerate(gold_biases):
        if i not in already_matched:
            fn += 1.0
            gold_type = gold.get("type", "unknown").lower()
            per_type_fn[gold_type] = per_type_fn.get(gold_type, 0) + 1

    # Compute metrics
    precision = tp / (tp + fp) if (tp + fp) > 0 else (1.0 if fn == 0 else 0.0)
    recall = tp / (tp + fn) if (tp + fn) > 0 else (1.0 if fp == 0 else 0.0)
    f1 = (
        2 * precision * recall / (precision + recall)
        if (precision + recall) > 0
        else 0.0
    )

    # Build namedScores
    named_scores: dict[str, float] = {
        "true_positives": tp,
        "false_positives": fp,
        "false_negatives": fn,
        "Precision": round(precision, 4),
        "Recall": round(recall, 4),
        "F1": round(f1, 4),
    }

    # Add per-type breakdown
    all_types = set(
        list(per_type_tp.keys())
        + list(per_type_fp.keys())
        + list(per_type_fn.keys())
    )
    for t in sorted(all_types):
        named_scores[f"{t}_tp"] = per_type_tp.get(t, 0)
        named_scores[f"{t}_fp"] = per_type_fp.get(t, 0)
        named_scores[f"{t}_fn"] = per_type_fn.get(t, 0)

    return {
        "pass": f1 >= PASS_THRESHOLD,
        "score": round(f1, 4),
        "reason": (
            f"P={precision:.2f} R={recall:.2f} F1={f1:.2f} "
            f"(TP={tp:.1f} FP={fp:.0f} FN={fn:.0f})"
        ),
        "namedScores": named_scores,
    }
